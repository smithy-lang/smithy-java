/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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

    private List<Document> getTools() {
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
        assertEquals(1, tools.size());
        return tools;
    }

    @Test
    public void nestedDefinitions() {
        var tool = getTools().get(0).asStringMap();
        assertEquals("TestOperation", tool.get("name").asString());
        var inputSchema = tool.get("inputSchema").asStringMap();
        assertEquals("object", inputSchema.get("type").asString());
        assertEquals("An input for TestOperation with a nested member",
                inputSchema.get("description").asString());

        var properties = inputSchema.get("properties").asStringMap();
        var definitions = inputSchema.get("definitions").asStringMap();
        var str = properties.get("str").asStringMap();
        assertEquals("string", str.get("type").asString());

        var nested = properties.get("nested").asStringMap();
        assertEquals("#/definitions/smithy.test#Nested", nested.get("$ref").asString());
        assertEquals("nested member", nested.get("description").asString());

        var nestedDef = definitions.get("smithy.test#Nested").asStringMap();
        assertEquals("object", nestedDef.get("type").asString());
        assertEquals("A structure that can be nested", nestedDef.get("description").asString());

        var nestedProperties = nestedDef.get("properties").asStringMap();

        var nestedStr = nestedProperties.get("nestedStr").asStringMap();
        assertEquals("string", nestedStr.get("type").asString());

        var nestedDocument = nestedProperties.get("nestedDocument").asStringMap();
        assertEquals("#/definitions/smithy.api#Document", nestedDocument.get("$ref").asString());

        assertEquals("nestedDocument member", nestedDocument.get("description").asString());

        var nestedDocumentDef = definitions.get("smithy.api#Document").asStringMap();
        assertTrue(nestedDocumentDef.get("additionalProperties").asBoolean());

        var list = properties.get("list").asStringMap();
        assertEquals("array", list.get("type").asString());
        assertEquals("A list of Nested", list.get("description").asString());
        var listItems = list.get("items").asStringMap();

        assertEquals("#/definitions/smithy.test#Nested", listItems.get("$ref").asString());
        assertEquals("member member", listItems.get("description").asString());

        var doubleNestedList = properties.get("doubleNestedList").asStringMap();
        assertEquals("array", doubleNestedList.get("type").asString());
        assertEquals("A double-nested list of Nested", doubleNestedList.get("description").asString());
        var doubleNestedListItems = doubleNestedList.get("items").asStringMap();
        assertEquals("array", doubleNestedListItems.get("type").asString());
        assertEquals("A list of Nested", doubleNestedListItems.get("description").asString());
        var doubleNestedListItemsItems = doubleNestedListItems.get("items").asStringMap();

        assertEquals("#/definitions/smithy.test#Nested", doubleNestedListItemsItems.get("$ref").asString());
        assertEquals("member member", doubleNestedListItemsItems.get("description").asString());
    }

    @Test
    public void recursiveDefinitions() {
        var tool = getTools().get(0).asStringMap();
        var inputSchema = tool.get("inputSchema").asStringMap();
        var definitions = inputSchema.get("definitions").asStringMap();

        var nestedDef = definitions.get("smithy.test#Nested").asStringMap();
        var nestedProperties = nestedDef.get("properties").asStringMap();
        var recursive = nestedProperties.get("recursive").asStringMap();

        assertEquals("recursive member", recursive.get("description").asString());
        assertEquals("#/definitions/smithy.test#Recursive", recursive.get("$ref").asString());

        var recursiveDef = definitions.get("smithy.test#Recursive").asStringMap();
        assertEquals("object", recursiveDef.get("type").asString());
        assertEquals("A structure that's recursively referenced", recursiveDef.get("description").asString());
        assertEquals("http://json-schema.org/draft-07/schema#", recursiveDef.get("$schema").asString());
        assertEquals(false, recursiveDef.get("additionalProperties").asBoolean());

        var recursiveProperties = recursiveDef.get("properties").asStringMap();
        var recurseNested = recursiveProperties.get("recurseNested").asStringMap();
        assertEquals("A structure that can be nested", recurseNested.get("description").asString());
        assertEquals("#/definitions/smithy.test#Nested", recurseNested.get("$ref").asString());

        var nestedRef = recurseNested.get("$ref").asString();
        var recursiveRef = recursive.get("$ref").asString();
        assertEquals("#/definitions/smithy.test#Nested", nestedRef);
        assertEquals("#/definitions/smithy.test#Recursive", recursiveRef);
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
                /// str member
                str: String

                bool: PrimitiveBoolean = false

                /// nested member
                nested: Nested

                // list member
                list: NestedList

                // doubleNestedList member
                doubleNestedList: DoubleNestedList

                // booleanList member
                booleanList: BooleanList
            }

            /// A list of Nested
            list NestedList {
                /// member member
                member: Nested
            }

            /// A double-nested list of Nested
            list DoubleNestedList {
                /// doubleNested member
                member: NestedList
            }

            list BooleanList {
                member: PrimitiveBoolean
            }

            /// A structure that can be nested
            structure Nested {
                /// nestedStr member
                nestedStr: String

                /// nestedDocument member
                nestedDocument: Document

                /// recursive member
                recursive: Recursive
            }

            /// A structure that's recursively referenced
            structure Recursive {
                // recurseNested member
                recurseNested: Nested
            }""";

    private static final Model MODEL = Model.assembler()
            .addUnparsedModel("test.smithy", MODEL_STR)
            .discoverModels()
            .assemble()
            .unwrap();
}
