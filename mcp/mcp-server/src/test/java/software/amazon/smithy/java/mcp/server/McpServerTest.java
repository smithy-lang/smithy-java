/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.InputHook;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicschemas.StructDocument;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.server.ProxyService;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

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

    private void initializeWithProtocolVersion(ProtocolVersion protocolVersion) {
        final Document pvDoc;
        final String expectedPv;
        if (protocolVersion == null) {
            pvDoc = Document.of(Map.of());
            expectedPv = ProtocolVersion.v2024_11_05.INSTANCE.identifier();
        } else {
            pvDoc = Document.of(Map.of("protocolVersion", Document.of(protocolVersion.identifier())));
            expectedPv = protocolVersion.identifier();
        }
        write("initialize", pvDoc);
        var pv = read().getResult().getMember("protocolVersion").asString();
        assertEquals(expectedPv, pv);
    }

    @Test
    public void noOutputSchemaWithUnsupportedProtocolVersion() {
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        initializeWithProtocolVersion(ProtocolVersion.v2025_03_26.INSTANCE);
        write("tools/list", Document.of(Map.of()));
        var response = read();
        var tools = response.getResult().asStringMap().get("tools").asList();

        var tool = tools.stream()
                .filter(t -> t.asStringMap().get("name").asString().equals("NoInputOperation"))
                .findFirst()
                .orElseThrow()
                .asStringMap();

        assertEquals("NoInputOperation", tool.get("name").asString());
        assertNotNull(tool.get("inputSchema"));
        assertNull(tool.get("outputSchema"));
    }

    @Test
    public void validateToolsList() {
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        initializeWithProtocolVersion(null);
        write("tools/list", Document.of(Map.of()));
        var response = read();
        var result = response.getResult().asStringMap();
        var tools = result.get("tools").asList();

        assertEquals(4, tools.size());

        var toolNames = tools.stream()
                .map(tool -> tool.asStringMap().get("name").asString())
                .toList();

        assertTrue(toolNames.contains("NoIOOperation"));
        assertTrue(toolNames.contains("NoOutputOperation"));
        assertTrue(toolNames.contains("TestOperation"));
        assertTrue(toolNames.contains("NoInputOperation"));
    }

    @Test
    public void validateNoIOOperationTool() {
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        initializeWithProtocolVersion(ProtocolVersion.v2025_06_18.INSTANCE);
        write("tools/list", Document.of(Map.of()));
        var response = read();
        var tools = response.getResult().asStringMap().get("tools").asList();

        var tool = tools.stream()
                .filter(t -> t.asStringMap().get("name").asString().equals("NoIOOperation"))
                .findFirst()
                .orElseThrow()
                .asStringMap();

        assertEquals("NoIOOperation", tool.get("name").asString());
        assertEquals("This tool invokes NoIOOperation API of TestService.", tool.get("description").asString());

        validateEmptySchema(tool.get("inputSchema").asStringMap());
        validateEmptySchema(tool.get("outputSchema").asStringMap());
    }

    @Test
    public void validateNoOutputOperationTool() {
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        initializeWithProtocolVersion(ProtocolVersion.v2025_06_18.INSTANCE);
        write("tools/list", Document.of(Map.of()));
        var response = read();
        var tools = response.getResult().asStringMap().get("tools").asList();

        var tool = tools.stream()
                .filter(t -> t.asStringMap().get("name").asString().equals("NoOutputOperation"))
                .findFirst()
                .orElseThrow()
                .asStringMap();

        assertEquals("NoOutputOperation", tool.get("name").asString());
        assertEquals("This tool invokes NoOutputOperation API of TestService.", tool.get("description").asString());

        var inputSchema = tool.get("inputSchema").asStringMap();
        assertEquals("object", inputSchema.get("type").asString());
        assertEquals("http://json-schema.org/draft-07/schema#", inputSchema.get("$schema").asString());
        assertTrue(inputSchema.get("required").asList().isEmpty());

        var properties = inputSchema.get("properties").asStringMap();
        assertTrue(properties.containsKey("inputStr"));
        var inputStr = properties.get("inputStr").asStringMap();
        assertEquals("string", inputStr.get("type").asString());

        validateEmptySchema(tool.get("outputSchema").asStringMap());
    }

    @Test
    public void validateNoInputOperationTool() {
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        initializeWithProtocolVersion(ProtocolVersion.v2025_06_18.INSTANCE);
        write("tools/list", Document.of(Map.of()));
        var response = read();
        var tools = response.getResult().asStringMap().get("tools").asList();

        var tool = tools.stream()
                .filter(t -> t.asStringMap().get("name").asString().equals("NoInputOperation"))
                .findFirst()
                .orElseThrow()
                .asStringMap();

        assertEquals("NoInputOperation", tool.get("name").asString());
        assertEquals("This tool invokes NoInputOperation API of TestService.", tool.get("description").asString());

        validateEmptySchema(tool.get("inputSchema").asStringMap());

        var outputSchema = tool.get("outputSchema").asStringMap();
        assertEquals("object", outputSchema.get("type").asString());
        assertEquals("http://json-schema.org/draft-07/schema#", outputSchema.get("$schema").asString());
        assertTrue(outputSchema.get("required").asList().isEmpty());

        var properties = outputSchema.get("properties").asStringMap();
        assertTrue(properties.containsKey("outputStr"));
        var outputStr = properties.get("outputStr").asStringMap();
        assertEquals("string", outputStr.get("type").asString());
    }

    @Test
    public void validateTestOperationTool() {
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        initializeWithProtocolVersion(ProtocolVersion.v2025_06_18.INSTANCE);
        write("tools/list", Document.of(Map.of()));
        var response = read();
        var tools = response.getResult().asStringMap().get("tools").asList();

        var tool = tools.stream()
                .filter(t -> t.asStringMap().get("name").asString().equals("TestOperation"))
                .findFirst()
                .orElseThrow()
                .asStringMap();

        assertEquals("TestOperation", tool.get("name").asString());
        assertEquals("A TestOperation", tool.get("description").asString());

        validateTestInputSchema(tool.get("inputSchema").asStringMap());
        validateTestInputSchema(tool.get("outputSchema").asStringMap());
    }

    @Test
    void testNumberAndStringIds() {
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        // Test with numeric ID
        write("tools/list", Document.of(Map.of()), Document.of(42));
        var response = read();
        assertEquals(42, response.getId().asNumber().intValue());
        assertNotNull(response.getResult());

        // Test with string ID
        write("tools/list", Document.of(Map.of()), Document.of("test-id-1"));
        response = read();
        assertEquals("test-id-1", response.getId().asString());
        assertNotNull(response.getResult());

        // Test sequence: number -> string -> number -> string
        write("tools/list", Document.of(Map.of()), Document.of(1));
        response = read();
        assertEquals(1, response.getId().asNumber().intValue());

        write("tools/list", Document.of(Map.of()), Document.of("mixed-test"));
        response = read();
        assertEquals("mixed-test", response.getId().asString());

        write("tools/list", Document.of(Map.of()), Document.of(999));
        response = read();
        assertEquals(999, response.getId().asNumber().intValue());

        write("tools/list", Document.of(Map.of()), Document.of("final-string-id"));
        response = read();
        assertEquals("final-string-id", response.getId().asString());
    }

    @Test
    void testInvalidIds() {
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        // Test with boolean ID (should fail)
        write("tools/list", Document.of(Map.of()), Document.of(true));
        var response = read();
        assertNotNull(response.getError());
        assertTrue(response.getError().getMessage().contains("Request id is of invalid type"));

        // Test with double ID (should fail)
        write("tools/list", Document.of(Map.of()), Document.of(3.14));
        response = read();
        assertNotNull(response.getError());
        assertTrue(response.getError().getMessage().contains("Request id is of invalid type"));

        // Test with array ID (should fail)
        write("tools/list",
                Document.of(Map.of()),
                Document.of(List.of(Document.of(1), Document.of(2), Document.of(3))));
        response = read();
        assertNotNull(response.getError());
        assertTrue(response.getError().getMessage().contains("Request id is of invalid type"));

        // Test with object ID (should fail)
        write("tools/list", Document.of(Map.of()), Document.of(Map.of("key", Document.of("value"))));
        response = read();
        assertNotNull(response.getError());
        assertTrue(response.getError().getMessage().contains("Request id is of invalid type"));
    }

    @Test
    void testRequestsRequireIds() {
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        // Test regular request without ID (should fail with specific message)
        write("tools/list", Document.of(Map.of()), null);
        var response = read();
        assertNotNull(response.getError());
        assertTrue(response.getError().getMessage().contains("Requests are expected to have ids"));
    }

    @Test
    void testInputAdaptation() {
        AtomicReference<StructDocument> capturedInput = new AtomicReference<>();
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .clientConfigurator(
                                        clientConfigurator -> clientConfigurator
                                                .addInterceptor(new ClientInterceptor() {
                                                    @Override
                                                    public void readBeforeSerialization(InputHook<?, ?> hook) {
                                                        capturedInput.set((StructDocument) hook.input());
                                                    }
                                                }))
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        var bigDecimalValue = BigDecimal.valueOf(Integer.MAX_VALUE).add(BigDecimal.TEN);
        var bigIntegerValue = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(100));
        var blobValue = "Hello, World!";
        var blobValueBase64 = Base64.getEncoder().encodeToString(blobValue.getBytes(StandardCharsets.UTF_8));
        var nestedBigDecimalValue = new BigDecimal("123.456");
        var nestedBigIntegerValue = new BigInteger("9876543210");
        var nestedBlobValue = "Nested blob content";
        var nestedBlobValueBase64 =
                Base64.getEncoder().encodeToString(nestedBlobValue.getBytes(StandardCharsets.UTF_8));

        write("tools/call",
                Document.of(
                        Map.of("name",
                                Document.of("TestOperation"),
                                "arguments",
                                Document.of(Map.of(
                                        "bigDecimalField",
                                        Document.of(bigDecimalValue.toString()),
                                        "bigIntegerField",
                                        Document.of(bigIntegerValue.toString()),
                                        "blobField",
                                        Document.of(blobValueBase64),
                                        "nestedWithBigNumbers",
                                        Document.of(Map.of(
                                                "nestedBigDecimal",
                                                Document.of(nestedBigDecimalValue.toString()),
                                                "nestedBigInteger",
                                                Document.of(nestedBigIntegerValue.toString()),
                                                "nestedBlob",
                                                Document.of(nestedBlobValueBase64),
                                                "bigDecimalList",
                                                Document.of(List.of(
                                                        Document.of("100.25"),
                                                        Document.of("200.75"))))))))));
        assertNotNull(read());
        var inputDocument = capturedInput.get();

        var bigDecimalField = inputDocument.getMember("bigDecimalField");
        assertNotNull(bigDecimalField);
        assertEquals(ShapeType.BIG_DECIMAL, bigDecimalField.type());
        assertEquals(bigDecimalValue, bigDecimalField.asBigDecimal());

        var bigIntegerField = inputDocument.getMember("bigIntegerField");
        assertNotNull(bigIntegerField);
        assertEquals(ShapeType.BIG_INTEGER, bigIntegerField.type());
        assertEquals(bigIntegerValue, bigIntegerField.asBigInteger());

        var blobField = inputDocument.getMember("blobField");
        assertNotNull(blobField);
        assertEquals(ShapeType.BLOB, blobField.type());
        // Blob is sent as Base64 encoded string, decoded to bytes
        assertEquals(blobValue, new String(blobField.asBlob().array(), StandardCharsets.UTF_8));

        var nestedWithBigNumbers = inputDocument.getMember("nestedWithBigNumbers");
        assertNotNull(nestedWithBigNumbers);
        assertEquals(ShapeType.STRUCTURE, nestedWithBigNumbers.type());

        var nestedStruct = (StructDocument) nestedWithBigNumbers;

        var nestedBigDecimalField = nestedStruct.getMember("nestedBigDecimal");
        assertNotNull(nestedBigDecimalField);
        assertEquals(ShapeType.BIG_DECIMAL, nestedBigDecimalField.type());
        assertEquals(nestedBigDecimalValue, nestedBigDecimalField.asBigDecimal());

        var nestedBigIntegerField = nestedStruct.getMember("nestedBigInteger");
        assertNotNull(nestedBigIntegerField);
        assertEquals(ShapeType.BIG_INTEGER, nestedBigIntegerField.type());
        assertEquals(nestedBigIntegerValue, nestedBigIntegerField.asBigInteger());

        var nestedBlobField = nestedStruct.getMember("nestedBlob");
        assertNotNull(nestedBlobField);
        assertEquals(ShapeType.BLOB, nestedBlobField.type());
        assertEquals(nestedBlobValue, new String(nestedBlobField.asBlob().array(), StandardCharsets.UTF_8));

        var bigDecimalListField = nestedStruct.getMember("bigDecimalList");
        assertNotNull(bigDecimalListField);
        assertEquals(ShapeType.LIST, bigDecimalListField.type());
        var bigDecimalList = bigDecimalListField.asList();
        assertEquals(2, bigDecimalList.size());
        assertEquals(ShapeType.BIG_DECIMAL, bigDecimalList.get(0).type());
        assertEquals(ShapeType.BIG_DECIMAL, bigDecimalList.get(1).type());
        assertEquals(new BigDecimal("100.25"), bigDecimalList.get(0).asBigDecimal());
        assertEquals(new BigDecimal("200.75"), bigDecimalList.get(1).asBigDecimal());

        server.shutdown().join();
    }

    @Test
    void testNotificationsDoNotRequireRequestId() {
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        // Test notifications/initialized without ID (should not produce any error response)
        writeNotification("notifications/initialized", Document.of(Map.of()));
        output.assertNoOutput();

        // Test another notification to ensure it's consistently handled
        writeNotification("notifications/progress", Document.of(Map.of("progressToken", Document.of("test"))));
        output.assertNoOutput();

        // Send a regular request to verify the server is still functioning
        write("tools/list", Document.of(Map.of()));
        var response = read();
        assertNotNull(response.getResult());
    }

    @Test
    void testPromptsList() {
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        write("prompts/list", Document.of(Map.of()));
        var response = read();
        var prompts = response.getResult().asStringMap().get("prompts").asList();

        prompts.forEach(prompt -> System.out.println(prompt.asStringMap()));
        assertEquals(2, prompts.size());

        // Check the prompt (service and operation have same name, so only one is returned)
        var servicePrompt = prompts.stream()
                .filter(p -> p.asStringMap().get("name").asString().equals("search_users"))
                .findFirst()
                .orElseThrow();
        var servicePromptMap = servicePrompt.asStringMap();
        assertEquals("search_users", servicePromptMap.get("name").asString());
        assertEquals("Test Template", servicePromptMap.get("description").asString());
        assertTrue(servicePromptMap.get("arguments").asList().isEmpty());

        var promptNames = prompts.stream()
                .map(p -> p.asStringMap().get("name").asString())
                .toList();
        assertTrue(promptNames.contains("search_users"));
        assertTrue(promptNames.contains("perform_operation"));

        for (String name : promptNames) {
            assertEquals(name.toLowerCase(), name, "Prompt name should be normalized to lowercase: " + name);
        }
    }

    @Test
    void testPromptsGetWithValidPrompt() {
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        write("prompts/get",
                Document.of(Map.of(
                        "name",
                        Document.of("search_users"))));
        var response = read();
        var result = response.getResult().asStringMap();

        assertEquals("Test Template", result.get("description").asString());
        var messages = result.get("messages").asList();
        assertEquals(1, messages.size());

        var message = messages.get(0).asStringMap();
        assertEquals("user", message.get("role").asString());
        var content = message.get("content").asStringMap();
        assertEquals("text", content.get("type").asString());
        assertEquals("Search for if many results expected.", content.get("text").asString());
    }

    @Test
    void testPromptsGetWithDifferentCasing() {
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        // Test with uppercase prompt name
        write("prompts/get",
                Document.of(Map.of(
                        "name",
                        Document.of("SEARCH_USERS"))));
        var response = read();
        var result = response.getResult().asStringMap();

        assertEquals("Test Template", result.get("description").asString());
        var messages = result.get("messages").asList();
        assertEquals(1, messages.size());

        var message = messages.get(0).asStringMap();
        assertEquals("user", message.get("role").asString());
        var content = message.get("content").asStringMap();
        assertEquals("text", content.get("type").asString());
        assertEquals("Search for if many results expected.", content.get("text").asString());

        // Test with mixed case prompt name
        write("prompts/get",
                Document.of(Map.of(
                        "name",
                        Document.of("Search_Users"))));
        response = read();
        result = response.getResult().asStringMap();

        assertEquals("Test Template", result.get("description").asString());
        messages = result.get("messages").asList();
        assertEquals(1, messages.size());

        message = messages.get(0).asStringMap();
        assertEquals("user", message.get("role").asString());
        content = message.get("content").asStringMap();
        assertEquals("text", content.get("type").asString());
        assertEquals("Search for if many results expected.", content.get("text").asString());

        // Test with perform_operation prompt in different case
        write("prompts/get",
                Document.of(Map.of(
                        "name",
                        Document.of("PERFORM_OPERATION"))));
        response = read();
        result = response.getResult().asStringMap();

        assertEquals("perform operation", result.get("description").asString());
        messages = result.get("messages").asList();
        assertEquals(1, messages.size());

        message = messages.get(0).asStringMap();
        assertEquals("user", message.get("role").asString());
        content = message.get("content").asStringMap();
        assertEquals("text", content.get("type").asString());
        assertEquals("use tool TestOperation with some information.", content.get("text").asString());
    }

    @Test
    void testPromptsGetWithInvalidPrompt() {
        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test#TestService"))
                                .proxyEndpoint("http://localhost")
                                .model(MODEL)
                                .build())
                .build();

        server.start();

        write("prompts/get",
                Document.of(Map.of(
                        "name",
                        Document.of("nonexistent_prompt"))));
        var response = read();
        assertNotNull(response.getError());
        assertTrue(response.getError().getMessage().contains("Prompt not found: nonexistent_prompt"));

        // Test with invalid prompt in different case - should still fail
        write("prompts/get",
                Document.of(Map.of(
                        "name",
                        Document.of("NONEXISTENT_PROMPT"))));
        response = read();
        assertNotNull(response.getError());
        assertTrue(response.getError().getMessage().contains("Prompt not found: NONEXISTENT_PROMPT"));
    }

    @Test
    void testPromptsGetWithTemplateArguments() {
        var modelWithArgs = Model.assembler()
                .addUnparsedModel("test-with-args.smithy", PROMPT_WITH_ARGS)
                .discoverModels()
                .assemble()
                .unwrap();

        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test.args#TestServiceWithArgs"))
                                .proxyEndpoint("http://localhost")
                                .model(modelWithArgs)
                                .build())
                .build();

        server.start();

        // Test with arguments provided
        write("prompts/get",
                Document.of(Map.of(
                        "name",
                        Document.of("search_with_args"),
                        "arguments",
                        Document.of(Map.of(
                                "query",
                                Document.of("test query"),
                                "limit",
                                Document.of("10"))))));
        var response = read();
        var result = response.getResult().asStringMap();

        var messages = result.get("messages").asList();
        var message = messages.get(0).asStringMap();
        var content = message.get("content").asStringMap();
        assertEquals("Search for test query with limit 10", content.get("text").asString());
    }

    @Test
    void testPromptsGetWithMissingRequiredArguments() {
        var modelWithArgs = Model.assembler()
                .addUnparsedModel("test-with-args.smithy", PROMPT_WITH_ARGS)
                .discoverModels()
                .assemble()
                .unwrap();

        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test.args#TestServiceWithArgs"))
                                .proxyEndpoint("http://localhost")
                                .model(modelWithArgs)
                                .build())
                .build();

        server.start();

        // Test without required arguments
        write("prompts/get",
                Document.of(Map.of(
                        "name",
                        Document.of("search_with_args"))));
        var response = read();
        var result = response.getResult().asStringMap();

        var messages = result.get("messages").asList();
        var message = messages.get(0).asStringMap();
        assertEquals("user", message.get("role").asString());
        var content = message.get("content").asStringMap();
        assertTrue(content.get("text").asString().contains("missing arguments"));
        assertTrue(content.get("text").asString().contains("query"));
    }

    @Test
    void testApplyTemplateArgumentsEdgeCases() {
        var modelEdgeCases = Model.assembler()
                .addUnparsedModel("test-edge-cases.smithy", PROMPT_EDGE_CASES)
                .discoverModels()
                .assemble()
                .unwrap();

        server = McpServer.builder()
                .name("smithy-mcp-server")
                .input(input)
                .output(output)
                .addService("test-mcp",
                        ProxyService.builder()
                                .service(ShapeId.from("smithy.test.edge#TestServiceEdgeCases"))
                                .proxyEndpoint("http://localhost")
                                .model(modelEdgeCases)
                                .build())
                .build();

        server.start();

        // Test with empty template
        write("prompts/get",
                Document.of(Map.of(
                        "name",
                        Document.of("empty_template"))));
        var response = read();
        var result = response.getResult().asStringMap();
        var messages = result.get("messages").asList();
        var message = messages.get(0).asStringMap();
        var content = message.get("content").asStringMap();
        assertEquals("", content.get("text").asString());

        // Test with no placeholders
        write("prompts/get",
                Document.of(Map.of(
                        "name",
                        Document.of("no_placeholders"))));
        response = read();
        result = response.getResult().asStringMap();
        messages = result.get("messages").asList();
        message = messages.get(0).asStringMap();
        content = message.get("content").asStringMap();
        assertEquals("This has no placeholders", content.get("text").asString());

        // Test with multiple same placeholders
        write("prompts/get",
                Document.of(Map.of(
                        "name",
                        Document.of("duplicate_placeholders"),
                        "arguments",
                        Document.of(Map.of(
                                "name",
                                Document.of("John"))))));
        response = read();
        result = response.getResult().asStringMap();
        messages = result.get("messages").asList();
        message = messages.get(0).asStringMap();
        content = message.get("content").asStringMap();
        assertEquals("Hello John, welcome John!", content.get("text").asString());

        // Test with missing argument (should leave placeholder as-is when no arguments provided)
        write("prompts/get",
                Document.of(Map.of(
                        "name",
                        Document.of("missing_arg_template"))));
        response = read();
        result = response.getResult().asStringMap();
        messages = result.get("messages").asList();
        message = messages.get(0).asStringMap();
        content = message.get("content").asStringMap();
        assertEquals("Hello {{name}}, how are you?", content.get("text").asString());
    }

    private void validateEmptySchema(Map<String, Document> schema) {
        assertEquals("object", schema.get("type").asString());
        assertEquals("http://json-schema.org/draft-07/schema#", schema.get("$schema").asString());
        assertTrue(schema.get("properties").asStringMap().isEmpty());
        assertTrue(schema.get("required").asList().isEmpty());
    }

    private void validateTestInputSchema(Map<String, Document> schema) {
        assertEquals("object", schema.get("type").asString());
        assertEquals("http://json-schema.org/draft-07/schema#", schema.get("$schema").asString());
        assertEquals("An input for TestOperation with a nested member", schema.get("description").asString());
        assertTrue(schema.get("required").asList().isEmpty());

        var properties = schema.get("properties").asStringMap();

        var str = properties.get("str").asStringMap();
        assertEquals("string", str.get("type").asString());
        assertEquals("It's a string", str.get("description").asString());

        var blobField = properties.get("blobField").asStringMap();
        assertEquals("string", blobField.get("type").asString());

        var bigIntegerField = properties.get("bigIntegerField").asStringMap();
        assertEquals("string", bigIntegerField.get("type").asString());

        var bigDecimalField = properties.get("bigDecimalField").asStringMap();
        assertEquals("string", bigDecimalField.get("type").asString());

        validateNestedWithBigNumbers(properties.get("nestedWithBigNumbers").asStringMap());
        validateNestedStructure(properties.get("nested").asStringMap());
        validateNestedList(properties.get("list").asStringMap());
        validateDoubleNestedList(properties.get("doubleNestedList").asStringMap());
    }

    private void validateNestedWithBigNumbers(Map<String, Document> nestedSchema) {
        assertEquals("object", nestedSchema.get("type").asString());
        assertEquals("A structure containing big number types", nestedSchema.get("description").asString());
        assertEquals("http://json-schema.org/draft-07/schema#", nestedSchema.get("$schema").asString());
        assertTrue(nestedSchema.get("required").asList().isEmpty());

        var properties = nestedSchema.get("properties").asStringMap();

        var nestedBigDecimal = properties.get("nestedBigDecimal").asStringMap();
        assertEquals("string", nestedBigDecimal.get("type").asString());
        assertEquals("A nested BigDecimal", nestedBigDecimal.get("description").asString());

        var nestedBigInteger = properties.get("nestedBigInteger").asStringMap();
        assertEquals("string", nestedBigInteger.get("type").asString());
        assertEquals("A nested BigInteger", nestedBigInteger.get("description").asString());

        var nestedBlob = properties.get("nestedBlob").asStringMap();
        assertEquals("string", nestedBlob.get("type").asString());
        assertEquals("A nested Blob", nestedBlob.get("description").asString());

        var bigDecimalList = properties.get("bigDecimalList").asStringMap();
        assertEquals("array", bigDecimalList.get("type").asString());
        assertFalse(bigDecimalList.get("uniqueItems").asBoolean());
        var listItems = bigDecimalList.get("items").asStringMap();
        assertEquals("string", listItems.get("type").asString());
    }

    private void validateNestedList(Map<String, Document> listSchema) {
        assertEquals("array", listSchema.get("type").asString());
        assertFalse(listSchema.get("uniqueItems").asBoolean());

        var listItems = listSchema.get("items").asStringMap();
        validateNestedStructure(listItems);
    }

    private void validateDoubleNestedList(Map<String, Document> doubleListSchema) {
        assertEquals("array", doubleListSchema.get("type").asString());
        assertFalse(doubleListSchema.get("uniqueItems").asBoolean());

        var outerItems = doubleListSchema.get("items").asStringMap();
        assertEquals("array", outerItems.get("type").asString());
        assertFalse(outerItems.get("uniqueItems").asBoolean());

        var innerItems = outerItems.get("items").asStringMap();
        validateNestedStructure(innerItems);
    }

    private void validateNestedStructure(Map<String, Document> nestedSchema) {
        assertEquals("object", nestedSchema.get("type").asString());
        assertEquals("A structure that can be nested", nestedSchema.get("description").asString());
        assertEquals("http://json-schema.org/draft-07/schema#", nestedSchema.get("$schema").asString());
        assertTrue(nestedSchema.get("required").asList().isEmpty());

        var properties = nestedSchema.get("properties").asStringMap();

        var nestedStr = properties.get("nestedStr").asStringMap();
        assertEquals("string", nestedStr.get("type").asString());
        assertEquals("A string that's nested", nestedStr.get("description").asString());

        var nestedDocument = properties.get("nestedDocument").asStringMap();
        var documentTypesDocs = nestedDocument.get("type").asList();
        var documentTypes = documentTypesDocs.stream().map(Document::asString).toList();
        assertEquals(6, documentTypes.size());
        assertTrue(documentTypes.contains("string"));
        assertTrue(documentTypes.contains("number"));
        assertTrue(documentTypes.contains("boolean"));
        assertTrue(documentTypes.contains("object"));
        assertTrue(documentTypes.contains("array"));
        assertTrue(documentTypes.contains("null"));

        var recursive = properties.get("recursive").asStringMap();
        assertEquals("object", recursive.get("type").asString());
        assertEquals("A structure that references itself recursively", recursive.get("description").asString());
        assertEquals("http://json-schema.org/draft-07/schema#", recursive.get("$schema").asString());
        assertTrue(recursive.get("required").asList().isEmpty());

        var recursiveProperties = recursive.get("properties").asStringMap();
        assertTrue(recursiveProperties.containsKey("nested"));
        var nestedRecursive = recursiveProperties.get("nested").asStringMap();
        assertEquals("object", nestedRecursive.get("type").asString());
        assertEquals("http://json-schema.org/draft-07/schema#", nestedRecursive.get("$schema").asString());
    }

    private void write(String method, Document document) {
        write(method, document, Document.of(id++));
    }

    private void write(String method, Document document, Document requestId) {
        var request = JsonRpcRequest.builder()
                .id(requestId)
                .method(method)
                .params(document)
                .jsonrpc("2.0")
                .build();
        input.write(CODEC.serializeToString(request));
        input.write("\n");
    }

    private JsonRpcResponse read() {
        var line = assertTimeoutPreemptively(Duration.ofSeconds(1), output::read, "No response within one second");
        return CODEC.deserializeShape(line, JsonRpcResponse.builder());
    }

    private void writeNotification(String method, Document params) {
        var request = JsonRpcRequest.builder()
                .method(method)
                .params(params)
                .jsonrpc("2.0")
                .build();
        input.write(CODEC.serializeToString(request));
        input.write("\n");
    }

    private static final String MODEL_STR =
            """
                    $version: "2"

                    namespace smithy.test

                    use smithy.ai#prompts

                    /// A TestService
                    @aws.protocols#awsJson1_0
                    @prompts({
                        search_users: { description: "Test Template", template: "Search for if many results expected." }
                    })
                    service TestService {
                        operations: [TestOperation, NoInputOperation, NoOutputOperation, NoIOOperation]
                    }

                    operation NoOutputOperation {
                        input := {
                            inputStr: String
                        }
                    }

                    operation NoInputOperation {
                        output := {
                            outputStr: String
                        }
                    }

                    operation NoIOOperation {}

                    /// A TestOperation
                    @prompts({
                        perform_operation: { description: "perform operation", template: "use tool TestOperation with some information." }
                    })
                    operation TestOperation {
                        input: TestInput
                        output: TestInput
                    }

                    /// An input for TestOperation with a nested member
                    structure TestInput {
                        /// It's a string
                        str: String

                        /// The nested member
                        nested: Nested

                        list: NestedList

                        doubleNestedList: DoubleNestedList

                        bigDecimalField: BigDecimal

                        bigIntegerField: BigInteger

                        blobField: Blob

                        nestedWithBigNumbers: NestedWithBigNumbers
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
                    }

                    /// A structure containing big number types
                    structure NestedWithBigNumbers {
                        /// A nested BigDecimal
                        nestedBigDecimal: BigDecimal

                        /// A nested BigInteger
                        nestedBigInteger: BigInteger

                        /// A nested Blob
                        nestedBlob: Blob

                        /// A list of BigDecimals
                        bigDecimalList: BigDecimalList
                    }

                    list BigDecimalList {
                        member: BigDecimal
                    }""";

    private static final String PROMPT_WITH_ARGS =
            """
                    $version: "2"

                    namespace smithy.test.args

                    use smithy.ai#prompts
                    use aws.protocols#awsJson1_0

                    @awsJson1_0
                    @prompts({
                        search_with_args: {
                            description: "Search with arguments",
                            template: "Search for {{query}} with limit {{limit}}",
                            arguments: SearchArgs
                        }
                    })
                    service TestServiceWithArgs {
                        operations: []
                    }

                    structure SearchArgs {
                        @required
                        query: String

                        limit: String
                    }""";

    private static final String PROMPT_EDGE_CASES =
            """
                    $version: "2"

                    namespace smithy.test.edge

                    use smithy.ai#prompts
                    use aws.protocols#awsJson1_0

                    @awsJson1_0
                    @prompts({
                        empty_template: {
                            description: "Empty template",
                            template: ""
                        },
                        no_placeholders: {
                            description: "No placeholders",
                            template: "This has no placeholders"
                        },
                        duplicate_placeholders: {
                            description: "Duplicate placeholders",
                            template: "Hello {{name}}, welcome {{name}}!",
                            arguments: NameArgs
                        },
                        missing_arg_template: {
                            description: "Missing argument",
                            template: "Hello {{name}}, how are you?"
                        }
                    })
                    service TestServiceEdgeCases {
                        operations: []
                    }

                    structure NameArgs {
                        name: String
                    }""";

    private static final Model MODEL = Model.assembler()
            .addUnparsedModel("test.smithy", MODEL_STR)
            .discoverModels()
            .assemble()
            .unwrap();
}
