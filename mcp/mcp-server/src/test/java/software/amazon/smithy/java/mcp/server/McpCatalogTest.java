/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonObjectSchema;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.ListToolsResult;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.java.mcp.model.ToolInfo;

class McpCatalogTest {

    @Test
    void failingRemoteDoesNotAbortInitializationOrHideHealthyRemotes() {
        var failing = new TestRemoteClient("failing") {
            @Override
            public McpPage<ToolInfo> listTools() {
                throw new McpRemoteException("unavailable");
            }
        };
        var healthy = new TestRemoteClient("healthy") {
            @Override
            public McpPage<ToolInfo> listTools() {
                return McpPage.last(List.of(tool("healthy-tool")));
            }
        };

        try (var engine = McpEngine.builder()
                .remoteClients(List.of(failing, healthy))
                .build()) {
            var initialize = engine.execute(initializeRequest(), KnownProtocolVersion.V2025_11_25);
            assertNull(initialize.getError());

            var response = engine.execute(
                    request(2, McpMethod.Standard.TOOLS_LIST.wireName()),
                    KnownProtocolVersion.V2025_11_25);
            var tools = response.getResult().asShape(ListToolsResult.builder()).getTools();

            assertEquals(List.of("healthy-tool"), tools.stream().map(ToolInfo::getName).toList());
        }
    }

    @Test
    void toolPagesPassThroughWithoutAggregatingFutureListings() {
        var continuationCalls = new AtomicInteger();
        var remote = new TestRemoteClient("paged") {
            @Override
            public McpPage<ToolInfo> listTools() {
                return McpPage.continued(
                        List.of(tool("FirstTool")),
                        () -> {
                            continuationCalls.incrementAndGet();
                            return McpPage.last(List.of(tool("SecondTool")));
                        });
            }
        };

        try (var catalog = new McpCatalog(Map.of(), List.of(remote))) {
            catalog.ensureRemoteCatalogLoaded();

            var first = catalog.listTools(null);
            assertEquals(List.of("FirstTool"), toolNames(first));
            assertNotNull(first.nextCursor());
            assertEquals(0, continuationCalls.get());

            var second = catalog.listTools(first.nextCursor());
            assertEquals(List.of("SecondTool"), toolNames(second));
            assertNull(second.nextCursor());
            assertEquals(1, continuationCalls.get());
            assertNotNull(catalog.tool("SecondTool"));

            var fresh = catalog.listTools(null);
            assertEquals(List.of("FirstTool"), toolNames(fresh));
            assertNotNull(fresh.nextCursor());
            assertEquals(1, continuationCalls.get());

            var error = assertThrows(
                    McpProtocolException.class,
                    () -> catalog.listTools(first.nextCursor()));
            assertEquals(-32602, error.code());
        }
    }

    @Test
    void promptPagesPassThroughWithoutAggregatingFutureListings() {
        var continuationCalls = new AtomicInteger();
        var remote = new TestRemoteClient("paged") {
            @Override
            public McpPage<PromptInfo> listPrompts() {
                return McpPage.continued(
                        List.of(prompt("FirstPrompt")),
                        () -> {
                            continuationCalls.incrementAndGet();
                            return McpPage.last(List.of(prompt("SecondPrompt")));
                        });
            }
        };

        try (var catalog = new McpCatalog(Map.of(), List.of(remote))) {
            catalog.ensureRemoteCatalogLoaded();

            var first = catalog.listPrompts(null);
            assertEquals(List.of("FirstPrompt"), promptNames(first));
            assertNotNull(first.nextCursor());
            assertEquals(0, continuationCalls.get());

            var second = catalog.listPrompts(first.nextCursor());
            assertEquals(List.of("SecondPrompt"), promptNames(second));
            assertNull(second.nextCursor());
            assertEquals(1, continuationCalls.get());
            assertNotNull(catalog.prompt(PromptLoader.normalize("SecondPrompt")));

            var fresh = catalog.listPrompts(null);
            assertEquals(List.of("FirstPrompt"), promptNames(fresh));
            assertNotNull(fresh.nextCursor());
            assertEquals(1, continuationCalls.get());
        }
    }

