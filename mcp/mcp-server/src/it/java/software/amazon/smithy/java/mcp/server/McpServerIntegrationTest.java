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

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.test.model.CalculateAreaInput;
import software.amazon.smithy.java.mcp.test.model.CalculateAreaOutput;
import software.amazon.smithy.java.mcp.test.model.Echo;
import software.amazon.smithy.java.mcp.test.model.McpEchoInput;
import software.amazon.smithy.java.mcp.test.model.McpEchoOutput;
import software.amazon.smithy.java.mcp.test.model.Shape;
import software.amazon.smithy.java.mcp.test.service.CalculateAreaOperation;
import software.amazon.smithy.java.mcp.test.service.McpEchoOperation;
import software.amazon.smithy.java.mcp.test.service.TestService;
import software.amazon.smithy.java.server.RequestContext;
import software.amazon.smithy.java.server.Server;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.MissingNode;

class McpServerIntegrationTest {

    private static final JsonCodec CODEC = JsonCodec.builder()
            .settings(JsonSettings.builder()
                    .serializeTypeInDocuments(false)
                    .useJsonName(true)
                    .build())
            .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SchemaRegistry SCHEMA_FACTORY =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private static final Schema JSON_SCHEMA = SCHEMA_FACTORY.getSchema(
            SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"));

    private Server mcpServer;
    private TestInputStream input;
    private TestOutputStream output;
    private McpEchoOperationImpl echoOperation;
    private CalculateAreaOperation calculateAreaOperation;
    private int requestId = 0;
    private final Map<String, Schema> outputSchemaCache = new HashMap<>();
    private ProtocolVersion currentProtocolVersion = null;

    @BeforeEach
    void init() {
        input = new TestInputStream();
        output = new TestOutputStream();
        echoOperation = new McpEchoOperationImpl();
        calculateAreaOperation = new CalculateAreaImpl();
        mcpServer = McpServer.builder()
                .name("test-mcp")
                .addService("test-service",
                        TestService.builder()
                                .addCalculateAreaOperation(calculateAreaOperation)
                                .addMcpEchoOperation(echoOperation)
                                .build())
                .input(input)
                .output(output)
                .build();
        mcpServer.start();
    }

    @AfterEach
    void teardown() {
        outputSchemaCache.clear();
        currentProtocolVersion = null;
        if (mcpServer != null) {
            mcpServer.shutdown().join();
        }
    }

    // ========== Helper Methods ==========

    private void initializeLatestProtocol() {
        initializeWithProtocolVersion(ProtocolVersion.v2025_06_18.INSTANCE);
    }

    private Document getEchoFromResponse(JsonRpcResponse response) {
        assertNull(response.getError());
        return response.getResult().getMember("structuredContent").getMember("echo");
    }

    private Document echoSingleField(String fieldName, Document value) {
        var echoInput = createEchoInput(Map.of(fieldName, value));
        var response = callTool("McpEcho", echoInput);
        return getEchoFromResponse(response);
    }

    private record ToolSchemas(Schema inputSchema, Schema outputSchema, JsonNode toolNode) {}

    private ToolSchemas getMcpEchoToolSchemas() {
        write("tools/list", Document.of(Map.of()));
        var responseJson = readRawResponse();
        var toolNode = OBJECT_MAPPER.readTree(responseJson).path("result").path("tools").get(0);
        var inputSchemaNode = toolNode.path("inputSchema");
        var outputSchemaNode = toolNode.path("outputSchema");
        return new ToolSchemas(
                SCHEMA_FACTORY.getSchema(inputSchemaNode),
                SCHEMA_FACTORY.getSchema(outputSchemaNode),
                toolNode);
    }

    private ToolSchemas getCalculateAreaToolSchemas() {
        write("tools/list", Document.of(Map.of()));
        var responseJson = readRawResponse();
        var toolsNode = OBJECT_MAPPER.readTree(responseJson).path("result").path("tools");
        for (var toolNode : toolsNode) {
            if (toolNode.path("name").asString().equals("CalculateArea")) {
                var inputSchemaNode = toolNode.path("inputSchema");
                var outputSchemaNode = toolNode.path("outputSchema");
                return new ToolSchemas(
                        SCHEMA_FACTORY.getSchema(inputSchemaNode),
                        SCHEMA_FACTORY.getSchema(outputSchemaNode),
                        toolNode);
            }
        }
        throw new AssertionError("CalculateArea tool not found");
    }

    private String readRawResponse() {
        return assertTimeoutPreemptively(Duration.ofSeconds(5), output::read, "No response within 5 seconds");
    }

    // ========== Protocol Version Tests ==========

    @Test
    void testInitializeWithDefaultVersion() {
        write("initialize", Document.of(Map.of()));
        var response = read();
        assertNotNull(response.getResult());
        assertEquals("2024-11-05", response.getResult().getMember("protocolVersion").asString());
    }

    @Test
    void testInitializeWithVersion2025_03_26() {
        initializeWithProtocolVersion(ProtocolVersion.v2025_03_26.INSTANCE);
    }

    @Test
    void testInitializeWithVersion2025_06_18() {
        initializeWithProtocolVersion(ProtocolVersion.v2025_06_18.INSTANCE);
    }

    @Test
    void testOutputSchemaNotPresentWithOlderProtocolVersion() {
        initializeWithProtocolVersion(ProtocolVersion.v2025_03_26.INSTANCE);
        write("tools/list", Document.of(Map.of()));
        var response = read();
        var tools = response.getResult().getMember("tools").asList();
        var mcpEchoTool = findTool(tools, "McpEcho");
        assertNotNull(mcpEchoTool.get("inputSchema"));
        assertNull(mcpEchoTool.get("outputSchema"));
    }

    @Test
    void testOutputSchemaPresentWithVersion2025_06_18() {
        initializeWithProtocolVersion(ProtocolVersion.v2025_06_18.INSTANCE);
        write("tools/list", Document.of(Map.of()));
        var response = read();
        var tools = response.getResult().getMember("tools").asList();
        var mcpEchoTool = findTool(tools, "McpEcho");
        assertNotNull(mcpEchoTool.get("inputSchema"));
        assertNotNull(mcpEchoTool.get("outputSchema"));
    }

    // ========== Schema Validation Tests ==========

    @Test
    void testGeneratedSchemasAreValidJsonSchemaDraft07() {
        initializeLatestProtocol();
        var schemas = getMcpEchoToolSchemas();

        // Validate that inputSchema conforms to JSON Schema Draft-07
        var inputErrors = JSON_SCHEMA.validate(schemas.toolNode().path("inputSchema"));
        assertTrue(inputErrors.isEmpty(), "Input schema validation errors: " + inputErrors);

        // Validate that outputSchema conforms to JSON Schema Draft-07
        var outputErrors = JSON_SCHEMA.validate(schemas.toolNode().path("outputSchema"));
        assertTrue(outputErrors.isEmpty(), "Output schema validation errors: " + outputErrors);
    }

    @Test
    void testToolsListSchemaStructure() {
        initializeLatestProtocol();
        var schemas = getMcpEchoToolSchemas();
        var toolNode = schemas.toolNode();

        assertEquals("McpEcho", toolNode.path("name").asString());
        assertFalse(toolNode.path("inputSchema").isMissingNode());
        assertFalse(toolNode.path("outputSchema").isMissingNode());

        // Use JsonSchema's getSchemaNode() for type assertions
        var inputSchemaNode = schemas.inputSchema().getSchemaNode();
        assertEquals("object", inputSchemaNode.path("type").asString());
        assertEquals("http://json-schema.org/draft-07/schema#", inputSchemaNode.path("$schema").asString());
        assertTrue(inputSchemaNode.path("properties").has("echo"));

        // Also verify output schema structure
        var outputSchemaNode = schemas.outputSchema().getSchemaNode();
        assertEquals("object", outputSchemaNode.path("type").asString());
        assertEquals("http://json-schema.org/draft-07/schema#", outputSchemaNode.path("$schema").asString());
        assertTrue(outputSchemaNode.path("properties").has("echo"));
    }

    @Test
    void testEchoSchemaTypeMapping() {
        initializeLatestProtocol();
        var schemas = getMcpEchoToolSchemas();

        // Navigate to echo properties using JsonSchema's getSchemaNode
        var inputEchoProps = schemas.inputSchema()
                .getSchemaNode()
                .path("properties")
                .path("echo")
                .path("properties");
        var outputEchoProps = schemas.outputSchema()
                .getSchemaNode()
                .path("properties")
                .path("echo")
                .path("properties");

        // Verify both input and output schemas have same type mappings
        for (var echoProps : List.of(inputEchoProps, outputEchoProps)) {
            // Primitives should map to appropriate JSON Schema types
            assertEquals("string", echoProps.path("stringValue").path("type").asString());
            assertEquals("boolean", echoProps.path("booleanValue").path("type").asString());
            assertEquals("number", echoProps.path("byteValue").path("type").asString());
            assertEquals("number", echoProps.path("shortValue").path("type").asString());
            assertEquals("number", echoProps.path("integerValue").path("type").asString());
            assertEquals("number", echoProps.path("longValue").path("type").asString());
            assertEquals("number", echoProps.path("floatValue").path("type").asString());
            assertEquals("number", echoProps.path("doubleValue").path("type").asString());

            // BigDecimal and BigInteger should be strings
            assertEquals("string", echoProps.path("bigDecimalValue").path("type").asString());
            assertEquals("string", echoProps.path("bigIntegerValue").path("type").asString());

            // Blob should be string (base64)
            assertEquals("string", echoProps.path("blobValue").path("type").asString());

            // All timestamps should be strings with format: date-time
            assertEquals("string", echoProps.path("epochSecondsTimestamp").path("type").asString());
            assertEquals("date-time", echoProps.path("epochSecondsTimestamp").path("format").asString());
            assertEquals("string", echoProps.path("dateTimeTimestamp").path("type").asString());
            assertEquals("date-time", echoProps.path("dateTimeTimestamp").path("format").asString());
            assertEquals("string", echoProps.path("httpDateTimestamp").path("type").asString());
            assertEquals("date-time", echoProps.path("httpDateTimestamp").path("format").asString());
            assertEquals("string", echoProps.path("defaultTimestamp").path("type").asString());
            assertEquals("date-time", echoProps.path("defaultTimestamp").path("format").asString());

            // Enum should be string with enum values
            assertEquals("string", echoProps.path("enumValue").path("type").asString());
            assertTrue(echoProps.path("enumValue").has("enum"), "enumValue should have enum constraint");
            var enumValues = new HashSet<String>();
            echoProps.path("enumValue").path("enum").forEach(node -> enumValues.add(node.asString()));
            assertEquals(Set.of("VALUE_ONE", "VALUE_TWO"), enumValues);

            // IntEnum should be number with enum values
            assertEquals("number", echoProps.path("intEnumValue").path("type").asString());
            assertTrue(echoProps.path("intEnumValue").has("enum"), "intEnumValue should have enum constraint");
            var intEnumValues = new HashSet<Integer>();
            echoProps.path("intEnumValue").path("enum").forEach(node -> intEnumValues.add(node.asInt()));
            assertEquals(Set.of(1, 2, 3), intEnumValues);

            // String with enum trait should be string with enum values
            assertEquals("string", echoProps.path("stringEnumValue").path("type").asString());
            assertTrue(echoProps.path("stringEnumValue").has("enum"), "stringEnumValue should have enum constraint");
            var stringEnumValues = new HashSet<String>();
            echoProps.path("stringEnumValue").path("enum").forEach(node -> stringEnumValues.add(node.asString()));
            assertEquals(Set.of("OPTION_A", "OPTION_B", "OPTION_C"), stringEnumValues);

            // Lists should be arrays
            assertEquals("array", echoProps.path("stringList").path("type").asString());
            assertEquals("array", echoProps.path("integerList").path("type").asString());
            assertEquals("array", echoProps.path("nestedList").path("type").asString());

            // Nested structure and map should be objects
            assertEquals("object", echoProps.path("nested").path("type").asString());
            assertEquals("object", echoProps.path("stringMap").path("type").asString());
            assertEquals("object", echoProps.path("nestedMap").path("type").asString());

            // Union should have oneOf (not type: object)
            assertTrue(echoProps.path("unionValue").has("oneOf"), "Union should have oneOf property");
            assertEquals("object", echoProps.path("unionValue").path("type").asString());

            // Document should have type as array of all JSON types
            var documentType = echoProps.path("documentValue").path("type");
            assertTrue(documentType.isArray(), "Document type should be an array");
            var types = new HashSet<String>();
            documentType.forEach(node -> types.add(node.asString()));
            assertEquals(Set.of("string", "number", "boolean", "object", "array", "null"), types);
        }
    }

    @Test
    void testRequiredFieldInSchema() {
        initializeLatestProtocol();
        var schemas = getMcpEchoToolSchemas();

        // Verify required field in both input and output schemas
        for (var schema : List.of(schemas.inputSchema(), schemas.outputSchema())) {
            var echoSchemaNode = schema.getSchemaNode().path("properties").path("echo");
            var required = echoSchemaNode.path("required");

            assertTrue(required.isArray(), "required should be an array");
            var requiredFields = new HashSet<String>();
            required.forEach(node -> requiredFields.add(node.asString()));
            assertTrue(requiredFields.contains("requiredField"), "requiredField should be in required array");
        }
    }

    // ========== Basic Primitive Round-Trip Tests ==========

    @Test
    void testStringRoundTrip() {
        initializeLatestProtocol();
        var echo = echoSingleField("stringValue", Document.of("hello world"));
        assertEquals("hello world", echo.getMember("stringValue").asString());
    }

    @Test
    void testBooleanRoundTrip() {
        initializeLatestProtocol();
        var echo = echoSingleField("booleanValue", Document.of(true));
        assertTrue(echo.getMember("booleanValue").asBoolean());
    }

    @Test
    void testIntegerTypesRoundTrip() {
        initializeLatestProtocol();
        var echoInput = createEchoInput(Map.of(
                "byteValue",
                Document.of(42),
                "shortValue",
                Document.of(1000),
                "integerValue",
                Document.of(100000),
                "longValue",
                Document.of(9999999999L)));
        var echo = getEchoFromResponse(callTool("McpEcho", echoInput));
        assertEquals(42, echo.getMember("byteValue").asNumber().intValue());
        assertEquals(1000, echo.getMember("shortValue").asNumber().intValue());
        assertEquals(100000, echo.getMember("integerValue").asNumber().intValue());
        assertEquals(9999999999L, echo.getMember("longValue").asNumber().longValue());
    }

    @Test
    void testFloatingPointRoundTrip() {
        initializeLatestProtocol();
        var echoInput = createEchoInput(Map.of(
                "floatValue",
                Document.of(3.14f),
                "doubleValue",
                Document.of(2.718281828)));
        var echo = getEchoFromResponse(callTool("McpEcho", echoInput));
        assertEquals(3.14f, echo.getMember("floatValue").asNumber().floatValue(), 0.001);
        assertEquals(2.718281828, echo.getMember("doubleValue").asNumber().doubleValue(), 0.0000001);
    }

    // ========== Big Number Tests ==========

    @Test
    void testBigDecimalRoundTrip() {
        initializeLatestProtocol();
        var bigDecValue = "123456789012345678901234567890.123456789";
        var echo = echoSingleField("bigDecimalValue", Document.of(bigDecValue));
        assertEquals(bigDecValue, echo.getMember("bigDecimalValue").asString());
    }

    @Test
    void testBigIntegerRoundTrip() {
        initializeLatestProtocol();
        var bigIntValue = "12345678901234567890123456789012345678901234567890";
        var echo = echoSingleField("bigIntegerValue", Document.of(bigIntValue));
        assertEquals(bigIntValue, echo.getMember("bigIntegerValue").asString());
    }

    // ========== Blob Tests ==========

    @Test
    void testBlobRoundTrip() {
        initializeLatestProtocol();
        var originalData = "Hello, Binary World!";
        var base64Encoded = Base64.getEncoder().encodeToString(originalData.getBytes(StandardCharsets.UTF_8));
        var echo = echoSingleField("blobValue", Document.of(base64Encoded));
        var returnedBase64 = echo.getMember("blobValue").asString();
        var decodedData = new String(Base64.getDecoder().decode(returnedBase64), StandardCharsets.UTF_8);
        assertEquals(originalData, decodedData);
    }

    // ========== Timestamp Tests ==========

    @Test
    void testEpochSecondsTimestampRoundTrip() {
        initializeLatestProtocol();
        var epochSeconds = 1700000000.0; // Input: epoch seconds number
        var expectedOutput = "2023-11-14T22:13:20Z"; // Output: date-time string
        var echo = echoSingleField("epochSecondsTimestamp", Document.of(epochSeconds));
        assertEquals(expectedOutput, echo.getMember("epochSecondsTimestamp").asString());
    }

    @Test
    void testDateTimeTimestampRoundTrip() {
        initializeLatestProtocol();
        var dateTimeStr = "2023-11-14T22:13:20Z";
        var echo = echoSingleField("dateTimeTimestamp", Document.of(dateTimeStr));
        assertEquals(dateTimeStr, echo.getMember("dateTimeTimestamp").asString());
    }

    @Test
    void testHttpDateTimestampRoundTrip() {
        initializeLatestProtocol();
        var httpDateStr = "Tue, 14 Nov 2023 22:13:20 GMT"; // Input: http-date string
        var expectedOutput = "2023-11-14T22:13:20Z"; // Output: date-time string
        var echo = echoSingleField("httpDateTimestamp", Document.of(httpDateStr));
        assertEquals(expectedOutput, echo.getMember("httpDateTimestamp").asString());
    }

    @Test
    void testDefaultTimestampRoundTrip() {
        initializeLatestProtocol();
        // Default format is date-time (ISO 8601 string)
        var dateTimeStr = "2023-11-14T22:13:20Z";
        var echo = echoSingleField("defaultTimestamp", Document.of(dateTimeStr));
        assertEquals(dateTimeStr, echo.getMember("defaultTimestamp").asString());
    }

    // ========== List Tests ==========

    @Test
    void testStringListRoundTrip() {
        initializeLatestProtocol();
        var stringList = List.of(Document.of("one"), Document.of("two"), Document.of("three"));
        var echo = echoSingleField("stringList", Document.of(stringList));
        var returnedList = echo.getMember("stringList").asList();
        assertEquals(3, returnedList.size());
        assertEquals("one", returnedList.get(0).asString());
        assertEquals("two", returnedList.get(1).asString());
        assertEquals("three", returnedList.get(2).asString());
    }

    @Test
    void testIntegerListRoundTrip() {
        initializeLatestProtocol();
        var intList = List.of(Document.of(1), Document.of(2), Document.of(3));
        var echo = echoSingleField("integerList", Document.of(intList));
        var returnedList = echo.getMember("integerList").asList();
        assertEquals(3, returnedList.size());
        assertEquals(1, returnedList.get(0).asNumber().intValue());
        assertEquals(2, returnedList.get(1).asNumber().intValue());
        assertEquals(3, returnedList.get(2).asNumber().intValue());
    }

    @Test
    void testNestedListRoundTrip() {
        initializeLatestProtocol();
        var nestedItem = Document.of(Map.of(
                "innerString",
                Document.of("nested"),
                "innerNumber",
                Document.of(42)));
        var echo = echoSingleField("nestedList", Document.of(List.of(nestedItem)));
        var returnedList = echo.getMember("nestedList").asList();
        assertEquals(1, returnedList.size());
        assertEquals("nested", returnedList.getFirst().getMember("innerString").asString());
        assertEquals(42, returnedList.getFirst().getMember("innerNumber").asNumber().intValue());
    }

    @Test
    void testEmptyListInDocumentValue() {
        initializeLatestProtocol();

        // Empty list inside documentValue
        var emptyListDoc = Document.of(List.of());
        var echoEmptyDocList = echoSingleField("documentValue", emptyListDoc);
        var returnedEmptyDocList = echoEmptyDocList.getMember("documentValue").asList();
        assertEquals(0, returnedEmptyDocList.size(), "Empty list in documentValue should have size 0");
        assertTrue(Document.equals(emptyListDoc, echoEmptyDocList.getMember("documentValue")),
                "Empty list document should match input");

        // Verify server received empty list in documentValue correctly
        var lastInputEmptyDocList = echoOperation.getLastInput().getEcho().getDocumentValue();
        assertTrue(Document.equals(emptyListDoc, lastInputEmptyDocList),
                "Server should receive empty list in documentValue");
    }

    @Test
    void testEmptyTypedListFields() {
        initializeLatestProtocol();

        // Empty stringList as typed Echo field
        var emptyStringListDoc = Document.of(List.of());
        var echoEmptyStringList = echoSingleField("stringList", emptyStringListDoc);
        var returnedStringList = echoEmptyStringList.getMember("stringList").asList();
        assertEquals(0, returnedStringList.size(), "Empty stringList should have size 0");

        // Verify server received empty list correctly
        var lastInputEmptyStringList = echoOperation.getLastInput().getEcho();
        assertEquals(0, lastInputEmptyStringList.getStringList().size(), "Server should receive empty stringList");

        // Empty integerList as typed Echo field
        var emptyIntListDoc = Document.of(List.of());
        var echoEmptyIntList = echoSingleField("integerList", emptyIntListDoc);
        var returnedIntList = echoEmptyIntList.getMember("integerList").asList();
        assertEquals(0, returnedIntList.size(), "Empty integerList should have size 0");

        // Verify server received empty list correctly
        var lastInputEmptyIntList = echoOperation.getLastInput().getEcho();
        assertEquals(0, lastInputEmptyIntList.getIntegerList().size(), "Server should receive empty integerList");
    }

    @Test
    void testNullTypedListFields() {
        initializeLatestProtocol();

        // Null stringList (explicit null in input)
        var echoDataWithNullStringList = new HashMap<String, Document>();
        echoDataWithNullStringList.put("stringList", null);

        var responseNullStringList = callTool("McpEcho", createEchoInput(echoDataWithNullStringList));
        assertNull(responseNullStringList.getError(),
                "Expected no error but got: "
                        + (responseNullStringList.getError() != null
                                ? responseNullStringList.getError().getMessage()
                                : ""));
        var echoNullStringList = getEchoFromResponse(responseNullStringList);

        // Verify null list is not present in response (omitted)
        assertNull(echoNullStringList.getMember("stringList"), "Null stringList should not be present in output");

        // Verify server did not receive stringList (null input means field is not set)
        var lastInputNullStringList = echoOperation.getLastInput().getEcho();
        assertFalse(lastInputNullStringList.hasStringList(), "Server should not have stringList set for null input");

        // Omitted stringList (field not present in input at all)
        var echoDataWithOmittedList = new HashMap<String, Document>();
        // stringList is intentionally not included

        var responseOmittedList = callTool("McpEcho", createEchoInput(echoDataWithOmittedList));
        assertNull(responseOmittedList.getError(),
                "Expected no error but got: "
                        + (responseOmittedList.getError() != null ? responseOmittedList.getError().getMessage() : ""));
        var echoOmittedList = getEchoFromResponse(responseOmittedList);

        // Verify omitted list is not present in response
        assertNull(echoOmittedList.getMember("stringList"), "Omitted stringList should not be present in output");

        // Verify server did not receive stringList (omitted input means field is not set)
        var lastInputOmittedList = echoOperation.getLastInput().getEcho();
        assertFalse(lastInputOmittedList.hasStringList(), "Server should not have stringList set for omitted input");
    }

    @Test
    void testNullDocumentValue() {
        initializeLatestProtocol();

        // Null documentValue (explicit null in input)
        var echoDataWithNullDoc = new HashMap<String, Document>();
        echoDataWithNullDoc.put("documentValue", null);

        var responseNullDoc = callTool("McpEcho", createEchoInput(echoDataWithNullDoc));
        assertNull(responseNullDoc.getError(),
                "Expected no error but got: "
                        + (responseNullDoc.getError() != null ? responseNullDoc.getError().getMessage() : ""));
        var echoNullDoc = getEchoFromResponse(responseNullDoc);
        assertNull(echoNullDoc.getMember("documentValue"), "Null documentValue should not be present in output");

        // Verify server received null for null documentValue
        var lastInputNullDoc = echoOperation.getLastInput().getEcho().getDocumentValue();
        assertNull(lastInputNullDoc, "Server should receive null for null documentValue");

        // Omitted documentValue (field not present in input)
        var echoDataWithOmittedDoc = new HashMap<String, Document>();
        // documentValue is intentionally not included

        var responseOmittedDoc = callTool("McpEcho", createEchoInput(echoDataWithOmittedDoc));
        assertNull(responseOmittedDoc.getError(),
                "Expected no error but got: "
                        + (responseOmittedDoc.getError() != null ? responseOmittedDoc.getError().getMessage() : ""));
        var echoOmittedDoc = getEchoFromResponse(responseOmittedDoc);
        assertNull(echoOmittedDoc.getMember("documentValue"), "Omitted documentValue should not be present in output");

        // Verify server received null for omitted documentValue
        var lastInputOmittedDoc = echoOperation.getLastInput().getEcho().getDocumentValue();
        assertNull(lastInputOmittedDoc, "Server should receive null for omitted documentValue");
    }

    @Test
    void testEmptyMapInDocumentValue() {
        initializeLatestProtocol();

        // Empty map inside documentValue
        var emptyMapDoc = Document.of(Map.of());
        var echoEmptyDocMap = echoSingleField("documentValue", emptyMapDoc);
        var returnedEmptyDocMap = echoEmptyDocMap.getMember("documentValue").asStringMap();
        assertEquals(0, returnedEmptyDocMap.size(), "Empty map in documentValue should have size 0");
        assertTrue(Document.equals(emptyMapDoc, echoEmptyDocMap.getMember("documentValue")),
                "Empty map document should match input");

        // Verify server received empty map in documentValue correctly
        var lastInputEmptyDocMap = echoOperation.getLastInput().getEcho().getDocumentValue();
        assertTrue(Document.equals(emptyMapDoc, lastInputEmptyDocMap),
                "Server should receive empty map in documentValue");
    }

    // ========== Map Tests ==========

    @Test
    void testStringMapRoundTrip() {
        initializeLatestProtocol();
        var stringMap = Map.of("key1", Document.of("value1"), "key2", Document.of("value2"));
        var echo = echoSingleField("stringMap", Document.of(stringMap));
        var returnedMap = echo.getMember("stringMap").asStringMap();
        assertEquals("value1", returnedMap.get("key1").asString());
        assertEquals("value2", returnedMap.get("key2").asString());
    }

    @Test
    void testNestedMapRoundTrip() {
        initializeLatestProtocol();
        var nestedValue = Document.of(Map.of(
                "innerString",
                Document.of("mapNested"),
                "innerNumber",
                Document.of(99)));
        var echo = echoSingleField("nestedMap", Document.of(Map.of("item", nestedValue)));
        var returnedMap = echo.getMember("nestedMap").asStringMap();
        assertEquals("mapNested", returnedMap.get("item").getMember("innerString").asString());
    }

    // ========== Nested Structure Tests ==========

    @Test
    void testNestedStructureRoundTrip() {
        initializeLatestProtocol();
        var nested = Document.of(Map.of(
                "innerString",
                Document.of("test"),
                "innerNumber",
                Document.of(123)));
        var echo = echoSingleField("nested", nested);
        var returnedNested = echo.getMember("nested");
        assertEquals("test", returnedNested.getMember("innerString").asString());
        assertEquals(123, returnedNested.getMember("innerNumber").asNumber().intValue());
    }

    @Test
    void testRecursiveStructureRoundTrip() {
        initializeLatestProtocol();
        var innerNested = Document.of(Map.of(
                "innerString",
                Document.of("innerLevel"),
                "innerNumber",
                Document.of(2)));
        var nested = Document.of(Map.of(
                "innerString",
                Document.of("outerLevel"),
                "innerNumber",
                Document.of(1),
                "recursive",
                innerNested));
        var echo = echoSingleField("nested", nested);
        var returnedNested = echo.getMember("nested");
        assertEquals("outerLevel", returnedNested.getMember("innerString").asString());
        assertEquals(1, returnedNested.getMember("innerNumber").asNumber().intValue());
        var recursive = returnedNested.getMember("recursive");
        assertEquals("innerLevel", recursive.getMember("innerString").asString());
        assertEquals(2, recursive.getMember("innerNumber").asNumber().intValue());
    }

    // ========== Document Tests ==========

    @Test
    void testDocumentWithObject() {
        initializeLatestProtocol();
        var doc = Document.of(Map.of(
                "arbitraryKey",
                Document.of("arbitraryValue"),
                "nestedDoc",
                Document.of(Map.of("deep", Document.of(123)))));
        var echoInput = createEchoInput(Map.of("documentValue", doc));
        var response = callTool("McpEcho", echoInput);
        assertNull(response.getError(),
                "Expected no error but got: " + (response.getError() != null ? response.getError().getMessage() : ""));
        assertNotNull(response.getResult());
        var echo = getEchoFromResponse(response);
        var returnedDoc = echo.getMember("documentValue");
        assertEquals("arbitraryValue", returnedDoc.getMember("arbitraryKey").asString());
        assertEquals(123, returnedDoc.getMember("nestedDoc").getMember("deep").asNumber().intValue());
    }

    @Test
    void testDocumentWithArray() {
        initializeLatestProtocol();
        var doc = Document.of(List.of(Document.of("a"), Document.of("b"), Document.of("c")));
        var echo = echoSingleField("documentValue", doc);
        var returnedDoc = echo.getMember("documentValue").asList();
        assertEquals(3, returnedDoc.size());
        assertEquals("a", returnedDoc.getFirst().asString());
    }

    @Test
    void testDocumentWithPrimitive() {
        initializeLatestProtocol();
        var echo = echoSingleField("documentValue", Document.of("just a string"));
        assertEquals("just a string", echo.getMember("documentValue").asString());
    }

    static Stream<Arguments> documentSchemaValidationTestCases() {
        var listOfNulls = new ArrayList<Document>();
        listOfNulls.add(null);
        listOfNulls.add(null);
        listOfNulls.add(Document.of("non-null"));

        var mapOfNulls = new HashMap<String, Document>();
        mapOfNulls.put("first", null);
        mapOfNulls.put("second", null);
        mapOfNulls.put("third", Document.of("non-null"));

        // All nulls array
        var allNullsArray = new ArrayList<Document>();
        allNullsArray.add(null);
        allNullsArray.add(null);
        allNullsArray.add(null);

        // All nulls map
        var allNullsMap = new HashMap<String, Document>();
        allNullsMap.put("a", null);
        allNullsMap.put("b", null);
        allNullsMap.put("c", null);

        // Nested structure with nulls
        var innerMapWithNull = new HashMap<String, Document>();
        innerMapWithNull.put("present", Document.of("value"));
        innerMapWithNull.put("absent", null);

        var listWithNestedNull = new ArrayList<Document>();
        listWithNestedNull.add(Document.of(innerMapWithNull));
        listWithNestedNull.add(null);

        var nestedWithNulls = new HashMap<String, Document>();
        nestedWithNulls.put("map", Document.of(innerMapWithNull));
        nestedWithNulls.put("list", Document.of(listWithNestedNull));
        nestedWithNulls.put("nullKey", null);

        return Stream.of(
                // String document
                Arguments.of("string document", Document.of("hello world")),
                // Number document (integer)
                Arguments.of("integer document", Document.of(42)),
                // Number document (double)
                Arguments.of("double document", Document.of(3.14159)),
                // Boolean document (true)
                Arguments.of("boolean true document", Document.of(true)),
                // Boolean document (false)
                Arguments.of("boolean false document", Document.of(false)),
                // Array document
                Arguments.of("array document",
                        Document.of(List.of(
                                Document.of("a"),
                                Document.of(1),
                                Document.of(true)))),
                // Object document
                Arguments.of("object document",
                        Document.of(Map.of(
                                "key",
                                Document.of("value"),
                                "number",
                                Document.of(123)))),
                // Nested object document
                Arguments.of("nested object document",
                        Document.of(Map.of(
                                "outer",
                                Document.of(Map.of(
                                        "inner",
                                        Document.of("deep value")))))),
                // Mixed array document
                Arguments.of("mixed array document",
                        Document.of(List.of(
                                Document.of("string"),
                                Document.of(42),
                                Document.of(true),
                                Document.of(Map.of("nested", Document.of("value"))),
                                Document.of(List.of(Document.of(1), Document.of(2)))))),
                Arguments.of("empty object document", Document.of(Map.of())),
                Arguments.of("empty array document", Document.of(List.of())),
                Arguments.of("array of nulls document", Document.of(listOfNulls)),
                Arguments.of("map of nulls document", Document.of(mapOfNulls)),
                Arguments.of("all nulls array document", Document.of(allNullsArray)),
                Arguments.of("all nulls map document", Document.of(allNullsMap)),
                Arguments.of("nested nulls document", Document.of(nestedWithNulls)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentSchemaValidationTestCases")
    void testDocumentValidatesAgainstSchema(String description, Document documentValue) {
        initializeLatestProtocol();

        // Get output schema from raw JSON (using Jackson directly)
        write("tools/list", Document.of(Map.of()));
        var toolsResponseJson = readRawResponse();
        var toolsResponseNode = OBJECT_MAPPER.readTree(toolsResponseJson);
        var outputSchemaNode = toolsResponseNode
                .path("result")
                .path("tools")
                .get(0)
                .path("outputSchema");

        // Create input with the document value
        var echoData = new HashMap<String, Document>();
        echoData.put("requiredField", Document.of("required"));
        echoData.put("documentValue", documentValue);

        // Call tool and get raw JSON response (using Jackson directly)
        var params = Document.of(Map.of(
                "name",
                Document.of("McpEcho"),
                "arguments",
                createEchoInput(echoData)));
        write("tools/call", params);
        var callResponseJson = readRawResponse();
        var callResponseNode = OBJECT_MAPPER.readTree(callResponseJson);
        var structuredContentNode = callResponseNode.path("result").path("structuredContent");

        assertFalse(structuredContentNode.isMissingNode(),
                "Structured content should not be missing for: " + description);

        // Validate using Jackson-parsed JSON directly
        Schema schema = SCHEMA_FACTORY.getSchema(outputSchemaNode);
        List<Error> errors = schema.validate(structuredContentNode);
        assertTrue(errors.isEmpty(),
                "Validation errors for " + description + ": " + errors);
    }

    // ========== Enum Tests ==========

    @Test
    void testEnumRoundTrip() {
        initializeLatestProtocol();
        var echo = echoSingleField("enumValue", Document.of("VALUE_ONE"));
        assertEquals("VALUE_ONE", echo.getMember("enumValue").asString());
    }

    @Test
    void testEnumSchemaValidatesInput() {
        initializeLatestProtocol();
        var schemas = getMcpEchoToolSchemas();

        // Valid enum value should pass validation
        var validInput = OBJECT_MAPPER.readTree("""
                {
                    "echo": {
                        "requiredField": "test",
                        "enumValue": "VALUE_ONE"
                    }
                }
                """);
        var validErrors = schemas.inputSchema().validate(validInput);
        assertTrue(validErrors.isEmpty(), "Valid enum value should pass: " + validErrors);

        // Invalid enum value should fail validation
        var invalidInput = OBJECT_MAPPER.readTree("""
                {
                    "echo": {
                        "requiredField": "test",
                        "enumValue": "INVALID_VALUE"
                    }
                }
                """);
        var invalidErrors = schemas.inputSchema().validate(invalidInput);
        assertFalse(invalidErrors.isEmpty(), "Invalid enum value should fail validation");
    }

    // ========== IntEnum Tests ==========

    @Test
    void testIntEnumRoundTrip() {
        initializeLatestProtocol();
        var echo = echoSingleField("intEnumValue", Document.of(2));
        assertEquals(2, echo.getMember("intEnumValue").asNumber().intValue());
    }

    @Test
    void testIntEnumSchemaValidatesInput() {
        initializeLatestProtocol();
        var schemas = getMcpEchoToolSchemas();

        // Valid int enum value should pass validation
        var validInput = OBJECT_MAPPER.readTree("""
                {
                    "echo": {
                        "requiredField": "test",
                        "intEnumValue": 1
                    }
                }
                """);
        var validErrors = schemas.inputSchema().validate(validInput);
        assertTrue(validErrors.isEmpty(), "Valid int enum value should pass: " + validErrors);

        // Invalid int enum value should fail validation
        var invalidInput = OBJECT_MAPPER.readTree("""
                {
                    "echo": {
                        "requiredField": "test",
                        "intEnumValue": 999
                    }
                }
                """);
        var invalidErrors = schemas.inputSchema().validate(invalidInput);
        assertFalse(invalidErrors.isEmpty(), "Invalid int enum value should fail validation");
    }

    // ========== String with Enum Trait Tests ==========

    @Test
    void testStringWithEnumTraitRoundTrip() {
        initializeLatestProtocol();
        var echo = echoSingleField("stringEnumValue", Document.of("OPTION_A"));
        assertEquals("OPTION_A", echo.getMember("stringEnumValue").asString());
    }

    @Test
    void testStringWithEnumTraitSchemaValidatesInput() {
        initializeLatestProtocol();
        var schemas = getMcpEchoToolSchemas();

        // Valid string enum value should pass validation
        var validInput = OBJECT_MAPPER.readTree("""
                {
                    "echo": {
                        "requiredField": "test",
                        "stringEnumValue": "OPTION_A"
                    }
                }
                """);
        var validErrors = schemas.inputSchema().validate(validInput);
        assertTrue(validErrors.isEmpty(), "Valid string enum value should pass: " + validErrors);

        // Invalid string enum value should fail validation
        var invalidInput = OBJECT_MAPPER.readTree("""
                {
                    "echo": {
                        "requiredField": "test",
                        "stringEnumValue": "INVALID_VALUE"
                    }
                }
                """);
        var invalidErrors = schemas.inputSchema().validate(invalidInput);
        assertFalse(invalidErrors.isEmpty(), "Invalid string enum value should fail validation");
    }

    // ========== Union Tests ==========

    @Test
    void testUnionWithStringOption() {
        initializeLatestProtocol();
        var union = Document.of(Map.of("stringOption", Document.of("union string value")));
        var echo = echoSingleField("unionValue", union);
        assertEquals("union string value", echo.getMember("unionValue").getMember("stringOption").asString());
    }

    @Test
    void testUnionWithIntegerOption() {
        initializeLatestProtocol();
        var union = Document.of(Map.of("integerOption", Document.of(42)));
        var echo = echoSingleField("unionValue", union);
        assertEquals(42, echo.getMember("unionValue").getMember("integerOption").asNumber().intValue());
    }

    @Test
    void testUnionWithNestedOption() {
        initializeLatestProtocol();
        var nestedVal = Document.of(Map.of(
                "innerString",
                Document.of("unionNested"),
                "innerNumber",
                Document.of(77)));
        var union = Document.of(Map.of("nestedOption", nestedVal));
        var echo = echoSingleField("unionValue", union);
        var nested = echo.getMember("unionValue").getMember("nestedOption");
        assertEquals("unionNested", nested.getMember("innerString").asString());
        assertEquals(77, nested.getMember("innerNumber").asNumber().intValue());
    }

    // ========== Input Deserialization Verification Tests ==========

    @Test
    void testInputFieldsAreCorrectlyDeserialized() {
        initializeLatestProtocol();
        var base64Blob = Base64.getEncoder().encodeToString("test data".getBytes(StandardCharsets.UTF_8));
        var echoData = new HashMap<String, Document>();
        echoData.put("requiredField", Document.of("required-value"));
        echoData.put("stringValue", Document.of("test-string"));
        echoData.put("booleanValue", Document.of(true));
        echoData.put("byteValue", Document.of(42));
        echoData.put("shortValue", Document.of(1000));
        echoData.put("integerValue", Document.of(100000));
        echoData.put("longValue", Document.of(9999999999L));
        echoData.put("floatValue", Document.of(3.14f));
        echoData.put("doubleValue", Document.of(2.718281828));
        echoData.put("bigDecimalValue", Document.of("123.456"));
        echoData.put("bigIntegerValue", Document.of("123456789012345678901234567890"));
        echoData.put("blobValue", Document.of(base64Blob));
        echoData.put("epochSecondsTimestamp", Document.of(1700000000.0));
        echoData.put("dateTimeTimestamp", Document.of("2023-11-14T22:13:20Z"));
        echoData.put("httpDateTimestamp", Document.of("Tue, 14 Nov 2023 22:13:20 GMT"));
        echoData.put("defaultTimestamp", Document.of("2023-11-14T22:13:20Z"));
        echoData.put("stringList", Document.of(List.of(Document.of("a"), Document.of("b"))));
        echoData.put("integerList", Document.of(List.of(Document.of(1), Document.of(2))));
        echoData.put("stringMap", Document.of(Map.of("key1", Document.of("value1"))));
        echoData.put("enumValue", Document.of("VALUE_ONE"));
        echoData.put("intEnumValue", Document.of(2));

        callTool("McpEcho", createEchoInput(echoData));

        // Verify the actual Echo object received by the operation
        Echo echo = echoOperation.getLastInput().getEcho();
        assertNotNull(echo, "Echo should not be null");

        // Verify primitives
        assertEquals("required-value", echo.getRequiredField());
        assertEquals("test-string", echo.getStringValue());
        assertTrue(echo.isBooleanValue());
        assertEquals((byte) 42, echo.getByteValue().byteValue());
        assertEquals((short) 1000, echo.getShortValue().shortValue());
        assertEquals(100000, echo.getIntegerValue().intValue());
        assertEquals(9999999999L, echo.getLongValue().longValue());
        assertEquals(3.14f, echo.getFloatValue(), 0.001f);
        assertEquals(2.718281828, echo.getDoubleValue(), 0.0000001);

        // Verify big numbers
        assertEquals(new BigDecimal("123.456"), echo.getBigDecimalValue());
        assertEquals(new BigInteger("123456789012345678901234567890"), echo.getBigIntegerValue());

        // Verify blob
        var blobBuffer = echo.getBlobValue().rewind(); //TODO Investigate why this needs to re-winded.
        assertNotNull(blobBuffer);
        var blobBytes = ByteBufferUtils.getBytes(blobBuffer);
        assertEquals("test data", new String(blobBytes, StandardCharsets.UTF_8));

        // Verify timestamps
        assertNotNull(echo.getEpochSecondsTimestamp());
        assertNotNull(echo.getDateTimeTimestamp());
        assertNotNull(echo.getHttpDateTimestamp());
        assertNotNull(echo.getDefaultTimestamp());

        // Verify collections
        assertEquals(2, echo.getStringList().size());
        assertEquals("a", echo.getStringList().getFirst());
        assertEquals(2, echo.getIntegerList().size());
        assertEquals(1, echo.getIntegerList().getFirst().intValue());

        // Verify map
        assertEquals("value1", echo.getStringMap().get("key1"));

        // Verify enum
        assertNotNull(echo.getEnumValue());

        // Verify intEnum
        assertNotNull(echo.getIntEnumValue());
    }

    // ========== JSON Schema Validation Tests ==========

    @Test
    void testStructuredContentValidatesAgainstOutputSchema() {
        initializeLatestProtocol();

        // Get output schema from raw JSON (using Jackson directly, not Smithy serializers)
        write("tools/list", Document.of(Map.of()));
        var toolsResponseJson = readRawResponse();
        var toolsResponseNode = OBJECT_MAPPER.readTree(toolsResponseJson);
        var outputSchemaNode = toolsResponseNode
                .path("result")
                .path("tools")
                .get(0)
                .path("outputSchema");

        // Create comprehensive input
        var base64Blob = Base64.getEncoder().encodeToString("test".getBytes(StandardCharsets.UTF_8));
        var echoData = new HashMap<String, Document>();
        echoData.put("requiredField", Document.of("required"));
        echoData.put("stringValue", Document.of("test"));
        echoData.put("booleanValue", Document.of(true));
        echoData.put("integerValue", Document.of(42));
        echoData.put("bigDecimalValue", Document.of("123.456"));
        echoData.put("bigIntegerValue", Document.of("123456789"));
        echoData.put("blobValue", Document.of(base64Blob));
        echoData.put("epochSecondsTimestamp", Document.of(1700000000.0));
        echoData.put("stringList", Document.of(List.of(Document.of("a"), Document.of("b"))));
        echoData.put("stringMap", Document.of(Map.of("key", Document.of("value"))));
        echoData.put("enumValue", Document.of("VALUE_ONE"));
        echoData.put("intEnumValue", Document.of(1));

        // Call tool and get raw JSON response (using Jackson directly)
        var params = Document.of(Map.of(
                "name",
                Document.of("McpEcho"),
                "arguments",
                createEchoInput(echoData)));
        write("tools/call", params);
        var callResponseJson = readRawResponse();
        var callResponseNode = OBJECT_MAPPER.readTree(callResponseJson);
        var structuredContentNode = callResponseNode.path("result").path("structuredContent");

        assertNotNull(structuredContentNode);

        // Validate using Jackson-parsed JSON directly
        Schema schema = SCHEMA_FACTORY.getSchema(outputSchemaNode);
        List<Error> errors = schema.validate(structuredContentNode);
        assertTrue(errors.isEmpty(), "Validation errors: " + errors);
    }

    @Test
    void testAllTypesValidateAgainstOutputSchema() {
        initializeLatestProtocol();

        // Get output schema from raw JSON (using Jackson directly)
        write("tools/list", Document.of(Map.of()));
        var toolsResponseJson = readRawResponse();
        var toolsResponseNode = OBJECT_MAPPER.readTree(toolsResponseJson);
        var outputSchemaNode = toolsResponseNode
                .path("result")
                .path("tools")
                .get(0)
                .path("outputSchema");

        // Create input with all types
        var base64Blob = Base64.getEncoder().encodeToString("binary data".getBytes(StandardCharsets.UTF_8));
        var nested = Document.of(Map.of(
                "innerString",
                Document.of("nested string"),
                "innerNumber",
                Document.of(99)));
        var union = Document.of(Map.of("stringOption", Document.of("union value")));
        var doc = Document.of(Map.of("arbitrary", Document.of("data")));

        var echoData = new HashMap<String, Document>();
        echoData.put("requiredField", Document.of("required"));
        echoData.put("stringValue", Document.of("string"));
        echoData.put("booleanValue", Document.of(false));
        echoData.put("byteValue", Document.of(8));
        echoData.put("shortValue", Document.of(16));
        echoData.put("integerValue", Document.of(32));
        echoData.put("longValue", Document.of(64L));
        echoData.put("floatValue", Document.of(1.5f));
        echoData.put("doubleValue", Document.of(2.5));
        echoData.put("bigDecimalValue", Document.of("999.999"));
        echoData.put("bigIntegerValue", Document.of("999999999999999999"));
        echoData.put("blobValue", Document.of(base64Blob));
        echoData.put("epochSecondsTimestamp", Document.of(1700000000.0));
        echoData.put("dateTimeTimestamp", Document.of("2023-11-14T22:13:20Z"));
        echoData.put("httpDateTimestamp", Document.of("Tue, 14 Nov 2023 22:13:20 GMT"));
        echoData.put("defaultTimestamp", Document.of("2023-11-14T22:13:20Z"));
        echoData.put("stringList", Document.of(List.of(Document.of("a"))));
        echoData.put("integerList", Document.of(List.of(Document.of(1))));
        echoData.put("nestedList", Document.of(List.of(nested)));
        echoData.put("stringMap", Document.of(Map.of("k", Document.of("v"))));
        echoData.put("nestedMap", Document.of(Map.of("nk", nested)));
        echoData.put("nested", nested);
        echoData.put("documentValue", doc);
        echoData.put("enumValue", Document.of("VALUE_TWO"));
        echoData.put("intEnumValue", Document.of(3));
        echoData.put("unionValue", union);

        // Call tool and get raw JSON response (using Jackson directly)
        var params = Document.of(Map.of(
                "name",
                Document.of("McpEcho"),
                "arguments",
                createEchoInput(echoData)));
        write("tools/call", params);
        var callResponseJson = readRawResponse();
        var callResponseNode = OBJECT_MAPPER.readTree(callResponseJson);
        var structuredContentNode = callResponseNode.path("result").path("structuredContent");

        assertFalse(structuredContentNode.isMissingNode(), "Missing structured content");

        // Validate using Jackson-parsed JSON directly
        Schema schema = SCHEMA_FACTORY.getSchema(outputSchemaNode);
        List<Error> errors = schema.validate(structuredContentNode);
        assertTrue(errors.isEmpty(), "Validation errors: " + errors);
    }

    // ========== Error Case Tests ==========

    @Test
    void testUnknownTool() {
        initializeLatestProtocol();
        var params = Document.of(Map.of(
                "name",
                Document.of("NonExistentTool"),
                "arguments",
                Document.of(Map.of())));
        write("tools/call", params);
        var response = read();
        assertNotNull(response.getError());
        assertTrue(response.getError().getMessage().contains("No such tool"));
    }

    @Test
    void testNoStructuredContentWithOlderProtocol() {
        initializeWithProtocolVersion(ProtocolVersion.v2025_03_26.INSTANCE);
        var response = callTool("McpEcho", createEchoInput(Map.of("stringValue", Document.of("test"))));
        // With older protocol, structuredContent should not be present
        assertNull(response.getResult().getMember("structuredContent"));
        // But content should still be present
        assertNotNull(response.getResult().getMember("content"));
    }

    // ========== Null Handling Tests ==========

    @Test
    void testNestedDocumentWithNulls() {
        initializeLatestProtocol();
        // Create nested structure with nulls
        var innerMap = new HashMap<String, Document>();
        innerMap.put("present", Document.of("value"));
        innerMap.put("absent", null);

        var listWithNulls = new ArrayList<Document>();
        listWithNulls.add(Document.of(innerMap));
        listWithNulls.add(null);

        var outerMap = new HashMap<String, Document>();
        outerMap.put("nested", Document.of(innerMap));
        outerMap.put("list", Document.of(listWithNulls));
        var inputDoc = Document.of(outerMap);

        var echo = echoSingleField("documentValue", inputDoc);
        var returnedDoc = echo.getMember("documentValue");
        assertNotNull(returnedDoc, "documentValue should not be null");

        // Verify nested map
        var nestedMap = returnedDoc.getMember("nested").asStringMap();
        assertEquals("value", nestedMap.get("present").asString());
        assertNull(nestedMap.get("absent"), "absent key should have null value");

        // Verify list with nulls
        var returnedList = returnedDoc.getMember("list").asList();
        assertEquals(2, returnedList.size());
        assertNotNull(returnedList.get(0), "First list element should not be null");
        assertNull(returnedList.get(1), "Second list element should be null");

        // Verify input matches output
        assertTrue(Document.equals(inputDoc, returnedDoc), "Input should match output");
        // Verify server received correct input
        var lastInput = echoOperation.getLastInput().getEcho().getDocumentValue();
        assertTrue(Document.equals(inputDoc, lastInput), "Input should match server received input");
    }

    @Test
    void testAllNullsContainers() {
        initializeLatestProtocol();

        // Test all-nulls array
        var allNullsArray = new ArrayList<Document>();
        allNullsArray.add(null);
        allNullsArray.add(null);
        allNullsArray.add(null);
        var inputArrayDoc = Document.of(allNullsArray);

        var echoArray = echoSingleField("documentValue", inputArrayDoc);
        var returnedList = echoArray.getMember("documentValue").asList();
        assertEquals(3, returnedList.size());
        assertNull(returnedList.get(0), "Array element 0 should be null");
        assertNull(returnedList.get(1), "Array element 1 should be null");
        assertNull(returnedList.get(2), "Array element 2 should be null");
        // Verify input matches output
        assertTrue(Document.equals(inputArrayDoc, echoArray.getMember("documentValue")),
                "Array input should match output");
        // Verify server received correct input
        var lastInputArray = echoOperation.getLastInput().getEcho().getDocumentValue();
        assertTrue(Document.equals(inputArrayDoc, lastInputArray), "Array input should match server received input");

        // Test all-nulls map
        var allNullsMap = new HashMap<String, Document>();
        allNullsMap.put("a", null);
        allNullsMap.put("b", null);
        allNullsMap.put("c", null);
        var inputMapDoc = Document.of(allNullsMap);

        var echoMap = echoSingleField("documentValue", inputMapDoc);
        var returnedMap = echoMap.getMember("documentValue").asStringMap();
        assertEquals(3, returnedMap.size());
        assertNull(returnedMap.get("a"), "Map key 'a' should have null value");
        assertNull(returnedMap.get("b"), "Map key 'b' should have null value");
        assertNull(returnedMap.get("c"), "Map key 'c' should have null value");
        // Verify input matches output
        assertTrue(Document.equals(inputMapDoc, echoMap.getMember("documentValue")), "Map input should match output");
        // Verify server received correct input
        var lastInputMap = echoOperation.getLastInput().getEcho().getDocumentValue();
        assertTrue(Document.equals(inputMapDoc, lastInputMap), "Map input should match server received input");
    }

    @Test
    void testNullsPreservePositionInArray() {
        initializeLatestProtocol();
        var list = new ArrayList<Document>();
        list.add(null); // position 0
        list.add(Document.of("middle")); // position 1
        list.add(null); // position 2
        list.add(Document.of("end")); // position 3
        var inputDoc = Document.of(list);

        var echo = echoSingleField("documentValue", inputDoc);
        var returnedList = echo.getMember("documentValue").asList();
        assertEquals(4, returnedList.size());
        assertNull(returnedList.get(0), "Position 0 should be null");
        assertEquals("middle", returnedList.get(1).asString());
        assertNull(returnedList.get(2), "Position 2 should be null");
        assertEquals("end", returnedList.get(3).asString());
        // Verify input matches output
        assertTrue(Document.equals(inputDoc, echo.getMember("documentValue")), "Input should match output");
        // Verify server received correct input
        var lastInput = echoOperation.getLastInput().getEcho().getDocumentValue();
        assertTrue(Document.equals(inputDoc, lastInput), "Input should match server received input");
    }

    @Test
    void testNullsInRawJson() {
        initializeLatestProtocol();

        // Test map with nulls in raw JSON
        var mapWithNulls = new HashMap<String, Document>();
        mapWithNulls.put("nullKey", null);
        mapWithNulls.put("present", Document.of("value"));
        var inputMapDoc = Document.of(mapWithNulls);

        var echoDataMap = new HashMap<String, Document>();
        echoDataMap.put("requiredField", Document.of("required"));
        echoDataMap.put("documentValue", inputMapDoc);

        var paramsMap = Document.of(Map.of(
                "name",
                Document.of("McpEcho"),
                "arguments",
                createEchoInput(echoDataMap)));
        write("tools/call", paramsMap);
        var mapResponseJson = readRawResponse();

        var mapResponseNode = OBJECT_MAPPER.readTree(mapResponseJson);
        var mapDocNode = mapResponseNode.path("result").path("structuredContent").path("echo").path("documentValue");
        assertTrue(mapDocNode.has("nullKey"), "JSON should contain nullKey");
        assertTrue(mapDocNode.get("nullKey").isNull(), "nullKey should be JSON null");
        assertEquals("value", mapDocNode.get("present").asString());

        // Test array with nulls in raw JSON
        var listWithNulls = new ArrayList<Document>();
        listWithNulls.add(null);
        listWithNulls.add(Document.of("value"));
        listWithNulls.add(null);
        var inputArrayDoc = Document.of(listWithNulls);

        var echoDataArray = new HashMap<String, Document>();
        echoDataArray.put("requiredField", Document.of("required"));
        echoDataArray.put("documentValue", inputArrayDoc);

        var paramsArray = Document.of(Map.of(
                "name",
                Document.of("McpEcho"),
                "arguments",
                createEchoInput(echoDataArray)));
        write("tools/call", paramsArray);
        var arrayResponseJson = readRawResponse();

        var arrayResponseNode = OBJECT_MAPPER.readTree(arrayResponseJson);
        var arrayDocNode =
                arrayResponseNode.path("result").path("structuredContent").path("echo").path("documentValue");
        assertTrue(arrayDocNode.isArray(), "documentValue should be an array");
        assertEquals(3, arrayDocNode.size());
        assertTrue(arrayDocNode.get(0).isNull(), "Array element 0 should be JSON null");
        assertEquals("value", arrayDocNode.get(1).asString());
        assertTrue(arrayDocNode.get(2).isNull(), "Array element 2 should be JSON null");
    }

    @Test
    void testNullsInTypedLists() {
        initializeLatestProtocol();

        // Test nulls in StringList
        var stringListWithNulls = new ArrayList<Document>();
        stringListWithNulls.add(Document.of("first"));
        stringListWithNulls.add(null);
        stringListWithNulls.add(Document.of("third"));

        var echoDataStringList = new HashMap<String, Document>();
        echoDataStringList.put("requiredField", Document.of("required"));
        echoDataStringList.put("stringList", Document.of(stringListWithNulls));

        var responseStringList = callTool("McpEcho", createEchoInput(echoDataStringList));
        assertNull(responseStringList.getError(),
                "Expected no error but got: "
                        + (responseStringList.getError() != null ? responseStringList.getError().getMessage() : ""));
        var echoStringList = getEchoFromResponse(responseStringList);
        var returnedStringList = echoStringList.getMember("stringList").asList();
        assertEquals(3, returnedStringList.size());
        assertEquals("first", returnedStringList.get(0).asString());
        assertNull(returnedStringList.get(1), "Element 1 should be null");
        assertEquals("third", returnedStringList.get(2).asString());
        // Verify server received correct input
        var lastInputStringList = echoOperation.getLastInput().getEcho().getStringList();
        assertEquals(3, lastInputStringList.size());
        assertEquals("first", lastInputStringList.get(0));
        assertNull(lastInputStringList.get(1), "Server received list element 1 should be null");
        assertEquals("third", lastInputStringList.get(2));

        // Test nulls in IntegerList
        var intListWithNulls = new ArrayList<Document>();
        intListWithNulls.add(Document.of(1));
        intListWithNulls.add(null);
        intListWithNulls.add(Document.of(3));

        var echoDataIntList = new HashMap<String, Document>();
        echoDataIntList.put("requiredField", Document.of("required"));
        echoDataIntList.put("integerList", Document.of(intListWithNulls));

        var responseIntList = callTool("McpEcho", createEchoInput(echoDataIntList));
        assertNull(responseIntList.getError(),
                "Expected no error but got: "
                        + (responseIntList.getError() != null ? responseIntList.getError().getMessage() : ""));
        var echoIntList = getEchoFromResponse(responseIntList);
        var returnedIntList = echoIntList.getMember("integerList").asList();
        assertEquals(3, returnedIntList.size());
        assertEquals(1, returnedIntList.get(0).asInteger());
        assertNull(returnedIntList.get(1), "Element 1 should be null");
        assertEquals(3, returnedIntList.get(2).asInteger());
        // Verify server received correct input
        var lastInputIntList = echoOperation.getLastInput().getEcho().getIntegerList();
        assertEquals(3, lastInputIntList.size());
        assertEquals(Integer.valueOf(1), lastInputIntList.get(0));
        assertNull(lastInputIntList.get(1), "Server received list element 1 should be null");
        assertEquals(Integer.valueOf(3), lastInputIntList.get(2));
    }

    @Test
    void testNullsInTypedMaps() {
        initializeLatestProtocol();

        // Test nulls in StringMap
        var stringMapWithNulls = new HashMap<String, Document>();
        stringMapWithNulls.put("present", Document.of("value"));
        stringMapWithNulls.put("nullKey", null);
        stringMapWithNulls.put("another", Document.of("anotherValue"));

        var echoDataStringMap = new HashMap<String, Document>();
        echoDataStringMap.put("requiredField", Document.of("required"));
        echoDataStringMap.put("stringMap", Document.of(stringMapWithNulls));

        var responseStringMap = callTool("McpEcho", createEchoInput(echoDataStringMap));
        assertNull(responseStringMap.getError(),
                "Expected no error but got: "
                        + (responseStringMap.getError() != null ? responseStringMap.getError().getMessage() : ""));
        var echoStringMap = getEchoFromResponse(responseStringMap);
        var returnedStringMap = echoStringMap.getMember("stringMap").asStringMap();
        assertEquals(3, returnedStringMap.size());
        assertEquals("value", returnedStringMap.get("present").asString());
        assertNull(returnedStringMap.get("nullKey"), "nullKey should have null value");
        assertEquals("anotherValue", returnedStringMap.get("another").asString());
        // Verify server received correct input
        var lastInputStringMap = echoOperation.getLastInput().getEcho().getStringMap();
        assertEquals(3, lastInputStringMap.size());
        assertEquals("value", lastInputStringMap.get("present"));
        assertNull(lastInputStringMap.get("nullKey"), "Server received map nullKey should be null");
        assertEquals("anotherValue", lastInputStringMap.get("another"));
    }

    @Test
    void testDeeplyNestedNulls() {
        initializeLatestProtocol();
        // Create deeply nested structure with nulls at various levels
        var level3 = new HashMap<String, Document>();
        level3.put("deep", Document.of("value"));
        level3.put("nullDeep", null);

        var level2List = new ArrayList<Document>();
        level2List.add(Document.of(level3));
        level2List.add(null);

        var level2Map = new HashMap<String, Document>();
        level2Map.put("list", Document.of(level2List));
        level2Map.put("nullKey", null);

        var level1 = new HashMap<String, Document>();
        level1.put("level2", Document.of(level2Map));
        level1.put("nullLevel", null);
        var inputDoc = Document.of(level1);

        var echo = echoSingleField("documentValue", inputDoc);
        var returnedDoc = echo.getMember("documentValue");

        // Verify level 1
        assertNull(returnedDoc.getMember("nullLevel"), "nullLevel should be null");
        var level2 = returnedDoc.getMember("level2");
        assertNotNull(level2, "level2 should exist");

        // Verify level 2
        assertNull(level2.getMember("nullKey"), "nullKey at level 2 should be null");
        var level2ListReturned = level2.getMember("list").asList();
        assertEquals(2, level2ListReturned.size());
        assertNull(level2ListReturned.get(1), "Second element in list should be null");

        // Verify level 3
        var level3Returned = level2ListReturned.get(0);
        assertEquals("value", level3Returned.getMember("deep").asString());
        assertNull(level3Returned.getMember("nullDeep"), "nullDeep should be null");

        // Verify server received correct input
        var lastInput = echoOperation.getLastInput().getEcho().getDocumentValue();
        assertTrue(Document.equals(inputDoc, lastInput), "Input should match server received input");
    }

    // ========== Explicit Null Input Tests ==========

    @Test
    void testExplicitNullInputsForAllFieldTypes() {
        initializeLatestProtocol();
        // Send explicit null for ALL optional field types in a single request
        var echoData = new HashMap<String, Document>();
        echoData.put("requiredField", Document.of("required"));
        // Primitives
        echoData.put("stringValue", null);
        echoData.put("booleanValue", null);
        echoData.put("byteValue", null);
        echoData.put("shortValue", null);
        echoData.put("integerValue", null);
        echoData.put("longValue", null);
        echoData.put("floatValue", null);
        echoData.put("doubleValue", null);
        // Big numbers
        echoData.put("bigDecimalValue", null);
        echoData.put("bigIntegerValue", null);
        // Blob
        echoData.put("blobValue", null);
        // Timestamps
        echoData.put("epochSecondsTimestamp", null);
        echoData.put("dateTimeTimestamp", null);
        echoData.put("httpDateTimestamp", null);
        echoData.put("defaultTimestamp", null);
        // Collections
        echoData.put("stringList", null);
        echoData.put("integerList", null);
        echoData.put("nestedList", null);
        echoData.put("stringMap", null);
        echoData.put("nestedMap", null);
        // Nested structure
        echoData.put("nested", null);
        // Document
        echoData.put("documentValue", null);
        // Enum and IntEnum
        echoData.put("enumValue", null);
        echoData.put("intEnumValue", null);
        // Union
        echoData.put("unionValue", null);

        var response = callTool("McpEcho", createEchoInput(echoData));
        assertNull(response.getError(),
                "Expected no error but got: " + (response.getError() != null ? response.getError().getMessage() : ""));

        var echo = getEchoFromResponse(response);
        // Verify all fields are null
        assertNull(echo.getMember("stringValue"));
        assertNull(echo.getMember("booleanValue"));
        assertNull(echo.getMember("byteValue"));
        assertNull(echo.getMember("shortValue"));
        assertNull(echo.getMember("integerValue"));
        assertNull(echo.getMember("longValue"));
        assertNull(echo.getMember("floatValue"));
        assertNull(echo.getMember("doubleValue"));
        assertNull(echo.getMember("bigDecimalValue"));
        assertNull(echo.getMember("bigIntegerValue"));
        assertNull(echo.getMember("blobValue"));
        assertNull(echo.getMember("epochSecondsTimestamp"));
        assertNull(echo.getMember("dateTimeTimestamp"));
        assertNull(echo.getMember("httpDateTimestamp"));
        assertNull(echo.getMember("defaultTimestamp"));
        assertNull(echo.getMember("stringList"));
        assertNull(echo.getMember("integerList"));
        assertNull(echo.getMember("nestedList"));
        assertNull(echo.getMember("stringMap"));
        assertNull(echo.getMember("nestedMap"));
        assertNull(echo.getMember("nested"));
        assertNull(echo.getMember("documentValue"));
        assertNull(echo.getMember("enumValue"));
        assertNull(echo.getMember("intEnumValue"));
        assertNull(echo.getMember("unionValue"));
    }

    @Test
    void testExplicitNullInputInRawJson() {
        initializeLatestProtocol();
        // Verify that explicitly null inputs appear correctly in JSON
        var echoData = new HashMap<String, Document>();
        echoData.put("requiredField", Document.of("required"));
        echoData.put("stringValue", null);
        echoData.put("integerValue", null);

        var params = Document.of(Map.of(
                "name",
                Document.of("McpEcho"),
                "arguments",
                createEchoInput(echoData)));
        write("tools/call", params);
        var callResponseJson = readRawResponse();

        var responseNode = OBJECT_MAPPER.readTree(callResponseJson);
        var echoNode = responseNode.path("result").path("structuredContent").path("echo");

        // Required field should be present
        assertEquals("required", echoNode.get("requiredField").asString());

        // Explicit null fields should either be null or absent
        if (echoNode.has("stringValue")) {
            assertTrue(echoNode.get("stringValue").isNull(), "stringValue if present should be null");
        }
        if (echoNode.has("integerValue")) {
            assertTrue(echoNode.get("integerValue").isNull(), "integerValue if present should be null");
        }
    }

    // ========== Optional Field Null Tests (Unset Fields) ==========

    @Test
    void testAllOptionalFieldsReturnNullWhenUnset() {
        initializeLatestProtocol();
        // Only set requiredField, verify all optional fields are null
        var response = callTool("McpEcho", createEchoInput(Map.of()));
        assertNull(response.getError(),
                "Expected no error but got: " + (response.getError() != null ? response.getError().getMessage() : ""));

        var echo = getEchoFromResponse(response);
        assertEquals("default-required", echo.getMember("requiredField").asString());

        // Verify all optional fields are null
        assertNull(echo.getMember("stringValue"));
        assertNull(echo.getMember("booleanValue"));
        assertNull(echo.getMember("byteValue"));
        assertNull(echo.getMember("shortValue"));
        assertNull(echo.getMember("integerValue"));
        assertNull(echo.getMember("longValue"));
        assertNull(echo.getMember("floatValue"));
        assertNull(echo.getMember("doubleValue"));
        assertNull(echo.getMember("bigDecimalValue"));
        assertNull(echo.getMember("bigIntegerValue"));
        assertNull(echo.getMember("blobValue"));
        assertNull(echo.getMember("epochSecondsTimestamp"));
        assertNull(echo.getMember("dateTimeTimestamp"));
        assertNull(echo.getMember("httpDateTimestamp"));
        assertNull(echo.getMember("defaultTimestamp"));
        assertNull(echo.getMember("stringList"));
        assertNull(echo.getMember("integerList"));
        assertNull(echo.getMember("nestedList"));
        assertNull(echo.getMember("stringMap"));
        assertNull(echo.getMember("nestedMap"));
        assertNull(echo.getMember("nested"));
        assertNull(echo.getMember("documentValue"));
        assertNull(echo.getMember("enumValue"));
        assertNull(echo.getMember("intEnumValue"));
        assertNull(echo.getMember("unionValue"));
    }

    @Test
    void testOptionalFieldsOmittedFromJson() {
        initializeLatestProtocol();
        // Only set requiredField
        var params = Document.of(Map.of(
                "name",
                Document.of("McpEcho"),
                "arguments",
                createEchoInput(Map.of())));
        write("tools/call", params);
        var callResponseJson = readRawResponse();

        var responseNode = OBJECT_MAPPER.readTree(callResponseJson);
        var echoNode = responseNode.path("result").path("structuredContent").path("echo");

        // requiredField should be present
        assertTrue(echoNode.has("requiredField"), "requiredField should be present");
        assertEquals("default-required", echoNode.get("requiredField").asString());

        // Optional fields should either be absent or null (implementation dependent)
        // Verify they are not present with non-null values
        if (echoNode.has("stringValue")) {
            assertTrue(echoNode.get("stringValue").isNull(), "stringValue if present should be null");
        }
        if (echoNode.has("integerValue")) {
            assertTrue(echoNode.get("integerValue").isNull(), "integerValue if present should be null");
        }
    }

    // ========== OneOf Schema Tests ==========

    @Test
    void testOneOfSchemaStructure() {
        initializeLatestProtocol();
        var schemas = getCalculateAreaToolSchemas();
        var inputSchema = schemas.inputSchema().getSchemaNode();

        // Get oneOfInput property
        var oneOfInputSchema = inputSchema.path("properties").path("oneOfInput");

        // Should have oneOf array, not type
        assertTrue(oneOfInputSchema.has("oneOf"), "oneOfInput should have 'oneOf' property");
        assertEquals("object", oneOfInputSchema.path("type").asString());

        var oneOfArray = oneOfInputSchema.path("oneOf");
        assertEquals(3, oneOfArray.size(), "Should have 3 variants: Circle, Square, Rectangle");

        // Each variant should be a wrapper object with member name as key (no discriminator in schema)
        var memberNames = Set.of("circle", "square", "rectangle");
        var foundMembers = new HashSet<String>();
        for (var variant : oneOfArray) {
            // Each variant should have exactly one property (the member name)
            var properties = variant.path("properties");
            assertEquals(1, properties.size(), "Each variant should have exactly one property (member name)");

            // Get the member name
            var memberName = properties.propertyNames().iterator().next();
            assertTrue(memberNames.contains(memberName),
                    "Member name should be one of: circle, square, rectangle");
            foundMembers.add(memberName);

            // Member name should be in required array
            var requiredArray = variant.path("required");
            boolean hasMemberInRequired = false;
            for (var req : requiredArray) {
                if (req.asString().equals(memberName)) {
                    hasMemberInRequired = true;
                    break;
                }
            }
            assertTrue(hasMemberInRequired, "Member name " + memberName + " should be in required array");

            // additionalProperties should be false
            assertFalse(variant.path("additionalProperties").asBoolean(true),
                    "additionalProperties should be false");
        }

        assertEquals(memberNames, foundMembers, "All member names should be present");
    }

    @Test
    void testOneOfSchemaIsValidJsonSchemaDraft07() {
        initializeLatestProtocol();
        var schemas = getCalculateAreaToolSchemas();

        // Validate that inputSchema conforms to JSON Schema Draft-07
        var inputErrors = JSON_SCHEMA.validate(schemas.toolNode().path("inputSchema"));
        assertTrue(inputErrors.isEmpty(), "Input schema validation errors: " + inputErrors);

        // Validate that outputSchema conforms to JSON Schema Draft-07
        var outputErrors = JSON_SCHEMA.validate(schemas.toolNode().path("outputSchema"));
        assertTrue(outputErrors.isEmpty(), "Output schema validation errors: " + outputErrors);
    }

    @Test
    void testCalculateAreaWithCircle() {
        initializeLatestProtocol();
        // New format: wrapper object with member name as key
        var circle = Document.of(Map.of("circle", Document.of(Map.of("radius", Document.of(5)))));
        var input = Document.of(Map.of("oneOfInput", circle));
        var response = callTool("CalculateArea", input);

        assertNull(response.getError(),
                "Expected no error but got: " + (response.getError() != null ? response.getError().getMessage() : ""));
        var result = response.getResult().getMember("structuredContent");
        assertNotNull(result, "structuredContent should be present");

        double expectedArea = Math.PI * 5 * 5;
        assertEquals(expectedArea, result.getMember("area").asNumber().doubleValue(), 0.001);
    }

    @Test
    void testCalculateAreaWithSquare() {
        initializeLatestProtocol();
        // New format: wrapper object with member name as key
        var square = Document.of(Map.of("square", Document.of(Map.of("side", Document.of(4)))));
        var input = Document.of(Map.of("oneOfInput", square));
        var response = callTool("CalculateArea", input);

        assertNull(response.getError(),
                "Expected no error but got: " + (response.getError() != null ? response.getError().getMessage() : ""));
        var result = response.getResult().getMember("structuredContent");
        assertNotNull(result, "structuredContent should be present");

        double expectedArea = 4 * 4;
        assertEquals(expectedArea, result.getMember("area").asNumber().doubleValue(), 0.001);
    }

    @Test
    void testCalculateAreaWithRectangle() {
        initializeLatestProtocol();
        // New format: wrapper object with member name as key
        var rectangle = Document.of(Map.of("rectangle",
                Document.of(Map.of(
                        "length",
                        Document.of(6),
                        "breadth",
                        Document.of(4)))));
        var input = Document.of(Map.of("oneOfInput", rectangle));
        var response = callTool("CalculateArea", input);

        assertNull(response.getError(),
                "Expected no error but got: " + (response.getError() != null ? response.getError().getMessage() : ""));
        var result = response.getResult().getMember("structuredContent");
        assertNotNull(result, "structuredContent should be present");

        double expectedArea = 6 * 4;
        assertEquals(expectedArea, result.getMember("area").asNumber().doubleValue(), 0.001);
    }

    @Test
    void testOneOfInputValidatesAgainstSchema() {
        initializeLatestProtocol();
        var schemas = getCalculateAreaToolSchemas();

        // Create valid Circle input and validate (wrapper format with member name as key)
        var circleInput = OBJECT_MAPPER.readTree("""
                {
                    "oneOfInput": {
                        "circle": {
                            "radius": 5
                        }
                    }
                }
                """);
        var circleErrors = schemas.inputSchema().validate(circleInput);
        assertTrue(circleErrors.isEmpty(), "Circle input validation errors: " + circleErrors);

        // Create valid Square input and validate
        var squareInput = OBJECT_MAPPER.readTree("""
                {
                    "oneOfInput": {
                        "square": {
                            "side": 4
                        }
                    }
                }
                """);
        var squareErrors = schemas.inputSchema().validate(squareInput);
        assertTrue(squareErrors.isEmpty(), "Square input validation errors: " + squareErrors);

        // Create valid Rectangle input and validate
        var rectangleInput = OBJECT_MAPPER.readTree("""
                {
                    "oneOfInput": {
                        "rectangle": {
                            "length": 6,
                            "breadth": 4
                        }
                    }
                }
                """);
        var rectangleErrors = schemas.inputSchema().validate(rectangleInput);
        assertTrue(rectangleErrors.isEmpty(), "Rectangle input validation errors: " + rectangleErrors);
    }

    @Test
    void testOneOfOutputValidatesAgainstSchema() {
        initializeLatestProtocol();

        // Get output schema
        write("tools/list", Document.of(Map.of()));
        var toolsResponseJson = readRawResponse();
        var toolsNode = OBJECT_MAPPER.readTree(toolsResponseJson).path("result").path("tools");
        JsonNode outputSchemaNode = MissingNode.getInstance();
        for (var tool : toolsNode) {
            if (tool.path("name").asString().equals("CalculateArea")) {
                outputSchemaNode = tool.path("outputSchema");
                break;
            }
        }
        assertFalse(outputSchemaNode.isMissingNode(), "CalculateArea output schema not found");

        // Call tool and get structuredContent (using wrapper format)
        var circle = Document.of(Map.of("circle", Document.of(Map.of("radius", Document.of(5)))));
        var params = Document.of(Map.of(
                "name",
                Document.of("CalculateArea"),
                "arguments",
                Document.of(Map.of("oneOfInput", circle))));
        write("tools/call", params);
        var callResponseJson = readRawResponse();
        var callResponseNode = OBJECT_MAPPER.readTree(callResponseJson);
        var structuredContentNode = callResponseNode.path("result").path("structuredContent");

        assertFalse(structuredContentNode.isMissingNode(), "structuredContent should be present");

        // Validate using output schema
        Schema schema = SCHEMA_FACTORY.getSchema(outputSchemaNode);
        List<Error> errors = schema.validate(structuredContentNode);
        assertTrue(errors.isEmpty(), "Output validation errors: " + errors);
    }

    @Test
    void testUnionMemberNamesInSchema() {
        initializeLatestProtocol();
        var schemas = getCalculateAreaToolSchemas();
        var inputSchema = schemas.inputSchema().getSchemaNode();

        var oneOfArray = inputSchema.path("properties").path("oneOfInput").path("oneOf");
        var memberNames = new HashSet<String>();
        for (var variant : oneOfArray) {
            // Each variant has a single property which is the member name
            var properties = variant.path("properties");
            var memberName = properties.propertyNames().iterator().next();
            memberNames.add(memberName);
        }

        // Verify member names match union members
        assertTrue(memberNames.contains("circle"), "Should contain circle member");
        assertTrue(memberNames.contains("square"), "Should contain square member");
        assertTrue(memberNames.contains("rectangle"), "Should contain rectangle member");
        assertEquals(3, memberNames.size(), "Should have exactly 3 members");
    }

    // ========== Helper Methods ==========

    private void initializeWithProtocolVersion(ProtocolVersion protocolVersion) {
        this.currentProtocolVersion = protocolVersion;
        var params = Document.of(Map.of("protocolVersion", Document.of(protocolVersion.identifier())));
        write("initialize", params);
        var response = read();
        assertEquals(protocolVersion.identifier(), response.getResult().getMember("protocolVersion").asString());
    }

    private Document createEchoInput(Map<String, Document> echoFields) {
        var echoWithRequired = new HashMap<>(echoFields);
        if (!echoWithRequired.containsKey("requiredField")) {
            echoWithRequired.put("requiredField", Document.of("default-required"));
        }
        return Document.of(Map.of("echo", Document.of(echoWithRequired)));
    }

    private void cacheToolSchemas() {
        write("tools/list", Document.of(Map.of()));
        var responseJson = readRawResponse();
        var responseNode = OBJECT_MAPPER.readTree(responseJson);
        var tools = responseNode.path("result").path("tools");
        for (var tool : tools) {
            var name = tool.path("name").asString();
            var outputSchemaNode = tool.path("outputSchema");
            if (!outputSchemaNode.isMissingNode()) {
                outputSchemaCache.put(name, SCHEMA_FACTORY.getSchema(outputSchemaNode));
            }
        }
    }

    private void validateStructuredContent(String toolName, String responseJson) {
        // Only validate for protocol versions that support structured content (v2025_06_18+)
        boolean supportsStructuredContent = currentProtocolVersion != null
                && currentProtocolVersion.compareTo(ProtocolVersion.v2025_06_18.INSTANCE) >= 0;

        if (!supportsStructuredContent) {
            return; // Skip validation for older protocols
        }

        // Cache schemas lazily
        if (outputSchemaCache.isEmpty()) {
            cacheToolSchemas();
        }

        var responseNode = OBJECT_MAPPER.readTree(responseJson);
        var structuredContentNode = responseNode.path("result").path("structuredContent");

        // Assert structured content IS present for compatible protocols
        assertFalse(structuredContentNode.isMissingNode(),
                "structuredContent should be present for protocol version " + currentProtocolVersion.identifier());

        // Validate against schema
        var schema = outputSchemaCache.get(toolName);
        if (schema != null) {
            var errors = schema.validate(structuredContentNode);
            assertTrue(errors.isEmpty(),
                    "Schema validation failed for " + toolName + ": " + errors);
        }
    }

    private JsonRpcResponse callTool(String toolName, Document arguments) {
        var params = Document.of(Map.of(
                "name",
                Document.of(toolName),
                "arguments",
                arguments));
        write("tools/call", params);
        var responseJson = readRawResponse();

        // Validate structured content against schema
        try {
            validateStructuredContent(toolName, responseJson);
        } catch (Exception e) {
            throw new RuntimeException("Schema validation failed", e);
        }

        return CODEC.deserializeShape(responseJson, JsonRpcResponse.builder());
    }

    private Map<String, Document> findTool(List<Document> tools, String name) {
        return tools.stream()
                .filter(t -> t.asStringMap().get("name").asString().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + name))
                .asStringMap();
    }

    private void write(String method, Document params) {
        var request = JsonRpcRequest.builder()
                .id(Document.of(requestId++))
                .method(method)
                .params(params)
                .jsonrpc("2.0")
                .build();
        input.write(CODEC.serializeToString(request));
        input.write("\n");
    }

    private JsonRpcResponse read() {
        var line = assertTimeoutPreemptively(Duration.ofSeconds(5), output::read, "No response within 5 seconds");
        return CODEC.deserializeShape(line, JsonRpcResponse.builder());
    }

    // ========== Operation Implementation ==========

    private static final class McpEchoOperationImpl implements McpEchoOperation {
        private volatile McpEchoInput lastInput;

        @Override
        public McpEchoOutput mcpEcho(McpEchoInput input, RequestContext context) {
            this.lastInput = input;
            return McpEchoOutput.builder().echo(input.getEcho()).build();
        }

        public McpEchoInput getLastInput() {
            return lastInput;
        }
    }

    private static final class CalculateAreaImpl implements CalculateAreaOperation {

        @Override
        public CalculateAreaOutput calculateArea(CalculateAreaInput input, RequestContext context) {
            var shape = Objects.requireNonNull(input.getOneOfInput());
            double area;
            switch (shape) {
                case Shape.CircleMember circleMember -> {
                    var circle = circleMember.circle();
                    area = circle.getRadius() * circle.getRadius() * Math.PI;
                }
                case Shape.SquareMember squareMember -> {
                    var square = squareMember.square();
                    area = square.getSide() * square.getSide();
                }
                case Shape.RectangleMember rectangleMember -> {
                    var rectangle = rectangleMember.rectangle();
                    area = rectangle.getLength() * rectangle.getBreadth();
                }
                default -> throw new IllegalArgumentException("Unknown shape type");
            }
            return CalculateAreaOutput.builder().area(area).originalShape(shape).build();
        }
    }
}
