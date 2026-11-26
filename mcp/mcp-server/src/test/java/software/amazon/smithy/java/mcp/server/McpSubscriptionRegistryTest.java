/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;

class McpSubscriptionRegistryTest {
    private static final McpSubscriptionFilter TOOLS = new McpSubscriptionFilter(true, false, false, List.of());

    private final Object lock = new Object();
    private final List<SerializableStruct> written = new ArrayList<>();

    @Test
    void limitRejectsExcessSubscriptionsAndCancellationFreesCapacity() {
        var registry = registry(2);
        registry.open(new McpOutcome.Subscribed(Document.of("a"), TOOLS));
        registry.open(new McpOutcome.Subscribed(Document.of("b"), TOOLS));
        registry.open(new McpOutcome.Subscribed(Document.of("c"), TOOLS));

        assertEquals(3, written.size());
        var rejected = assertInstanceOf(JsonRpcResponse.class, written.get(2));
        assertEquals("c", rejected.getId().asString());
        assertEquals(-32603, rejected.getError().getCode());

        written.clear();
        registry.deliver(McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED);
        assertEquals(2, written.size(), "the rejected subscription must not be retained");

        written.clear();
        registry.cancel(Document.of("a"));
        registry.open(new McpOutcome.Subscribed(Document.of("c"), TOOLS));
        assertEquals("notifications/subscriptions/acknowledged", method(written.getFirst()));
    }

    @Test
    void numericIdsMatchByValueButNeverMatchStrings() {
        var registry = registry(10);
        registry.open(new McpOutcome.Subscribed(Document.of(7), TOOLS));
        registry.open(new McpOutcome.Subscribed(Document.of("7"), TOOLS));
        registry.open(new McpOutcome.Subscribed(Document.of(8L), TOOLS));

        // 7.0 and 7 are the same JSON-RPC id; a second listen with it is a duplicate.
        written.clear();
        registry.open(new McpOutcome.Subscribed(Document.of(7.0), TOOLS));
        assertEquals(-32600, assertInstanceOf(JsonRpcResponse.class, written.getFirst()).getError().getCode());

        registry.cancel(Document.of(7.0));
        registry.cancel(Document.of(new BigDecimal("8E0")));
        written.clear();
        registry.deliver(McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED);

        assertEquals(1, written.size());
        assertEquals("7", subscriptionId(written.getFirst()).asString());
    }

    @Test
    void nonFiniteIdsAreRejectedAndIgnored() {
        var registry = registry(10);
        registry.open(new McpOutcome.Subscribed(Document.of("s"), TOOLS));
        written.clear();

        registry.open(new McpOutcome.Subscribed(Document.of(Double.POSITIVE_INFINITY), TOOLS));
        assertEquals(-32600, assertInstanceOf(JsonRpcResponse.class, written.getFirst()).getError().getCode());
        // A cancellation such as {"requestId": 1e309} decodes to infinity; it names no subscription.
        registry.cancel(Document.of(Double.POSITIVE_INFINITY));
        registry.cancel(Document.of(Float.NaN));

        written.clear();
        registry.deliver(McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED);
        assertEquals(1, written.size());
    }

    @Test
    void fanOutStopsOnceTheTransportCloses() {
        var holder = new McpSubscriptionRegistry[1];
        var delivered = new ArrayList<SerializableStruct>();
        holder[0] = new McpSubscriptionRegistry(lock, message -> {
            if (message instanceof JsonRpcRequest request
                    && McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED.wireName().equals(request.getMethod())) {
                delivered.add(message);
                // End of input arrives while this write is in progress.
                holder[0].close();
            }
        }, new McpServerIdentity("test-server", "1"), 10);
        holder[0].open(new McpOutcome.Subscribed(Document.of("a"), TOOLS));
        holder[0].open(new McpOutcome.Subscribed(Document.of("b"), TOOLS));

        holder[0].deliver(McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED);

        assertEquals(1, delivered.size());
    }

    @Test
    void idsThatAreNeitherStringsNorNumbersAreRejected() {
        var registry = registry(10);
        registry.open(new McpOutcome.Subscribed(Document.of(true), TOOLS));

        assertEquals(1, written.size());
        assertEquals(-32600, assertInstanceOf(JsonRpcResponse.class, written.getFirst()).getError().getCode());
    }

    @Test
    void nothingIsAcceptedOrWrittenAfterShutdown() {
        var registry = registry(10);
        registry.open(new McpOutcome.Subscribed(Document.of("s"), TOOLS));
        registry.completeAll();
        written.clear();

        // A listen that was still being handled when shutdown completed the others.
        registry.open(new McpOutcome.Subscribed(Document.of("late"), TOOLS));
        registry.deliver(McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED);
        registry.completeAll();

        assertTrue(written.isEmpty(), written::toString);
    }

    @Test
    void nothingIsAcceptedOrWrittenAfterTheTransportCloses() {
        var registry = registry(10);
        registry.open(new McpOutcome.Subscribed(Document.of("s"), TOOLS));
        registry.close();
        written.clear();

        registry.open(new McpOutcome.Subscribed(Document.of("late"), TOOLS));
        registry.deliver(McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED);
        registry.completeAll();

        assertTrue(written.isEmpty(), written::toString);
    }

    @Test
    void closingDoesNotWaitForABlockedWrite() throws Exception {
        var registry = registry(10);
        var holding = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        // Simulates a write that blocks while holding the output monitor, e.g. on a full pipe.
        var writerThread = Thread.ofPlatform().start(() -> {
            synchronized (lock) {
                holding.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        holding.await();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(2), registry::close);
        } finally {
            release.countDown();
            writerThread.join();
        }
    }

    private McpSubscriptionRegistry registry(int maxSubscriptions) {
        return new McpSubscriptionRegistry(
                lock,
                written::add,
                new McpServerIdentity("test-server", "1"),
                maxSubscriptions);
    }

    private static String method(SerializableStruct message) {
        return assertInstanceOf(JsonRpcRequest.class, message).getMethod();
    }

    private static Document subscriptionId(SerializableStruct message) {
        return assertInstanceOf(JsonRpcRequest.class, message)
                .getParams()
                .getMember("_meta")
                .getMember(McpWireNames.SUBSCRIPTION_ID);
    }
}
