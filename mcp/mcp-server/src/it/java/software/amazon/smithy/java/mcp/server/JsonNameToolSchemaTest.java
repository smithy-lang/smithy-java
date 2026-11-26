/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.example.jsonname.model.ListTagsInRegionOutput;
import software.amazon.smithy.java.example.jsonname.model.ListTagsPlainOutput;
import software.amazon.smithy.java.example.jsonname.model.ListTagsWithTimestampOutput;
import software.amazon.smithy.java.example.jsonname.service.ListTagsInRegionOperation;
import software.amazon.smithy.java.example.jsonname.service.ListTagsPlainOperation;
import software.amazon.smithy.java.example.jsonname.service.ListTagsWithTimestampOperation;
import software.amazon.smithy.java.example.jsonname.service.TagService;
import software.amazon.smithy.java.server.Server;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The names a tool advertises in inputSchema/outputSchema must be the names it accepts in
 * {@code arguments} and emits in {@code structuredContent}, for members that carry {@code @jsonName}.
 *
 * <p>ListTagsPlain has no member that needs document adaptation; ListTagsWithTimestamp adds a
 * timestamp member to the input and to the output, which routes it through SmithyDocumentAdapter.
 */
class JsonNameToolSchemaTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Server server;
    private TestInputStream input;
    private TestOutputStream output;
    private int requestId;
    private final AtomicReference<List<String>> receivedTagNames = new AtomicReference<>();

    @BeforeEach
    void init() {
        input = new TestInputStream();
        output = new TestOutputStream();
        server = StdioMcpServer.builder()
                .name("jsonname-test")
                .addService("tags",
                        TagService.builder()
                                .addListTagsInRegionOperation(
                                        (ListTagsInRegionOperation) (in, context) -> ListTagsInRegionOutput.builder()
                                                .build())
                                .addListTagsPlainOperation((ListTagsPlainOperation) (in, context) -> {
                                    receivedTagNames.set(in.getTagNames());
                                    return ListTagsPlainOutput.builder().isComplete(true).nextToken("t").build();
                                })
                                .addListTagsWithTimestampOperation((ListTagsWithTimestampOperation) (in, context) -> {
                                    receivedTagNames.set(in.getTagNames());
                                    return ListTagsWithTimestampOutput.builder()
                                            .isComplete(true)
                                            .nextToken("t")
                                            .generatedAt(Instant.parse("2026-01-02T03:04:05Z"))
                                            .build();
                                })
                                .build())
                .input(input)
                .output(output)
                .build();
        server.start();
        rpc("initialize", "{\"protocolVersion\":\"2025-06-18\"}");
    }

    @AfterEach
    void teardown() {
        if (server != null) {
            server.shutdown().join();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ListTagsPlain", "ListTagsWithTimestamp"})
    void schemasAdvertiseJsonName(String operation) {
        var tool = tool(operation);

        assertTrue(tool.path("inputSchema").path("properties").has("tag_names"),
                operation + " inputSchema should advertise the @jsonName 'tag_names', but has "
                        + propertyNames(tool.path("inputSchema")));
        assertTrue(tool.path("outputSchema").path("properties").has("is_complete"),
                operation + " outputSchema should advertise the @jsonName 'is_complete', but has "
                        + propertyNames(tool.path("outputSchema")));
        assertEquals("[\"is_complete\"]",
                tool.path("outputSchema").path("required").toString(),
                operation + " outputSchema.required should use the @jsonName");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ListTagsPlain", "ListTagsWithTimestamp"})
    void structuredContentValidatesAgainstOutputSchema(String operation) {
        var tool = tool(operation);
        var result = call(operation, "tag_names").path("result");
        var structuredContent = result.path("structuredContent");

        var errors = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(tool.path("outputSchema"))
                .validate(structuredContent);
        assertTrue(errors.isEmpty(),
                operation + " structuredContent " + structuredContent
                        + " does not satisfy its advertised outputSchema: " + errors);
        assertEquals(structuredContent,
                OBJECT_MAPPER.readTree(result.path("content").path(0).path("text").asString()),
                operation + " content[0].text should carry the same JSON as structuredContent");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ListTagsPlain", "ListTagsWithTimestamp"})
    void argumentUnderWireNameReachesHandler(String operation) {
        call(operation, "tag_names");

        assertEquals(List.of("a", "b"),
                receivedTagNames.get(),
                operation + ": an argument sent under the @jsonName 'tag_names' did not reach the handler");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ListTagsPlain", "ListTagsWithTimestamp"})
    void argumentUnderMemberNameAliasReachesHandler(String operation) {
        call(operation, "tagNames");

        assertEquals(List.of("a", "b"),
                receivedTagNames.get(),
                operation + ": an argument sent under the member name 'tagNames' (alias) did not reach the handler");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ListTagsPlain", "ListTagsWithTimestamp"})
    void whenBothKeysAreSentWireNameWins(String operation) {
        receivedTagNames.set(null);
        var since = operation.equals("ListTagsWithTimestamp") ? ",\"since\":\"2026-01-01T00:00:00Z\"" : "";
        var request = "{\"jsonrpc\":\"2.0\",\"id\":" + (requestId++) + ",\"method\":\"tools/call\""
                + ",\"params\":{\"name\":\"" + operation + "\",\"arguments\":{\"tag_names\":[\"wire\",\"value\"]"
                + ",\"tagNames\":[\"member\",\"name\"],\"pageSize\":10" + since + "}}}";
        input.write(request + "\n");
        assertTimeoutPreemptively(Duration.ofSeconds(5), output::read, "No response within 5 seconds");

        assertEquals(List.of("wire", "value"),
                receivedTagNames.get(),
                operation + ": when both 'tag_names' and 'tagNames' are sent, the wire-name value should win");
    }

    private JsonNode tool(String name) {
        for (var tool : rpc("tools/list", "{}").path("result").path("tools")) {
            if (tool.path("name").asString().equals(name)) {
                return tool;
            }
        }
        throw new AssertionError("Tool not found: " + name);
    }

    private JsonNode call(String operation, String tagNamesKey) {
        receivedTagNames.set(null);
        var since = operation.equals("ListTagsWithTimestamp") ? ",\"since\":\"2026-01-01T00:00:00Z\"" : "";
        var response = rpc("tools/call",
                "{\"name\":\"" + operation + "\",\"arguments\":{\"" + tagNamesKey
                        + "\":[\"a\",\"b\"],\"pageSize\":10" + since + "}}");
        return response;
    }

    private JsonNode rpc(String method, String params) {
        var request = "{\"jsonrpc\":\"2.0\",\"id\":" + (requestId++) + ",\"method\":\"" + method
                + "\",\"params\":" + params + "}";
        input.write(request + "\n");
        var response = assertTimeoutPreemptively(Duration.ofSeconds(5), output::read, "No response within 5 seconds");
        return OBJECT_MAPPER.readTree(response);
    }

    private static List<String> propertyNames(JsonNode schema) {
        var names = new ArrayList<String>();
        for (var entry : schema.path("properties").properties()) {
            names.add(entry.getKey());
        }
        return names;
    }
}
