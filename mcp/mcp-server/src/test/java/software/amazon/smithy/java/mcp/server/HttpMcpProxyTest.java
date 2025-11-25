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
}
