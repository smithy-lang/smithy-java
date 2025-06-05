/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.server.ProxyService;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public class McpServerTest {
    private static final JsonCodec CODEC = JsonCodec.builder()
            .settings(JsonSettings.builder()
                    .serializeTypeInDocuments(false)
                    .useJsonName(true)
                    .build())
            .build();

    private TestInputStream input;
    private TestOutputStream output;
    private Server server;
    private int id;

    @BeforeEach
    public void beforeEach() {
        input = new TestInputStream();
        output = new TestOutputStream();
    }

    @AfterEach
    public void afterEach() {
        if (server != null) {
            server.shutdown().join();
        }
    }

    @Test
    public void validateToolStructure() {
        server = McpServer.builder()
                .input(input)
                .output(output)
                .addService(ProxyService.builder()
                        .service(ShapeId.from("smithy.test#TestService"))
                        .proxyEndpoint("http://localhost")
                        .model(MODEL)
                        .build())
                .build();

        server.start();

        write("tools/list", Document.of(Map.of()));
        var response = read();
        var tools = response.getResult().asStringMap().get("tools").asList();

        var tool = tools.get(0).asStringMap();
        var inputSchema = tool.get("inputSchema").asStringMap();
        var properties = inputSchema.get("properties").asStringMap();

        assertEquals("TestOperation", tool.get("name").asString());
        assertEquals("This tool invokes TestOperation API of TestService.A TestOperation",
                tool.get("description").asString());
        assertEquals("object", inputSchema.get("type").asString());
        assertEquals("An input for TestOperation with a nested member",
                inputSchema.get("description").asString());

        var str = properties.get("str").asStringMap();
        assertEquals("string", str.get("type").asString());
        assertEquals("It's a string", str.get("description").asString());

        var list = properties.get("list").asStringMap();
        assertEquals("array", list.get("type").asString());

        var listItems = list.get("items").asStringMap();
        assertEquals("object", listItems.get("type").asString());
        var listItemProperties = listItems.get("properties").asStringMap();
        validateNestedStructure(listItemProperties);

        var nested = properties.get("nested").asStringMap();
        assertEquals("object", nested.get("type").asString());
        var nestedProperties = nested.get("properties").asStringMap();
        validateNestedStructure(nestedProperties);

        var doubleNestedList = properties.get("doubleNestedList").asStringMap();
        assertEquals("array", doubleNestedList.get("type").asString());

        var doubleNestedListItems = doubleNestedList.get("items").asStringMap();
        assertEquals("array", doubleNestedListItems.get("type").asString());

        var doubleNestedListItemsItems = doubleNestedListItems.get("items").asStringMap();
        assertEquals("object", doubleNestedListItemsItems.get("type").asString());
        var doubleNestedProperties = doubleNestedListItemsItems.get("properties").asStringMap();
        validateNestedStructure(doubleNestedProperties);
    }

    private void validateNestedStructure(Map<String, Document> properties) {
        var nestedStr = properties.get("nestedStr").asStringMap();
        assertEquals("string", nestedStr.get("type").asString());
        assertEquals("A string that's nested", nestedStr.get("description").asString());

        var nestedDocument = properties.get("nestedDocument").asStringMap();
        assertTrue(nestedDocument.get("additionalProperties").asBoolean());
        assertTrue(nestedDocument.get("properties").asStringMap().isEmpty());
        assertTrue(nestedDocument.get("required").asList().isEmpty());

        var recursive = properties.get("recursive").asStringMap();
        assertEquals("object", recursive.get("type").asString());
        assertEquals("A structure that references itself recursively",
                recursive.get("description").asString());
        var recursiveProperties = recursive.get("properties").asStringMap();
        assertTrue(recursiveProperties.containsKey("nested"));
    }

    private void write(String method, Document document) {
        var request = JsonRpcRequest.builder()
                .id(id++)
                .method(method)
                .params(document)
                .jsonrpc("2.0")
                .build();
        input.write(CODEC.serializeToString(request));
        input.write("\n");
    }

    private JsonRpcResponse read() {
        var line = output.read();
        return CODEC.deserializeShape(line, JsonRpcResponse.builder());
    }

    private static final String MODEL_STR = """
            $version: "2"

            namespace smithy.test

            /// A TestService
            @aws.protocols#awsJson1_0
            service TestService {
                operations: [TestOperation]
            }

            /// A TestOperation
            operation TestOperation {
                input: TestInput
            }

            /// An input for TestOperation with a nested member
            structure TestInput {
                /// It's a string
                str: String

                /// The nested member
                nested: Nested

                list: NestedList

                doubleNestedList: DoubleNestedList
            }

            list NestedList {
                member: Nested
            }

            list DoubleNestedList {
                member: NestedList
            }

            /// A structure that can be nested
            structure Nested {
                /// A string that's nested
                nestedStr: String

                /// A document that's nested
                nestedDocument: Document

                /// A field that recurses back into us
                recursive: Recursive
            }

            /// A structure that references itself recursively
            structure Recursive {
                /// the nested field that points back to us
                nested: Nested
            }""";

    private static final Model MODEL = Model.assembler()
            .addUnparsedModel("test.smithy", MODEL_STR)
            .discoverModels()
            .assemble()
            .unwrap();
}
