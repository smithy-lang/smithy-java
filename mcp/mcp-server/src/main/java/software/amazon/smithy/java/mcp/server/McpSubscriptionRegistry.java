/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;

/**
 * Connection-scoped {@code subscriptions/listen} streams sharing one ordered output channel.
 *
 * <p>Every mutation and every write happens while holding {@code lock}, the same monitor the
 * transport uses for all frame writes. Registering a subscription and writing its
 * acknowledgment is therefore atomic with respect to event delivery: no notification tagged
 * with a subscription id can precede that subscription's acknowledgment, and no message is
 * written for a subscription after it is cancelled.
 */
final class McpSubscriptionRegistry {
    private final Object lock;
    private final Consumer<SerializableStruct> writer;
    private final McpServerIdentity identity;
    private final Map<String, Subscription> active = new LinkedHashMap<>();

    McpSubscriptionRegistry(Object lock, Consumer<SerializableStruct> writer, McpServerIdentity identity) {
        this.lock = lock;
        this.writer = writer;
        this.identity = identity;
    }

    void open(McpOutcome.Subscribed subscribed) {
        var key = StdioMcpClient.requestKey(subscribed.id());
        synchronized (lock) {
            if (active.containsKey(key)) {
                writer.accept(JsonRpcResponse.builder()
                        .jsonrpc("2.0")
                        .id(subscribed.id())
                        .error(JsonRpcErrorResponse.builder()
                                .code(-32600)
                                .message("A subscription with this id is already active")
                                .build())
                        .build());
                return;
            }
            writer.accept(notification(
                    McpMethod.Standard.NOTIFICATIONS_SUBSCRIPTIONS_ACKNOWLEDGED,
                    subscribed.id(),
                    subscribed.accepted().toDocument()));
            active.put(key, new Subscription(subscribed.id(), subscribed.accepted()));
        }
    }

    void cancel(Document requestId) {
        if (requestId == null) {
            return;
        }
        final String key;
        try {
            key = StdioMcpClient.requestKey(requestId);
        } catch (IllegalStateException e) {
            return; // Malformed cancellation: ignore per the cancellation rules.
        }
        synchronized (lock) {
            active.remove(key);
        }
    }

    /**
     * Writes a tagged list-changed notification to every subscription that requested it.
     * Callers must hold {@code lock} when combining this with other writes.
     */
    void deliver(McpMethod.Standard method) {
        synchronized (lock) {
            for (var subscription : active.values()) {
                if (subscription.accepts(method)) {
                    writer.accept(notification(method, subscription.id(), null));
                }
            }
        }
    }

    /**
     * Ends every subscription gracefully with a {@code complete} result.
     */
    void completeAll() {
        synchronized (lock) {
            for (var subscription : active.values()) {
                writer.accept(JsonRpcResponse.builder()
                        .jsonrpc("2.0")
                        .id(subscription.id())
                        .result(Document.of(Map.of(
                                "resultType",
                                Document.of("complete"),
                                "_meta",
                                Document.of(Map.of(
                                        McpWireNames.SUBSCRIPTION_ID,
                                        subscription.id(),
                                        McpWireNames.SERVER_INFO,
                                        Document.of(Map.of(
                                                "name",
                                                Document.of(identity.name()),
                                                "version",
                                                Document.of(identity.version()))))))))
                        .build());
            }
            active.clear();
        }
    }

    /**
     * Drops every subscription without writing: the transport closed underneath it.
     */
    void clear() {
        synchronized (lock) {
            active.clear();
        }
    }

    private static JsonRpcRequest notification(McpMethod.Standard method, Document id, Document filter) {
        var params = new LinkedHashMap<String, Document>();
        params.put("_meta", Document.of(Map.of(McpWireNames.SUBSCRIPTION_ID, id)));
        if (filter != null) {
            params.put("notifications", filter);
        }
        return JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .method(method.wireName())
                .params(Document.of(params))
                .build();
    }

    private record Subscription(Document id, McpSubscriptionFilter filter) {
        boolean accepts(McpMethod.Standard method) {
            return switch (method) {
                case NOTIFICATIONS_TOOLS_LIST_CHANGED -> filter.toolsListChanged();
                case NOTIFICATIONS_PROMPTS_LIST_CHANGED -> filter.promptsListChanged();
                default -> false;
            };
        }
    }
}
