/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.ListToolsResult;
import software.amazon.smithy.java.mcp.model.ToolInfo;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

class HttpMcpClientTest {
    private static final JsonCodec JSON_CODEC = JsonCodec.builder().build();

    private HttpServer mockServer;
    private ExecutorService mockServerExecutor;
    private HttpMcpClient proxy;
    private String serverUrl;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        mockServer.setExecutor(mockServerExecutor);
        int port = mockServer.getAddress().getPort();
        serverUrl = "http://localhost:" + port + "/mcp";

        mockServer.createContext("/mcp", new MockMcpHandler());
        mockServer.start();

        proxy = HttpMcpClient.builder()
                .endpoint(serverUrl)
                .name("Test MCP")
                .build();
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
        if (mockServerExecutor != null) {
            mockServerExecutor.shutdownNow();
        }
        if (proxy != null) {
            proxy.close();
        }
    }

    @Test
    void testBuilderValidation() {
        assertThrows(IllegalArgumentException.class, () -> HttpMcpClient.builder().build());

        assertThrows(IllegalArgumentException.class, () -> HttpMcpClient.builder().endpoint("").build());
    }

    @Test
    void testBuilderRejectsSignerAndAuthSchemeTogether() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpMcpClient.builder()
                        .endpoint(serverUrl)
                        .signer((request, identity, context) -> new SignResult<>(request))
                        .authScheme(new TestAuthScheme())
                        .identityResolver(TestIdentityResolver.INSTANCE)
                        .build());
    }

    @Test
    void testBuilderRejectsAuthSchemeWithoutIdentityResolver() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpMcpClient.builder()
                        .endpoint(serverUrl)
                        .authScheme(new TestAuthScheme())
                        .build());
    }

    @Test
    void testBuilderRejectsIdentityResolverWithoutAuthScheme() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpMcpClient.builder()
                        .endpoint(serverUrl)
                        .identityResolver(TestIdentityResolver.INSTANCE)
                        .build());
    }

    @Test
    void testAuthSchemeSignsRequest() throws IOException {
        String[] capturedHeader = {null};

        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", exchange -> {
            capturedHeader[0] = exchange.getRequestHeaders().getFirst("X-Test-Signed");
            String response = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"signed\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
            exchange.close();
        });

        HttpMcpClient authProxy = HttpMcpClient.builder()
                .endpoint(serverUrl)
                .authScheme(new TestAuthScheme())
                .identityResolver(TestIdentityResolver.INSTANCE)
                .build();

        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("test/method")
                .id(Document.of(1))
                .jsonrpc("2.0")
                .build();

        JsonRpcResponse response = authProxy.exchange(request);

        assertNotNull(response);
        assertEquals("signed", response.getResult().asString());
        assertEquals("test-token", capturedHeader[0]);
        authProxy.close();
    }

    @Test
    void testAuthSchemeReceivesSignerContext() throws IOException {
        String[] capturedRegion = {null};

        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", exchange -> {
            capturedRegion[0] = exchange.getRequestHeaders().getFirst("X-Region");
            String response = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"ok\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
            exchange.close();
        });

        Context signerCtx = Context.create();
        signerCtx.put(TestAuthScheme.REGION_KEY, "us-west-2");

        HttpMcpClient authProxy = HttpMcpClient.builder()
                .endpoint(serverUrl)
                .authScheme(new TestAuthScheme())
                .identityResolver(TestIdentityResolver.INSTANCE)
                .signerContext(signerCtx)
                .build();

        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("test/method")
                .id(Document.of(1))
                .jsonrpc("2.0")
                .build();

        JsonRpcResponse response = authProxy.exchange(request);

        assertNotNull(response);
        assertEquals("us-west-2", capturedRegion[0]);
        authProxy.close();
    }

    @Test
    void testBuilderWithCustomName() {
        HttpMcpClient customProxy = HttpMcpClient.builder()
                .endpoint(serverUrl)
                .name("Custom Name")
                .build();

        assertEquals("Custom Name", customProxy.name());
        customProxy.close();
    }

    @Test
    void testBuilderWithHeaders() {
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        HttpMcpClient proxyWithHeaders = HttpMcpClient.builder()
                .endpoint(serverUrl)
                .signer((request, identity, context) -> {
                    var r = request.toModifiable();
                    var h = r.headers().toModifiable();
                    headers.forEach(h::setHeader);
                    r.setHeaders(h);
                    return new SignResult<>(r);
                })
                .build();

        assertNotNull(proxyWithHeaders);
        proxyWithHeaders.close();
    }

    @Test
    void testBuilderWithDynamicHeaders() {
        int[] counter = {0};
        HttpMcpClient proxyWithDynamicHeaders = HttpMcpClient.builder()
                .endpoint(serverUrl)
                .signer((request, identity, context) -> {
                    var r = request.toModifiable();
                    var h = r.headers().toModifiable();
                    h.setHeader("X-Request-Count", String.valueOf(++counter[0]));
                    r.setHeaders(h);
                    return new SignResult<>(r);
                })
                .build();

        assertNotNull(proxyWithDynamicHeaders);
        proxyWithDynamicHeaders.close();
    }

    @Test
    void testDefaultName() {
        HttpMcpClient defaultProxy = HttpMcpClient.builder()
                .endpoint(serverUrl)
                .build();

        assertEquals("localhost", defaultProxy.name());
        defaultProxy.close();
    }

    @Test
    void testSanitizedName() {
        HttpMcpClient sanitizedProxy = HttpMcpClient.builder()
                .endpoint("http://api.example.com:8080/path")
                .build();

        assertEquals("api-example-com", sanitizedProxy.name());
        sanitizedProxy.close();
    }

    @Test
    void testRpcCall() {
        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("test/method")
                .id(Document.of(1))
                .jsonrpc("2.0")
                .build();

        JsonRpcResponse response = proxy.exchange(request);

        assertNotNull(response);
        assertEquals("2.0", response.getJsonrpc());
        assertEquals(1, response.getId().asInteger());
        assertEquals("success", response.getResult().asString());
    }

    @Test
    void testRpcWithNullRequest() {
        assertThrows(McpRemoteException.class, () -> proxy.exchange(null));
    }

    @Test
    void testNotificationAcceptsEmptySuccessfulResponse() throws IOException {
        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });

        var notification = JsonRpcRequest.builder()
                .method(McpMethod.Standard.NOTIFICATIONS_INITIALIZED.wireName())
                .jsonrpc("2.0")
                .build();

        assertNull(proxy.exchange(notification));
    }

    @Test
    void testRpcHttpError() throws IOException {
        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", exchange -> {
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
        });

        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("test/method")
                .id(Document.of(1))
                .jsonrpc("2.0")
                .build();

        JsonRpcResponse response = proxy.exchange(request);

        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals(1, response.getId().asInteger());
        assertEquals(-32000, response.getError().getCode());
        assertTrue(response.getError().getMessage().contains("HTTP 500"));
    }

    @Test
    void testStartAndShutdown() {
        assertDoesNotThrow(() -> {
            proxy.start();
            proxy.close();
        });
    }

    @Test
    void testSseStreamingResponse() throws IOException {
        // Set up SSE handler
        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", new SseStreamingHandler());

        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("test/streaming")
                .id(Document.of(1))
                .jsonrpc("2.0")
                .build();

        JsonRpcResponse response = proxy.exchange(request);

        assertNotNull(response);
        assertEquals("2.0", response.getJsonrpc());
        assertEquals(1, response.getId().asInteger());
        assertEquals("final result", response.getResult().asString());
    }

    @Test
    void testSseIgnoresResponsesForOtherRequestIds() throws IOException {
        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", new SseMismatchedResponseHandler());

        var response = proxy.exchange(JsonRpcRequest.builder()
                .method("test/streaming")
                .id(Document.of(BigInteger.ONE))
                .jsonrpc("2.0")
                .build());

        assertNotNull(response);
        assertEquals(BigInteger.ONE, response.getId().asBigInteger());
        assertEquals("matching result", response.getResult().asString());
    }

    @Test
    void testSseStreamingWithNotifications() throws IOException {
        // Track notifications
        final JsonRpcRequest[] capturedNotification = {null};

        // Set up SSE handler
        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", new SseStreamingWithNotificationsHandler());

        // Initialize proxy with notification consumer
        JsonRpcRequest initRequest = JsonRpcRequest.builder()
                .method("initialize")
                .id(Document.of(0))
                .jsonrpc("2.0")
                .build();

        proxy.initialize(
                notification -> {}, // Old-style consumer (not used)
                notification -> capturedNotification[0] = notification, // Request notification consumer
                initRequest,
                BuiltInProtocols.protocol(ProtocolVersion.defaultVersion()));

        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("test/streaming")
                .id(Document.of(1))
                .jsonrpc("2.0")
                .build();

        JsonRpcResponse response = proxy.exchange(request);

        // Verify final response
        assertNotNull(response);
        assertEquals("2.0", response.getJsonrpc());
        assertEquals(1, response.getId().asInteger());
        assertEquals("final result", response.getResult().asString());

        // Verify notification was captured (notifications don't have an id field)
        assertNotNull(capturedNotification[0]);
        assertNull(capturedNotification[0].getId());
    }

    @Test
    void testSseNotificationsAreDeliveredBeforeTheFinalResponse() throws IOException {
        var notificationObserved = new CountDownLatch(1);
        var observedBeforeFinal = new AtomicBoolean();

        mockServer.removeContext("/mcp");
        mockServer.createContext(
                "/mcp",
                new LiveSseNotificationHandler(notificationObserved, observedBeforeFinal));

        var initRequest = JsonRpcRequest.builder()
                .method(McpMethod.Standard.INITIALIZE.wireName())
                .id(Document.of(0))
                .jsonrpc("2.0")
                .build();
        proxy.initialize(
                ignored -> {},
                ignored -> notificationObserved.countDown(),
                initRequest,
                BuiltInProtocols.protocol(ProtocolVersion.defaultVersion()));

        var response = proxy.exchange(JsonRpcRequest.builder()
                .method("test/streaming")
                .id(Document.of(1))
                .jsonrpc("2.0")
                .build());

        assertEquals("final result", response.getResult().asString());
        assertTrue(observedBeforeFinal.get());
    }

    @Test
    void testSseStreamingWithoutFinalResponse() throws IOException {
        // Set up SSE handler that doesn't send a final response
        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", new SseStreamingNoFinalResponseHandler());

        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("test/streaming")
                .id(Document.of(1))
                .jsonrpc("2.0")
                .build();

        JsonRpcResponse response = proxy.exchange(request);

        // Should return an error response
        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals(-32001, response.getError().getCode());
        assertTrue(response.getError().getMessage().contains("SSE parsing error"));
    }

    @Test
    void testSseStreamingMalformedJson() throws IOException {
        // Set up SSE handler with malformed JSON
        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", new SseMalformedJsonHandler());

        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("test/streaming")
                .id(Document.of(1))
                .jsonrpc("2.0")
                .build();

        JsonRpcResponse response = proxy.exchange(request);

        // Should return an error response
        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals(-32001, response.getError().getCode());
    }

    @Test
    void testSseStreamingWithMethodInToolResponse() throws IOException {
        // This tests the bug fix where tool responses containing "method" in their data
        // were incorrectly classified as notifications
        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", new SseToolResponseWithMethodHandler());

        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("tools/call")
                .id(Document.of(1))
                .jsonrpc("2.0")
                .build();

        JsonRpcResponse response = proxy.exchange(request);

        // Should correctly parse as a response, not a notification
        assertNotNull(response);
        assertEquals("2.0", response.getJsonrpc());
        assertEquals(1, response.getId().asInteger());
        assertNotNull(response.getResult());

        // The result should contain the tool response with "method" in it
        Document result = response.getResult();
        assertTrue(result.isType(ShapeType.STRUCTURE) || result.isType(ShapeType.MAP));
        Document content = result.asStringMap().get("content");
        assertNotNull(content);
        assertTrue(content.asString().contains("method"));
    }

    @Test
    void testSessionIdHandling() throws IOException {
        // Set up handler that returns and expects session ID
        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", new SessionIdHandler());

        // First request - should be initialize to receive session ID
        JsonRpcRequest request1 = JsonRpcRequest.builder()
                .method("initialize")
                .id(Document.of(1))
                .jsonrpc("2.0")
                .params(Document.of(Map.of(
                        "protocolVersion",
                        Document.of("2024-11-05"),
                        "capabilities",
                        Document.of(Map.of()),
                        "clientInfo",
                        Document.of(Map.of(
                                "name",
                                Document.of("test-client"),
                                "version",
                                Document.of("1.0.0"))))))
                .build();

        JsonRpcResponse response1 = proxy.exchange(request1);

        assertNotNull(response1);
        assertEquals("session-created", response1.getResult().asString());

        // Second request - should include session ID
        JsonRpcRequest request2 = JsonRpcRequest.builder()
                .method("test/method")
                .id(Document.of(2))
                .jsonrpc("2.0")
                .build();

        JsonRpcResponse response2 = proxy.exchange(request2);

        assertNotNull(response2);
        assertEquals("session-valid", response2.getResult().asString());
    }

    @Test
    void expiredSessionIsReinitializedAndTheRequestIsRetried() throws IOException {
        var handler = new RecoveringSessionHandler();
        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", handler);

        var protocol = BuiltInProtocols.protocol(KnownProtocolVersion.V2025_11_25);
        proxy.initialize(
                ignored -> {},
                ignored -> {},
                JsonRpcRequest.builder()
                        .method(McpMethod.Standard.INITIALIZE.wireName())
                        .id(Document.of(1))
                        .jsonrpc("2.0")
                        .params(Document.of(Map.of(
                                "protocolVersion",
                                Document.of(protocol.id().identifier()))))
                        .build(),
                protocol);

        var response = proxy.exchange(JsonRpcRequest.builder()
                .method(McpMethod.Standard.PING.wireName())
                .id(Document.of(2))
                .jsonrpc("2.0")
                .build());

        assertEquals("recovered", response.getResult().asString());
        assertEquals(2, handler.initializeCount.get());
    }

    @Test
    void concurrentStaleSessionResponsesShareOneRecovery() throws Exception {
        var handler = new ConcurrentRecoveringSessionHandler();
        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", handler);

        var protocol = BuiltInProtocols.protocol(KnownProtocolVersion.V2025_11_25);
        proxy.initialize(
                ignored -> {},
                ignored -> {},
                JsonRpcRequest.builder()
                        .method(McpMethod.Standard.INITIALIZE.wireName())
                        .id(Document.of(1))
                        .jsonrpc("2.0")
                        .params(Document.of(Map.of(
                                "protocolVersion",
                                Document.of(protocol.id().identifier()))))
                        .build(),
                protocol);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> proxy.exchange(JsonRpcRequest.builder()
                    .method(McpMethod.Standard.PING.wireName())
                    .id(Document.of(2))
                    .jsonrpc("2.0")
                    .build()));
            var second = executor.submit(() -> proxy.exchange(JsonRpcRequest.builder()
                    .method(McpMethod.Standard.PING.wireName())
                    .id(Document.of(3))
                    .jsonrpc("2.0")
                    .build()));

            assertEquals("recovered", first.get().getResult().asString());
            assertEquals("recovered", second.get().getResult().asString());
        }
        assertEquals(2, handler.initializeCount.get());
        assertEquals(2, handler.recoveredRequestCount.get());
    }

    private static class SseStreamingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String sseResponse = "data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"final result\"}\n\n";

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, sseResponse.getBytes(StandardCharsets.UTF_8).length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(sseResponse.getBytes(StandardCharsets.UTF_8));
            } finally {
                exchange.close();
            }
        }
    }

    private static final class RecoveringSessionHandler implements HttpHandler {
        private final AtomicInteger initializeCount = new AtomicInteger();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var request = JsonRpcRequest.builder()
                    .deserialize(JSON_CODEC.createDeserializer(exchange.getRequestBody().readAllBytes()))
                    .build();
            if (McpMethod.Standard.NOTIFICATIONS_INITIALIZED.wireName().equals(request.getMethod())) {
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
                return;
            }

            if (McpHttpBinding.isInitialize(request)) {
                var count = initializeCount.incrementAndGet();
                exchange.getResponseHeaders().set("Mcp-Session-Id", "session-" + count);
                writeResponse(
                        exchange,
                        request.getId(),
                        Document.of(Map.of(
                                "protocolVersion",
                                Document.of(KnownProtocolVersion.V2025_11_25.identifier()))));
                return;
            }

            var sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
            if ("session-1".equals(sessionId)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            writeResponse(exchange, request.getId(), Document.of("recovered"));
        }

        private void writeResponse(
                HttpExchange exchange,
                Document id,
                Document result
        ) throws IOException {
            var body = JSON_CODEC.serializeToString(JsonRpcResponse.builder()
                    .jsonrpc("2.0")
                    .id(id)
                    .result(result)
                    .build()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            } finally {
                exchange.close();
            }
        }
    }

    private static final class ConcurrentRecoveringSessionHandler implements HttpHandler {
        private final AtomicInteger initializeCount = new AtomicInteger();
        private final AtomicInteger recoveredRequestCount = new AtomicInteger();
        private final AtomicInteger staleRequestOrder = new AtomicInteger();
        private final CountDownLatch staleRequests = new CountDownLatch(2);
        private final CountDownLatch firstRequestRecovered = new CountDownLatch(1);

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var request = JsonRpcRequest.builder()
                    .deserialize(JSON_CODEC.createDeserializer(exchange.getRequestBody().readAllBytes()))
                    .build();
            if (McpMethod.Standard.NOTIFICATIONS_INITIALIZED.wireName().equals(request.getMethod())) {
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
                return;
            }

            if (McpHttpBinding.isInitialize(request)) {
                var count = initializeCount.incrementAndGet();
                exchange.getResponseHeaders().set("Mcp-Session-Id", "session-" + count);
                writeResponse(
                        exchange,
                        request.getId(),
                        Document.of(Map.of(
                                "protocolVersion",
                                Document.of(KnownProtocolVersion.V2025_11_25.identifier()))));
                return;
            }

            var sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
            if ("session-1".equals(sessionId)) {
                staleRequests.countDown();
                try {
                    assertTrue(staleRequests.await(5, TimeUnit.SECONDS));
                    if (staleRequestOrder.incrementAndGet() > 1) {
                        assertTrue(firstRequestRecovered.await(5, TimeUnit.SECONDS));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            assertEquals("session-2", sessionId);
            recoveredRequestCount.incrementAndGet();
            firstRequestRecovered.countDown();
            writeResponse(exchange, request.getId(), Document.of("recovered"));
        }

        private void writeResponse(
                HttpExchange exchange,
                Document id,
                Document result
        ) throws IOException {
            var body = JSON_CODEC.serializeToString(JsonRpcResponse.builder()
                    .jsonrpc("2.0")
                    .id(id)
                    .result(result)
                    .build()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            } finally {
                exchange.close();
            }
        }
    }

    private static class SseMismatchedResponseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var sseResponse = """
                    data: {"jsonrpc":"2.0","id":999,"result":"wrong result"}

                    data: {"jsonrpc":"2.0","id":1,"result":"matching result"}

                    """;

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            var responseBytes = sseResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(responseBytes);
            } finally {
                exchange.close();
            }
        }
    }

    private static class SseStreamingWithNotificationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var request = JsonRpcRequest.builder()
                    .deserialize(JSON_CODEC.createDeserializer(exchange.getRequestBody().readAllBytes()))
                    .build();
            if (request.getId() == null) {
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
                return;
            }

            StringBuilder sseResponse = new StringBuilder();

            // Send a notification first
            sseResponse.append(
                    "data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{\"progress\":50}}\n\n");

            // Then send the final response
            sseResponse.append("data: {\"jsonrpc\":\"2.0\",\"id\":")
                    .append(request.getId().asNumber())
                    .append(",\"result\":\"final result\"}\n\n");

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            byte[] responseBytes = sseResponse.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            } finally {
                exchange.close();
            }
        }
    }

    private static class LiveSseNotificationHandler implements HttpHandler {
        private final CountDownLatch notificationObserved;
        private final AtomicBoolean observedBeforeFinal;

        LiveSseNotificationHandler(
                CountDownLatch notificationObserved,
                AtomicBoolean observedBeforeFinal
        ) {
            this.notificationObserved = notificationObserved;
            this.observedBeforeFinal = observedBeforeFinal;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var requestBytes = exchange.getRequestBody().readAllBytes();
            var request = JsonRpcRequest.builder()
                    .deserialize(JSON_CODEC.createDeserializer(requestBytes))
                    .build();

            if (McpMethod.Standard.NOTIFICATIONS_INITIALIZED.wireName().equals(request.getMethod())) {
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
                return;
            }
            if (McpMethod.Standard.INITIALIZE.wireName().equals(request.getMethod())) {
                writeJsonResponse(exchange, request.getId(), Document.of(Map.of()));
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write(("data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\","
                        + "\"params\":{\"progress\":50}}\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                output.flush();

                try {
                    observedBeforeFinal.set(notificationObserved.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                output.write(
                        "data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"final result\"}\n\n"
                                .getBytes(StandardCharsets.UTF_8));
            } finally {
                exchange.close();
            }
        }

        private void writeJsonResponse(
                HttpExchange exchange,
                Document id,
                Document result
        ) throws IOException {
            var response = JsonRpcResponse.builder()
                    .jsonrpc("2.0")
                    .id(id)
                    .result(result)
                    .build();
            var body = JSON_CODEC.serializeToString(response).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            } finally {
                exchange.close();
            }
        }
    }

    private static class SseStreamingNoFinalResponseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Only send notifications, no final response
            String sseResponse =
                    "data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{\"progress\":100}}\n\n";

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, sseResponse.getBytes(StandardCharsets.UTF_8).length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(sseResponse.getBytes(StandardCharsets.UTF_8));
            } finally {
                exchange.close();
            }
        }
    }

    private static class SseMalformedJsonHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String sseResponse = "data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":malformed\n\n";

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, sseResponse.getBytes(StandardCharsets.UTF_8).length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(sseResponse.getBytes(StandardCharsets.UTF_8));
            } finally {
                exchange.close();
            }
        }
    }

    private static class SseToolResponseWithMethodHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Simulate a tool response that contains "method" in its content
            // This should NOT be classified as a notification because it has an "id" field
            String sseResponse =
                    "data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":\"The HTTP method used was POST\",\"isError\":false}}\n\n";

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, sseResponse.getBytes(StandardCharsets.UTF_8).length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(sseResponse.getBytes(StandardCharsets.UTF_8));
            } finally {
                exchange.close();
            }
        }
    }

    @Test
    void testListToolsReturnsAContinuationForTheNextPage() throws IOException {
        // Real wire round-trip: the client must expose nextCursor as a lazy continuation and avoid
        // fetching the second page until that continuation is invoked.
        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", exchange -> {
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonRpcRequest request = JsonRpcRequest.builder()
                        .deserialize(JSON_CODEC.createDeserializer(requestBody.getBytes(StandardCharsets.UTF_8)))
                        .build();
                var params = request.getParams();
                String cursor = params != null && params.getMember("cursor") != null
                        ? params.getMember("cursor").asString()
                        : null;

                var page = ListToolsResult.builder();
                if (cursor == null) {
                    page.tools(List.of(tool("t1"), tool("t2"))).nextCursor("page2");
                } else {
                    page.tools(List.of(tool("t3")));
                }

                JsonRpcResponse response = JsonRpcResponse.builder()
                        .jsonrpc("2.0")
                        .id(request.getId())
                        .result(Document.of(page.build()))
                        .build();
                byte[] body = JSON_CODEC.serializeToString(response).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, 0);
            } finally {
                exchange.close();
            }
        });

        var first = proxy.listTools();
        assertEquals(List.of("t1", "t2"), first.items().stream().map(ToolInfo::getName).toList());

        var second = first.nextPage().orElseThrow().fetch();
        assertEquals(List.of("t3"), second.items().stream().map(ToolInfo::getName).toList());
    }

    @Test
    void modernListingsUseModernHeadersAndMetadata() throws IOException {
        var protocolHeader = new AtomicReference<String>();
        var methodHeader = new AtomicReference<String>();
        var requestBody = new AtomicReference<JsonRpcRequest>();
        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", exchange -> {
            try {
                protocolHeader.set(exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"));
                methodHeader.set(exchange.getRequestHeaders().getFirst("Mcp-Method"));
                requestBody.set(JsonRpcRequest.builder()
                        .deserialize(JSON_CODEC.createDeserializer(exchange.getRequestBody().readAllBytes()))
                        .build());

                var response = JsonRpcResponse.builder()
                        .jsonrpc("2.0")
                        .id(requestBody.get().getId())
                        .result(Document.of(ListToolsResult.builder().tools(List.of()).build()))
                        .build();
                var body = JSON_CODEC.serializeToString(response).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            } finally {
                exchange.close();
            }
        });

        proxy.usingProtocol(
                BuiltInProtocols.protocol(KnownProtocolVersion.V2026_07_28),
                proxy::listTools);

        assertEquals(KnownProtocolVersion.V2026_07_28.identifier(), protocolHeader.get());
        assertEquals(McpMethod.Standard.TOOLS_LIST.wireName(), methodHeader.get());
        var metadata = requestBody.get().getParams().getMember("_meta");
        assertEquals(
                KnownProtocolVersion.V2026_07_28.identifier(),
                metadata.getMember(McpWireNames.PROTOCOL_VERSION).asString());
        assertEquals(
                Map.of(),
                metadata.getMember(McpWireNames.CLIENT_CAPABILITIES).asStringMap());
    }

    private static ToolInfo tool(String name) {
        return ToolInfo.builder().name(name).build();
    }

    private static class MockMcpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
                return;
            }

            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            try {
                JsonRpcRequest request = JsonRpcRequest.builder()
                        .deserialize(JSON_CODEC.createDeserializer(requestBody.getBytes(StandardCharsets.UTF_8)))
                        .build();

                JsonRpcResponse response = JsonRpcResponse.builder()
                        .jsonrpc("2.0")
                        .id(request.getId())
                        .result(Document.of("success"))
                        .build();

                String responseBody = JSON_CODEC.serializeToString(response);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, 0);
            } finally {
                exchange.close();
            }
        }
    }

    private static class SessionIdHandler implements HttpHandler {
        private static final String SESSION_ID = "test-session-123";

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
                return;
            }

            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            try {
                JsonRpcRequest request = JsonRpcRequest.builder()
                        .deserialize(JSON_CODEC.createDeserializer(requestBody.getBytes(StandardCharsets.UTF_8)))
                        .build();

                // Check if session ID is present in request
                String sessionIdHeader = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
                String resultMessage;

                if (sessionIdHeader == null) {
                    // First request - return session ID
                    resultMessage = "session-created";
                    exchange.getResponseHeaders().set("Mcp-Session-Id", SESSION_ID);
                } else if (SESSION_ID.equals(sessionIdHeader)) {
                    // Subsequent request with valid session ID
                    resultMessage = "session-valid";
                } else {
                    // Invalid session ID
                    resultMessage = "session-invalid";
                }

                JsonRpcResponse response = JsonRpcResponse.builder()
                        .jsonrpc("2.0")
                        .id(request.getId())
                        .result(Document.of(resultMessage))
                        .build();

                String responseBody = JSON_CODEC.serializeToString(response);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, 0);
            } finally {
                exchange.close();
            }
        }
    }

    private record TestIdentity(String token) implements Identity {}

    private static final class TestIdentityResolver implements IdentityResolver<TestIdentity> {
        static final TestIdentityResolver INSTANCE = new TestIdentityResolver();

        @Override
        public IdentityResult<TestIdentity> resolveIdentity(Context requestProperties) {
            return IdentityResult.of(new TestIdentity("test-token"));
        }

        @Override
        public Class<TestIdentity> identityType() {
            return TestIdentity.class;
        }
    }

    private static final class TestAuthScheme implements AuthScheme<HttpRequest, TestIdentity> {
        static final Context.Key<String> REGION_KEY = Context.key("test-region");

        @Override
        public ShapeId schemeId() {
            return ShapeId.from("smithy.test#testAuth");
        }

        @Override
        public Class<HttpRequest> requestClass() {
            return HttpRequest.class;
        }

        @Override
        public Class<TestIdentity> identityClass() {
            return TestIdentity.class;
        }

        @Override
        public Context getSignerProperties(Context context) {
            var ctx = Context.create();
            var region = context.get(REGION_KEY);
            if (region != null) {
                ctx.put(REGION_KEY, region);
            }
            return ctx;
        }

        @Override
        public Signer<HttpRequest, TestIdentity> signer() {
            return (request, identity, properties) -> {
                var r = request.toModifiable();
                var h = r.headers().toModifiable();
                h.setHeader("X-Test-Signed", identity.token());
                var region = properties.get(REGION_KEY);
                if (region != null) {
                    h.setHeader("X-Region", region);
                }
                r.setHeaders(h);
                return new SignResult<>(r);
            };
        }
    }
}
