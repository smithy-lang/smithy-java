/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicschemas.StructDocument;
import software.amazon.smithy.java.example.jsonname.model.ListTagsInRegionInput;
import software.amazon.smithy.java.example.jsonname.model.ListTagsInRegionOutput;
import software.amazon.smithy.java.example.jsonname.model.ListTagsPlainOutput;
import software.amazon.smithy.java.example.jsonname.model.ListTagsWithTimestampOutput;
import software.amazon.smithy.java.example.jsonname.model.RegionDetail;
import software.amazon.smithy.java.example.jsonname.service.ListTagsInRegionOperation;
import software.amazon.smithy.java.example.jsonname.service.ListTagsPlainOperation;
import software.amazon.smithy.java.example.jsonname.service.ListTagsWithTimestampOperation;
import software.amazon.smithy.java.example.jsonname.service.TagService;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.JsonNameTrait;

/**
 * A member that carries both {@code @mcpHeader} and {@code @jsonName} is validated over HTTP the way it is
 * bound for invocation: the {@code Mcp-Param-*} header is compared with the body argument stored under the
 * {@code @jsonName} key. Such a member is never accepted under its member name, so that an intermediary that
 * only knows the advertised name cannot be bypassed.
 */
class JsonNameHeaderParameterTest {
    private static final String TOOL = "ListTagsInRegion";
    private static final String PROTOCOL_VERSION = KnownProtocolVersion.V2026_07_28.identifier();

    private final AtomicReference<String> receivedRegion = new AtomicReference<>();
    private final AtomicReference<BigDecimal> receivedWeight = new AtomicReference<>();
    private final AtomicInteger invocations = new AtomicInteger();
    private final AtomicInteger plainInvocations = new AtomicInteger();
    private McpEngine engine;
    private McpHttpHandler handler;

    @BeforeEach
    void init() {
        engine = McpEngine.builder()
                .services(Map.of("tags",
                        TagService.builder()
                                .addListTagsInRegionOperation((ListTagsInRegionOperation) (in, context) -> {
                                    invocations.incrementAndGet();
                                    receivedRegion.set(in.getTagRegion());
                                    receivedWeight.set(in.getWeight());
                                    return ListTagsInRegionOutput.builder()
                                            .nextToken("t")
                                            .detail(in.getDetail())
                                            .build();
                                })
                                .addListTagsPlainOperation(
                                        (ListTagsPlainOperation) (in, context) -> {
                                            plainInvocations.incrementAndGet();
                                            return ListTagsPlainOutput.builder().isComplete(true).build();
                                        })
                                .addListTagsWithTimestampOperation(
                                        (ListTagsWithTimestampOperation) (
                                                in,
                                                context) -> ListTagsWithTimestampOutput.builder()
                                                        .isComplete(true)
                                                        .build())
                                .build()))
                .name("jsonname-header-test")
                .version("1.0.0")
                .build();
        handler = McpHttpHandler.forLoopback(engine);

        // Guards every test below against passing because header validation was skipped.
        assertEquals(Set.of("tag_region"),
                engine.headerParameters(TOOL).keySet(),
                "The @mcpHeader parameter should be keyed by its @jsonName");
    }

    @AfterEach
    void teardown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void wireNameArgumentWithMatchingHeaderReachesHandler() {
        var response = call("tag_region", "region-a", "region-a");

        assertNull(response.body().getError());
        assertEquals(200, response.statusCode());
        assertEquals(1, invocations.get());
        assertEquals("region-a", receivedRegion.get());
    }

    @Test
    void wireNameArgumentWithMismatchingHeaderIsRejected() {
        var response = call("tag_region", "region-a", "region-b");

        assertHeaderMismatch(response);
    }

    @Test
    void wireNameArgumentWithoutHeaderIsRejected() {
        var response = call("tag_region", "region-a", null);

        assertHeaderMismatch(response);
    }

    @Test
    void memberNameAliasArgumentWithMatchingHeaderIsRejected() {
        var response = call("tagRegion", "region-a", "region-a");

        assertHeaderMismatch(response);
    }

    @Test
    void memberNameAliasArgumentWithMismatchingHeaderIsRejected() {
        var response = call("tagRegion", "region-a", "region-b");

        assertHeaderMismatch(response);
    }

    @Test
    void memberNameAliasArgumentWithoutHeaderIsRejected() {
        var response = call("tagRegion", "region-a", null);

        assertInvalidAlias(response.body());
        assertEquals(400, response.statusCode());
    }

    @Test
    void memberNameAliasArgumentIsRejectedWithoutHttp() {
        var response = engine.execute(request(Document.of(Map.of("tagRegion", Document.of("region-a")))),
                ProtocolVersion.parse(PROTOCOL_VERSION));

        assertInvalidAlias(response);
    }

