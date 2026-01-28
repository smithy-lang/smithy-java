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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.model.shapes.ShapeType;

class HttpMcpProxyTest {
    private static final JsonCodec JSON_CODEC = JsonCodec.builder().build();

    private HttpServer mockServer;
    private HttpMcpProxy proxy;
    private String serverUrl;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        int port = mockServer.getAddress().getPort();
        serverUrl = "http://localhost:" + port + "/mcp";

        mockServer.createContext("/mcp", new MockMcpHandler());
        mockServer.start();

        proxy = HttpMcpProxy.builder()
                .endpoint(serverUrl)
                .name("Test MCP")
                .build();
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
        if (proxy != null) {
            proxy.shutdown().join();
        }
    }

    @Test
    void testBuilderValidation() {
        assertThrows(IllegalArgumentException.class, () -> HttpMcpProxy.builder().build());

        assertThrows(IllegalArgumentException.class, () -> HttpMcpProxy.builder().endpoint("").build());
    }

    @Test
    void testBuilderWithCustomName() {
        HttpMcpProxy customProxy = HttpMcpProxy.builder()
                .endpoint(serverUrl)
                .name("Custom Name")
                .build();

        assertEquals("Custom Name", customProxy.name());
        customProxy.shutdown().join();
    }

    @Test
    void testBuilderWithHeaders() {
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        HttpMcpProxy proxyWithHeaders = HttpMcpProxy.builder()
                .endpoint(serverUrl)
                .signer((request, identity, context) -> {
                    var r = request.toModifiable();
                    var h = r.headers().toModifiable();
                    headers.forEach(h::setHeader);
                    r.setHeaders(h);
                    return r;
                })
                .build();

        assertNotNull(proxyWithHeaders);
        proxyWithHeaders.shutdown().join();
    }

    @Test
    void testBuilderWithDynamicHeaders() {
        int[] counter = {0};
        HttpMcpProxy proxyWithDynamicHeaders = HttpMcpProxy.builder()
                .endpoint(serverUrl)
                .signer((request, identity, context) -> {
                    var r = request.toModifiable();
                    var h = r.headers().toModifiable();
                    h.setHeader("X-Request-Count", String.valueOf(++counter[0]));
                    r.setHeaders(h);
                    return r;
                })
                .build();

        assertNotNull(proxyWithDynamicHeaders);
        proxyWithDynamicHeaders.shutdown().join();
    }

    @Test
    void testDefaultName() {
        HttpMcpProxy defaultProxy = HttpMcpProxy.builder()
                .endpoint(serverUrl)
                .build();

        assertEquals("localhost", defaultProxy.name());
        defaultProxy.shutdown().join();
    }

    @Test
    void testSanitizedName() {
        HttpMcpProxy sanitizedProxy = HttpMcpProxy.builder()
                .endpoint("http://api.example.com:8080/path")
                .build();

        assertEquals("api-example-com", sanitizedProxy.name());
        sanitizedProxy.shutdown().join();
    }

    @Test
    void testRpcCall() {
        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("test/method")
                .id(Document.of(1))
                .jsonrpc("2.0")
                .build();

        CompletableFuture<JsonRpcResponse> future = proxy.rpc(request);
        JsonRpcResponse response = future.join();

        assertNotNull(response);
        assertEquals("2.0", response.getJsonrpc());
        assertEquals(1, response.getId().asInteger());
        assertEquals("success", response.getResult().asString());
    }

    @Test
    void testRpcWithNullRequest() {
        CompletableFuture<JsonRpcResponse> future = proxy.rpc(null);

        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof NullPointerException);
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

        CompletableFuture<JsonRpcResponse> future = proxy.rpc(request);
        JsonRpcResponse response = future.join();

        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals(500, response.getError().getCode());
        assertTrue(response.getError().getMessage().contains("HTTP 500"));
    }

    @Test
    void testStartAndShutdown() {
        assertDoesNotThrow(() -> {
            proxy.start();
            proxy.shutdown().join();
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

        CompletableFuture<JsonRpcResponse> future = proxy.rpc(request);
        JsonRpcResponse response = future.join();

        assertNotNull(response);
        assertEquals("2.0", response.getJsonrpc());
        assertEquals(1, response.getId().asInteger());
        assertEquals("final result", response.getResult().asString());
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
                ProtocolVersion.defaultVersion());

        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("test/streaming")
                .id(Document.of(1))
                .jsonrpc("2.0")
                .build();

        CompletableFuture<JsonRpcResponse> future = proxy.rpc(request);
        JsonRpcResponse response = future.join();

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
    void testSseStreamingWithoutFinalResponse() throws IOException {
        // Set up SSE handler that doesn't send a final response
        mockServer.removeContext("/mcp");
        mockServer.createContext("/mcp", new SseStreamingNoFinalResponseHandler());

        JsonRpcRequest request = JsonRpcRequest.builder()
                .method("test/streaming")
                .id(Document.of(1))
                .jsonrpc("2.0")
                .build();

        CompletableFuture<JsonRpcResponse> future = proxy.rpc(request);
        JsonRpcResponse response = future.join();

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

        CompletableFuture<JsonRpcResponse> future = proxy.rpc(request);
        JsonRpcResponse response = future.join();

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

        CompletableFuture<JsonRpcResponse> future = proxy.rpc(request);
        JsonRpcResponse response = future.join();

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

        CompletableFuture<JsonRpcResponse> future1 = proxy.rpc(request1);
        JsonRpcResponse response1 = future1.join();

        assertNotNull(response1);
        assertEquals("session-created", response1.getResult().asString());

        // Second request - should include session ID
        JsonRpcRequest request2 = JsonRpcRequest.builder()
                .method("test/method")
                .id(Document.of(2))
                .jsonrpc("2.0")
                .build();

        CompletableFuture<JsonRpcResponse> future2 = proxy.rpc(request2);
        JsonRpcResponse response2 = future2.join();

        assertNotNull(response2);
        assertEquals("session-valid", response2.getResult().asString());
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

    private static class SseStreamingWithNotificationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sseResponse = new StringBuilder();

            // Send a notification first
            sseResponse.append(
                    "data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{\"progress\":50}}\n\n");

            // Then send the final response
            sseResponse.append("data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"final result\"}\n\n");

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
}
