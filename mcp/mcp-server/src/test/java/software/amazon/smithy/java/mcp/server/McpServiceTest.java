/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.ListToolsResult;
import software.amazon.smithy.java.mcp.model.ToolInfo;

class McpServiceTest {

    /** A proxy whose tool set can change and that records which thread its listTools() ran on. */
    private static final class FakeProxy extends McpServerProxy {
        volatile List<ToolInfo> toolSet;
        volatile String lastListToolsThread;
        volatile CountDownLatch listToolsLatch = new CountDownLatch(1);
        private final String name;

        FakeProxy(String name, List<ToolInfo> initial) {
            this.name = name;
            this.toolSet = initial;
        }

        @Override
        public List<ToolInfo> listTools() {
            lastListToolsThread = Thread.currentThread().getName();
            listToolsLatch.countDown();
            return toolSet;
        }

        @Override
        protected CompletableFuture<JsonRpcResponse> rpc(JsonRpcRequest request) {
            return CompletableFuture.completedFuture(JsonRpcResponse.builder()
                    .jsonrpc("2.0")
                    .id(request.getId() == null ? Document.of(0) : request.getId())
                    .result(Document.of(Map.of()))
                    .build());
        }

        @Override
        protected void start() {}

        @Override
        protected CompletableFuture<Void> shutdown() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public String name() {
            return name;
        }

        void fireListChanged() {
            notify(JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .method("notifications/tools/list_changed")
                    .build());
        }
    }

    private static ToolInfo tool(String name) {
        return ToolInfo.builder().name(name).build();
    }

    /** Builds a service with the fake proxy and drives initialize so its notification writer is wired. */
    private static McpService initializedService(FakeProxy proxy) {
        var service = new McpService(Map.of(),
                List.of(proxy),
                "test",
                "1.0",
                (s, t) -> true,
                null,
                McpServerInterceptor.NOOP);
        service.handleRequest(
                JsonRpcRequest.builder()
                        .jsonrpc("2.0")
                        .id(Document.of(1))
                        .method("initialize")
                        .params(Document.of(Map.of()))
                        .build(),
                r -> {},
                ProtocolVersion.defaultVersion());
        return service;
    }

    private static List<String> listToolNames(McpService service) {
        var resp = service.handleRequest(
                JsonRpcRequest.builder().jsonrpc("2.0").id(Document.of(2)).method("tools/list").build(),
                r -> {},
                ProtocolVersion.defaultVersion());
        return resp.getResult()
                .asShape(ListToolsResult.builder())
                .getTools()
                .stream()
                .map(ToolInfo::getName)
                .sorted()
                .toList();
    }

    @Test
    void listChangedRefreshRunsOffTheNotifyingThread() throws Exception {
        // Regression for the reader-thread deadlock: a tools/list_changed refresh must NOT run
        // listTools() on the thread that delivered the notification (on stdio that is the transport
        // reader thread, which must stay free to read the tools/list response).
        var proxy = new FakeProxy("fake", List.of(tool("a")));
        initializedService(proxy);

        // initialize() already called listTools() once on this thread; reset for the refresh.
        proxy.lastListToolsThread = null;
        proxy.listToolsLatch = new CountDownLatch(1);

        proxy.fireListChanged();

        assertTrue(proxy.listToolsLatch.await(5, SECONDS), "refresh never ran");
        assertNotEquals(Thread.currentThread().getName(),
                proxy.lastListToolsThread,
                "refresh must not run on the notifying thread");
        assertTrue(proxy.lastListToolsThread != null && proxy.lastListToolsThread.startsWith("mcp-tools-refresh"),
                "refresh should run on the dedicated executor thread, was: " + proxy.lastListToolsThread);
    }

    @Test
    void listChangedRefreshAddsNewToolsAndPrunesStaleOnes() throws Exception {
        var proxy = new FakeProxy("fake", List.of(tool("old1"), tool("old2")));
        var service = initializedService(proxy);
        assertEquals(List.of("old1", "old2"), listToolNames(service));

        // Server's set changes: old1 kept, old2 gone, new1 added.
        proxy.toolSet = List.of(tool("old1"), tool("new1"));
        proxy.listToolsLatch = new CountDownLatch(1);
        proxy.fireListChanged();
        assertTrue(proxy.listToolsLatch.await(5, SECONDS));

        // The snapshot swap happens after listTools() returns, so poll for the expected state.
        assertEventually(() -> List.of("new1", "old1").equals(listToolNames(service)),
                "expected [new1, old1] but was " + listToolNames(service));
    }

    @Test
    void concurrentRefreshAddProxyAndListDoNotLoseUpdatesOrThrow() throws Exception {
        // Hammer the registry from multiple threads: repeated tools/list_changed refreshes on an
        // existing proxy, dynamic addNewProxy calls, and concurrent tools/list reads. With
        // copy-on-write under a single lock, reads must never throw and every added proxy's tool
        // must be present at the end (no lost updates).
        var refreshProxy = new FakeProxy("refresher", List.of(tool("r0")));
        var service = initializedService(refreshProxy);

        int adders = 4;
        int refreshers = 4;
        int readers = 4;
        int iterations = 200;
        var barrier = new CyclicBarrier(adders + refreshers + readers);
        var error = new AtomicReference<Throwable>();
        var threads = new ArrayList<Thread>();

        for (int a = 0; a < adders; a++) {
            final int id = a;
            threads.add(new Thread(() -> {
                try {
                    barrier.await();
                    for (int i = 0; i < iterations; i++) {
                        var p = new FakeProxy("added-" + id + "-" + i, List.of(tool("added-" + id + "-" + i)));
                        service.addNewProxy(p, r -> {});
                    }
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                }
            }));
        }
        for (int r = 0; r < refreshers; r++) {
            threads.add(new Thread(() -> {
                try {
                    barrier.await();
                    for (int i = 0; i < iterations; i++) {
                        refreshProxy.toolSet = List.of(tool("r" + i));
                        service.refreshProxyTools(refreshProxy);
                    }
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                }
            }));
        }
        for (int r = 0; r < readers; r++) {
            threads.add(new Thread(() -> {
                try {
                    barrier.await();
                    for (int i = 0; i < iterations; i++) {
                        listToolNames(service);
                    }
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                }
            }));
        }

        threads.forEach(Thread::start);
        for (var t : threads) {
            t.join(30_000);
        }

        if (error.get() != null) {
            throw new AssertionError("concurrent access threw", error.get());
        }

        // Every proxy added by every adder thread must have its tool registered (no lost updates).
        var finalNames = listToolNames(service);
        for (int id = 0; id < adders; id++) {
            var expected = "added-" + id + "-" + (iterations - 1);
            assertTrue(finalNames.contains(expected),
                    "missing tool from a concurrently added proxy: " + expected);
        }
    }

    private static void assertEventually(BooleanSupplier condition, String message)
            throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError(message);
    }
}
