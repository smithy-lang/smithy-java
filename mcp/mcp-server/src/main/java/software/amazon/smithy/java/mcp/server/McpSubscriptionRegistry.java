/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.math.BigDecimal;
import java.math.BigInteger;
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
 *
 * <p>Once the registry is closed, by a graceful shutdown or because the transport ended, it
 * accepts no further subscriptions and writes nothing more.
 */
final class McpSubscriptionRegistry {
    /** Active subscriptions allowed per connection, matching the MCP TypeScript SDK's default. */
    static final int DEFAULT_MAX_SUBSCRIPTIONS = 1024;

    private final Object lock;
    private final Consumer<SerializableStruct> writer;
    private final McpServerIdentity identity;
    private final int maxSubscriptions;
    private final Map<String, Subscription> active = new LinkedHashMap<>();
    // Read without the lock so the transport can close even while a write blocks on the output.
    private volatile boolean closed;

    McpSubscriptionRegistry(Object lock, Consumer<SerializableStruct> writer, McpServerIdentity identity) {
        this(lock, writer, identity, DEFAULT_MAX_SUBSCRIPTIONS);
    }

    McpSubscriptionRegistry(
            Object lock,
            Consumer<SerializableStruct> writer,
            McpServerIdentity identity,
            int maxSubscriptions
    ) {
        this.lock = lock;
        this.writer = writer;
        this.identity = identity;
        this.maxSubscriptions = maxSubscriptions;
    }

    void open(McpOutcome.Subscribed subscribed) {
        var key = key(subscribed.id());
        synchronized (lock) {
            if (closed) {
                // Shutdown or end of input won the race with this listen; the stream is already gone.
                return;
            }
            if (key == null) {
                writer.accept(error(subscribed.id(), -32600, "A subscription id must be a string or a number"));
                return;
            }
            if (active.containsKey(key)) {
                writer.accept(error(subscribed.id(), -32600, "A subscription with this id is already active"));
                return;
            }
            if (active.size() >= maxSubscriptions) {
                writer.accept(error(subscribed.id(), -32603, "Subscription limit reached"));
                return;
            }
            writer.accept(notification(
                    McpMethod.Standard.NOTIFICATIONS_SUBSCRIPTIONS_ACKNOWLEDGED,
                    subscribed.id(),
                    subscribed.accepted().toDocument()));
            if (subscribed.accepted().isEmpty()) {
                // Nothing can ever be delivered, so end the stream instead of holding it open.
                writer.accept(complete(subscribed.id()));
                return;
            }
            active.put(key, new Subscription(subscribed.id(), subscribed.accepted()));
        }
    }

    void cancel(Document requestId) {
        var key = requestId == null ? null : key(requestId);
        if (key == null) {
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
            if (closed) {
                return;
            }
            for (var subscription : active.values()) {
                if (closed) {
                    return; // The transport ended while an earlier write in this fan-out blocked.
                }
                if (subscription.accepts(method)) {
                    writer.accept(notification(method, subscription.id(), null));
                }
            }
        }
    }

    /**
     * Ends every subscription gracefully with a {@code complete} result and closes the registry.
     */
    void completeAll() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            for (var subscription : active.values()) {
                writer.accept(complete(subscription.id()));
            }
            active.clear();
        }
    }

    /**
     * Closes the registry without writing: the transport ended underneath it. This does not wait
     * for the output, so a client that stops reading cannot keep the server from noticing end of input.
     */
    void close() {
        closed = true;
    }

    private JsonRpcResponse complete(Document id) {
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .result(Document.of(Map.of(
                        "resultType",
                        Document.of("complete"),
                        "_meta",
                        Document.of(Map.of(
                                McpWireNames.SUBSCRIPTION_ID,
                                id,
                                McpWireNames.SERVER_INFO,
                                Document.of(Map.of(
                                        "name",
                                        Document.of(identity.name()),
                                        "version",
                                        Document.of(identity.version()))))))))
                .build();
    }

    private static JsonRpcResponse error(Document id, int code, String message) {
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .error(JsonRpcErrorResponse.builder().code(code).message(message).build())
                .build();
    }

    /**
     * Identifies a subscription by its JSON-RPC id. Strings and numbers never collide, and numbers
     * compare by value, so {@code 7}, {@code 7.0} and {@code 7e0} name the same subscription.
     *
     * @return the key, or {@code null} if the id is neither a string nor a finite number
     */
    private static String key(Document id) {
        return switch (id.type()) {
            case STRING -> "string:" + id.asString();
            case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL -> {
                var number = id.asNumber();
                if ((number instanceof Double d && !Double.isFinite(d))
                        || (number instanceof Float f && !Float.isFinite(f))) {
                    yield null; // e.g. 1e309, which decodes to infinity
                }
                var decimal = switch (number) {
                    case BigDecimal d -> d;
                    case BigInteger i -> new BigDecimal(i);
                    default -> new BigDecimal(number.toString());
                };
                yield "number:" + decimal.stripTrailingZeros().toPlainString();
            }
            default -> null;
        };
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
