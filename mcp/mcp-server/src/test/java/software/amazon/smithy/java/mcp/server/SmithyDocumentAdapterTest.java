/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicschemas.SchemaConverter;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.mcp.OneOfMember;
import software.amazon.smithy.java.mcp.OneOfTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

class SmithyDocumentAdapterTest {
    private static final ShapeId BASE_ID = ShapeId.from("test#Base");
    private static final ShapeId CHILD_ID = ShapeId.from("test#Child");
    private static final Schema BASE = Schema.structureBuilder(BASE_ID)
            .putMember("name", PreludeSchemas.STRING)
            .putMember("when", PreludeSchemas.TIMESTAMP)
            .build();
    private static final Schema CHILD = Schema.structureBuilder(CHILD_ID)
            .putMember("name", PreludeSchemas.STRING)
            .putMember("count", PreludeSchemas.BIG_INTEGER)
            .build();
    private static final ShapeId CONTAINER_ID = ShapeId.from("test#Container");
    private static final Schema CONTAINER = Schema.structureBuilder(CONTAINER_ID)
            .putMember("child", CHILD)
            .build();
    private static final JsonCodec CODEC = JsonCodec.builder().build();
    private final SchemaIndex index = new SchemaIndex() {
        private final Map<ShapeId, Schema> schemas = Map.of(
                BASE_ID,
                BASE,
                CHILD_ID,
                CHILD,
                CONTAINER_ID,
                CONTAINER,
                PreludeSchemas.STRING.id(),
                PreludeSchemas.STRING);

        @Override
        public Schema getSchema(ShapeId id) {
            return schemas.get(id);
        }

        @Override
        public void visit(Consumer<Schema> visitor) {
            schemas.values().forEach(visitor);
        }
    };
    private final SmithyDocumentAdapter adapter = new SmithyDocumentAdapter(index);

    private static OneOfMember member(String name, ShapeId target) {
        return OneOfMember.builder().name(name).target(target).build();
    }

    private static Document parse(String json) {
        return CODEC.createDeserializer(json.getBytes(StandardCharsets.UTF_8)).readDocument();
    }

    private static Schema schema(ShapeId defaultTarget) {
        var builder = OneOfTrait.builder()
                .discriminator("__type")
                .members(List.of(member("base", BASE_ID), member("child", CHILD_ID)));
        if (defaultTarget != null) {
            builder.defaultTarget(defaultTarget);
        }
        return Schema.createDocument(ShapeId.from("test#Polymorphic"), builder.build());
    }

    static Stream<Arguments> untagged() {
        return Stream.of(
                Arguments.of("{\"name\":\"base\"}"),
                Arguments.of("{\"__type\":null,\"name\":\"base\"}"));
    }

    @ParameterizedTest
    @MethodSource("untagged")
    void usesDefaultOnlyWhenConfigured(String json) {
        var document = parse(json);
        assertSame(document, adapter.fromSmithy(document, schema(null)));
        assertSame(document, adapter.fromSmithy(document, PreludeSchemas.DOCUMENT));
        assertTrue(Document.equals(Document.ofObject(Map.of("base", Map.of("name", "base"))),
                adapter.fromSmithy(document, schema(BASE_ID))));
    }

    @Test
    void explicitChildOverridesDefaultAndAdaptsItsFields() {
        var document = parse("""
                {"__type":"test#Child","name":"child","count":123}
                """);
        var result = adapter.fromSmithy(document, schema(BASE_ID));
        assertTrue(
                Document.equals(Document.ofObject(Map.of("child", Map.of("name", "child", "count", "123"))), result));
    }