    @Test
    void failedPageFetchDoesNotConsumeTheCursor() {
        var continuationCalls = new AtomicInteger();
        var remote = new TestRemoteClient("retryable-page") {
            @Override
            public McpPage<ToolInfo> listTools() {
                return McpPage.continued(
                        List.of(tool("FirstTool")),
                        () -> {
                            if (continuationCalls.incrementAndGet() == 1) {
                                throw new McpRemoteException("temporary failure");
                            }
                            return McpPage.last(List.of(tool("SecondTool")));
                        });
            }
        };

        try (var catalog = new McpCatalog(Map.of(), List.of(remote))) {
            catalog.ensureRemoteCatalogLoaded();
            var first = catalog.listTools(null);

            assertThrows(
                    McpRemoteException.class,
                    () -> catalog.listTools(first.nextCursor()));
            assertEquals(
                    List.of("SecondTool"),
                    toolNames(catalog.listTools(first.nextCursor())));
        }
    }

    @Test
    void refreshRetainsAdvertisedPagesAndExistingCursors() throws Exception {
        var refreshComplete = new CountDownLatch(1);
        var listings = new AtomicInteger();
        var remote = new TestRemoteClient("refreshing-pages") {
            @Override
            public McpPage<ToolInfo> listTools() {
                if (listings.incrementAndGet() == 1) {
                    return McpPage.continued(
                            List.of(tool("FirstTool")),
                            () -> McpPage.last(List.of(tool("SecondTool"))));
                }
                refreshComplete.countDown();
                return McpPage.last(List.of(tool("RefreshedFirstTool")));
            }
        };

        try (var catalog = new McpCatalog(Map.of(), List.of(remote))) {
            catalog.bindTransport(ignored -> {}, ignored -> {});
            catalog.initializeRemoteClients(
                    BuiltInProtocols.protocol(KnownProtocolVersion.V2025_11_25));

            var first = catalog.listTools(null);
            catalog.listTools(first.nextCursor());
            var cursorCreatedBeforeRefresh = catalog.listTools(null).nextCursor();

            remote.sendNotification(JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .method(McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED.wireName())
                    .build());
            assertTrue(refreshComplete.await(2, SECONDS));

            assertNotNull(catalog.tool("SecondTool"));
            assertEquals(
                    List.of("SecondTool"),
                    toolNames(catalog.listTools(cursorCreatedBeforeRefresh)));
        }
    }

    @Test
    void cursorRegistryIsBounded() {
        var remote = new TestRemoteClient("bounded-cursors") {
            @Override
            public McpPage<ToolInfo> listTools() {
                return McpPage.continued(
                        List.of(tool("FirstTool")),
                        () -> McpPage.last(List.of(tool("SecondTool"))));
            }
        };

        try (var catalog = new McpCatalog(Map.of(), List.of(remote))) {
            catalog.ensureRemoteCatalogLoaded();
            var oldest = catalog.listTools(null).nextCursor();
            for (int index = 0; index < 1_024; index++) {
                catalog.listTools(null);
            }

            var error = assertThrows(
                    McpProtocolException.class,
                    () -> catalog.listTools(oldest));
            assertEquals(-32602, error.code());
        }
    }

    @Test
    void remoteCatalogRefreshesRunInParallel() {
        var entered = new CountDownLatch(2);
        var firstTimedOut = new AtomicBoolean();
        var secondTimedOut = new AtomicBoolean();
        var first = blockingToolClient("first", "first-tool", entered, firstTimedOut);
        var second = blockingToolClient("second", "second-tool", entered, secondTimedOut);

        try (var catalog = new McpCatalog(Map.of(), List.of(first, second))) {
            catalog.ensureRemoteCatalogLoaded();

            assertFalse(firstTimedOut.get());
            assertFalse(secondTimedOut.get());
            assertEquals(2, catalog.snapshot().tools().size());
        }
    }

