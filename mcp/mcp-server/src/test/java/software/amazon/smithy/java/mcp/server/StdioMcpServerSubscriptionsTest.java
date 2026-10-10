/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.java.mcp.model.ToolInfo;

class StdioMcpServerSubscriptionsTest {
    private static final String TOOLS_CHANGED = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}";

    private TestInputStream input;
    private TestOutputStream output;
    private StdioMcpServer server;
    private FakeRemote remote;

    @BeforeEach
    void setUp() {
        input = new TestInputStream();
        output = new TestOutputStream();
        remote = new FakeRemote("fake");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.shutdown().join();
        }
    }

    @Test
    void discoverAdvertisesListChangedOnStdio() {
        start();

        send("d", "server/discover", modern(Map.of()));
        var capabilities = read().getMember("result").getMember("capabilities");

        assertTrue(capabilities.getMember("tools").getMember("listChanged").asBoolean());
        assertTrue(capabilities.getMember("prompts").getMember("listChanged").asBoolean());
    }

    @Test
    void listenAcknowledgesTheSupportedSubsetAndTagsChanges() {
        start();

        send("listen:0",
                "subscriptions/listen",
                listen(Map.of(
                        "toolsListChanged",
                        Document.of(true),
                        "resourcesListChanged",
                        Document.of(true),
                        "somethingNew",
                        Document.of(true))));
        var ack = read();

        assertEquals("notifications/subscriptions/acknowledged", ack.getMember("method").asString());
        assertNull(ack.getMember("id"));
        assertEquals("listen:0", subscriptionId(ack).asString());
        var accepted = ack.getMember("params").getMember("notifications");
        assertTrue(accepted.getMember("toolsListChanged").asBoolean());
        assertNull(accepted.getMember("promptsListChanged"));
        assertNull(accepted.getMember("resourcesListChanged"));
        assertNull(accepted.getMember("somethingNew"));

        server.refreshTools();
        var changed = read();
        assertEquals("notifications/tools/list_changed", changed.getMember("method").asString());
        assertEquals("listen:0", subscriptionId(changed).asString());
        // No untagged copy: this connection never completed initialize.
        output.assertNoOutput(100);
    }

    @Test
    void subscriptionIdsKeepTheirJsonType() {
        start();

        send("7", "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of(true))));
        assertEquals("\"7\"", rawSubscriptionId(output.read()));
        send(7, "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of(true))));
        assertEquals("7", rawSubscriptionId(output.read()));

        server.refreshTools();
        var ids = new ArrayList<String>();
        for (int i = 0; i < 2; i++) {
            ids.add(rawSubscriptionId(output.read()));
        }
        assertEquals(List.of("\"7\"", "7"), ids.stream().sorted().toList());
        output.assertNoOutput(100);
    }

    @Test
    void invalidFiltersAreRejected() {
        start();

        send("missing", "subscriptions/listen", modern(Map.of()));
        assertEquals(-32602, errorCode(read()));
        send("not-boolean", "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of("yes"))));
        assertEquals(-32602, errorCode(read()));
        send("not-array",
                "subscriptions/listen",
                listen(Map.of("resourceSubscriptions", Document.of("file:///a"))));
        assertEquals(-32602, errorCode(read()));
        send("not-strings",
                "subscriptions/listen",
                listen(Map.of("resourceSubscriptions", Document.of(List.of(Document.of(1))))));
        assertEquals(-32602, errorCode(read()));

        send("ok", "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of(true))));
        assertEquals("ok", subscriptionId(read()).asString());
    }

    @Test
    void emptyAcceptedFilterIsStillAcknowledged() {
        start();

        send("resources-only", "subscriptions/listen", listen(Map.of("resourcesListChanged", Document.of(true))));
        var ack = read();

        assertEquals("notifications/subscriptions/acknowledged", ack.getMember("method").asString());
        assertTrue(ack.getMember("params").getMember("notifications").asStringMap().isEmpty());
        server.refreshTools();
        output.assertNoOutput(100);
    }

    @Test
    void handshakeEraListenIsMethodNotFound() {
        start();
        initialize();

        send(2,
                "subscriptions/listen",
                Document.of(Map.of(
                        "notifications",
                        Document.of(Map.of("toolsListChanged", Document.of(true))))));

        assertEquals(-32601, errorCode(read()));
    }

    @Test
    void duplicateActiveSubscriptionIdIsRejected() {
        start();

        send("s", "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of(true))));
        read();
        send("s", "subscriptions/listen", listen(Map.of("promptsListChanged", Document.of(true))));
        var duplicate = read();

        assertEquals("s", duplicate.getMember("id").asString());
        assertEquals(-32600, errorCode(duplicate));
        server.refreshTools();
        assertEquals("s", subscriptionId(read()).asString());
        output.assertNoOutput(100);
    }

    @Test
    void cancellationStopsDeliveryAndMatchesTheIdType() {
        start();
        send("7", "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of(true))));
        read();
        send(7, "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of(true))));
        read();

        // Unknown and malformed cancellations are ignored without a response.
        cancel(Document.of("unknown"));
        writeLine(McpJson.CODEC.serializeToString(JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .method("notifications/cancelled")
                .params(Document.of(Map.of("reason", Document.of("no id"))))
                .build()));
        // The string id cancels only the string subscription.
        cancel(Document.of("7"));
        barrier();

        server.refreshTools();
        assertEquals("7", rawSubscriptionId(output.read()));
        output.assertNoOutput(100);

        cancel(Document.of(7));
        barrier();
        server.refreshTools();
        output.assertNoOutput(100);

        // A cancelled subscription is never answered, even on graceful shutdown.
        server.shutdown().join();
        server = null;
        output.assertNoOutput(100);
    }

    @Test
    void shutdownCompletesOpenSubscriptions() {
        start();
        send("s", "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of(true))));
        read();

        server.shutdown().join();
        server = null;
        var complete = read();

        assertEquals("s", complete.getMember("id").asString());
        assertNull(complete.getMember("error"));
        var result = complete.getMember("result");
        assertEquals("complete", result.getMember("resultType").asString());
        assertEquals("s", result.getMember("_meta").getMember(McpWireNames.SUBSCRIPTION_ID).asString());
        assertEquals("test-server",
                result.getMember("_meta").getMember(McpWireNames.SERVER_INFO).getMember("name").asString());
    }

    @Test
    void endOfInputDropsSubscriptionsWithoutAResponse() throws Exception {
        var clientEnd = new PipedOutputStream();
        var serverInput = new PipedInputStream(clientEnd, 64 * 1024);
        server = StdioMcpServer.builder()
                .engine(engine(McpInterceptor.NOOP))
                .input(serverInput)
                .output(output)
                .build();
        server.start();

        clientEnd.write((McpJson.CODEC.serializeToString(request(
                "s",
                "subscriptions/listen",
                listen(Map.of("toolsListChanged", Document.of(true))))) + "\n")
                .getBytes(StandardCharsets.UTF_8));
        clientEnd.flush();
        read();
        clientEnd.close();

        // Once input ends, the stream is gone: neither changes nor a completion are written.
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            while (true) {
                server.refreshTools();
                if (!output.hasOutput()) {
                    Thread.sleep(50);
                    if (!output.hasOutput()) {
                        return;
                    }
                }
                output.read();
            }
        });
        server.shutdown().join();
        server = null;
        output.assertNoOutput(100);
    }

    @Test
    void untaggedChangesAreSentOnlyAfterInitialize() {
        start();

        server.refreshTools();
        output.assertNoOutput(100);

        initialize();
        server.refreshTools();
        assertEquals(TOOLS_CHANGED, output.read().strip());
        remote.emit("notifications/tools/list_changed", null);
        assertEquals(TOOLS_CHANGED, output.read().strip());
        output.assertNoOutput(100);
    }

    @Test
    void remoteChangesReachOnlyMatchingSubscriptionsWithTheirOwnTag() {
        start();
        send("d", "server/discover", modern(Map.of()));
        read();
        send("tools", "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of(true))));
        read();
        send("prompts", "subscriptions/listen", listen(Map.of("promptsListChanged", Document.of(true))));
        read();

        // An upstream tag must never be echoed downstream.
        remote.emit("notifications/prompts/list_changed",
                Document.of(Map.of("_meta",
                        Document.of(Map.of(McpWireNames.SUBSCRIPTION_ID, Document.of("upstream-1"))))));
        var prompts = read();
        assertEquals("notifications/prompts/list_changed", prompts.getMember("method").asString());
        assertEquals("prompts", subscriptionId(prompts).asString());
        output.assertNoOutput(100);

        remote.emit("notifications/tools/list_changed", null);
        assertEquals("tools", subscriptionId(read()).asString());
        output.assertNoOutput(100);
    }

    @Test
    void resourceChangesAreOnlyRelayedToHandshakeConnections() {
        start();
        send("d", "server/discover", modern(Map.of()));
        read();

        remote.emit("notifications/resources/list_changed", null);
        remote.emit("notifications/resources/updated", Document.of(Map.of("uri", Document.of("file:///a"))));
        output.assertNoOutput(100);

        // Request-scoped notifications keep flowing.
        remote.emit("notifications/progress",
                Document.of(Map.of("progressToken", Document.of("t"), "progress", Document.of(1))));
        assertEquals("notifications/progress", read().getMember("method").asString());

        initialize();
        remote.emit("notifications/resources/list_changed", null);
        assertEquals("notifications/resources/list_changed", read().getMember("method").asString());
    }

    @Test
    void localChangesNotifyToolAndPromptSubscribers() {
        start();
        send("tools", "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of(true))));
        read();
        send("prompts", "subscriptions/listen", listen(Map.of("promptsListChanged", Document.of(true))));
        read();

        server.addRemoteClient(new FakeRemote("added"));

        var methods = new ArrayList<String>();
        for (int i = 0; i < 2; i++) {
            var message = read();
            methods.add(message.getMember("method").asString() + "@" + subscriptionId(message).asString());
        }
        assertEquals(
                List.of(
                        "notifications/prompts/list_changed@prompts",
                        "notifications/tools/list_changed@tools"),
                methods.stream().sorted().toList());
        output.assertNoOutput(100);
    }

    @Test
    void handshakeConnectionsKeepTheSingleToolsNotificationForLocalChanges() {
        start();
        initialize();

        server.addRemoteClient(new FakeRemote("added"));

        assertEquals(TOOLS_CHANGED, output.read().strip());
        output.assertNoOutput(100);
    }

    @Test
    void connectionsUsingBothEraReceiveBothCopies() {
        start();
        initialize();
        send("s", "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of(true))));
        read();

        server.refreshTools();

        assertEquals(TOOLS_CHANGED, output.read().strip());
        assertEquals("s", subscriptionId(read()).asString());
        output.assertNoOutput(100);
    }

    @Test
    void acknowledgmentPrecedesChangesThatRaceActivation() throws Exception {
        var inAfterHook = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        start(new McpInterceptor() {
            @Override
            public void readAfterExecution(McpExecutionContext context, McpOutcome outcome, RuntimeException error) {
                if (context.call() instanceof McpCall.Listen) {
                    inAfterHook.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });

        send("s", "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of(true))));
        assertTrue(inAfterHook.await(2, TimeUnit.SECONDS));
        // The subscription is not active yet, so this change is not delivered to it.
        server.refreshTools();
        output.assertNoOutput(100);
        release.countDown();

        assertEquals("notifications/subscriptions/acknowledged", read().getMember("method").asString());
        server.refreshTools();
        assertEquals("notifications/tools/list_changed", read().getMember("method").asString());
    }

    @Test
    void interceptorsCanRejectASubscription() {
        start(new McpInterceptor() {
            @Override
            public McpOutcome modifyAfterExecution(
                    McpExecutionContext context,
                    McpOutcome outcome,
                    RuntimeException error
            ) {
                if (context.call() instanceof McpCall.Listen listen) {
                    return new McpOutcome.Failure(listen.id(), new McpError(-32603, "No subscriptions", null));
                }
                return McpInterceptor.super.modifyAfterExecution(context, outcome, error);
            }
        });

        send("s", "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of(true))));

        assertEquals(-32603, errorCode(read()));
        server.refreshTools();
        output.assertNoOutput(100);
    }

    @Test
    void httpNeitherAdvertisesNorAcceptsListen() {
        try (var engine = engine(McpInterceptor.NOOP)) {
            var handler = new McpHttpHandler(engine);

            var discover = handler.handle(
                    request("d", "server/discover", modern(Map.of())),
                    Map.of(
                            "MCP-Protocol-Version",
                            List.of("2026-07-28"),
                            "Mcp-Method",
                            List.of("server/discover")));
            assertNull(discover.body()
                    .getResult()
                    .getMember("capabilities")
                    .getMember("tools")
                    .getMember("listChanged"));

            var listen = handler.handle(
                    request("l", "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of(true)))),
                    Map.of(
                            "MCP-Protocol-Version",
                            List.of("2026-07-28"),
                            "Mcp-Method",
                            List.of("subscriptions/listen")));
            assertEquals(404, listen.statusCode());
            assertEquals(-32601, listen.body().getError().getCode());
        }
    }

    @Test
    void engineExecuteReturnsNoResponseForAnAcceptedListen() {
        try (var engine = engine(McpInterceptor.NOOP)) {
            assertNull(engine.execute(
                    request("s", "subscriptions/listen", listen(Map.of("toolsListChanged", Document.of(true)))),
                    null));
        }
    }

    private void start() {
        start(McpInterceptor.NOOP);
    }

    private void start(McpInterceptor interceptor) {
        server = StdioMcpServer.builder()
                .engine(engine(interceptor))
                .input(input)
                .output(output)
                .build();
        server.start();
    }

    private McpEngine engine(McpInterceptor interceptor) {
        return McpEngine.builder()
                .name("test-server")
                .remoteClients(List.of(remote))
                .interceptor(interceptor)
                .build();
    }

    private void initialize() {
        send(1,
                "initialize",
                Document.of(Map.of(
                        "protocolVersion",
                        Document.of("2025-11-25"),
                        "capabilities",
                        Document.of(Map.of()),
                        "clientInfo",
                        Document.of(Map.of("name", Document.of("client"), "version", Document.of("1"))))));
        assertNull(read().getMember("error"));
    }

    /**
     * Cancellations are applied in arrival order on the reader thread; once a later request is answered,
     * every earlier cancellation has taken effect.
     */
    private void barrier() {
        send("barrier", "server/discover", modern(Map.of()));
        assertEquals("barrier", read().getMember("id").asString());
    }

    private void cancel(Document requestId) {
        writeLine(McpJson.CODEC.serializeToString(JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .method("notifications/cancelled")
                .params(Document.of(Map.of("requestId", requestId)))
                .build()));
    }

    private void send(Object id, String method, Document params) {
        writeLine(McpJson.CODEC.serializeToString(request(id, method, params)));
    }

    private void writeLine(String line) {
        input.write(line + "\n");
    }

    private Document read() {
        var line = assertTimeoutPreemptively(Duration.ofSeconds(2), output::read);
        return McpJson.CODEC.createDeserializer(line.getBytes(StandardCharsets.UTF_8)).readDocument();
    }

    private static JsonRpcRequest request(Object id, String method, Document params) {
        return JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(id instanceof Integer number ? Document.of(number) : Document.of((String) id))
                .method(method)
                .params(params)
                .build();
    }

    private static Document listen(Map<String, Document> filter) {
        return modern(Map.of("notifications", Document.of(filter)));
    }

    private static Document modern(Map<String, Document> members) {
        var params = new HashMap<>(members);
        params.put("_meta",
                Document.of(Map.of(
                        "io.modelcontextprotocol/protocolVersion",
                        Document.of("2026-07-28"),
                        "io.modelcontextprotocol/clientCapabilities",
                        Document.of(Map.of()))));
        return Document.of(params);
    }

    private static Document subscriptionId(Document message) {
        return message.getMember("params").getMember("_meta").getMember(McpWireNames.SUBSCRIPTION_ID);
    }

    /**
     * Returns the subscription id exactly as written, so string and number ids stay distinguishable.
     */
    private static String rawSubscriptionId(String line) {
        var key = "\"" + McpWireNames.SUBSCRIPTION_ID + "\":";
        var start = line.indexOf(key) + key.length();
        var end = line.indexOf('}', start);
        return line.substring(start, end).strip();
    }

    private static int errorCode(Document response) {
        return response.getMember("error").getMember("code").asInteger();
    }

    /**
     * A session-based remote: it rejects {@code server/discover}, so the catalog initializes it, which
     * installs the notification consumer.
     */
    private static final class FakeRemote extends McpRemoteClient {
        private final String name;

        FakeRemote(String name) {
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
            if (request.getId() == null) {
                return null;
            }
            if ("server/discover".equals(request.getMethod())) {
                return JsonRpcResponse.builder()
                        .jsonrpc("2.0")
                        .id(request.getId())
                        .error(JsonRpcErrorResponse.builder().code(-32601).message("Method not found").build())
                        .build();
            }
            if ("initialize".equals(request.getMethod())) {
                return JsonRpcResponse.builder()
                        .jsonrpc("2.0")
                        .id(request.getId())
                        .result(Document.of(Map.of("protocolVersion", Document.of("2025-06-18"))))
                        .build();
            }
            return JsonRpcResponse.builder()
                    .jsonrpc("2.0")
                    .id(request.getId())
                    .result(Document.of(Map.of()))
                    .build();
        }

        void emit(String method, Document params) {
            var notification = JsonRpcRequest.builder().jsonrpc("2.0").method(method);
            if (params != null) {
                notification.params(params);
            }
            notify(notification.build());
        }

        @Override
        public void start() {}

        @Override
        public void close() {}

        @Override
        public String name() {
            return name;
        }
    }
}
