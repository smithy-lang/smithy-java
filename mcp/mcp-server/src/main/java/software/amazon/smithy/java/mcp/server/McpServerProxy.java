/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.ListPromptsResult;
import software.amazon.smithy.java.mcp.model.ListToolsResult;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.java.mcp.model.ToolInfo;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public abstract class McpServerProxy {

    private static final InternalLogger LOG = InternalLogger.getLogger(McpServerProxy.class);
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

    // Cap list pages so a server that always returns a fresh, advancing cursor fails the call
    // instead of looping forever. MCP cursors are opaque and the spec does not guarantee
    // termination; at a typical ~30 items/page this bounds a listing at ~30k items.
    private static final int MAX_LIST_PAGES = 1000;

    private final AtomicReference<Consumer<JsonRpcResponse>> notificationConsumer = new AtomicReference<>();
    private final AtomicReference<Consumer<JsonRpcRequest>> requestNotificationConsumer = new AtomicReference<>();
    private final AtomicReference<ProtocolVersion> protocolVersion =
            new AtomicReference<>(ProtocolVersion.defaultVersion());

    public List<ToolInfo> listTools() {
        return listPaginated("tools/list", "listing tools", result -> {
            ListToolsResult page = result.asShape(ListToolsResult.builder());
            return new Page<>(page.getTools(), page.getNextCursor());
        });
    }

    public List<PromptInfo> listPrompts() {
        return listPaginated("prompts/list", "listing prompts", result -> {
            ListPromptsResult page = result.asShape(ListPromptsResult.builder());
            return new Page<>(page.getPrompts(), page.getNextCursor());
        });
    }

    /**
     * Maximum number of pages {@link #listTools()} / {@link #listPrompts()} will fetch before
     * aborting — a backstop against a server that keeps returning a fresh, advancing cursor and
     * never terminates. Subclasses may override to tighten or relax the bound.
     */
    protected int maxListPages() {
        return MAX_LIST_PAGES;
    }

    /**
     * Drives MCP cursor pagination for a {@code tools/list}-style method: repeatedly calls
     * {@code method}, threading the previous page's {@code nextCursor} back as the {@code cursor}
     * request param, and accumulates items across all pages in page order until the server stops
     * returning a cursor. A single-page server (no {@code nextCursor}) makes exactly one round-trip.
     *
     * <p>Three guards bound a misbehaving server: an absent or blank {@code nextCursor} ends
     * pagination; a previously-seen cursor (including a non-advancing {@code A -> B -> A} cycle)
     * aborts; and the page count is capped at {@link #maxListPages()}.
     */
    private <T> List<T> listPaginated(String method, String errorLabel, PageExtractor<T> extractor) {
        List<T> all = new ArrayList<>();
        // Cursors already requested this call, so a repeated or cycling cursor is caught immediately
        // rather than only when two identical cursors happen to be adjacent.
        Set<String> seenCursors = new HashSet<>();
        String cursor = null;
        int page = 0;
        do {
            if (++page > maxListPages()) {
                throw new IllegalStateException(
                        "Aborting " + method + ": server returned more than " + maxListPages()
                                + " pages without terminating (possible pagination bug or misbehaving server)");
            }

            JsonRpcRequest.Builder requestBuilder = JsonRpcRequest.builder()
                    .method(method)
                    .id(generateRequestId())
                    .jsonrpc("2.0");
            if (cursor != null) {
                requestBuilder.params(Document.of(Map.of("cursor", Document.of(cursor))));
            }

            JsonRpcResponse response = rpc(requestBuilder.build()).join();
            if (response.getError() != null) {
                throw new RuntimeException("Error " + errorLabel + ": " + response.getError().getMessage());
            }

            Document result = response.getResult();
            if (result == null) {
                throw new RuntimeException(
                        "Error " + errorLabel + ": response contained neither a result nor an error");
            }

            Page<T> parsed = extractor.extract(result);
            all.addAll(parsed.items());

            // MCP signals "no more pages" by omitting nextCursor; defensively treat a blank cursor the
            // same way, since some servers send "" instead of omitting the field.
            String nextCursor = parsed.nextCursor();
            if (nextCursor != null && nextCursor.isBlank()) {
                nextCursor = null;
            }
            if (nextCursor != null && !seenCursors.add(nextCursor)) {
                throw new IllegalStateException(
                        "Aborting " + method + ": server repeated a pagination cursor (no forward progress)");
            }
            cursor = nextCursor;
        } while (cursor != null);

        LOG.debug("{}: fetched {} item(s) across {} page(s)", method, all.size(), page);
        return List.copyOf(all);
    }

    /** One page of a paginated list: the page's items plus the server's {@code nextCursor} (null when last). */
    private record Page<T>(List<T> items, String nextCursor) {}

    /** Parses a {@code *_/list} result {@code Document} into its items and {@code nextCursor}. */
    @FunctionalInterface
    private interface PageExtractor<T> {
        Page<T> extract(Document result);
    }

    public void initialize(
            Consumer<JsonRpcResponse> notificationConsumer,
            Consumer<JsonRpcRequest> requestNotificationConsumer,
            JsonRpcRequest initializeRequest,
            ProtocolVersion protocolVersion
    ) {

        var result = Objects.requireNonNull(rpc(initializeRequest).join());
        if (result.getError() != null) {
            throw new RuntimeException("Error during initialization: " + result.getError().getMessage());
        }

        // Send the initialized notification per MCP protocol spec
        JsonRpcRequest initializedNotification = JsonRpcRequest.builder()
                .method("notifications/initialized")
                .jsonrpc("2.0")
                .build();
        rpc(initializedNotification);

        this.notificationConsumer.set(notificationConsumer);
        this.requestNotificationConsumer.set(requestNotificationConsumer);
        this.protocolVersion.set(protocolVersion);
    }

    protected final ProtocolVersion getProtocolVersion() {
        return protocolVersion.get();
    }

    protected abstract CompletableFuture<JsonRpcResponse> rpc(JsonRpcRequest request);

    protected abstract void start();

    protected abstract CompletableFuture<Void> shutdown();

    protected <T extends SerializableStruct> CompletableFuture<T> rpc(String method, ShapeBuilder<T> builder) {
        JsonRpcRequest request = JsonRpcRequest.builder()
                .method(method)
                .id(generateRequestId())
                .jsonrpc("2.0")
                .build();

        return rpc(request).thenApply(response -> {
            if (response.getError() != null) {
                throw new RuntimeException("Error in RPC call: " + response.getError().getMessage());
            }
            return response.getResult().asShape(builder);
        });
    }

    // Generate a unique request ID for each RPC call
    protected Document generateRequestId() {
        return Document.of(ID_GENERATOR.incrementAndGet());
    }

    protected void notify(JsonRpcResponse response) {
        var nc = notificationConsumer.get();
        if (nc != null) {
            nc.accept(response);
        }
    }

    /**
     * Forwards a notification request by converting it to a response format.
     * Notifications have a method field but no id.
     */
    protected void notify(JsonRpcRequest notification) {
        var rnc = requestNotificationConsumer.get();
        if (rnc != null) {
            LOG.debug("Forwarding notification to consumer: method={}", notification.getMethod());
            rnc.accept(notification);
        } else {
            LOG.warn("No request notification consumer set, dropping notification: method={}",
                    notification.getMethod());
        }
    }

    /**
     * Determines if a Document represents a notification (has "method" but no "id")
     * rather than a response (has "id").
     *
     * - Responses have an "id" field at the top level
     * - Notifications have a "method" field but no "id" field at the top level
     */
    protected static boolean isNotification(Document doc) {
        try {
            if (!doc.isType(ShapeType.STRUCTURE) && !doc.isType(ShapeType.MAP)) {
                return false;
            }

            // If it has a "method" field but no "id", it's a notification
            return doc.getMember("id") == null && doc.getMember("method") != null;
        } catch (Exception e) {
            LOG.warn("Failed to determine if notification from Document", e);
            return false;
        }
    }

    public abstract String name();
}