    @Test
    void remoteCatalogCanReloadUsingANewerProtocol() {
        var versions = new CopyOnWriteArrayList<ProtocolVersion>();
        var remote = new TestRemoteClient("versioned") {
            @Override
            public McpPage<ToolInfo> listTools() {
                versions.add(protocolVersion());
                return McpPage.last(List.of());
            }
        };

        try (var catalog = new McpCatalog(Map.of(), List.of(remote))) {
            catalog.ensureRemoteCatalogLoaded(
                    BuiltInProtocols.protocol(KnownProtocolVersion.V2025_11_25));
            catalog.ensureRemoteCatalogLoaded(
                    BuiltInProtocols.protocol(KnownProtocolVersion.V2026_07_28));

            assertEquals(
                    List.of(
                            KnownProtocolVersion.V2025_11_25,
                            KnownProtocolVersion.V2026_07_28),
                    versions);
        }
    }

    @Test
    void statelessFrontendInitializesALegacyRemoteWithProxyIdentity() {
        var initializeCount = new AtomicInteger();
        var clientName = new AtomicReference<String>();
        var initialized = new AtomicBoolean();
        var remote = new TestRemoteClient("legacy-only") {
            @Override
            protected JsonRpcResponse exchange(JsonRpcRequest request) {
                if (McpMethod.Standard.SERVER_DISCOVER.wireName().equals(request.getMethod())) {
                    return JsonRpcResponse.builder()
                            .jsonrpc("2.0")
                            .id(request.getId())
                            .error(JsonRpcErrorResponse.builder()
                                    .code(-32601)
                                    .message("Method not found")
                                    .build())
                            .build();
                }
                if (McpHttpBinding.isInitialize(request)) {
                    initializeCount.incrementAndGet();
                    clientName.set(request.getParams()
                            .getMember("clientInfo")
                            .getMember("name")
                            .asString());
                    initialized.set(true);
                    return JsonRpcResponse.builder()
                            .jsonrpc("2.0")
                            .id(request.getId())
                            .result(Document.of(Map.of(
                                    "protocolVersion",
                                    Document.of(KnownProtocolVersion.V2025_11_25.identifier()))))
                            .build();
                }
                return super.exchange(request);
            }

            @Override
            public McpPage<ToolInfo> listTools() {
                if (!initialized.get()) {
                    throw new McpRemoteException("not initialized");
                }
                return McpPage.last(List.of(tool("LegacyTool")));
            }
        };

        try (var catalog = new McpCatalog(Map.of(), List.of(remote))) {
            catalog.ensureRemoteCatalogLoaded(
                    BuiltInProtocols.protocol(KnownProtocolVersion.V2026_07_28));

            assertEquals(1, initializeCount.get());
            assertEquals("mcp-server", clientName.get());
            assertNotNull(catalog.tool("LegacyTool"));
        }
    }

