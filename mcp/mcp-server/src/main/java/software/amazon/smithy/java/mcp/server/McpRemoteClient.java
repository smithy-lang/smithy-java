/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.ListPromptsResult;
import software.amazon.smithy.java.mcp.model.ListToolsResult;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.java.mcp.model.ToolInfo;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Blocking client for a remote MCP server.
 *
 * <p>Implementations may use asynchronous I/O internally, but callers observe a single
 * blocking exchange operation suitable for execution on virtual threads.
 */
@SmithyUnstableApi
public abstract class McpRemoteClient implements AutoCloseable {

    private static final InternalLogger LOG = InternalLogger.getLogger(McpRemoteClient.class);
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();
    private static final int MAX_LIST_PAGES = 1_000;

    private final AtomicReference<Consumer<JsonRpcResponse>> responseNotificationConsumer = new AtomicReference<>();
    private final AtomicReference<Consumer<JsonRpcRequest>> requestNotificationConsumer = new AtomicReference<>();
    private final AtomicReference<McpProtocol> negotiatedProtocol = new AtomicReference<>();
    private final AtomicReference<Initialization> initialization = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> initializationFlight = new AtomicReference<>();
    private final AtomicLong initializationGeneration = new AtomicLong();
    private final AtomicReference<CompletableFuture<Void>> sessionRecovery = new AtomicReference<>();
    private final ThreadLocal<McpProtocol> requestProtocol = new ThreadLocal<>();

    public McpPage<ToolInfo> listTools() {
        return listTools(null, new PageTraversal(), McpMetadata.forProtocol(protocol()));
    }

    private McpPage<ToolInfo> listTools(
            String cursor,
            PageTraversal traversal,
            McpMetadata metadata
    ) {
        var currentTraversal = traversal.beforeFetch();
        var params = cursor == null
                ? null
                : Document.of(Map.of("cursor", Document.of(cursor)));
        var response = exchange(JsonRpcRequest.builder()
                .method(McpMethod.Standard.TOOLS_LIST.wireName())
                .id(generateRequestId())
                .jsonrpc("2.0")
                .params(metadata.applyTo(params))
                .build());
        requireSuccess(response, "listing tools");
        var result = response.getResult().asShape(ListToolsResult.builder());
        var items = result.getTools().stream().toList();
        onToolsPage(items);
        return page(
                items,
                result.getNextCursor(),
                (nextCursor, nextTraversal) -> listTools(nextCursor, nextTraversal, metadata),
                currentTraversal);
    }

    public McpPage<PromptInfo> listPrompts() {
        return listPrompts(null, new PageTraversal(), McpMetadata.forProtocol(protocol()));
    }

    private McpPage<PromptInfo> listPrompts(
            String cursor,
            PageTraversal traversal,
            McpMetadata metadata
    ) {
        var currentTraversal = traversal.beforeFetch();
        var params = cursor == null
                ? null
                : Document.of(Map.of("cursor", Document.of(cursor)));
        var response = exchange(JsonRpcRequest.builder()
                .method(McpMethod.Standard.PROMPTS_LIST.wireName())
                .id(generateRequestId())
                .jsonrpc("2.0")
                .params(metadata.applyTo(params))
                .build());
        requireSuccess(response, "listing prompts");
        var result = response.getResult().asShape(ListPromptsResult.builder());
        var items = result.getPrompts().stream().toList();
        return page(
                items,
                result.getNextCursor(),
                (nextCursor, nextTraversal) -> listPrompts(nextCursor, nextTraversal, metadata),
                currentTraversal);
    }

    protected void onToolsPage(List<ToolInfo> tools) {}

    final void initialize(
            Consumer<JsonRpcResponse> responseNotificationConsumer,
            Consumer<JsonRpcRequest> requestNotificationConsumer,
            JsonRpcRequest initializeRequest,
            McpProtocol protocol
    ) {
        initialize(
                responseNotificationConsumer,
                requestNotificationConsumer,
                initializeRequest,
                protocol,
                McpProtocolRegistry.create(List.of(), List.of(), false));
    }

