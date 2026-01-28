/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;

public final class StdioProxy extends McpServerProxy {
    private static final InternalLogger LOG = InternalLogger.getLogger(StdioProxy.class);
    private static final JsonCodec JSON_CODEC = JsonCodec.builder().build();

    private final ProcessBuilder processBuilder;
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;
    private final Lock writeLock = new ReentrantLock();
    private Thread responseReaderThread;
    private Thread errorReaderThread;
    private final Map<String, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private final String name;

    private StdioProxy(Builder builder) {
        processBuilder = new ProcessBuilder();
        processBuilder.command().add(builder.command);

        if (builder.arguments != null) {
            processBuilder.command().addAll(builder.arguments);
        }

        // Set environment variables if provided
        if (builder.environmentVariables != null) {
            processBuilder.environment().putAll(builder.environmentVariables);
        }

        // Set working directory if provided
        if (builder.workingDirectory != null) {
            processBuilder.directory(builder.workingDirectory);
        }

        this.name = builder.name;

        processBuilder.redirectErrorStream(false); // Keep stderr separate
    }

    public static class Builder {
        private String command;
        private String name;
        private List<String> arguments;
        private Map<String, String> environmentVariables;
        private File workingDirectory;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public Builder arguments(List<String> arguments) {
            this.arguments = arguments;
            return this;
        }

        public Builder environmentVariables(Map<String, String> environmentVariables) {
            this.environmentVariables = environmentVariables;
            return this;
        }

        public Builder workingDirectory(File workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public StdioProxy build() {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("Command must be provided");
            }
            return new StdioProxy(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public CompletableFuture<JsonRpcResponse> rpc(JsonRpcRequest request) {
        if (process == null || !process.isAlive()) {
            CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("MCP server process is not running"));
            return future;
        }

        String requestId = getStringRequestId(request.getId());
        CompletableFuture<JsonRpcResponse> responseFuture = new CompletableFuture<>();
        pendingRequests.put(requestId, responseFuture);

        try {
            writeLock.lock();
            String serializedRequest = JSON_CODEC.serializeToString(request);
            LOG.debug("Sending request ID {}: {}", requestId, serializedRequest);

            writer.write(serializedRequest);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            LOG.error("Error sending request to MCP server", e);
            pendingRequests.remove(requestId);
            responseFuture.completeExceptionally(
                    new RuntimeException("Failed to send request to MCP server: " + e.getMessage(), e));
        } finally {
            writeLock.unlock();
        }

        return responseFuture;
    }

    private String getStringRequestId(Document id) {
        return switch (id.type()) {
            case STRING -> id.asString();
            case INTEGER -> Integer.toString(id.asInteger());
            default -> throw new IllegalStateException("Unexpected value: " + id.type());
        };
    }

    @Override
    public synchronized void start() {
        if (process != null && process.isAlive()) {
            return;
        }
        try {
            process = processBuilder.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            running = true;

            // Start a thread to consume stderr so it doesn't block
            errorReaderThread = Thread.ofVirtual().start(() -> {
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        LOG.debug("MCP server stderr: {}", line);
                    }
                } catch (IOException e) {
                    LOG.debug("Error reading MCP server stderr", e);
                }
            });

            // Start a thread to read responses asynchronously
            responseReaderThread = Thread.ofVirtual()
                    .name("mcp-response-reader")
                    .start(() -> {
                        while (running && process.isAlive()) {
                            try {
                                String responseLine = reader.readLine();
                                if (responseLine == null) {
                                    LOG.debug("Response reader received EOF, exiting");
                                    break;
                                }

                                LOG.debug("Received response: {}", responseLine);
                                JsonRpcResponse response = JsonRpcResponse.builder()
                                        .deserialize(
                                                JSON_CODEC.createDeserializer(
                                                        responseLine.getBytes(StandardCharsets.UTF_8)))
                                        .build();

                                String responseId = getStringRequestId(response.getId());
                                LOG.debug("Processing response ID: {}", responseId);

                                CompletableFuture<JsonRpcResponse> future = pendingRequests.remove(responseId);
                                if (future != null) {
                                    future.complete(response);
                                } else {
                                    notify(response);
                                }
                            } catch (IOException e) {
                                if (running) {
                                    LOG.error("Error reading response from MCP server", e);
                                }
                                break;
                            } catch (Exception e) {
                                LOG.error("Error processing response from MCP server", e);
                            }
                        }

                        // Complete all pending requests with an exception if the reader exits
                        if (!pendingRequests.isEmpty()) {
                            pendingRequests.forEach((id, future) -> future
                                    .completeExceptionally(new RuntimeException("MCP server connection closed")));
                            pendingRequests.clear();
                        }
                    });

        } catch (IOException e) {
            throw new RuntimeException("Failed to start MCP server: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            running = false;
            if (process != null && process.isAlive()) {
                try {
                    // Complete all pending requests with exceptions
                    pendingRequests.forEach((id, future) -> future
                            .completeExceptionally(new RuntimeException("MCP server shutting down")));
                    pendingRequests.clear();

                    // Close streams
                    if (writer != null) {
                        writer.close();
                    }
                    if (reader != null) {
                        reader.close();
                    }

                    // Interrupt the response reader thread
                    if (responseReaderThread != null && responseReaderThread.isAlive()) {
                        responseReaderThread.interrupt();
                    }

                    if (errorReaderThread != null && errorReaderThread.isAlive()) {
                        errorReaderThread.interrupt();
                    }

                    // Destroy the process
                    process.destroy();

                    // Wait for termination with timeout
                    if (!process.waitFor(5, SECONDS)) {
                        // Force kill if it doesn't terminate gracefully
                        process.destroyForcibly();
                    }
                } catch (IOException | InterruptedException e) {
                    LOG.error("Error shutting down MCP server process", e);
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    @Override
    public String name() {
        return this.name;
    }
}