    @Test
    void failedRemoteInitializationRetriesAfterTheCooldown() {
        var initializeCount = new AtomicInteger();
        var initialized = new AtomicBoolean();
        var nanoTime = new AtomicLong();
        var remote = new TestRemoteClient("retryable-initialize") {
            @Override
            protected JsonRpcResponse exchange(JsonRpcRequest request) {
                if (McpMethod.Standard.SERVER_DISCOVER.wireName().equals(request.getMethod())) {
                    return JsonRpcResponse.builder()
                            .jsonrpc("2.0")
                            .id(request.getId())
                            .error(JsonRpcErrorResponse.builder()
                                    .code(-32601)
                                    .message("Method not found")
                                    .build())
                            .build();
                }
                if (McpHttpBinding.isInitialize(request)) {
                    if (initializeCount.incrementAndGet() == 1) {
                        return JsonRpcResponse.builder()
                                .jsonrpc("2.0")
                                .id(request.getId())
                                .error(JsonRpcErrorResponse.builder()
                                        .code(-32000)
                                        .message("temporarily unavailable")
                                        .build())
                                .build();
                    }
                    initialized.set(true);
                    return JsonRpcResponse.builder()
                            .jsonrpc("2.0")
                            .id(request.getId())
                            .result(Document.of(Map.of(
                                    "protocolVersion",
                                    Document.of(KnownProtocolVersion.V2025_11_25.identifier()))))
                            .build();
                }
                return super.exchange(request);
            }

            @Override
            public McpPage<ToolInfo> listTools() {
                if (!initialized.get()) {
                    throw new McpRemoteException("not initialized");
                }
                return McpPage.last(List.of(tool("RecoveredTool")));
            }
        };
        var modern = BuiltInProtocols.protocol(KnownProtocolVersion.V2026_07_28);

        try (var catalog = catalogWithClock(remote, nanoTime)) {
            catalog.ensureRemoteCatalogLoaded(modern);
            assertNull(catalog.tool("RecoveredTool"));

            catalog.ensureRemoteCatalogLoaded(modern);
            assertEquals(1, initializeCount.get());

            nanoTime.set(SECONDS.toNanos(31));
            catalog.ensureRemoteCatalogLoaded(modern);

            assertEquals(2, initializeCount.get());
            assertNotNull(catalog.tool("RecoveredTool"));
        }
    }