    final void initialize(
            Consumer<JsonRpcResponse> responseNotificationConsumer,
            Consumer<JsonRpcRequest> requestNotificationConsumer,
            JsonRpcRequest initializeRequest,
            McpProtocol protocol,
            McpProtocolRegistry protocols
    ) {
        while (true) {
            if (initialized()) {
                return;
            }
            var active = initializationFlight.get();
            if (active != null) {
                await(active);
                return;
            }

            var created = new CompletableFuture<Void>();
            if (!initializationFlight.compareAndSet(null, created)) {
                continue;
            }
            try {
                if (!initialized()) {
                    var selected = performInitialize(initializeRequest, protocol, protocols);
                    this.responseNotificationConsumer.set(responseNotificationConsumer);
                    this.requestNotificationConsumer.set(requestNotificationConsumer);
                    initialization.set(new Initialization(initializeRequest, protocol, protocols));
                    negotiatedProtocol.set(selected);
                    exchange(JsonRpcRequest.builder()
                            .method(McpMethod.Standard.NOTIFICATIONS_INITIALIZED.wireName())
                            .jsonrpc("2.0")
                            .build());
                    initializationGeneration.incrementAndGet();
                }
                created.complete(null);
                return;
            } catch (RuntimeException e) {
                created.completeExceptionally(e);
                throw e;
            } finally {
                initializationFlight.compareAndSet(created, null);
            }
        }
    }

    private McpProtocol performInitialize(
            JsonRpcRequest initializeRequest,
            McpProtocol requestedProtocol,
            McpProtocolRegistry protocols
    ) {
        var result = withRequestProtocol(
                requestedProtocol,
                () -> Objects.requireNonNull(
                        exchangeForwarded(initializeRequest),
                        "initialize response"));
        requireSuccess(result, "initialization");

        var negotiatedVersion = McpHttpBinding.stringMember(result.getResult(), "protocolVersion");
        if (negotiatedVersion == null) {
            return requestedProtocol;
        }
        try {
            return protocols.require(ProtocolVersion.parse(negotiatedVersion));
        } catch (McpProtocolException e) {
            throw new McpRemoteException(
                    "Remote MCP server negotiated an unsupported protocol version: " + negotiatedVersion,
                    e);
        }
    }

    protected final ProtocolVersion protocolVersion() {
        return protocol().protocolVersion();
    }

    protected final McpProtocol protocol() {
        var requested = requestProtocol.get();
        if (requested != null) {
            return requested;
        }
        var negotiated = negotiatedProtocol.get();
        return negotiated == null
                ? BuiltInProtocols.protocol(ProtocolVersion.defaultVersion())
                : negotiated;
    }

    final <T> T usingProtocol(McpProtocol requestedProtocol, Supplier<T> operation) {
        return withRequestProtocol(requestedProtocol, operation);
    }

    private <T> T withRequestProtocol(McpProtocol protocol, Supplier<T> operation) {
        var previous = requestProtocol.get();
        requestProtocol.set(protocol);
        try {
            return operation.get();
        } finally {
            if (previous == null) {
                requestProtocol.remove();
            } else {
                requestProtocol.set(previous);
            }
        }
    }

    protected final long initializationGeneration() {
        return initializationGeneration.get();
    }

    protected final boolean restartSession(long observedGeneration) {
        var currentInitialization = initialization.get();
        if (currentInitialization == null) {
            return false;
        }

        while (true) {
            if (initializationGeneration.get() != observedGeneration) {
                return true;
            }
            var active = sessionRecovery.get();
            if (active != null) {
                await(active);
                return true;
            }

            var created = new CompletableFuture<Void>();
            if (!sessionRecovery.compareAndSet(null, created)) {
                continue;
            }
            try {
                if (initializationGeneration.get() == observedGeneration) {
                    var selected = performInitialize(
                            currentInitialization.request(),
                            currentInitialization.requestedProtocol(),
                            currentInitialization.protocols());
                    negotiatedProtocol.set(selected);
                    exchange(JsonRpcRequest.builder()
                            .method(McpMethod.Standard.NOTIFICATIONS_INITIALIZED.wireName())
                            .jsonrpc("2.0")
                            .build());
                    initializationGeneration.incrementAndGet();
                }
                created.complete(null);
                return true;
            } catch (RuntimeException e) {
                created.completeExceptionally(e);
                throw e;
            } finally {
                sessionRecovery.compareAndSet(created, null);
            }
        }
    }

    final boolean initialized() {
        return initialization.get() != null;
    }

    final McpProtocol negotiatedProtocol() {
        return negotiatedProtocol.get();
    }

    final boolean supportsStatelessProtocol(McpProtocol requestedProtocol) {
        return usingProtocol(requestedProtocol, () -> {
            var response = exchange(JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .id(generateRequestId())
                    .method(McpMethod.Standard.SERVER_DISCOVER.wireName())
                    .params(McpMetadata.forProtocol(requestedProtocol).applyTo(null))
                    .build());
            if (response == null) {
                throw new McpRemoteException("Remote MCP discovery did not return a response");
            }
            if (response.getError() != null) {
                return false;
            }
            if (response.getResult() == null) {
                throw new McpRemoteException(
                        "Remote MCP discovery response did not contain a result");
            }
            return true;
        });
    }

