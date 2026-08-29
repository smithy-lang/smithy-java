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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public final class StdioMcpClient extends McpRemoteClient {
    private static final InternalLogger LOG = InternalLogger.getLogger(StdioMcpClient.class);

    private final ProcessBuilder processBuilder;
    private volatile Process process;
    private volatile BufferedReader reader;
    private volatile BufferedWriter writer;
    private final Lock writeLock = new ReentrantLock();
    private Thread responseReaderThread;
    private Thread errorReaderThread;
    private final Map<String, PendingResponse> pendingRequests = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private final String name;

    private StdioMcpClient(Builder builder) {
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

        public StdioMcpClient build() {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("Command must be provided");
            }
            return new StdioMcpClient(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected JsonRpcResponse exchange(JsonRpcRequest request) {
        if (process == null || !process.isAlive()) {
            throw new McpRemoteException("MCP server process is not running");
        }

        // Notifications don't have an ID and don't expect a response
        if (request.getId() == null) {
            String serializedRequest = McpJson.CODEC.serializeToString(request);
            try {
                writeLock.lock();
                LOG.debug("Sending notification: {}", serializedRequest);
                writer.write(serializedRequest);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                LOG.error("Error sending notification to MCP server", e);
                throw new McpRemoteException("Failed to send notification to MCP server", e);
            } finally {
                writeLock.unlock();
            }
            return null;
        }

        String requestId = requestKey(request.getId());
        String serializedRequest = McpJson.CODEC.serializeToString(request);
        var pending = new PendingResponse();
        pendingRequests.put(requestId, pending);

        try {
            writeLock.lock();
            LOG.debug("Sending request ID {}: {}", requestId, serializedRequest);

            writer.write(serializedRequest);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            LOG.error("Error sending request to MCP server", e);
            pendingRequests.remove(requestId);
            throw new McpRemoteException("Failed to send request to MCP server", e);
        } finally {
            writeLock.unlock();
        }

        return pending.await();
    }

    static String requestKey(Document id) {
        return switch (id.type()) {
            case STRING -> "string:" + id.asString();
            case INTEGER, LONG, BIG_INTEGER -> "number:" + id.asBigInteger();
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
                                var output =
                                        McpJson.CODEC.createDeserializer(responseLine.getBytes(StandardCharsets.UTF_8))
                                                .readDocument();
                                if (isNotification(output)) {
                                    notify(output.asShape(JsonRpcRequest.builder()));
                                } else {
                                    JsonRpcResponse response = output.asShape(JsonRpcResponse.builder());

                                    String responseId = requestKey(response.getId());
                                    LOG.debug("Processing response ID: {}", responseId);

                                    PendingResponse pending = pendingRequests.remove(responseId);
                                    if (pending != null) {
                                        pending.complete(response);
                                    } else {
                                        notify(response);
                                    }
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
                            pendingRequests.forEach((id, pending) -> pending
                                    .fail(new McpRemoteException("MCP server connection closed")));
                            pendingRequests.clear();
                        }
                    });

        } catch (IOException e) {
            throw new RuntimeException("Failed to start MCP server: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        running = false;
        if (process != null && process.isAlive()) {
            try {
                pendingRequests.forEach((id, pending) -> pending
                        .fail(new McpRemoteException("MCP server shutting down")));
                pendingRequests.clear();

                if (writer != null) {
                    writer.close();
                }
                if (reader != null) {
                    reader.close();
                }
                if (responseReaderThread != null && responseReaderThread.isAlive()) {
                    responseReaderThread.interrupt();
                }
                if (errorReaderThread != null && errorReaderThread.isAlive()) {
                    errorReaderThread.interrupt();
                }

                process.destroy();
                if (!process.waitFor(5, SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (IOException e) {
                LOG.error("Error shutting down MCP server process", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new McpRemoteException("Interrupted while shutting down MCP server", e);
            }
        }
    }

    @Override
    public String name() {
        return this.name;
    }

    private static final class PendingResponse {
        private final BlockingQueue<Object> result = new ArrayBlockingQueue<>(1);

        void complete(JsonRpcResponse response) {
            if (!result.offer(response)) {
                throw new IllegalStateException("MCP request was already completed");
            }
        }

        void fail(RuntimeException error) {
            if (!result.offer(error)) {
                throw new IllegalStateException("MCP request was already completed");
            }
        }

        JsonRpcResponse await() {
            try {
                return switch (result.take()) {
                    case JsonRpcResponse response -> response;
                    case RuntimeException error -> throw error;
                    default -> throw new IllegalStateException("Unexpected pending MCP response");
                };
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new McpRemoteException("Interrupted while waiting for MCP response", e);
            }
        }
    }
}