    @Test
    void concurrentProtocolLoadsInitializeARemoteOnlyOnce() throws Exception {
        var initializeCount = new AtomicInteger();
        var secondInitialize = new CountDownLatch(1);
        var remote = new TestRemoteClient("single-flight-initialize") {
            @Override
            protected JsonRpcResponse exchange(JsonRpcRequest request) {
                if (McpHttpBinding.isInitialize(request)) {
                    if (initializeCount.incrementAndGet() > 1) {
                        secondInitialize.countDown();
                    }
                    try {
                        secondInitialize.await(250, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new McpRemoteException("interrupted", e);
                    }
                    var requestedVersion = request.getParams()
                            .getMember("protocolVersion")
                            .asString();
                    return JsonRpcResponse.builder()
                            .jsonrpc("2.0")
                            .id(request.getId())
                            .result(Document.of(Map.of(
                                    "protocolVersion",
                                    Document.of(requestedVersion))))
                            .build();
                }
                return super.exchange(request);
            }
        };

        try (var catalog = new McpCatalog(Map.of(), List.of(remote));
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                catalog.ensureRemoteCatalogLoaded(
                        BuiltInProtocols.protocol(KnownProtocolVersion.V2024_11_05));
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                catalog.ensureRemoteCatalogLoaded(
                        BuiltInProtocols.protocol(KnownProtocolVersion.V2025_11_25));
                return null;
            });

            start.countDown();
            first.get();
            second.get();

            assertEquals(1, initializeCount.get());
        }
    }

    @Test
    void failedRemoteLoadUsesCooldownInsteadOfBlockingEveryRequest() {
        var attempts = new AtomicInteger();
        var nanoTime = new AtomicLong();
        var remote = new TestRemoteClient("down") {
            @Override
            public McpPage<ToolInfo> listTools() {
                attempts.incrementAndGet();
                throw new McpRemoteException("unavailable");
            }

            @Override
            public McpPage<PromptInfo> listPrompts() {
                attempts.incrementAndGet();
                throw new McpRemoteException("unavailable");
            }
        };

        try (var catalog = catalogWithClock(remote, nanoTime)) {
            catalog.ensureRemoteCatalogLoaded();
            assertEquals(2, attempts.get());

            assertTimeoutPreemptively(
                    Duration.ofMillis(250),
                    () -> {
                        catalog.ensureRemoteCatalogLoaded();
                    });
            assertEquals(2, attempts.get());

            nanoTime.set(SECONDS.toNanos(31));
            catalog.ensureRemoteCatalogLoaded();
            assertEquals(4, attempts.get());
        }
    }

    @Test
    void remoteIoDoesNotBlockCatalogReads() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var remote = new TestRemoteClient("blocking") {
            @Override
            public McpPage<ToolInfo> listTools() {
                entered.countDown();
                try {
                    assertTrue(release.await(5, SECONDS));
                    return McpPage.last(List.of(tool("blocking-tool")));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new McpRemoteException("interrupted", e);
                }
            }
        };

        try (var catalog = new McpCatalog(Map.of(), List.of(remote))) {
            var refresh = Thread.ofVirtual().start(catalog::ensureRemoteCatalogLoaded);
            try {
                assertTrue(entered.await(2, SECONDS));
                assertTimeoutPreemptively(
                        Duration.ofMillis(500),
                        () -> assertTrue(catalog.containsServer("blocking")));
            } finally {
                release.countDown();
                refresh.join();
            }
        }
    }

    @Test
    void concurrentRemoteAdditionsPublishWithoutLostUpdates() throws Exception {
        var clientCount = 32;
        var ready = new CountDownLatch(clientCount);
        var start = new CountDownLatch(1);

        try (var catalog = new McpCatalog(Map.of(), List.of());
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = IntStream.range(0, clientCount)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        try {
                            assertTrue(start.await(5, SECONDS));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new McpRemoteException("interrupted", e);
                        }
                        catalog.addRemoteClient(new TestRemoteClient("remote-" + index));
                    }))
                    .toList();

            assertTrue(ready.await(5, SECONDS));
            start.countDown();
            for (var task : tasks) {
                task.get();
            }

            assertEquals(clientCount, catalog.remoteClients().size());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> catalog.remoteClients().clear());
        }
    }

    @Test
    void dynamicallyAddedRemoteUsesTheNegotiatedProtocolVersion() {
        var observedVersion = new AtomicReference<ProtocolVersion>();
        var remote = new TestRemoteClient("dynamic") {
            @Override
            public McpPage<ToolInfo> listTools() {
                observedVersion.set(protocolVersion());
                return McpPage.last(List.of());
            }
        };

        try (var engine = McpEngine.builder().build()) {
            var initialize = JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .id(Document.of(1))
                    .method(McpMethod.Standard.INITIALIZE.wireName())
                    .params(Document.of(Map.of(
                            "protocolVersion",
                            Document.of(KnownProtocolVersion.V2024_11_05.identifier()),
                            "capabilities",
                            Document.of(Map.of()),
                            "clientInfo",
                            Document.of(Map.of()))))
                    .build();
            assertNull(engine.execute(initialize, KnownProtocolVersion.V2024_11_05).getError());

            engine.addRemoteClient(remote);

            assertEquals(KnownProtocolVersion.V2024_11_05, observedVersion.get());
        }
    }

    @Test
    void notificationRefreshDoesNotRunOnTheNotifyingThread() throws Exception {
        var refreshEntered = new CountDownLatch(1);
        var releaseRefresh = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var remote = new TestRemoteClient("notifying") {
            @Override
            public McpPage<ToolInfo> listTools() {
                if (calls.incrementAndGet() > 1) {
                    refreshEntered.countDown();
                    try {
                        assertTrue(releaseRefresh.await(5, SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new McpRemoteException("interrupted", e);
                    }
                }
                return McpPage.last(List.of(tool("notifying-tool")));
            }
        };

        try (var catalog = new McpCatalog(Map.of(), List.of(remote))) {
            try {
                catalog.bindTransport(ignored -> {}, ignored -> {});
                catalog.initializeRemoteClients(
                        BuiltInProtocols.protocol(KnownProtocolVersion.V2025_11_25));

                var notification = JsonRpcRequest.builder()
                        .jsonrpc("2.0")
                        .method(McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED.wireName())
                        .build();
                assertTimeoutPreemptively(
                        Duration.ofMillis(500),
                        () -> remote.sendNotification(notification));
                assertTrue(refreshEntered.await(2, SECONDS));
            } finally {
                releaseRefresh.countDown();
            }
        }
    }

    @Test
    void remoteNotificationsFanOutToEveryBoundTransport() {
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        var remote = new TestRemoteClient("notifications");

        try (var catalog = new McpCatalog(Map.of(), List.of(remote))) {
            catalog.bindTransport(ignored -> first.incrementAndGet(), ignored -> {});
            catalog.bindTransport(ignored -> second.incrementAndGet(), ignored -> {});
            catalog.initializeRemoteClients(
                    BuiltInProtocols.protocol(KnownProtocolVersion.V2025_11_25));

            remote.sendNotification(JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .method("notifications/progress")
                    .build());

            assertEquals(1, first.get());
            assertEquals(1, second.get());
        }
    }

    private TestRemoteClient blockingToolClient(
            String name,
            String toolName,
            CountDownLatch entered,
            AtomicBoolean timedOut
    ) {
        return new TestRemoteClient(name) {
            @Override
            public McpPage<ToolInfo> listTools() {
                entered.countDown();
                try {
                    if (!entered.await(2, SECONDS)) {
                        timedOut.set(true);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new McpRemoteException("interrupted", e);
                }
                return McpPage.last(List.of(tool(toolName)));
            }
        };
    }

    private ToolInfo tool(String name) {
        return ToolInfo.builder()
                .name(name)
                .inputSchema(JsonObjectSchema.builder().build())
                .build();
    }

    private PromptInfo prompt(String name) {
        return PromptInfo.builder().name(name).build();
    }

    private List<String> toolNames(McpCursorPage<McpToolDescriptor> page) {
        return page.items().stream().map(tool -> tool.info().getName()).toList();
    }

    private List<String> promptNames(McpCursorPage<McpPromptDescriptor> page) {
        return page.items()
                .stream()
                .map(prompt -> prompt.prompt().promptInfo().getName())
                .toList();
    }

    private JsonRpcRequest initializeRequest() {
        return JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(Document.of(1))
                .method(McpMethod.Standard.INITIALIZE.wireName())
                .params(Document.of(Map.of(
                        "protocolVersion",
                        Document.of(KnownProtocolVersion.V2025_11_25.identifier()),
                        "capabilities",
                        Document.of(Map.of()),
                        "clientInfo",
                        Document.of(Map.of()))))
                .build();
    }

    private McpCatalog catalogWithClock(
            McpRemoteClient remote,
            AtomicLong nanoTime
    ) {
        return new McpCatalog(
                Map.of(),
                List.of(remote),
                McpProtocolRegistry.create(List.of(), List.of(), false),
                new McpServerIdentity("mcp-server", "1.0.0"),
                nanoTime::get,
                SECONDS.toNanos(30));
    }

    private JsonRpcRequest request(int id, String method) {
        return JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(Document.of(id))
                .method(method)
                .build();
    }

    private static class TestRemoteClient extends McpRemoteClient {
        private final String name;

        TestRemoteClient(String name) {
            this.name = name;
        }

        @Override
        public McpPage<ToolInfo> listTools() {
            return McpPage.last(List.of());
        }

        @Override
        public McpPage<PromptInfo> listPrompts() {
            return McpPage.last(List.of());
        }

        @Override
        protected JsonRpcResponse exchange(JsonRpcRequest request) {
            return request.getId() == null
                    ? null
                    : JsonRpcResponse.builder()
                            .jsonrpc("2.0")
                            .id(request.getId())
                            .result(Document.of(Map.of()))
                            .build();
        }

        @Override
        public void start() {}

        @Override
        public void close() {}

        @Override
        public String name() {
            return name;
        }

        void sendNotification(JsonRpcRequest notification) {
            notify(notification);
        }
    }
}