    private void await(CompletableFuture<Void> future) {
        try {
            future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    /**
     * Performs one blocking JSON-RPC exchange. Notifications return {@code null}.
     */
    protected abstract JsonRpcResponse exchange(JsonRpcRequest request);

    final JsonRpcResponse exchangeForwarded(JsonRpcRequest request) {
        var callerId = request.getId();
        if (callerId == null) {
            return exchange(request);
        }

        var forwarded = JsonRpcRequest.builder()
                .jsonrpc(request.getJsonrpc())
                .id(generateRequestId())
                .method(request.getMethod())
                .params(request.getParams())
                .build();
        var response = exchange(forwarded);
        if (response == null) {
            return null;
        }

        var restored = JsonRpcResponse.builder()
                .jsonrpc(response.getJsonrpc())
                .id(callerId);
        if (response.getError() != null) {
            restored.error(response.getError());
        } else if (response.getResult() == null) {
            restored.error(JsonRpcErrorResponse.builder()
                    .code(-32000)
                    .message("Remote MCP response did not contain a result or error")
                    .build());
        } else {
            restored.result(response.getResult());
        }
        return restored.build();
    }

    /**
     * Starts resources owned by this client.
     */
    public abstract void start();

    /**
     * Stops resources owned by this client.
     */
    @Override
    public abstract void close();

    protected final <T extends SerializableStruct> T exchange(String method, ShapeBuilder<T> builder) {
        var response = exchange(JsonRpcRequest.builder()
                .method(method)
                .id(generateRequestId())
                .jsonrpc("2.0")
                .build());
        requireSuccess(response, method);
        return response.getResult().asShape(builder);
    }

    protected final Document generateRequestId() {
        return Document.of(ID_GENERATOR.incrementAndGet());
    }

    protected final void notify(JsonRpcResponse response) {
        var consumer = responseNotificationConsumer.get();
        if (consumer != null) {
            consumer.accept(response);
        }
    }

    protected final void notify(JsonRpcRequest notification) {
        var consumer = requestNotificationConsumer.get();
        if (consumer != null) {
            LOG.debug("Forwarding notification to consumer: method={}", notification.getMethod());
            consumer.accept(notification);
        } else {
            LOG.warn("No request notification consumer set, dropping notification: method={}",
                    notification.getMethod());
        }
    }

    protected static boolean isNotification(Document doc) {
        try {
            return (doc.isType(ShapeType.STRUCTURE) || doc.isType(ShapeType.MAP))
                    && doc.getMember("id") == null
                    && doc.getMember("method") != null;
        } catch (RuntimeException e) {
            LOG.warn("Failed to determine whether MCP document is a notification", e);
            return false;
        }
    }

    private static void requireSuccess(JsonRpcResponse response, String action) {
        Objects.requireNonNull(response, action + " response");
        if (response.getError() != null) {
            throw new McpRemoteException("Remote MCP error during " + action + ": "
                    + response.getError().getMessage());
        }
        if (response.getResult() == null) {
            throw new McpRemoteException("Remote MCP response during " + action + " did not contain a result");
        }
    }

    private <T> McpPage<T> page(
            List<T> items,
            String nextCursor,
            PageFetcher<T> fetcher,
            PageTraversal traversal
    ) {
        if (nextCursor == null || nextCursor.isBlank()) {
            return McpPage.last(items);
        }
        var nextTraversal = traversal.record(nextCursor);
        return McpPage.continued(items, () -> fetcher.fetch(nextCursor, nextTraversal));
    }

    @FunctionalInterface
    private interface PageFetcher<T> {
        McpPage<T> fetch(String cursor, PageTraversal traversal);
    }

    private record PageTraversal(int pageCount, Set<String> cursors) {
        private PageTraversal() {
            this(0, Set.of());
        }

        private PageTraversal {
            cursors = Set.copyOf(cursors);
        }

        PageTraversal beforeFetch() {
            if (pageCount >= MAX_LIST_PAGES) {
                throw new McpRemoteException(
                        "Remote MCP listing exceeded the maximum of " + MAX_LIST_PAGES + " pages");
            }
            return new PageTraversal(pageCount + 1, cursors);
        }

        PageTraversal record(String cursor) {
            if (cursors.contains(cursor)) {
                throw new McpRemoteException("Remote MCP listing repeated cursor: " + cursor);
            }
            var updated = new HashSet<>(cursors);
            updated.add(cursor);
            return new PageTraversal(pageCount, updated);
        }
    }

    private record Initialization(
            JsonRpcRequest request,
            McpProtocol requestedProtocol,
            McpProtocolRegistry protocols) {}

    public abstract String name();
}
