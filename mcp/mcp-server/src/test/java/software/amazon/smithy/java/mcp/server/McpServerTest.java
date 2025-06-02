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
    public void nestedDefinitions() {
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
        var tool = tools.get(0).asStringMap();
        assertEquals("TestOperation", tool.get("name").asString());
        var inputSchema = tool.get("inputSchema").asStringMap();
        assertEquals("object", inputSchema.get("type").asString());
        assertEquals("An input for TestOperation with a nested member",
                inputSchema.get("description").asString());

        var properties = inputSchema.get("properties").asStringMap();
        var str = properties.get("str").asStringMap();
        assertEquals("string", str.get("type").asString());
        assertEquals("It's a string", str.get("description").asString());

        var nested = properties.get("nested").asStringMap();
        assertEquals("object", nested.get("type").asString());
        assertEquals("The nested member. A structure that can be nested", nested.get("description").asString());
        var nestedProperties = nested.get("properties").asStringMap();

        var nestedStr = nestedProperties.get("nestedStr").asStringMap();
        assertEquals("string", nestedStr.get("type").asString());
        assertEquals("A string that's nested", nestedStr.get("description").asString());
        assertEquals("string", nestedStr.get("type").asString());

        var nestedDocument = nestedProperties.get("nestedDocument").asStringMap();
        assertEquals("A document that's nested", nestedDocument.get("description").asString());
        assertTrue(nestedDocument.get("additionalProperties").asBoolean());

        var list = properties.get("list").asStringMap();
        assertEquals("array", list.get("type").asString());
        var listItems = list.get("items").asStringMap();
        assertEquals("object", listItems.get("type").asString());

        var doubleNestedList = properties.get("doubleNestedList").asStringMap();
        assertEquals("array", doubleNestedList.get("type").asString());
        var doubleNestedListItems = doubleNestedList.get("items").asStringMap();
        assertEquals("array", doubleNestedListItems.get("type").asString());
        var doubleNestedListItemsItems = doubleNestedListItems.get("items").asStringMap();
        assertEquals("object", doubleNestedListItemsItems.get("type").asString());
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
            }""";

    private static final Model MODEL = Model.assembler()
            .addUnparsedModel("test.smithy", MODEL_STR)
            .discoverModels()
            .assemble()
            .unwrap();
}
