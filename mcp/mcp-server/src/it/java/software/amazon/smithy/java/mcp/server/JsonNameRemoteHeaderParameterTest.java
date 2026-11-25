/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.example.jsonname.model.ListTagsInRegionOutput;
import software.amazon.smithy.java.example.jsonname.model.ListTagsPlainOutput;
import software.amazon.smithy.java.example.jsonname.model.ListTagsWithTimestampOutput;
import software.amazon.smithy.java.example.jsonname.service.ListTagsInRegionOperation;
import software.amazon.smithy.java.example.jsonname.service.ListTagsPlainOperation;
import software.amazon.smithy.java.example.jsonname.service.ListTagsWithTimestampOperation;
import software.amazon.smithy.java.example.jsonname.service.TagService;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;

/**
 * An HTTP server in front of a forwarded tool whose member carries both {@code @mcpHeader} and
 * {@code @jsonName}. The front server only knows the name the remote advertises, so the remote must not bind
 * the argument under any other name.
 */
class JsonNameRemoteHeaderParameterTest {
    private static final String TOOL = "ListTagsInRegion";
    private static final String PROTOCOL_VERSION = KnownProtocolVersion.V2026_07_28.identifier();

    private final AtomicReference<String> receivedRegion = new AtomicReference<>();
    private final AtomicInteger invocations = new AtomicInteger();
    private McpEngine remoteEngine;
    private McpEngine engine;
    private McpHttpHandler handler;

    @BeforeEach
    void init() {
        remoteEngine = McpEngine.builder()
                .services(Map.of("tags",
                        TagService.builder()
                                .addListTagsInRegionOperation((ListTagsInRegionOperation) (in, context) -> {
                                    invocations.incrementAndGet();
                                    receivedRegion.set(in.getTagRegion());
                                    return ListTagsInRegionOutput.builder().nextToken("t").build();
                                })
                                .addListTagsPlainOperation(
                                        (ListTagsPlainOperation) (in, context) -> ListTagsPlainOutput.builder()
                                                .isComplete(true)
                                                .build())
                                .addListTagsWithTimestampOperation(
                                        (ListTagsWithTimestampOperation) (
                                                in,
                                                context) -> ListTagsWithTimestampOutput.builder()
                                                        .isComplete(true)
                                                        .build())
                                .build()))
                .name("jsonname-remote")
                .version("1.0.0")
                .build();

        var remoteSession = remoteEngine.newSession();
        var remote = new McpRemoteClient() {
            @Override
            protected JsonRpcResponse exchange(JsonRpcRequest request) {
                // Everything crosses the boundary as JSON, as it would between two processes.
                var response = remoteEngine.encode(remoteEngine.execute(
                        overTheWire(request, JsonRpcRequest.builder()),
                        remoteSession,
                        null,
                        McpTransportContext.STDIO));
                return response == null ? null : overTheWire(response, JsonRpcResponse.builder());
            }

            @Override
            public void start() {}

            @Override
            public void close() {}

            @Override
            public String name() {
                return "tags-remote";
            }
        };
        engine = McpEngine.builder()
                .addRemoteClient(remote)
                .name("jsonname-front")
                .version("1.0.0")
                .build();
        handler = McpHttpHandler.forLoopback(engine);

        // Loads the remote catalog, so that the calls below are checked against the remote tool.
        var listed = handler.handle(request(McpMethod.Standard.TOOLS_LIST.wireName(), Map.of()),
                headers(McpMethod.Standard.TOOLS_LIST.wireName(), null, null));
        assertNull(listed.body().getError());
        assertEquals(Set.of("tag_region"), engine.headerParameters(TOOL).keySet());
        assertEquals(0, invocations.get());
    }

    @AfterEach
    void teardown() {
        if (engine != null) {
            engine.close();
        }
        if (remoteEngine != null) {
            remoteEngine.close();
        }
    }

    @Test
    void wireNameArgumentWithMatchingHeaderReachesTheRemoteHandler() {
        var response = call("tag_region", "region-a", "region-a");

        assertNull(response.body().getError());
        assertEquals(200, response.statusCode());
        assertEquals(1, invocations.get());
        assertEquals("region-a", receivedRegion.get());
    }

    @Test
    void wireNameArgumentWithoutHeaderIsRejected() {
        var response = call("tag_region", "region-a", null);

        assertError(response, McpHttpHandler.HEADER_MISMATCH_ERROR_CODE);
    }

    @Test
    void memberNameAliasArgumentWithoutHeaderIsRejectedByTheRemote() {
        var response = call("tagRegion", "region-a", null);

        assertError(response, -32602);
        assertTrue(response.body().getError().getMessage().contains("`tag_region`"),
                response.body().getError().getMessage());
    }

    @Test
    void memberNameAliasArgumentWithHeaderIsRejected() {
        var response = call("tagRegion", "region-a", "region-a");

        assertError(response, McpHttpHandler.HEADER_MISMATCH_ERROR_CODE);
    }

    private void assertError(McpHttpHandler.Response response, int code) {
        assertNotNull(response.body().getError());
        assertEquals(code, response.body().getError().getCode());
        assertEquals(0, invocations.get());
    }

    private static <T extends SerializableShape> T overTheWire(SerializableShape shape, ShapeBuilder<T> builder) {
        return McpJson.CODEC.deserializeShape(
                McpJson.CODEC.serializeToString(shape).getBytes(StandardCharsets.UTF_8),
                builder);
    }

    private McpHttpHandler.Response call(String argumentKey, String argumentValue, String headerValue) {
        return handler.handle(
                request(McpMethod.Standard.TOOLS_CALL.wireName(),
                        Map.of(
                                "name",
                                Document.of(TOOL),
                                "arguments",
                                Document.of(Map.of(argumentKey, Document.of(argumentValue))))),
                headers(McpMethod.Standard.TOOLS_CALL.wireName(), TOOL, headerValue));
    }

    private static JsonRpcRequest request(String method, Map<String, Document> params) {
        var members = new HashMap<>(params);
        members.put("_meta",
                Document.of(Map.of(
                        McpWireNames.PROTOCOL_VERSION,
                        Document.of(PROTOCOL_VERSION),
                        McpWireNames.CLIENT_CAPABILITIES,
                        Document.of(Map.of()))));
        return JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(Document.of(1))
                .method(method)
                .params(Document.of(members))
                .build();
    }

    private static Map<String, List<String>> headers(String method, String name, String headerValue) {
        var headers = new HashMap<String, List<String>>(Map.of(
                "Host",
                List.of("127.0.0.1:8080"),
                "MCP-Protocol-Version",
                List.of(PROTOCOL_VERSION),
                "Mcp-Method",
                List.of(method)));
        if (name != null) {
            headers.put("Mcp-Name", List.of(name));
        }
        if (headerValue != null) {
            headers.put("Mcp-Param-Tag-Region", List.of(McpHttpBinding.encodeParameter(headerValue)));
        }
        return headers;
    }
}
