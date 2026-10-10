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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final Set<String> SUBSCRIPTION_ONLY_NOTIFICATIONS = Set.of(
            "notifications/resources/list_changed",
            "notifications/resources/updated");
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
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private final McpSubscriptionRegistry subscriptions;

    StdioMcpServer(StdioMcpServerBuilder builder) {
        engine = builder.engine;
        session = engine.newSession();
        input = builder.input;
        output = builder.output;
        subscriptions = new McpSubscriptionRegistry(output, this::write, engine.identity());
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

                var method = McpMethod.parse(request.getMethod());
                if (method == McpMethod.Standard.NOTIFICATIONS_CANCELLED && request.getId() == null) {
                    // Handled in arrival order on the reader thread, after any listen it references.
                    var params = request.getParams();
                    subscriptions.cancel(McpHttpBinding.isObject(params) ? params.getMember("requestId") : null);
                    continue;
                }
                var task = requests.submit(() -> handleRequest(request));
                if (method == McpMethod.Standard.INITIALIZE || method == McpMethod.Standard.SUBSCRIPTIONS_LISTEN) {
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
            if (!shuttingDown.get()) {
                LOG.error("Error reading MCP input", e);
            }
        } finally {
            // EOF or read failure: the transport is gone, so subscriptions end without a response.
            subscriptions.clear();
            requests.shutdown();
        }
    }

    private void handleRequest(JsonRpcRequest request) {
        var outcome = engine.execute(request, session, null, McpTransportContext.STDIO);
        if (outcome instanceof McpOutcome.Subscribed subscribed) {
            subscriptions.open(subscribed);
            return;
        }
        var response = engine.encode(outcome);
        if (response != null) {
            write(response);
        }
    }

    /**
     * Tells the client that the tool list changed.
     *
     * <p>A connection that completed {@code initialize} receives an untagged
     * {@code notifications/tools/list_changed}; {@code subscriptions/listen} streams that requested tool
     * changes receive a copy tagged with their subscription id. Nothing is sent to a connection that
     * has neither.
     */
    public void refreshTools() {
        publish(McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED, null);
    }

    /**
     * Writes the untagged copy for a handshake connection and tagged copies for matching subscriptions,
     * under the output monitor so they cannot interleave with an acknowledgment.
     *
     * @param original the remote notification that reported the change, written unchanged to a
     *                 handshake connection; {@code null} for a local change
     */
    private void publish(McpMethod.Standard method, JsonRpcRequest original) {
        synchronized (output) {
            if (session.handshake()) {
                if (original != null) {
                    write(original);
                } else if (method == McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED) {
                    writeToolsChanged();
                }
            }
            subscriptions.deliver(method);
        }
    }

    /**
     * A local change can affect both tools and prompts. Handshake connections keep receiving the
     * single untagged tools notification they always have.
     */
    private void publishLocalChange() {
        synchronized (output) {
            publish(McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED, null);
            subscriptions.deliver(McpMethod.Standard.NOTIFICATIONS_PROMPTS_LIST_CHANGED);
        }
    }

    private void writeToolsChanged() {
        try {
            synchronized (output) {
                output.write(TOOLS_CHANGED);
                output.flush();
            }
        } catch (IOException e) {
            LOG.error("Failed to write tools-changed notification", e);
        }
    }

    /**
     * Applies this connection's delivery policy to catalog events.
     */
    private final class CatalogEvents implements McpSources.CatalogListener {
        @Override
        public void onListChanged(McpMethod.Standard method, JsonRpcRequest original) {
            publish(method, original);
        }

        @Override
        public void onNotification(JsonRpcRequest notification) {
            // 2026-07-28 delivers resource change notifications only on subscriptions, and resource
            // subscriptions are not accepted, so only handshake connections receive them.
            if (SUBSCRIPTION_ONLY_NOTIFICATIONS.contains(notification.getMethod()) && !session.handshake()) {
                return;
            }
            write(notification);
        }
    }

    public void addService(String id, Service service) {
        engine.addService(id, service);
        publishLocalChange();
    }

    public void addRemoteClient(McpRemoteClient client) {
        engine.addRemoteClient(client);
        publishLocalChange();
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
        engine.bindTransport(new CatalogEvents(), this::write);
        listener.start();
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
            subscriptions.completeAll();
            requests.shutdownNow();
            engine.close();
            try {
                input.close();
            } catch (IOException e) {
                LOG.debug("Error closing MCP input during shutdown", e);
            }
            listener.interrupt();
            if (listener.getState() == Thread.State.NEW) {
                done.countDown();
            }
        }
        return CompletableFuture.runAsync(() -> {
            try {
                done.await();
                requests.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new McpRemoteException("Interrupted while shutting down MCP server", e);
            }
        });
    }

    public void awaitCompletion() throws InterruptedException {
        done.await();
        requests.awaitTermination(30, TimeUnit.SECONDS);
    }

    public static StdioMcpServerBuilder builder() {
        return new StdioMcpServerBuilder();
    }
}
