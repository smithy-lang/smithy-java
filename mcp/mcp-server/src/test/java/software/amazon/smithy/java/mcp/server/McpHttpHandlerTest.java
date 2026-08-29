/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;

class McpHttpHandlerTest {

    @Test
    void loopbackHandlerAcceptsLoopbackHostAndOrigin() {
        var response = handler().handle(
                initializeRequest(),
                Map.of(
                        "Host",
                        List.of("127.0.0.1:8080"),
                        "Origin",
                        List.of("http://localhost:3000")));

        assertEquals(200, response.statusCode());
        assertNull(response.body().getError());
    }

    @Test
    void loopbackHandlerAcceptsIpv6LoopbackHostAndOrigin() {
        var response = handler().handle(
                initializeRequest(),
                Map.of(
                        "Host",
                        List.of("[::1]:8080"),
                        "Origin",
                        List.of("http://[::1]:3000")));

        assertEquals(200, response.statusCode());
        assertNull(response.body().getError());
    }

    @Test
    void loopbackHandlerRejectsNonLoopbackHost() {
        var response = handler().handle(
                initializeRequest(),
                Map.of(
                        "Host",
                        List.of("evil.example.com"),
                        "Origin",
                        List.of("http://evil.example.com")));

        assertEquals(400, response.statusCode());
        assertEquals(-32020, response.body().getError().getCode());
    }

    @Test
    void loopbackHandlerRejectsNonLoopbackOrigin() {
        var response = handler().handle(
                initializeRequest(),
                Map.of(
                        "Host",
                        List.of("localhost:8080"),
                        "Origin",
                        List.of("http://evil.example.com")));

        assertEquals(400, response.statusCode());
        assertEquals(-32020, response.body().getError().getCode());
    }

    @Test
    void protocolNegotiationIsScopedToEachHttpRequest() {
        try (var engine = McpEngine.builder().build()) {
            var handler = new McpHttpHandler(engine);

            var legacyInitialize = JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .id(Document.of(1))
                    .method(McpMethod.Standard.INITIALIZE.wireName())
                    .params(Document.of(Map.of(
                            "protocolVersion",
                            Document.of(KnownProtocolVersion.V2024_11_05.identifier()))))
                    .build();
            assertNull(handler.handle(legacyInitialize, Map.of()).body().getError());

            var statelessParams = Document.of(Map.of(
                    "_meta",
                    Document.of(Map.of(
                            McpWireNames.PROTOCOL_VERSION,
                            Document.of(KnownProtocolVersion.V2026_07_28.identifier()),
                            McpWireNames.CLIENT_CAPABILITIES,
                            Document.of(Map.of())))));
            var discover = JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .id(Document.of(2))
                    .method(McpMethod.Standard.SERVER_DISCOVER.wireName())
                    .params(statelessParams)
                    .build();
            var discoverResponse = handler.handle(
                    discover,
                    Map.of(
                            "MCP-Protocol-Version",
                            List.of(KnownProtocolVersion.V2026_07_28.identifier()),
                            "Mcp-Method",
                            List.of(McpMethod.Standard.SERVER_DISCOVER.wireName())));
            assertNull(discoverResponse.body().getError());

            var headerlessPing = JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .id(Document.of(3))
                    .method(McpMethod.Standard.PING.wireName())
                    .build();
            var pingResponse = handler.handle(headerlessPing, Map.of());

            assertEquals(200, pingResponse.statusCode());
            assertNull(pingResponse.body().getError());
        }
    }

    @Test
    void nonStringInitializeVersionReturnsInvalidParams() {
        try (var engine = McpEngine.builder().build()) {
            var handler = new McpHttpHandler(engine);
            var request = JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .id(Document.of(1))
                    .method(McpMethod.Standard.INITIALIZE.wireName())
                    .params(Document.of(Map.of("protocolVersion", Document.of(1))))
                    .build();

            var response = handler.handle(request, Map.of());

            assertEquals(200, response.statusCode());
            assertEquals(-32602, response.body().getError().getCode());
        }
    }

    @Test
    void unsupportedLegacyProtocolHeaderReturnsBadRequest() {
        try (var engine = McpEngine.builder().build()) {
            var handler = new McpHttpHandler(engine);
            var request = JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .id(Document.of(1))
                    .method(McpMethod.Standard.PING.wireName())
                    .build();

            var response = handler.handle(
                    request,
                    Map.of("MCP-Protocol-Version", List.of("unsupported-version")));

            assertEquals(400, response.statusCode());
            assertEquals(-32022, response.body().getError().getCode());
        }
    }

    @Test
    void literalBase64MarkerIsEscaped() {
        var value = "=?base64?not-encoded?=";
        var encoded = McpHttpBinding.encodeParameter(value);

        assertNotEquals(value, encoded);
        assertEquals(value, McpHttpBinding.decodeParameter(encoded));
    }

    private McpHttpHandler handler() {
        var service = McpEngine.builder().services(Map.of()).build();
        return McpHttpHandler.forLoopback(service);
    }

    private JsonRpcRequest initializeRequest() {
        return JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(Document.of(1))
                .method("initialize")
                .params(Document.of(Map.of(
                        "protocolVersion",
                        Document.of(KnownProtocolVersion.V2025_11_25.identifier()))))
                .build();
    }
}
