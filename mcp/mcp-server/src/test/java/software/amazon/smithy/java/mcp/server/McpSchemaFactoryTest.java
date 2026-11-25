/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.smithy.ai.McpHeaderTrait;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.Unit;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.OneOfMember;
import software.amazon.smithy.java.mcp.OneOfTrait;
import software.amazon.smithy.java.mcp.model.JsonObjectSchema;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

class McpSchemaFactoryTest {

    @Test
    void toolUsesRuntimeOperationNameInsteadOfSourceSchemaName() {
        var service = new TestService();
        var operation = Operation.of(
                "Echo",
                (input, ignored) -> input,
                new TestApiOperation("EchoProxy"),
                service);

        var descriptor = new McpSchemaFactory(service.schemaIndex())
                .createTool("test", service, operation);

        assertEquals("Echo", descriptor.info().getName());
        assertEquals(
                "This tool invokes Echo API of TestService.",
                descriptor.info().getDescription());
    }

    @Test
    void ordinaryToolAlsoUsesItsRuntimeOperationName() {
        var service = new TestService();
        var operation = Operation.of(
                "TestSimpleText",
                (input, ignored) -> input,
                new TestApiOperation("SimpleTextSchema"),
                service);

        var descriptor = new McpSchemaFactory(service.schemaIndex())
                .createTool("test", service, operation);

        assertEquals("TestSimpleText", descriptor.info().getName());
    }

    @Test
    void objectSchemaUsesJsonNameForPropertiesAndRequired() {
        var inputSchema = Schema.structureBuilder(ShapeId.from("example#Input"))
                .putMember("tagNames",
                        Schema.createString(ShapeId.from("example#StringType")),
                        new JsonNameTrait("tag_names"))
                .build();
        var outputSchema = Schema.structureBuilder(ShapeId.from("example#Output"))
                .putMember("isComplete",
                        Schema.createBoolean(ShapeId.from("example#BoolType")),
                        new RequiredTrait(),
                        new JsonNameTrait("is_complete"))
                .build();

        var service = new TestService();
        var operation = Operation.of(
                "TestOp",
                (input, ignored) -> input,
                new TestApiOperationWithSchemas(inputSchema, outputSchema),
                service);

        var descriptor = new McpSchemaFactory(service.schemaIndex())
                .createTool("test", service, operation);

        JsonObjectSchema input = descriptor.info().getInputSchema();
        assertTrue(input.getProperties().containsKey("tag_names"),
                "inputSchema.properties should contain 'tag_names'");
        assertFalse(input.getProperties().containsKey("tagNames"),
                "inputSchema.properties should not contain 'tagNames'");

        JsonObjectSchema output = descriptor.info().getOutputSchema();
        assertTrue(output.getProperties().containsKey("is_complete"),
                "outputSchema.properties should contain 'is_complete'");
        assertFalse(output.getProperties().containsKey("isComplete"),
                "outputSchema.properties should not contain 'isComplete'");
        assertEquals(List.of("is_complete"),
                output.getRequired(),
                "outputSchema.required should contain 'is_complete'");
    }

