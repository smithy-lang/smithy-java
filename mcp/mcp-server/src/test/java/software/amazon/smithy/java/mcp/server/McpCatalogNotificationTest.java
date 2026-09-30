/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonObjectSchema;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.java.mcp.model.ToolInfo;

class McpCatalogNotificationTest {
    @ParameterizedTest
    @EnumSource(CatalogType.class)
    void listingsAfterNotificationShareRefreshAndWaitForFreshData(CatalogType type) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var remote = new Remote(type, call -> {
            if (call > 1) {
                entered.countDown();
                await(release);
                return List.of("updated");
            }
            return List.of("original");
        });
        var observed = new LinkedBlockingQueue<JsonRpcRequest>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
                var catalog = initializedCatalog(remote, observed)) {
            try {
                var notification = type.notification(1);
                remote.send(notification);
                assertSame(notification, take(observed));
                assertEquals(1, remote.calls.get(), "Notifications must not fetch on their own");

                var first = executor.submit(() -> type.names(catalog));
                var second = executor.submit(() -> type.names(catalog));
                assertTrue(entered.await(2, SECONDS));
                assertThrows(TimeoutException.class, () -> first.get(100, MILLISECONDS));
                assertThrows(TimeoutException.class, () -> second.get(100, MILLISECONDS));
                assertEquals(2, remote.calls.get(), "Concurrent listings share one refresh");
                release.countDown();

                assertEquals(List.of("updated"), first.get(2, SECONDS));
                assertEquals(List.of("updated"), second.get(2, SECONDS));
                assertEquals(2, remote.calls.get());
                assertNull(observed.poll(100, MILLISECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(CatalogType.class)
    void failedRefreshLeavesListingsDirtyAndRetriesAfterCooldown(CatalogType type) throws Exception {
        var now = new AtomicLong();
        var failing = new AtomicBoolean(true);
        var failure = new McpRemoteException("upstream unavailable");
        var remote = new Remote(type, call -> {
            if (call > 1 && failing.get()) {
                throw failure;
            }
            return List.of(call == 1 ? "original" : "updated");
        });
        var observed = new LinkedBlockingQueue<JsonRpcRequest>();
        try (var catalog = initializedCatalog(remote, observed, now::get)) {
            var original = catalog.snapshot();
            var notification = type.notification(1);
            remote.send(notification);
            assertSame(notification, take(observed));
            var error = assertThrows(McpProtocolException.class, () -> type.names(catalog));
            assertEquals(-32603, error.code());
            assertSame(failure, error.getCause());
            assertSame(original, catalog.snapshot());
            assertEquals(2, remote.calls.get());

            // Nothing is retried within the cooldown, even after another notification.
            remote.send(type.notification(2));
            take(observed);
            now.set(SECONDS.toNanos(29));
            error = assertThrows(McpProtocolException.class, () -> type.names(catalog));
            assertSame(failure, error.getCause().getCause());
            assertEquals(2, remote.calls.get());

            now.set(SECONDS.toNanos(30));
            assertThrows(McpProtocolException.class, () -> type.names(catalog));
            assertEquals(3, remote.calls.get());

            failing.set(false);
            now.set(SECONDS.toNanos(59));
            assertThrows(McpProtocolException.class, () -> type.names(catalog));
            assertEquals(3, remote.calls.get());
            now.set(SECONDS.toNanos(60));
            assertEquals(List.of("updated"), type.names(catalog));
            assertEquals(List.of("updated"), type.names(catalog));
            assertEquals(4, remote.calls.get());
            assertNull(observed.poll(100, MILLISECONDS));
        }
    }

    @ParameterizedTest
    @EnumSource(CatalogType.class)
    void cursorPagesDoNotTriggerOrWaitForRefresh(CatalogType type) throws Exception {
        var remote = new Remote(type, call -> {
            if (call > 1) {
                throw new McpRemoteException("upstream unavailable");
            }
            return List.of("original");
        }, true);
        var observed = new LinkedBlockingQueue<JsonRpcRequest>();
        try (var catalog = initializedCatalog(remote, observed)) {
            var firstCursor = type.cursor(catalog);
            var secondCursor = type.cursor(catalog);
            assertNotNull(firstCursor);
            assertNotNull(secondCursor);
            remote.send(type.notification(1));
            take(observed);

            assertEquals(List.of("continuation"), type.names(catalog, firstCursor));
            assertEquals(1, remote.calls.get(), "Cursor pages must not refresh");
            assertThrows(McpProtocolException.class, () -> type.names(catalog));
            assertEquals(2, remote.calls.get());
            assertEquals(List.of("continuation"), type.names(catalog, secondCursor));
            assertEquals(2, remote.calls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(CatalogType.class)
    void emptyRefreshRemovesEntriesFromListings(CatalogType type) throws Exception {
        var remote = new Remote(type, call -> call == 1 ? List.of("original") : List.of());
        var observed = new LinkedBlockingQueue<JsonRpcRequest>();
        try (var catalog = initializedCatalog(remote, observed)) {
            var notification = type.notification(1);
            remote.send(notification);
            assertSame(notification, take(observed));
            assertEquals(List.of(), type.names(catalog));
            assertEquals(2, remote.calls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(CatalogType.class)
    void notificationsDuringRefreshRequireAnotherRefreshBeforeListingsReturn(CatalogType type) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var nextEntered = new CountDownLatch(1);
        var nextRelease = new CountDownLatch(1);
        var remote = new Remote(type, call -> {
            if (call == 2) {
                entered.countDown();
                await(release);
            } else if (call == 3) {
                nextEntered.countDown();
                await(nextRelease);
            }
            return List.of("version" + call);
        });
        var observed = new LinkedBlockingQueue<JsonRpcRequest>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
                var catalog = initializedCatalog(remote, observed)) {
            try {
                remote.send(type.notification(1));
                take(observed);
                var listing = executor.submit(() -> type.names(catalog));
                assertTrue(entered.await(2, SECONDS));

                remote.send(type.notification(2));
                remote.send(type.notification(3));
                take(observed);
                take(observed);
                release.countDown();

                assertTrue(nextEntered.await(2, SECONDS));
                assertThrows(TimeoutException.class, () -> listing.get(100, MILLISECONDS));
                nextRelease.countDown();
                assertEquals(List.of("version3"), listing.get(2, SECONDS));
                assertEquals(3, remote.calls.get(), "Overlapping notifications coalesce into one extra refresh");
                assertEquals(List.of("version3"), type.names(catalog));
                assertEquals(3, remote.calls.get());
                assertNull(observed.poll(100, MILLISECONDS));
            } finally {
                release.countDown();
                nextRelease.countDown();
            }
        }
    }

    private static McpCatalog initializedCatalog(Remote remote, LinkedBlockingQueue<JsonRpcRequest> observed) {
        return initializedCatalog(remote, observed, System::nanoTime);
    }

    private static McpCatalog initializedCatalog(
            Remote remote,
            LinkedBlockingQueue<JsonRpcRequest> observed,
            LongSupplier clock
    ) {
        var catalog = new McpCatalog(Map.of(),
                List.of(remote),
                McpProtocolRegistry.create(List.of(), List.of(), false),
                new McpServerIdentity("test", "1"),
                clock,
                SECONDS.toNanos(30));
        catalog.bindTransport(observed::add, ignored -> {});
        catalog.initializeRemoteClients(BuiltInProtocols.protocol(KnownProtocolVersion.V2025_11_25));
        return catalog;
    }

    private static JsonRpcRequest take(LinkedBlockingQueue<JsonRpcRequest> observed) throws InterruptedException {
        var result = observed.poll(2, SECONDS);
        assertNotNull(result, "No forwarded notification");
        return result;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, SECONDS)) {
                throw new McpRemoteException("test refresh timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpRemoteException("test refresh interrupted", e);
        }
    }

    enum CatalogType {
        TOOLS("notifications/tools/list_changed"),
        PROMPTS("notifications/prompts/list_changed");

        final String method;

        CatalogType(String method) {
            this.method = method;
        }

        JsonRpcRequest notification(int sequence) {
            return JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .method(method)
                    .params(Document.ofObject(Map.of("sequence", sequence)))
                    .build();
        }

        List<String> names(McpCatalog catalog) {
            return names(catalog, null);
        }

        List<String> names(McpCatalog catalog, String cursor) {
            return this == TOOLS
                    ? catalog.listTools(cursor).items().stream().map(tool -> tool.info().getName()).toList()
                    : catalog.listPrompts(cursor)
                            .items()
                            .stream()
                            .map(prompt -> prompt.prompt().promptInfo().getName())
                            .toList();
        }

        String cursor(McpCatalog catalog) {
            return this == TOOLS ? catalog.listTools(null).nextCursor() : catalog.listPrompts(null).nextCursor();
        }
    }

    private static final class Remote extends McpRemoteClient {
        final CatalogType type;
        final AtomicInteger calls = new AtomicInteger();
        private final IntFunction<List<String>> listing;
        private final boolean paginated;

        Remote(CatalogType type, IntFunction<List<String>> listing) {
            this(type, listing, false);
        }

        Remote(CatalogType type, IntFunction<List<String>> listing, boolean paginated) {
            this.type = type;
            this.listing = listing;
            this.paginated = paginated;
        }

        @Override
        public McpPage<ToolInfo> listTools() {
            return list(CatalogType.TOOLS,
                    name -> ToolInfo.builder()
                            .name(name)
                            .inputSchema(JsonObjectSchema.builder().build())
                            .build());
        }

        @Override
        public McpPage<PromptInfo> listPrompts() {
            return list(CatalogType.PROMPTS, name -> PromptInfo.builder().name(name).build());
        }

        private <T> McpPage<T> list(CatalogType requestedType, Function<String, T> mapper) {
            if (type != requestedType) {
                return McpPage.last(List.of());
            }
            var items = listing.apply(calls.incrementAndGet()).stream().map(mapper).toList();
            return paginated
                    ? McpPage.continued(items, () -> McpPage.last(List.of(mapper.apply("continuation"))))
                    : McpPage.last(items);
        }

        @Override
        protected JsonRpcResponse exchange(JsonRpcRequest request) {
            return request.getId() == null ? null
                    : JsonRpcResponse.builder()
                            .jsonrpc("2.0")
                            .id(request.getId())
                            .result(Document.ofObject(Map.of()))
                            .build();
        }

        void send(JsonRpcRequest notification) {
            notify(notification);
        }

        @Override
        public void start() {}

        @Override
        public void close() {}

        @Override
        public String name() {
            return "notification-test";
        }
    }
}
