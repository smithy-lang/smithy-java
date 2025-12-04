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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.test.model.Echo;
import software.amazon.smithy.java.mcp.test.model.McpEchoInput;
import software.amazon.smithy.java.mcp.test.model.McpEchoOutput;
import software.amazon.smithy.java.mcp.test.service.McpEchoOperation;
import software.amazon.smithy.java.mcp.test.service.TestService;
import software.amazon.smithy.java.server.RequestContext;
import software.amazon.smithy.java.server.Server;

class McpServerIntegrationTest {

    private static final JsonCodec CODEC = JsonCodec.builder()
            .settings(JsonSettings.builder()
                    .serializeTypeInDocuments(false)
                    .useJsonName(true)
                    .build())
            .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory SCHEMA_FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    private static final JsonSchema JSON_SCHEMA_DRAFT_07 = SCHEMA_FACTORY.getSchema(
            SchemaLocation.of("https://json-schema.org/draft-07/schema"));

    private Server mcpServer;
    private TestInputStream input;
    private TestOutputStream output;
    private McpEchoOperationImpl echoOperation;
    private int requestId = 0;

    @BeforeEach
    void init() {
        input = new TestInputStream();
        output = new TestOutputStream();
        echoOperation = new McpEchoOperationImpl();
        mcpServer = McpServer.builder()
                .name("test-mcp")
                .addService("test-service",
                        TestService.builder()
                                .addMcpEchoOperation(echoOperation)
                                .build())
                .input(input)
                .output(output)
                .build();
        mcpServer.start();
    }

    @AfterEach
    void teardown() {
        if (mcpServer != null) {
            mcpServer.shutdown().join();
        }
    }

    // ========== Helper Methods ==========

    private void initializeLatestProtocol() {
        initializeWithProtocolVersion(ProtocolVersion.v2025_06_18.INSTANCE);
    }

    private Document getEchoFromResponse(JsonRpcResponse response) {
        return response.getResult().getMember("structuredContent").getMember("echo");
    }

    private Document echoSingleField(String fieldName, Document value) {
        var echoInput = createEchoInput(Map.of(fieldName, value));
        var response = callTool("McpEcho", echoInput);
        return getEchoFromResponse(response);
    }

    private record ToolSchemas(JsonSchema inputSchema, JsonSchema outputSchema, JsonNode toolNode) {}

    private ToolSchemas getMcpEchoToolSchemas() throws Exception {
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
    void testGeneratedSchemasAreValidJsonSchemaDraft07() throws Exception {
        initializeLatestProtocol();
        var schemas = getMcpEchoToolSchemas();

        // Validate that inputSchema conforms to JSON Schema Draft-07
        var inputErrors = JSON_SCHEMA_DRAFT_07.validate(schemas.toolNode().path("inputSchema"));
        assertTrue(inputErrors.isEmpty(), "Input schema validation errors: " + inputErrors);

        // Validate that outputSchema conforms to JSON Schema Draft-07
        var outputErrors = JSON_SCHEMA_DRAFT_07.validate(schemas.toolNode().path("outputSchema"));
        assertTrue(outputErrors.isEmpty(), "Output schema validation errors: " + outputErrors);
    }

    @Test
    void testToolsListSchemaStructure() throws Exception {
        initializeLatestProtocol();
        var schemas = getMcpEchoToolSchemas();
        var toolNode = schemas.toolNode();

        assertEquals("McpEcho", toolNode.path("name").asText());
        assertFalse(toolNode.path("inputSchema").isMissingNode());
        assertFalse(toolNode.path("outputSchema").isMissingNode());

        // Use JsonSchema's getSchemaNode() for type assertions
        var inputSchemaNode = schemas.inputSchema().getSchemaNode();
        assertEquals("object", inputSchemaNode.path("type").asText());
        assertEquals("http://json-schema.org/draft-07/schema#", inputSchemaNode.path("$schema").asText());
        assertTrue(inputSchemaNode.path("properties").has("echo"));

        // Also verify output schema structure
        var outputSchemaNode = schemas.outputSchema().getSchemaNode();
        assertEquals("object", outputSchemaNode.path("type").asText());
        assertEquals("http://json-schema.org/draft-07/schema#", outputSchemaNode.path("$schema").asText());
        assertTrue(outputSchemaNode.path("properties").has("echo"));
    }

    @Test
    void testEchoSchemaTypeMapping() throws Exception {
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
            assertEquals("string", echoProps.path("stringValue").path("type").asText());
            assertEquals("boolean", echoProps.path("booleanValue").path("type").asText());
            assertEquals("number", echoProps.path("byteValue").path("type").asText());
            assertEquals("number", echoProps.path("shortValue").path("type").asText());
            assertEquals("number", echoProps.path("integerValue").path("type").asText());
            assertEquals("number", echoProps.path("longValue").path("type").asText());
            assertEquals("number", echoProps.path("floatValue").path("type").asText());
            assertEquals("number", echoProps.path("doubleValue").path("type").asText());

            // BigDecimal and BigInteger should be strings
            assertEquals("string", echoProps.path("bigDecimalValue").path("type").asText());
            assertEquals("string", echoProps.path("bigIntegerValue").path("type").asText());

            // Blob should be string (base64)
            assertEquals("string", echoProps.path("blobValue").path("type").asText());

            // Timestamps - epoch-seconds is number, date-time and http-date are strings
            assertEquals("number", echoProps.path("epochSecondsTimestamp").path("type").asText());
            assertEquals("string", echoProps.path("dateTimeTimestamp").path("type").asText());
            assertEquals("string", echoProps.path("httpDateTimestamp").path("type").asText());

            // Enum should be string
            assertEquals("string", echoProps.path("enumValue").path("type").asText());

            // IntEnum should be number
            assertEquals("number", echoProps.path("intEnumValue").path("type").asText());

            // Lists should be arrays
            assertEquals("array", echoProps.path("stringList").path("type").asText());
            assertEquals("array", echoProps.path("integerList").path("type").asText());
            assertEquals("array", echoProps.path("nestedList").path("type").asText());

            // Nested structure, map, union, and document should be objects
            assertEquals("object", echoProps.path("nested").path("type").asText());
            assertEquals("object", echoProps.path("stringMap").path("type").asText());
            assertEquals("object", echoProps.path("nestedMap").path("type").asText());
            assertEquals("object", echoProps.path("unionValue").path("type").asText());
            assertEquals("object", echoProps.path("documentValue").path("type").asText());
        }
    }

