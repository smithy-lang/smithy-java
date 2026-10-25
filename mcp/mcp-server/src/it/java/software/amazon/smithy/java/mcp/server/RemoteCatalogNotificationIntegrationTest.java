/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;

class RemoteCatalogNotificationIntegrationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stdioListingAfterHttpNotificationWaitsForFreshCatalog(boolean failRefresh) throws Exception {
        var refreshEntered = new CountDownLatch(1);
        var releaseRefresh = new CountDownLatch(1);
        var discovered = new AtomicBoolean();
        var unavailable = new AtomicBoolean(failRefresh);
        var listings = new AtomicInteger();
        var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            http.setExecutor(executor);
            http.createContext("/mcp", exchange -> {
                try (exchange) {
                    var request = McpJson.CODEC.deserializeShape(
                            exchange.getRequestBody().readAllBytes(),
                            JsonRpcRequest.builder());
                    if (request.getId() == null) {
                        exchange.sendResponseHeaders(202, -1);
                        return;
                    }
                    Map<String, ?> result;
                    boolean sendNotification = false;
                    switch (request.getMethod()) {
                        case "initialize" -> result = Map.of(
                                "protocolVersion",
                                "2025-06-18",
                                "capabilities",
                                Map.of("tools", Map.of("listChanged", true)),
                                "serverInfo",
                                Map.of("name", "discovery-server", "version", "1"));
                        case "tools/list" -> {
                            listings.incrementAndGet();
                            if (discovered.get()) {
                                refreshEntered.countDown();
                                try {
                                    if (!releaseRefresh.await(5, SECONDS)) {
                                        throw new IOException("Refresh was not released");
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException(e);
                                }
                                if (unavailable.get()) {
                                    respond(exchange,
                                            "application/json",
                                            McpJson.CODEC.serializeToString(JsonRpcResponse.builder()
                                                    .jsonrpc("2.0")
                                                    .id(request.getId())
                                                    .error(JsonRpcErrorResponse.builder()
                                                            .code(-32603)
                                                            .message("Catalog unavailable")
                                                            .build())
                                                    .build()));
                                    return;
                                }
                            }
                            result = Map.of("tools",
                                    discovered.get()
                                            ? List.of(tool("discover"), tool("new_tool"))
                                            : List.of(tool("discover")));
                        }
                        case "prompts/list" -> result = Map.of("prompts", List.of());
                        case "tools/call" -> {
                            sendNotification = request.getParams().getMember("name").asString().equals("discover");
                            if (sendNotification) {
                                discovered.set(true);
                            }
                            result = Map.of("content", List.of(Map.of("type", "text", "text", "success")));
                        }
                        default -> throw new IOException("Unexpected method: " + request.getMethod());
                    }
                    var response = McpJson.CODEC.serializeToString(JsonRpcResponse.builder()
                            .jsonrpc("2.0")
                            .id(request.getId())
                            .result(Document.ofObject(result))
                            .build());
                    if (sendNotification) {
                        respond(exchange,
                                "text/event-stream",
                                "data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}\n\n"
                                        + "data: " + response + "\n\n");
                    } else {
                        respond(exchange, "application/json", response);
                    }
                }
            });
            http.start();

            var input = new TestInputStream();
            var output = new TestOutputStream();
            var remote = HttpMcpClient.builder()
                    .endpoint("http://127.0.0.1:" + http.getAddress().getPort() + "/mcp")
                    .build();
            var server = StdioMcpServer.builder()
                    .engine(McpEngine.builder().remoteClients(List.of(remote)).build())
                    .input(input)
                    .output(output)
                    .build();
            server.start();
            try {
                send(input,
                        1,
                        "initialize",
                        Map.of(
                                "protocolVersion",
                                "2025-06-18",
                                "capabilities",
                                Map.of(),
                                "clientInfo",
                                Map.of("name", "test", "version", "1")));
                assertResponse(read(output), 1);
                send(input, null, "notifications/initialized", Map.of());
                send(input, 2, "tools/list", Map.of());
                assertEquals(List.of("discover"), toolNames(assertResponse(read(output), 2)));

                send(input, 3, "tools/call", Map.of("name", "discover", "arguments", Map.of()));
                var notification = read(output);
                assertEquals("notifications/tools/list_changed", notification.getMember("method").asString());
                assertNull(notification.getMember("id"));
                assertResponse(read(output), 3);
                assertEquals(1, listings.get(), "Notifications must not fetch on their own");

                send(input, 4, "tools/list", Map.of());
                assertTrue(refreshEntered.await(2, SECONDS));
                output.assertNoOutput(100);
                releaseRefresh.countDown();
                var listing = read(output);
                if (failRefresh) {
                    assertError(listing, 4);
                    unavailable.set(false);
                    send(input, 5, "tools/list", Map.of());
                    assertError(read(output), 5);
                    assertEquals(2, listings.get());
                    output.assertNoOutput(100);
                    return;
                }
                assertEquals(List.of("discover", "new_tool"), toolNames(assertResponse(listing, 4)));
                send(input, 6, "tools/list", Map.of());
                assertEquals(List.of("discover", "new_tool"), toolNames(assertResponse(read(output), 6)));
                assertEquals(2, listings.get());
                send(input, 7, "tools/call", Map.of("name", "new_tool", "arguments", Map.of()));
                assertEquals("success",
                        assertResponse(read(output), 7)
                                .getMember("content")
                                .asList()
                                .getFirst()
                                .getMember("text")
                                .asString());
                output.assertNoOutput(100);
            } finally {
                releaseRefresh.countDown();
                server.shutdown().join();
            }
        } finally {
            releaseRefresh.countDown();
            http.stop(0);
        }
    }

    @Test
    void handshakeOnlyStdioServerKeepsDeliveringListChangedToStatelessCapableClients() throws Exception {
        var discovered = new AtomicBoolean();
        var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            http.setExecutor(executor);
            http.createContext("/mcp", exchange -> {
                try (exchange) {
                    var request = McpJson.CODEC.deserializeShape(
                            exchange.getRequestBody().readAllBytes(),
                            JsonRpcRequest.builder());
                    if (request.getId() == null) {
                        exchange.sendResponseHeaders(202, -1);
                        return;
                    }
                    Map<String, ?> result;
                    boolean sendNotification = false;
                    switch (request.getMethod()) {
                        case "initialize" -> result = Map.of(
                                "protocolVersion",
                                "2025-06-18",
                                "capabilities",
                                Map.of("tools", Map.of("listChanged", true)),
                                "serverInfo",
                                Map.of("name", "discovery-server", "version", "1"));
                        case "tools/list" -> result = Map.of("tools",
                                discovered.get()
                                        ? List.of(tool("discover"), tool("new_tool"))
                                        : List.of(tool("discover")));
                        case "prompts/list" -> result = Map.of("prompts", List.of());
                        case "tools/call" -> {
                            sendNotification = request.getParams().getMember("name").asString().equals("discover");
                            if (sendNotification) {
                                discovered.set(true);
                            }
                            result = Map.of("content", List.of(Map.of("type", "text", "text", "success")));
                        }
                        default -> throw new IOException("Unexpected method: " + request.getMethod());
                    }
                    var response = McpJson.CODEC.serializeToString(JsonRpcResponse.builder()
                            .jsonrpc("2.0")
                            .id(request.getId())
                            .result(Document.ofObject(result))
                            .build());
                    if (sendNotification) {
                        respond(exchange,
                                "text/event-stream",
                                "data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}\n\n"
                                        + "data: " + response + "\n\n");
                    } else {
                        respond(exchange, "application/json", response);
                    }
                }
            });
            http.start();

            var input = new TestInputStream();
            var output = new TestOutputStream();
            var remote = HttpMcpClient.builder()
                    .endpoint("http://127.0.0.1:" + http.getAddress().getPort() + "/mcp")
                    .build();
            var server = StdioMcpServer.builder()
                    .addRemoteClient(remote)
                    .protocolVersions(
                            KnownProtocolVersion.V2025_11_25,
                            KnownProtocolVersion.V2025_06_18,
                            KnownProtocolVersion.V2025_03_26,
                            KnownProtocolVersion.V2024_11_05)
                    .input(input)
                    .output(output)
                    .build();
            server.start();
            try {
                // A client that prefers 2026-07-28 probes first, learns the server only speaks the handshake
                // versions, and initializes instead.
                send(input,
                        1,
                        "server/discover",
                        Map.of("_meta",
                                Map.of(
                                        "io.modelcontextprotocol/protocolVersion",
                                        "2026-07-28",
                                        "io.modelcontextprotocol/clientCapabilities",
                                        Map.of(),
                                        "io.modelcontextprotocol/clientInfo",
                                        Map.of("name", "test", "version", "1"))));
                var probe = read(output);
                assertEquals(1, probe.getMember("id").asInteger());
                assertEquals(-32022, probe.getMember("error").getMember("code").asInteger());

                send(input,
                        2,
                        "initialize",
                        Map.of(
                                "protocolVersion",
                                "2026-07-28",
                                "capabilities",
                                Map.of(),
                                "clientInfo",
                                Map.of("name", "test", "version", "1")));
                var initialize = assertResponse(read(output), 2);
                assertEquals("2025-11-25", initialize.getMember("protocolVersion").asString());
                assertTrue(initialize.getMember("capabilities")
                        .getMember("tools")
                        .getMember("listChanged")
                        .asBoolean());
                send(input, null, "notifications/initialized", Map.of());

                send(input, 3, "tools/list", Map.of());
                assertEquals(List.of("discover"), toolNames(assertResponse(read(output), 3)));

                send(input, 4, "tools/call", Map.of("name", "discover", "arguments", Map.of()));
                var notification = read(output);
                assertEquals("notifications/tools/list_changed", notification.getMember("method").asString());
                assertNull(notification.getMember("id"));
                assertResponse(read(output), 4);

                send(input, 5, "tools/list", Map.of());
                assertEquals(List.of("discover", "new_tool"), toolNames(assertResponse(read(output), 5)));
                send(input, 6, "tools/call", Map.of("name", "new_tool", "arguments", Map.of()));
                assertEquals("success",
                        assertResponse(read(output), 6)
                                .getMember("content")
                                .asList()
                                .getFirst()
                                .getMember("text")
                                .asString());
                output.assertNoOutput(100);
            } finally {
                server.shutdown().join();
            }
        } finally {
            http.stop(0);
        }
    }

    private static Map<String, Object> tool(String name) {
        return Map.of("name", name, "inputSchema", Map.of("type", "object", "properties", Map.of()));
    }

    private static void respond(HttpExchange exchange, String contentType, String content) throws IOException {
        var bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void send(TestInputStream input, Integer id, String method, Map<String, ?> params) {
        input.write(McpJson.CODEC.serializeToString(JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(id == null ? null : Document.of(id))
                .method(method)
                .params(Document.ofObject(params))
                .build()) + "\n");
    }

    private static Document read(TestOutputStream output) {
        var line = assertTimeoutPreemptively(Duration.ofSeconds(3), output::read);
        return McpJson.CODEC.createDeserializer(line.getBytes(StandardCharsets.UTF_8)).readDocument();
    }

    private static Document assertResponse(Document message, int id) {
        assertNotNull(message.getMember("id"), message.toString());
        assertEquals(id, message.getMember("id").asInteger());
        assertNull(message.getMember("error"), message.toString());
        return message.getMember("result");
    }

    private static void assertError(Document message, int id) {
        assertEquals(id, message.getMember("id").asInteger());
        assertEquals(-32603, message.getMember("error").getMember("code").asInteger());
        assertEquals("Internal error", message.getMember("error").getMember("message").asString());
        assertNull(message.getMember("result"));
    }

    private static List<String> toolNames(Document result) {
        return result.getMember("tools")
                .asList()
                .stream()
                .map(tool -> tool.getMember("name").asString())
                .sorted()
                .toList();
    }
}
