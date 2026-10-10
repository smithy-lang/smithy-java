/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;

class StdioSubscriptionsIntegrationTest {
    @Test
    void modernStdioClientLearnsAboutUpstreamToolChanges() throws Exception {
        var discovered = new AtomicBoolean();
        var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            http.setExecutor(executor);
            http.createContext("/mcp", exchange -> {
                try (exchange) {
                    var request = McpJson.CODEC.deserializeShape(exchange.getRequestBody().readAllBytes(),
                            JsonRpcRequest.builder());
                    if (request.getId() == null) {
                        exchange.sendResponseHeaders(202, -1);
                        return;
                    }
                    Map<String, ?> result;
                    boolean notify = false;
                    switch (request.getMethod()) {
                        case "server/discover" -> {
                            respond(exchange,
                                    "application/json",
                                    "{\"jsonrpc\":\"2.0\",\"id\":"
                                            + McpJson.CODEC.serializeToString(request.getId())
                                            + ",\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}");
                            return;
                        }
                        case "initialize" -> result = Map.of("protocolVersion",
                                "2025-06-18",
                                "capabilities",
                                Map.of("tools", Map.of("listChanged", true)),
                                "serverInfo",
                                Map.of("name", "upstream", "version", "1"));
                        case "tools/list" -> result = Map.of("tools",
                                discovered.get() ? List.of(tool("discover"), tool("new_tool"))
                                        : List.of(tool("discover")));
                        case "prompts/list" -> result = Map.of("prompts", List.of());
                        case "tools/call" -> {
                            notify = request.getParams().getMember("name").asString().equals("discover");
                            if (notify) {
                                discovered.set(true);
                            }
                            result = Map.of("content", List.of(Map.of("type", "text", "text", "success")));
                        }
                        default -> throw new IOException("Unexpected " + request.getMethod());
                    }
                    var response = McpJson.CODEC.serializeToString(JsonRpcResponse.builder()
                            .jsonrpc("2.0")
                            .id(request.getId())
                            .result(Document.ofObject(result))
                            .build());
                    if (notify) {
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
                send(input, "d", "server/discover", Map.of());
                var caps = read(output).getMember("result").getMember("capabilities");
                assertTrue(caps.getMember("tools").getMember("listChanged").asBoolean());

                send(input,
                        "listen:0",
                        "subscriptions/listen",
                        Map.of("notifications",
                                Document.of(Map.of("toolsListChanged",
                                        Document.of(true),
                                        "promptsListChanged",
                                        Document.of(true)))));
                var ack = read(output);
                assertEquals("notifications/subscriptions/acknowledged", ack.getMember("method").asString());

                send(input, "l1", "tools/list", Map.of());
                assertEquals(List.of("discover"), names(read(output)));

                // The upstream reports the change inside the call's response stream; the client learns about
                // it through its subscription, tagged with the listen request's id, before the call result.
                send(input,
                        "c1",
                        "tools/call",
                        Map.of("name",
                                Document.of("discover"),
                                "arguments",
                                Document.of(Map.of())));
                var changed = read(output);
                assertEquals("notifications/tools/list_changed", changed.getMember("method").asString());
                assertEquals("listen:0",
                        changed.getMember("params")
                                .getMember("_meta")
                                .getMember(McpWireNames.SUBSCRIPTION_ID)
                                .asString());
                var callResult = read(output);
                assertEquals("c1", callResult.getMember("id").asString());

                send(input, "l2", "tools/list", Map.of());
                assertEquals(List.of("discover", "new_tool"), names(read(output)));

                send(input,
                        "c2",
                        "tools/call",
                        Map.of("name",
                                Document.of("new_tool"),
                                "arguments",
                                Document.of(Map.of())));
                var called = read(output);
                assertNull(called.getMember("error"), called.toString());
                output.assertNoOutput(100);

                // Graceful shutdown ends the open subscription with a complete result.
                server.shutdown().join();
                var complete = read(output);
                assertEquals("listen:0", complete.getMember("id").asString());
                assertEquals("complete", complete.getMember("result").getMember("resultType").asString());
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

    private static List<String> names(Document response) {
        return response.getMember("result")
                .getMember("tools")
                .asList()
                .stream()
                .map(t -> t.getMember("name").asString())
                .sorted()
                .toList();
    }

    private static void respond(HttpExchange exchange, String contentType, String content) throws IOException {
        var bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void send(TestInputStream input, String id, String method, Map<String, Document> members) {
        var params = new HashMap<>(members);
        params.put("_meta",
                Document.of(Map.of(
                        "io.modelcontextprotocol/protocolVersion",
                        Document.of("2026-07-28"),
                        "io.modelcontextprotocol/clientInfo",
                        Document.of(Map.of("name",
                                Document.of("c"),
                                "version",
                                Document.of("1"))),
                        "io.modelcontextprotocol/clientCapabilities",
                        Document.of(Map.of()))));
        input.write(McpJson.CODEC.serializeToString(JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(Document.of(id))
                .method(method)
                .params(Document.of(params))
                .build()) + "\n");
    }

    private static Document read(TestOutputStream output) {
        var line = assertTimeoutPreemptively(Duration.ofSeconds(5), output::read);
        return McpJson.CODEC.createDeserializer(line.getBytes(StandardCharsets.UTF_8)).readDocument();
    }
}