    @Test
    void typedArgumentsAreComparedByWireName() {
        var arguments = Document.of(ListTagsInRegionInput.builder().tagRegion("region-a").build());

        assertHeaderMismatch(handler.handle(request(arguments), headers(null)));
        assertHeaderMismatch(handler.handle(request(arguments), headers("region-b")));

        var response = handler.handle(request(arguments), headers("region-a"));
        assertNull(response.body().getError());
        assertEquals(1, invocations.get());
        assertEquals("region-a", receivedRegion.get());
    }

    @Test
    void typedArgumentsOfAnotherModelVersionAreBoundAsTheyWereChecked() {
        // The caller was built against a model in which the member was not renamed yet. What is compared with
        // the header and what is bound must be the same document, or the value reaches the handler unchecked.
        var older = Schema.structureBuilder(ShapeId.from("smithy.example.jsonname#ListTagsInRegionInput"))
                .putMember("tagRegion", PreludeSchemas.STRING)
                .build();
        var arguments = Document.of(new SerializableStruct() {
            @Override
            public Schema schema() {
                return older;
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                serializer.writeString(older.member("tagRegion"), "region-a");
            }

            @Override
            public <T> T getMemberValue(Schema member) {
                throw new UnsupportedOperationException();
            }
        });

        var withoutHeader = handler.handle(request(arguments), headers(null));
        assertInvalidAlias(withoutHeader.body());
        assertEquals(400, withoutHeader.statusCode());

        assertHeaderMismatch(handler.handle(request(arguments), headers("region-a")));
    }

    @Test
    void typedArgumentsKeepTheirPrecision() {
        // Re-keying typed arguments by wire name must not write them out and read them back.
        var weight = new BigDecimal("1.0000000000000001");
        var arguments = Document.of(ListTagsInRegionInput.builder().tagRegion("region-a").weight(weight).build());

        var response = handler.handle(request(arguments), headers("region-a"));

        assertNull(response.body().getError());
        assertEquals(1, invocations.get());
        assertEquals("region-a", receivedRegion.get());
        assertEquals(weight, receivedWeight.get());
    }

    @Test
    void typedDocumentArgumentKeepsItsShape() {
        // A document member is opaque: what it holds is not re-keyed and stays the shape it is, so that the
        // handler can return it as the variant it is.
        var detail = Document.of(RegionDetail.builder().zoneName("z").build());
        var arguments = Document.of(ListTagsInRegionInput.builder().tagRegion("region-a").detail(detail).build());

        var response = handler.handle(request(arguments), headers("region-a"));

        assertNull(response.body().getError());
        assertEquals(1, invocations.get());
        var structuredContent = response.body().getResult().getMember("structuredContent");
        assertTrue(Document.equals(
                Document.ofObject(Map.of("region", Map.of("zone_name", "z"))),
                structuredContent.getMember("detail")), McpJson.CODEC.serializeToString(structuredContent));
    }

    @Test
    void typedArgumentsAreOnlyReKeyedWhereHeadersAreChecked() {
        // Without a modern protocol claim no parameter header is compared, and typed arguments are bound as
        // they are over any other transport.
        var older = Schema.structureBuilder(ShapeId.from("smithy.example.jsonname#ListTagsInRegionInput"))
                .putMember("tagRegion", PreludeSchemas.STRING)
                .build();
        var arguments = Document.of(new SerializableStruct() {
            @Override
            public Schema schema() {
                return older;
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                serializer.writeString(older.member("tagRegion"), "region-a");
            }

            @Override
            public <T> T getMemberValue(Schema member) {
                throw new UnsupportedOperationException();
            }
        });
        var legacy = JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(Document.of(1))
                .method(McpMethod.Standard.TOOLS_CALL.wireName())
                .params(Document.of(Map.of("name", Document.of(TOOL), "arguments", arguments)))
                .build();

        var overStdio = engine.execute(legacy, KnownProtocolVersion.V2025_06_18);
        assertNull(overStdio.getError());
        assertEquals(1, invocations.get());

        var overHttp = handler.handle(legacy,
                Map.of(
                        "Host",
                        List.of("127.0.0.1:8080"),
                        "MCP-Protocol-Version",
                        List.of(KnownProtocolVersion.V2025_06_18.identifier())));
        assertNull(overHttp.body().getError());
        assertEquals(2, invocations.get());
        assertEquals("region-a", receivedRegion.get());
    }