    @Test
    void testRequiredFieldInSchema() throws Exception {
        initializeLatestProtocol();
        var schemas = getMcpEchoToolSchemas();

        // Verify required field in both input and output schemas
        for (var schema : List.of(schemas.inputSchema(), schemas.outputSchema())) {
            var echoSchemaNode = schema.getSchemaNode().path("properties").path("echo");
            var required = echoSchemaNode.path("required");

            assertTrue(required.isArray(), "required should be an array");
            var requiredFields = new HashSet<String>();
            required.forEach(node -> requiredFields.add(node.asText()));
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
        var epochSeconds = 1700000000.0;
        var echo = echoSingleField("epochSecondsTimestamp", Document.of(epochSeconds));
        assertEquals(epochSeconds, echo.getMember("epochSecondsTimestamp").asNumber().doubleValue(), 0.001);
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
        var httpDateStr = "Tue, 14 Nov 2023 22:13:20 GMT";
        var echo = echoSingleField("httpDateTimestamp", Document.of(httpDateStr));
        assertEquals(httpDateStr, echo.getMember("httpDateTimestamp").asString());
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

    // ========== Enum Tests ==========

    @Test
    void testEnumRoundTrip() {
        initializeLatestProtocol();
        var echo = echoSingleField("enumValue", Document.of("VALUE_ONE"));
        assertEquals("VALUE_ONE", echo.getMember("enumValue").asString());
    }

    // ========== IntEnum Tests ==========

    @Test
    void testIntEnumRoundTrip() {
        initializeLatestProtocol();
        var echo = echoSingleField("intEnumValue", Document.of(2));
        assertEquals(2, echo.getMember("intEnumValue").asNumber().intValue());
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
        assertEquals(new java.math.BigDecimal("123.456"), echo.getBigDecimalValue());
        assertEquals(new java.math.BigInteger("123456789012345678901234567890"), echo.getBigIntegerValue());

        // Verify blob
        var blobBuffer = echo.getBlobValue().rewind(); //TODO Investigate why this needs to re-winded.
        assertNotNull(blobBuffer);
        var blobBytes = ByteBufferUtils.getBytes(blobBuffer);
        assertEquals("test data", new String(blobBytes, StandardCharsets.UTF_8));

        // Verify timestamps
        assertNotNull(echo.getEpochSecondsTimestamp());
        assertNotNull(echo.getDateTimeTimestamp());
        assertNotNull(echo.getHttpDateTimestamp());

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
    void testStructuredContentValidatesAgainstOutputSchema() throws Exception {
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
        JsonSchema schema = SCHEMA_FACTORY.getSchema(outputSchemaNode);
        Set<ValidationMessage> errors = schema.validate(structuredContentNode);
        assertTrue(errors.isEmpty(), "Validation errors: " + errors);
    }

    @Test
    void testAllTypesValidateAgainstOutputSchema() throws Exception {
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

        assertNotNull(structuredContentNode);

        // Validate using Jackson-parsed JSON directly
        JsonSchema schema = SCHEMA_FACTORY.getSchema(outputSchemaNode);
        Set<ValidationMessage> errors = schema.validate(structuredContentNode);
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

    // ========== Helper Methods ==========

    private void initializeWithProtocolVersion(ProtocolVersion protocolVersion) {
        var params = Document.of(Map.of("protocolVersion", Document.of(protocolVersion.identifier())));
        write("initialize", params);
        var response = read();
        assertEquals(protocolVersion.identifier(), response.getResult().getMember("protocolVersion").asString());
    }

    private Document createEchoInput(Map<String, Document> echoFields) {
        var echoWithRequired = new java.util.HashMap<>(echoFields);
        if (!echoWithRequired.containsKey("requiredField")) {
            echoWithRequired.put("requiredField", Document.of("default-required"));
        }
        return Document.of(Map.of("echo", Document.of(echoWithRequired)));
    }

    private JsonRpcResponse callTool(String toolName, Document arguments) {
        var params = Document.of(Map.of(
                "name",
                Document.of(toolName),
                "arguments",
                arguments));
        write("tools/call", params);
        return read();
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
}
