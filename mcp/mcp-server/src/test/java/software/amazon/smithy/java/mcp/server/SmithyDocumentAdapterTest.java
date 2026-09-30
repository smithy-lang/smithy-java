/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.mcp.OneOfMember;
import software.amazon.smithy.java.mcp.OneOfTrait;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

class SmithyDocumentAdapterTest {
    private static final ShapeId BASE_ID = ShapeId.from("test#Base");
    private static final ShapeId CHILD_ID = ShapeId.from("test#Child");
    private static final Schema BASE = Schema.structureBuilder(BASE_ID)
            .putMember("name", PreludeSchemas.STRING)
            .build();
    private static final Schema CHILD = Schema.structureBuilder(CHILD_ID)
            .putMember("name", PreludeSchemas.STRING)
            .putMember("count", PreludeSchemas.BIG_INTEGER)
            .build();
    private static final JsonCodec CODEC = JsonCodec.builder().build();
    private final SmithyDocumentAdapter adapter = new SmithyDocumentAdapter(new SchemaIndex() {
        private final Map<ShapeId, Schema> schemas = Map.of(
                BASE_ID,
                BASE,
                CHILD_ID,
                CHILD,
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
    });

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
        var legacyValues = Stream.of(
                null,
                TimestampFormatTrait.EPOCH_SECONDS,
                TimestampFormatTrait.DATE_TIME,
                TimestampFormatTrait.HTTP_DATE)
                .flatMap(format -> Stream.of(
                        Arguments.of(format, "1700000000"),
                        Arguments.of(format, "\"2023-11-14T22:13:20Z\""),
                        Arguments.of(format, "\"Tue, 14 Nov 2023 22:13:20 GMT\"")));
        return Stream.concat(legacyValues,
                Stream.of(Arguments.of(TimestampFormatTrait.EPOCH_SECONDS, "\"1700000000\"")));
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
}