    @Test
    void nestedAndListMembersUseJsonName() {
        var innerStruct = Schema.structureBuilder(ShapeId.from("example#Inner"))
                .putMember("userId",
                        Schema.createString(ShapeId.from("example#StringType")),
                        new JsonNameTrait("user_id"))
                .build();
        var listSchema = Schema.listBuilder(ShapeId.from("example#UserList"))
                .putMember("member", innerStruct)
                .build();
        var outerStruct = Schema.structureBuilder(ShapeId.from("example#Outer"))
                .putMember("users", listSchema)
                .build();

        var service = new TestService();
        var operation = Operation.of(
                "TestOp",
                (input, ignored) -> input,
                new TestApiOperationWithSchemas(outerStruct, Unit.SCHEMA),
                service);

        var descriptor = new McpSchemaFactory(service.schemaIndex())
                .createTool("test", service, operation);

        JsonObjectSchema input = descriptor.info().getInputSchema();
        @SuppressWarnings("unchecked")
        var usersDoc = (Map<String, Object>) input.getProperties().get("users").asObject();
        @SuppressWarnings("unchecked")
        var itemsDoc = (Map<String, Object>) usersDoc.get("items");
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) itemsDoc.get("properties");
        assertTrue(properties.containsKey("user_id"),
                "nested items.properties should contain 'user_id'");
        assertFalse(properties.containsKey("userId"),
                "nested items.properties should not contain 'userId'");
    }

    @Test
    void unionVariantsUseJsonName() {
        var unionSchema = Schema.unionBuilder(ShapeId.from("example#SearchUnion"))
                .putMember("byName",
                        Schema.createString(ShapeId.from("example#StringType")),
                        new JsonNameTrait("by_name"))
                .build();
        var inputSchema = Schema.structureBuilder(ShapeId.from("example#Input"))
                .putMember("search", unionSchema)
                .build();

        var service = new TestService();
        var operation = Operation.of(
                "TestOp",
                (input, ignored) -> input,
                new TestApiOperationWithSchemas(inputSchema, Unit.SCHEMA),
                service);

        var descriptor = new McpSchemaFactory(service.schemaIndex())
                .createTool("test", service, operation);

        JsonObjectSchema input = descriptor.info().getInputSchema();
        @SuppressWarnings("unchecked")
        var searchDoc = (Map<String, Object>) input.getProperties().get("search").asObject();
        @SuppressWarnings("unchecked")
        var oneOfVariants = (List<Object>) searchDoc.get("oneOf");
        @SuppressWarnings("unchecked")
        var variant = (Map<String, Object>) oneOfVariants.get(0);
        @SuppressWarnings("unchecked")
        var variantProperties = (Map<String, Object>) variant.get("properties");
        @SuppressWarnings("unchecked")
        var variantRequired = (List<Object>) variant.get("required");

        assertTrue(variantProperties.containsKey("by_name"),
                "union variant properties should contain 'by_name'");
        assertFalse(variantProperties.containsKey("byName"),
                "union variant properties should not contain 'byName'");
        assertEquals("by_name",
                variantRequired.get(0).toString(),
                "union variant required should contain 'by_name'");
    }

    @Test
    void jsonNameEqualToMemberNameIsUnchanged() {
        var inputSchema = Schema.structureBuilder(ShapeId.from("example#Input"))
                .putMember("plain",
                        Schema.createString(ShapeId.from("example#StringType")),
                        new JsonNameTrait("plain"))
                .build();

        var service = new TestService();
        var operation = Operation.of(
                "TestOp",
                (input, ignored) -> input,
                new TestApiOperationWithSchemas(inputSchema, Unit.SCHEMA),
                service);

        var descriptor = new McpSchemaFactory(service.schemaIndex())
                .createTool("test", service, operation);

        JsonObjectSchema input = descriptor.info().getInputSchema();
        assertTrue(input.getProperties().containsKey("plain"),
                "properties should contain 'plain'");
        assertEquals(1,
                input.getProperties().size(),
                "properties should have exactly one entry");
    }

    @Test
    void headerParametersAreKeyedByWireName() {
        var inputSchema = Schema.structureBuilder(ShapeId.from("example#Input"))
                .putMember("region",
                        Schema.createString(ShapeId.from("example#StringType")),
                        new JsonNameTrait("aws_region"),
                        new McpHeaderTrait("Region"))
                .build();

        var service = new TestService();
        var operation = Operation.of(
                "TestOp",
                (input, ignored) -> input,
                new TestApiOperationWithSchemas(inputSchema, Unit.SCHEMA),
                service);

        var descriptor = new McpSchemaFactory(service.schemaIndex())
                .createTool("test", service, operation);

        assertEquals(Map.of("aws_region", "Region"),
                descriptor.headerParameters(),
                "headerParameters should be keyed by wire name 'aws_region'");
    }

    @Test
    void membersWithOverlappingNamesAreNotAdvertised() {
        var stringType = Schema.createString(ShapeId.from("example#StringType"));
        var inputSchema = Schema.structureBuilder(ShapeId.from("example#Input"))
                .putMember("a", stringType, new JsonNameTrait("b"), new McpHeaderTrait("First"))
                .putMember("c", stringType, new JsonNameTrait("a"))
                .putMember("plain", stringType, new McpHeaderTrait("Plain"), new RequiredTrait())
                .putMember("d", stringType, new JsonNameTrait("e"), new RequiredTrait())
                .build();
        // The same overlap between the members of a union and of a nested structure.
        var union = Schema.unionBuilder(ShapeId.from("example#Lookup"))
                .putMember("archive", stringType, new JsonNameTrait("lookup"))
                .putMember("fetch", stringType, new JsonNameTrait("archive"))
                .putMember("byId", stringType, new JsonNameTrait("by_id"))
                .build();
        var outputSchema = Schema.structureBuilder(ShapeId.from("example#Output"))
                .putMember("nested", inputSchema)
                .putMember("selector", union)
                .build();

        var service = new TestService();
        var operation = Operation.of(
                "TestOp",
                (input, ignored) -> input,
                new TestApiOperationWithSchemas(inputSchema, outputSchema),
                service);

        var descriptor = new McpSchemaFactory(service.schemaIndex())
                .createTool("test", service, operation);

        // "a" names one member in the model and another one in JSON: neither is advertised, or checked
        // against a header.
        assertEquals(Map.of("plain", "Plain"), descriptor.headerParameters());
        var input = Document.of(descriptor.info().getInputSchema());
        assertEquals(Set.of("plain", "e"), input.getMember("properties").asStringMap().keySet());
        assertEquals(Set.of("plain", "e"),
                input.getMember("required").asList().stream().map(Document::asString).collect(Collectors.toSet()));

        var output = Document.of(descriptor.info().getOutputSchema()).getMember("properties");
        assertEquals(Set.of("plain", "e"), output.getMember("nested").getMember("properties").asStringMap().keySet());
        var variants = output.getMember("selector").getMember("oneOf").asList();
        assertEquals(1, variants.size());
        assertEquals(Set.of("by_id"), variants.getFirst().getMember("properties").asStringMap().keySet());
    }

    @ParameterizedTest
    @CsvSource({"kind,kind", "kind,label", "label,kind"})
    void oneOfVariantMemberWithTheNameOfTheDiscriminatorIsNotAdvertised(String memberName, String jsonName) {
        var stringType = Schema.createString(ShapeId.from("example#StringType"));
        var variant = Schema.structureBuilder(ShapeId.from("example#Circle"))
                .putMember(memberName, stringType, new JsonNameTrait(jsonName), new RequiredTrait())
                .putMember("radius", stringType, new RequiredTrait())
                .build();
        var polymorphic = Schema.createDocument(ShapeId.from("example#Shape"),
                OneOfTrait.builder()
                        .discriminator("kind")
                        .members(List.of(OneOfMember.builder().name("circle").target(variant.id()).build()))
                        .build());
        var holder = Schema.structureBuilder(ShapeId.from("example#Holder"))
                .putMember("shape", polymorphic)
                // Outside the document the structure has no discriminator and keeps the member.
                .putMember("plain", variant)
                .build();

        var service = new TestService();
        var operation = Operation.of(
                "TestOp",
                (input, ignored) -> input,
                new TestApiOperationWithSchemas(holder, holder),
                service);
        var index = new SchemaIndex() {
            @Override
            public Schema getSchema(ShapeId id) {
                return id.equals(variant.id()) ? variant : null;
            }

            @Override
            public void visit(Consumer<Schema> visitor) {
                visitor.accept(variant);
            }
        };

        // The tool is still built: a flattened variant cannot hold both the discriminator and that member.
        var info = new McpSchemaFactory(index).createTool("test", service, operation).info();
        for (var schema : List.of(info.getInputSchema(), info.getOutputSchema())) {
            var content = Document.of(schema)
                    .getMember("properties")
                    .getMember("shape")
                    .getMember("oneOf")
                    .asList()
                    .getFirst()
                    .getMember("properties")
                    .getMember("circle");
            assertEquals(Set.of("radius"), content.getMember("properties").asStringMap().keySet());
            assertEquals(List.of("radius"),
                    content.getMember("required").asList().stream().map(Document::asString).toList());

            var plain = Document.of(schema).getMember("properties").getMember("plain");
            assertEquals(Set.of(jsonName, "radius"), plain.getMember("properties").asStringMap().keySet());
        }

        // A call that is accepted and echoed by the handler produces a result the output schema accepts.
        var adapter = new SmithyDocumentAdapter(index);
        var arguments = McpJson.CODEC
                .createDeserializer("{\"shape\":{\"circle\":{\"radius\":\"1\"}}}".getBytes(StandardCharsets.UTF_8))
                .readDocument();
        var received = adapter.toSmithy(arguments, holder);
        assertEquals("example#Circle", received.getMember("shape").getMember("kind").asString());
        var result = McpJson.CODEC.serializeToString(adapter.fromSmithy(received, holder));
        assertEquals("{\"shape\":{\"circle\":{\"radius\":\"1\"}}}", result);
        var errors = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(McpJson.CODEC.serializeToString(info.getOutputSchema()))
                .validate(result, InputFormat.JSON);
        assertTrue(errors.isEmpty(), "result should validate against outputSchema: " + errors);
    }

    @Test
    void unionWithoutAnyAvailableMemberAdvertisesAValidSchema() {
        var stringType = Schema.createString(ShapeId.from("example#StringType"));
        // Each member is named in JSON like the other one in the model, so neither is available.
        var union = Schema.unionBuilder(ShapeId.from("example#Lookup"))
                .putMember("archive", stringType, new JsonNameTrait("fetch"))
                .putMember("fetch", stringType, new JsonNameTrait("archive"))
                .build();
        var inputSchema = Schema.structureBuilder(ShapeId.from("example#Input"))
                .putMember("selector", union)
                .putMember("plain", stringType)
                .build();
        var service = new TestService();
        var operation = Operation.of(
                "TestOp",
                (input, ignored) -> input,
                new TestApiOperationWithSchemas(inputSchema, inputSchema),
                service);

        var info = new McpSchemaFactory(service.schemaIndex()).createTool("test", service, operation).info();

        var registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        var metaschema = registry.getSchema(SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"));
        for (var schema : List.of(info.getInputSchema(), info.getOutputSchema())) {
            var json = McpJson.CODEC.serializeToString(schema);
            var invalid = metaschema.validate(json, InputFormat.JSON);
            assertTrue(invalid.isEmpty(), "the tool schema should be a valid JSON Schema: " + invalid + " " + json);

            // The member accepts no value, and can be left away.
            var tool = registry.getSchema(json);
            assertTrue(tool.validate("{\"plain\":\"p\"}", InputFormat.JSON).isEmpty());
            for (var value : List.of("{}", "{\"archive\":\"a\"}", "{\"fetch\":\"a\"}", "null", "\"a\"")) {
                assertFalse(tool.validate("{\"selector\":" + value + "}", InputFormat.JSON).isEmpty(), value);
            }
        }
    }

    @Test
    void membersWithTheSameJsonNameAreNotAdvertised() {
        var stringType = Schema.createString(ShapeId.from("example#StringType"));
        // Model validation refuses this, but not every model is validated before it is served.
        var inputSchema = Schema.structureBuilder(ShapeId.from("example#Input"))
                .putMember("first", stringType, new JsonNameTrait("key"), new RequiredTrait())
                .putMember("second", stringType, new JsonNameTrait("key"), new McpHeaderTrait("Second"))
                .putMember("plain", stringType)
                .build();
        var service = new TestService();
        var operation = Operation.of(
                "TestOp",
                (input, ignored) -> input,
                new TestApiOperationWithSchemas(inputSchema, Unit.SCHEMA),
                service);

        var descriptor = new McpSchemaFactory(service.schemaIndex()).createTool("test", service, operation);

        var input = Document.of(descriptor.info().getInputSchema());
        assertEquals(Set.of("plain"), input.getMember("properties").asStringMap().keySet());
        assertTrue(input.getMember("required").asList().isEmpty());
        assertEquals(Map.of(), descriptor.headerParameters());

        var adapter = new SmithyDocumentAdapter(service.schemaIndex());
        var error = assertThrows(IllegalArgumentException.class,
                () -> adapter.toSmithy(Document.ofObject(Map.of("key", "v", "plain", "p")), inputSchema));
        assertEquals("`key` names a member of Input that is not available over MCP", error.getMessage());
        assertEquals(Set.of("plain"),
                adapter.toSmithy(Document.ofObject(Map.of("plain", "p")), inputSchema).asStringMap().keySet());
    }

    @Test
    void discriminatorNamedMemberAlsoCountsForOverlaps() {
        var stringType = Schema.createString(ShapeId.from("example#StringType"));
        // "kind" is left out for its name, and "x" because the JSON name of "kind" is its member name.
        var variant = Schema.structureBuilder(ShapeId.from("example#Circle"))
                .putMember("kind", stringType, new JsonNameTrait("x"))
                .putMember("x", stringType, new JsonNameTrait("y"))
                .putMember("radius", stringType)
                .build();
        var polymorphic = Schema.createDocument(ShapeId.from("example#Shape"),
                OneOfTrait.builder()
                        .discriminator("kind")
                        .members(List.of(OneOfMember.builder().name("circle").target(variant.id()).build()))
                        .build());
        var holder = Schema.structureBuilder(ShapeId.from("example#Holder"))
                .putMember("shape", polymorphic)
                .build();
        var service = new TestService();
        var operation = Operation.of(
                "TestOp",
                (input, ignored) -> input,
                new TestApiOperationWithSchemas(holder, Unit.SCHEMA),
                service);
        var index = new SchemaIndex() {
            @Override
            public Schema getSchema(ShapeId id) {
                return id.equals(variant.id()) ? variant : null;
            }

            @Override
            public void visit(Consumer<Schema> visitor) {
                visitor.accept(variant);
            }
        };

        var input = new McpSchemaFactory(index).createTool("test", service, operation).info().getInputSchema();

        var content = Document.of(input)
                .getMember("properties")
                .getMember("shape")
                .getMember("oneOf")
                .asList()
                .getFirst()
                .getMember("properties")
                .getMember("circle");
        assertEquals(Set.of("radius"), content.getMember("properties").asStringMap().keySet());
    }

    @Test
    void oneOfVariantMembersWithOverlappingNamesAreNotAdvertised() {
        var stringType = Schema.createString(ShapeId.from("example#StringType"));
        // "first" names one member in the model and the other one in JSON, and so does "second".
        var variant = Schema.structureBuilder(ShapeId.from("example#Pair"))
                .putMember("first", stringType, new JsonNameTrait("second"), new RequiredTrait())
                .putMember("second", stringType, new JsonNameTrait("first"))
                .putMember("label", stringType, new JsonNameTrait("pair_label"), new RequiredTrait())
                .build();
        var polymorphic = Schema.createDocument(ShapeId.from("example#Shape"),
                OneOfTrait.builder()
                        .discriminator("kind")
                        .members(List.of(OneOfMember.builder().name("pair").target(variant.id()).build()))
                        .build());
        var holder = Schema.structureBuilder(ShapeId.from("example#Holder"))
                .putMember("shape", polymorphic)
                .putMember("plain", variant)
                .build();

        var service = new TestService();
        var operation = Operation.of(
                "TestOp",
                (input, ignored) -> input,
                new TestApiOperationWithSchemas(holder, holder),
                service);
        var index = new SchemaIndex() {
            @Override
            public Schema getSchema(ShapeId id) {
                return id.equals(variant.id()) ? variant : null;
            }

            @Override
            public void visit(Consumer<Schema> visitor) {
                visitor.accept(variant);
            }
        };

        var info = new McpSchemaFactory(index).createTool("test", service, operation).info();
        for (var schema : List.of(info.getInputSchema(), info.getOutputSchema())) {
            var content = Document.of(schema)
                    .getMember("properties")
                    .getMember("shape")
                    .getMember("oneOf")
                    .asList()
                    .getFirst()
                    .getMember("properties")
                    .getMember("pair");
            assertEquals(Set.of("pair_label"), content.getMember("properties").asStringMap().keySet());
            assertEquals(List.of("pair_label"),
                    content.getMember("required").asList().stream().map(Document::asString).toList());

            // The overlap belongs to the structure, wherever it is used.
            var plain = Document.of(schema).getMember("properties").getMember("plain");
            assertEquals(Set.of("pair_label"), plain.getMember("properties").asStringMap().keySet());
        }

        var adapter = new SmithyDocumentAdapter(index);
        var arguments = McpJson.CODEC
                .createDeserializer("{\"shape\":{\"pair\":{\"pair_label\":\"l\"}}}".getBytes(StandardCharsets.UTF_8))
                .readDocument();
        var received = adapter.toSmithy(arguments, holder);
        assertEquals(Set.of("kind", "label"), received.getMember("shape").asStringMap().keySet());
        var result = McpJson.CODEC.serializeToString(adapter.fromSmithy(received, holder));
        assertEquals("{\"shape\":{\"pair\":{\"pair_label\":\"l\"}}}", result);
        var errors = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(McpJson.CODEC.serializeToString(info.getOutputSchema()))
                .validate(result, InputFormat.JSON);
        assertTrue(errors.isEmpty(), "result should validate against outputSchema: " + errors);
    }

    private static final class TestService implements Service {
        private static final Schema SCHEMA =
                Schema.createService(ShapeId.from("example#TestService"));

        @Override
        public <I extends SerializableStruct,
                O extends SerializableStruct> Operation<I, O> getOperation(
                        String operationName
                ) {
            return null;
        }

        @Override
        public List<Operation<? extends SerializableStruct,
                ? extends SerializableStruct>> getAllOperations() {
            return List.of();
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public TypeRegistry typeRegistry() {
            return TypeRegistry.empty();
        }

        @Override
        public SchemaIndex schemaIndex() {
            return SchemaIndex.compose();
        }
    }

    private static final class TestApiOperation implements ApiOperation<Unit, Unit> {
        private static final ApiService SERVICE =
                () -> Schema.createService(ShapeId.from("example#TestService"));
        private final Schema schema;

        private TestApiOperation(String name) {
            schema = Schema.createOperation(ShapeId.from("example#" + name));
        }

        @Override
        public ShapeBuilder<Unit> inputBuilder() {
            return Unit.builder();
        }

        @Override
        public ShapeBuilder<Unit> outputBuilder() {
            return Unit.builder();
        }

        @Override
        public Schema schema() {
            return schema;
        }

        @Override
        public Schema inputSchema() {
            return Unit.SCHEMA;
        }

        @Override
        public Schema outputSchema() {
            return Unit.SCHEMA;
        }

        @Override
        public TypeRegistry errorRegistry() {
            return TypeRegistry.empty();
        }

        @Override
        public List<ShapeId> effectiveAuthSchemes() {
            return List.of();
        }

        @Override
        public List<Schema> errorSchemas() {
            return List.of();
        }

        @Override
        public ApiService service() {
            return SERVICE;
        }
    }

    private static final class TestApiOperationWithSchemas implements ApiOperation<Unit, Unit> {
        private static final ApiService SERVICE =
                () -> Schema.createService(ShapeId.from("example#TestService"));
        private final Schema operationSchema;
        private final Schema inputSchema;
        private final Schema outputSchema;

        private TestApiOperationWithSchemas(Schema inputSchema, Schema outputSchema) {
            this.operationSchema = Schema.createOperation(ShapeId.from("example#TestOperation"));
            this.inputSchema = inputSchema;
            this.outputSchema = outputSchema;
        }

        @Override
        public ShapeBuilder<Unit> inputBuilder() {
            return Unit.builder();
        }

        @Override
        public ShapeBuilder<Unit> outputBuilder() {
            return Unit.builder();
        }

        @Override
        public Schema schema() {
            return operationSchema;
        }

        @Override
        public Schema inputSchema() {
            return inputSchema;
        }

        @Override
        public Schema outputSchema() {
            return outputSchema;
        }

        @Override
        public TypeRegistry errorRegistry() {
            return TypeRegistry.empty();
        }

        @Override
        public List<ShapeId> effectiveAuthSchemes() {
            return List.of();
        }

        @Override
        public List<Schema> errorSchemas() {
            return List.of();
        }

        @Override
        public ApiService service() {
            return SERVICE;
        }
    }
}
