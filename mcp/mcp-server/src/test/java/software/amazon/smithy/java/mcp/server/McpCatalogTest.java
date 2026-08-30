/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonObjectSchema;
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
            public List<ToolInfo> listTools() {
                throw new McpRemoteException("unavailable");
            }
        };
        var healthy = new TestRemoteClient("healthy") {
            @Override
            public List<ToolInfo> listTools() {
                return List.of(tool("healthy-tool"));
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
    void remoteIoDoesNotBlockCatalogReads() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var remote = new TestRemoteClient("blocking") {
            @Override
            public List<ToolInfo> listTools() {
                entered.countDown();
                try {
                    assertTrue(release.await(5, SECONDS));
                    return List.of(tool("blocking-tool"));
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
            public List<ToolInfo> listTools() {
                observedVersion.set(protocolVersion());
                return List.of();
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
            public List<ToolInfo> listTools() {
                if (calls.incrementAndGet() > 1) {
                    refreshEntered.countDown();
                    try {
                        assertTrue(releaseRefresh.await(5, SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new McpRemoteException("interrupted", e);
                    }
                }
                return List.of(tool("notifying-tool"));
            }
        };

        try (var catalog = new McpCatalog(Map.of(), List.of(remote))) {
            try {
                catalog.bindTransport(ignored -> {}, ignored -> {});
                catalog.initializeRemoteClients(
                        initializeRequest(),
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

    private TestRemoteClient blockingToolClient(
            String name,
            String toolName,
            CountDownLatch entered,
            AtomicBoolean timedOut
    ) {
        return new TestRemoteClient(name) {
            @Override
            public List<ToolInfo> listTools() {
                entered.countDown();
                try {
                    if (!entered.await(2, SECONDS)) {
                        timedOut.set(true);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new McpRemoteException("interrupted", e);
                }
                return List.of(tool(toolName));
            }
        };
    }

    private ToolInfo tool(String name) {
        return ToolInfo.builder()
                .name(name)
                .inputSchema(JsonObjectSchema.builder().build())
                .build();
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
        public List<ToolInfo> listTools() {
            return List.of();
        }

        @Override
        public List<PromptInfo> listPrompts() {
            return List.of();
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