    private static SerializableStruct struct(Schema schema, Consumer<ShapeSerializer> members) {
        return new SerializableStruct() {
            @Override
            public Schema schema() {
                return schema;
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                members.accept(serializer);
            }

            @Override
            public <T> T getMemberValue(Schema member) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static Document holding(Schema holder, Document value) {
        return Document.of(struct(holder, s -> s.writeDocument(holder.member("shape"), value)));
    }

    @ParameterizedTest
    @MethodSource("typedDefaults")
    void typedDocumentUsesItsShapeIdInsteadOfTheDefault(ShapeId defaultTarget) {
        var holder = Schema.structureBuilder(ShapeId.from("test#Holder"))
                .putMember("shape", schema(defaultTarget))
                .build();
        var child = Document.of(struct(CHILD, s -> {
            s.writeString(CHILD.member("name"), "child");
            s.writeBigInteger(CHILD.member("count"), BigInteger.TEN);
        }));
        var result = adapter.fromSmithy(holding(holder, child), holder);
        assertTrue(Document.equals(
                Document.ofObject(Map.of("shape", Map.of("child", Map.of("name", "child", "count", "10")))),
                result), result.toString());
    }

    static Stream<Arguments> typedDefaults() {
        return Stream.of(Arguments.of((ShapeId) null), Arguments.of(BASE_ID));
    }

    @Test
    void typedDocumentOutsideTheMembersIsNotRelabeledAsTheDefault() {
        var otherSchema = Schema.structureBuilder(ShapeId.from("test#Other"))
                .putMember("name", PreludeSchemas.STRING)
                .putMember("extra", PreludeSchemas.STRING)
                .build();
        var holder = Schema.structureBuilder(ShapeId.from("test#Holder"))
                .putMember("shape", schema(BASE_ID))
                .build();
        var other = Document.of(struct(otherSchema, s -> {
            s.writeString(otherSchema.member("name"), "other");
            s.writeString(otherSchema.member("extra"), "kept");
        }));
        var shape = adapter.fromSmithy(holding(holder, other), holder).getMember("shape");
        assertNull(shape.getMember("base"));
        assertEquals("other", shape.getMember("name").asString());
        assertEquals("kept", shape.getMember("extra").asString());
    }

    @Test
    void untaggedStringMapStillUsesTheDefault() {
        var result = adapter.fromSmithy(Document.ofObject(Map.of("name", "base", "when", 1700000000)),
                schema(BASE_ID));
        assertTrue(Document.equals(
                Document.ofObject(Map.of("base", Map.of("name", "base", "when", "2023-11-14T22:13:20Z"))),
                result), result.toString());
    }

    @ParameterizedTest
    @MethodSource("typedDefaults")
    void memberBackedTypedDocumentResolvesToTheMemberTarget(ShapeId defaultTarget) {
        var holder = Schema.structureBuilder(ShapeId.from("test#Holder"))
                .putMember("shape", schema(defaultTarget))
                .build();
        var child = Document.of(struct(CONTAINER.member("child"), s -> {
            s.writeString(CHILD.member("name"), "child");
            s.writeBigInteger(CHILD.member("count"), BigInteger.TEN);
        }));
        assertEquals(ShapeId.from("test#Container$child"), child.discriminator());
        var result = adapter.fromSmithy(holding(holder, child), holder);
        assertTrue(Document.equals(
                Document.ofObject(Map.of("shape", Map.of("child", Map.of("name", "child", "count", "10")))),
                result), result.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"simple", "composed", "model-backed"})
    void unresolvableMemberBackedDocumentUsesTheDefault(String indexKind) {
        // Simple indexes return null for unknown shapes; composed and model-backed indexes throw.
        var adapter = switch (indexKind) {
            case "composed" -> new SmithyDocumentAdapter(SchemaIndex.compose(index));
            case "model-backed" -> new SmithyDocumentAdapter(
                    SchemaIndex.compose(index, new SchemaConverter(Model.builder().build()).getSchemaIndex()));
            default -> this.adapter;
        };
        var unindexed = Schema.structureBuilder(ShapeId.from("test#Unindexed"))
                .putMember("base", BASE)
                .build();
        var holder = Schema.structureBuilder(ShapeId.from("test#Holder"))
                .putMember("shape", schema(BASE_ID))
                .build();
        var base = Document.of(struct(unindexed.member("base"), s -> s.writeString(BASE.member("name"), "base")));
        var result = adapter.fromSmithy(holding(holder, base), holder);
        assertTrue(Document.equals(
                Document.ofObject(Map.of("shape", Map.of("base", Map.of("name", "base")))),
                result), result.toString());
    }

    @Test
    void unknownExplicitDiscriminatorNeverFallsBack() {
        var document = parse("""
                {"__type":"test#Unknown","name":"unknown","extra":true}
                """);
        assertSame(document, adapter.fromSmithy(document, schema(BASE_ID)));
        assertSame(document, adapter.fromSmithy(document, schema(null)));
    }

    static Stream<Arguments> invalidDiscriminators() {
        return Stream.of(Arguments.of("123"), Arguments.of("\"not a shape id\""));
    }

    @ParameterizedTest
    @MethodSource("invalidDiscriminators")
    void malformedDiscriminatorKeepsLegacyFailure(String value) {
        var document = parse("{\"__type\":" + value + "}");
        var legacyError = assertThrows(RuntimeException.class, () -> adapter.fromSmithy(document, schema(null)));
        var defaultError = assertThrows(RuntimeException.class, () -> adapter.fromSmithy(document, schema(BASE_ID)));
        assertEquals(legacyError.getClass(), defaultError.getClass());
    }

    @Test
    void rejectsDefaultOutsideMembersWithoutModelValidation() {
        var error = assertThrows(SerializationException.class,
                () -> adapter.fromSmithy(Document.ofObject(Map.of()), schema(ShapeId.from("test#Other"))));
        assertTrue(error.getMessage().contains("must identify exactly one member"));
    }

    @Test
    void rejectsAmbiguousDefaultWithoutChangingLegacyTraits() {
        var members = List.of(member("base", BASE_ID), member("alias", BASE_ID));
        var legacyTrait = OneOfTrait.builder().discriminator("__type").members(members).build();
        var defaultTrait = OneOfTrait.builder().discriminator("__type").members(members).defaultTarget(BASE_ID).build();
        var document = Document.ofObject(Map.of("name", "base"));
        assertSame(document,
                adapter.fromSmithy(document, Schema.createDocument(ShapeId.from("test#Legacy"), legacyTrait)));
        var error = assertThrows(SerializationException.class,
                () -> adapter.fromSmithy(document,
                        Schema.createDocument(ShapeId.from("test#Default"), defaultTrait)));
        assertTrue(error.getMessage().contains("must identify exactly one member"));
    }

    @Test
    void rejectsNonStructureDefaultWithoutModelValidation() {
        var trait = OneOfTrait.builder()
                .discriminator("__type")
                .defaultTarget(PreludeSchemas.STRING.id())
                .members(List.of(member("string", PreludeSchemas.STRING.id())))
                .build();
        var error = assertThrows(SerializationException.class,
                () -> adapter.fromSmithy(Document.ofObject(Map.of()),
                        Schema.createDocument(ShapeId.from("test#Invalid"), trait)));
        assertTrue(error.getMessage().contains("must target a structure"));
    }

    @Test
    void wrappedInputStillInjectsDiscriminatorForDefault() {
        var input = Document.ofObject(Map.of("base", Map.of("name", "base")));
        assertEquals(Document.ofObject(Map.of("__type", "test#Base", "name", "base")),
                adapter.toSmithy(input, schema(BASE_ID)));
    }

    static Stream<Arguments> timestampFormats() {
        return Stream.of(
                null,
                TimestampFormatTrait.EPOCH_SECONDS,
                TimestampFormatTrait.DATE_TIME,
                TimestampFormatTrait.HTTP_DATE)
                .flatMap(format -> Stream.of(
                        Arguments.of(format, "1700000000"),
                        Arguments.of(format, "\"2023-11-14T22:13:20Z\""),
                        Arguments.of(format, "\"Tue, 14 Nov 2023 22:13:20 GMT\"")));
    }

    @ParameterizedTest
    @MethodSource("timestampFormats")
    void timestampsUseModeledFormatWithLegacyFallbackInBothDirections(String format, String value) {
        var builder = Schema.structureBuilder(ShapeId.from("test#WithTimestamp"));
        if (format == null) {
            builder.putMember("timestamp", PreludeSchemas.TIMESTAMP);
        } else {
            builder.putMember("timestamp", PreludeSchemas.TIMESTAMP, new TimestampFormatTrait(format));
        }
        var schema = builder.build().member("timestamp");
        assertEquals("2023-11-14T22:13:20Z", adapter.fromSmithy(parse(value), schema).asString());
        assertEquals("2023-11-14T22:13:20Z",
                adapter.fromSmithy(Document.of(Instant.parse("2023-11-14T22:13:20Z")), schema).asString());
        assertEquals(Instant.parse("2023-11-14T22:13:20Z"),
                adapter.toSmithy(parse(value), schema).asTimestamp());
    }

    static Stream<Arguments> numericEpochSecondStrings() {
        return Stream.of("1700000000",
                "1700000000.5",
                "NaN",
                "Infinity",
                "-Infinity",
                "0x1p4",
                " 1700000000 ",
                "1e400",
                "1e20",
                "-1e20",
                "1e308",
                "1".repeat(65),
                "1.2.3")
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("numericEpochSecondStrings")
    void epochSecondsTimestampsDoNotAcceptNumericStrings(String value) {
        var schema = Schema.structureBuilder(ShapeId.from("test#WithTimestamp"))
                .putMember("timestamp",
                        PreludeSchemas.TIMESTAMP,
                        new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS))
                .build()
                .member("timestamp");
        var document = Document.of(value);
        assertThrows(RuntimeException.class, () -> adapter.toSmithy(document, schema));
        assertThrows(RuntimeException.class, () -> adapter.fromSmithy(document, schema));
    }

    @Test
    void oversizedEpochSecondStringsAreRejectedQuickly() {
        var schema = Schema.structureBuilder(ShapeId.from("test#WithTimestamp"))
                .putMember("timestamp",
                        PreludeSchemas.TIMESTAMP,
                        new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS))
                .build()
                .member("timestamp");
        var document = Document.of("1." + "1".repeat(300_000));
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            assertThrows(RuntimeException.class, () -> adapter.toSmithy(document, schema));
            assertThrows(RuntimeException.class, () -> adapter.fromSmithy(document, schema));
        });
    }

    @Test
    void unknownTimestampFormatUsesLegacyParsing() {
        var schema = Schema.structureBuilder(ShapeId.from("test#WithTimestamp"))
                .putMember("timestamp", PreludeSchemas.TIMESTAMP, new TimestampFormatTrait("unix-millis"))
                .build()
                .member("timestamp");
        assertEquals("2023-11-14T22:13:20Z", adapter.fromSmithy(parse("1700000000"), schema).asString());
        assertEquals(Instant.parse("2023-11-14T22:13:20Z"),
                adapter.toSmithy(parse("1700000000"), schema).asTimestamp());
    }
}
