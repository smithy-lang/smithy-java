/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * MCP server using newline-delimited JSON-RPC over standard input and output.
 *
 * <p>Requests execute on virtual threads. Initialization is awaited before additional
 * input is dispatched so protocol negotiation cannot race later requests.
 */
@SmithyUnstableApi
public final class StdioMcpServer implements Server {
    private static final InternalLogger LOG = InternalLogger.getLogger(StdioMcpServer.class);
    private static final byte[] TOOLS_CHANGED = """
            {"jsonrpc":"2.0","method":"notifications/tools/list_changed"}
            """.getBytes(StandardCharsets.UTF_8);

    private final McpEngine engine;
    private final Thread listener;
    private final InputStream input;
    private final OutputStream output;
    private final McpSession session;
    private final ExecutorService requests = Executors.newVirtualThreadPerTaskExecutor();
    private final CountDownLatch done = new CountDownLatch(1);

    StdioMcpServer(StdioMcpServerBuilder builder) {
        engine = builder.engine;
        session = engine.newSession();
        input = builder.input;
        output = builder.output;
        listener = Thread.ofPlatform()
                .name("stdio-dispatcher")
                .daemon()
                .unstarted(() -> {
                    try {
                        listen();
                    } catch (RuntimeException e) {
                        LOG.error("Error handling MCP input", e);
                    } finally {
                        done.countDown();
                    }
                });
    }

    private void listen() {
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final JsonRpcRequest request;
                try {
                    request = McpJson.CODEC.deserializeShape(line, JsonRpcRequest.builder());
                } catch (RuntimeException e) {
                    LOG.error("Error decoding MCP request", e);
                    write(parseError());
                    continue;
                }

                var task = requests.submit(() -> handleRequest(request));
                if (McpMethod.Standard.INITIALIZE.wireName().equals(request.getMethod())) {
                    try {
                        task.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (ExecutionException e) {
                        LOG.error("Error dispatching MCP initialize request", e.getCause());
                    }
                }
            }
        } catch (IOException e) {
            LOG.error("Error reading MCP input", e);
        } finally {
            requests.shutdown();
        }
    }

    private void handleRequest(JsonRpcRequest request) {
        var outcome = engine.execute(request, session, null, McpTransportContext.STDIO);
        var response = engine.encode(outcome);
        if (response != null) {
            write(response);
        }
    }

    public void refreshTools() {
        try {
            synchronized (output) {
                output.write(TOOLS_CHANGED);
                output.flush();
            }
        } catch (IOException e) {
            LOG.error("Failed to write tools-changed notification", e);
        }
    }

    public void addService(String id, Service service) {
        engine.addService(id, service);
        refreshTools();
    }

    public void addRemoteClient(McpRemoteClient client) {
        engine.addRemoteClient(client);
        refreshTools();
    }

    public boolean containsServer(String id) {
        return engine.containsServer(id);
    }

    private void write(SerializableStruct shape) {
        var bytes = McpJson.CODEC.serialize(shape);
        synchronized (output) {
            try {
                if (bytes.hasArray()) {
                    output.write(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
                } else {
                    output.write(ByteBufferUtils.getBytes(bytes));
                }
                output.write('\n');
                output.flush();
            } catch (IOException e) {
                LOG.error("Error writing MCP output", e);
            }
        }
    }

    private JsonRpcResponse parseError() {
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .error(JsonRpcErrorResponse.builder()
                        .code(-32700)
                        .message("Parse error")
                        .build())
                .build();
    }

    @Override
    public void start() {
        engine.bindTransport(this::write, this::write);
        listener.start();
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        requests.shutdownNow();
        engine.close();
        return CompletableFuture.completedFuture(null);
    }

    public void awaitCompletion() throws InterruptedException {
        done.await();
        requests.awaitTermination(30, TimeUnit.SECONDS);
    }

    public static StdioMcpServerBuilder builder() {
        return new StdioMcpServerBuilder();
    }
}