    @Test
    void typedParamsNameTheSameToolForEveryCheck() {
        // Params backed by a schema in which another member is called "name" in JSON. The tool is the one the
        // member "name" holds, for the Mcp-Name check and for the dispatch alike.
        var paramsSchema = Schema.structureBuilder(ShapeId.from("smithy.example.jsonname#CallParams"))
                .putMember("name", PreludeSchemas.STRING)
                .putMember("arguments", PreludeSchemas.DOCUMENT)
                .putMember("_meta", PreludeSchemas.DOCUMENT)
                .putMember("other", PreludeSchemas.STRING, new JsonNameTrait("name"))
                .build();
        var arguments = Document.of(ListTagsInRegionInput.builder().tagRegion("region-a").build());
        var params = Document.of(new SerializableStruct() {
            @Override
            public Schema schema() {
                return paramsSchema;
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                serializer.writeString(paramsSchema.member("name"), TOOL);
                serializer.writeDocument(paramsSchema.member("arguments"), arguments);
                serializer.writeDocument(paramsSchema.member("_meta"), META);
                serializer.writeString(paramsSchema.member("other"), "ListTagsPlain");
            }

            @Override
            public <T> T getMemberValue(Schema member) {
                throw new UnsupportedOperationException();
            }
        });
        var request = JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(Document.of(1))
                .method(McpMethod.Standard.TOOLS_CALL.wireName())
                .params(params)
                .build();

        var mismatch = new HashMap<>(headers("region-a"));
        mismatch.put("Mcp-Name", List.of("ListTagsPlain"));
        assertHeaderMismatch(handler.handle(request, mismatch));
        assertEquals(0, plainInvocations.get());

        var response = handler.handle(request, headers("region-a"));
        assertNull(response.body().getError());
        assertEquals(1, invocations.get());
        assertEquals(0, plainInvocations.get());
    }

    @Test
    void wrappedArgumentsAreComparedAsTheValueTheyWrap() {
        // A dynamic structure gives the value of a modeled document member a wrapper that reports DOCUMENT.
        var holder = Schema.structureBuilder(ShapeId.from("smithy.example.jsonname#ArgumentsHolder"))
                .putMember("value", PreludeSchemas.DOCUMENT)
                .build();
        var untyped = Document.of(Map.of("tag_region", Document.of("region-a")));
        var typed = Document.of(ListTagsInRegionInput.builder().tagRegion("region-a").build());
        for (var value : List.of(untyped, typed)) {
            var arguments = StructDocument.of(holder, Document.of(Map.of("value", value))).getMember("value");
            assertEquals(ShapeType.DOCUMENT, arguments.type());
            invocations.set(0);

            assertHeaderMismatch(handler.handle(request(arguments), headers(null)));
            assertHeaderMismatch(handler.handle(request(arguments), headers("region-b")));

            var response = handler.handle(request(arguments), headers("region-a"));
            assertNull(response.body().getError());
            assertEquals(1, invocations.get());
            assertEquals("region-a", receivedRegion.get());
        }
    }

    private void assertInvalidAlias(JsonRpcResponse response) {
        assertNotNull(response.getError());
        assertEquals(-32602, response.getError().getCode());
        assertTrue(response.getError().getMessage().contains("`tag_region`"), response.getError().getMessage());
        assertEquals(0, invocations.get());
    }

    private void assertHeaderMismatch(McpHttpHandler.Response response) {
        assertNotNull(response.body().getError());
        assertEquals(McpHttpHandler.HEADER_MISMATCH_ERROR_CODE, response.body().getError().getCode());
        assertEquals(0, invocations.get());
    }

    private McpHttpHandler.Response call(String argumentKey, String argumentValue, String headerValue) {
        return handler.handle(
                request(Document.of(Map.of(argumentKey, Document.of(argumentValue)))),
                headers(headerValue));
    }

    private static JsonRpcRequest request(Document arguments) {
        return JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(Document.of(1))
                .method(McpMethod.Standard.TOOLS_CALL.wireName())
                .params(Document.of(Map.of(
                        "name",
                        Document.of(TOOL),
                        "arguments",
                        arguments,
                        "_meta",
                        META)))
                .build();
    }

    private static final Document META = Document.of(Map.of(
            McpWireNames.PROTOCOL_VERSION,
            Document.of(PROTOCOL_VERSION),
            McpWireNames.CLIENT_CAPABILITIES,
            Document.of(Map.of())));

    private static Map<String, List<String>> headers(String headerValue) {
        var headers = new HashMap<String, List<String>>(Map.of(
                "Host",
                List.of("127.0.0.1:8080"),
                "MCP-Protocol-Version",
                List.of(PROTOCOL_VERSION),
                "Mcp-Method",
                List.of(McpMethod.Standard.TOOLS_CALL.wireName()),
                "Mcp-Name",
                List.of(TOOL)));
        if (headerValue != null) {
            headers.put("Mcp-Param-Tag-Region", List.of(McpHttpBinding.encodeParameter(headerValue)));
        }
        return headers;
    }
}
