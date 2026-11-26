/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.ai.McpHeaderTrait;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.BigNumbers;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicschemas.SchemaConverter;
import software.amazon.smithy.java.dynamicschemas.StructDocument;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.mcp.OneOfMember;
import software.amazon.smithy.java.mcp.OneOfTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
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

    @Test
    void boundsArbitraryPrecisionStringsWhenAdaptingToSmithy() {
        var count = CHILD.member("count");
        var decimal = Schema.structureBuilder(ShapeId.from("test#Decimal"))
                .putMember("value", PreludeSchemas.BIG_DECIMAL)
                .build()
                .member("value");
        String maxDigits = "7".repeat(BigNumbers.MAX_DIGITS);
        assertEquals(new BigInteger(maxDigits), adapter.toSmithy(Document.of(maxDigits), count).asBigInteger());
        String maxDigitsDecimal = "7".repeat(BigNumbers.MAX_DIGITS - 1) + ".5";
        assertEquals(new BigDecimal(maxDigitsDecimal),
                adapter.toSmithy(Document.of(maxDigitsDecimal), decimal).asBigDecimal());
        String tooManyDigits = "7".repeat(BigNumbers.MAX_TEXT_DIGITS + 1);
        assertThrows(SerializationException.class, () -> adapter.toSmithy(Document.of(tooManyDigits), count));
        assertThrows(SerializationException.class,
                () -> adapter.toSmithy(Document.of(tooManyDigits + ".5"), decimal));
        assertThrows(SerializationException.class,
                () -> adapter.toSmithy(Document.of("1".repeat(2_000_000)), count));
        String tooManySignificantDigits = "7".repeat(BigNumbers.MAX_TEXT_DIGITS);
        assertThrows(SerializationException.class,
                () -> adapter.toSmithy(Document.of(tooManySignificantDigits), count));
        assertThrows(SerializationException.class,
                () -> adapter.toSmithy(Document.of(maxDigits + ".5"), decimal));
        String tooManyFullwidthDigits = "\uFF17".repeat(BigNumbers.MAX_TEXT_DIGITS + 1);
        assertThrows(SerializationException.class,
                () -> adapter.toSmithy(Document.of(tooManyFullwidthDigits), count));
        assertThrows(SerializationException.class,
                () -> adapter.toSmithy(Document.of(tooManyFullwidthDigits), decimal));
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

    private static final Schema STRING_LIST = Schema.listBuilder(ShapeId.from("test#StringList"))
            .putMember("member", PreludeSchemas.STRING)
            .build();
    private static final Schema TAGGED = Schema.structureBuilder(ShapeId.from("test#Tagged"))
            .putMember("tagNames", STRING_LIST, new JsonNameTrait("tag_names"))
            .build();
    private static final Schema TAGGED_SINCE = Schema.structureBuilder(ShapeId.from("test#TaggedSince"))
            .putMember("tagNames", STRING_LIST, new JsonNameTrait("tag_names"))
            .putMember("since", PreludeSchemas.TIMESTAMP)
            .build();

    private static Document parseMcp(String json) {
        return McpJson.CODEC.createDeserializer(json.getBytes(StandardCharsets.UTF_8)).readDocument();
    }

    private static String toMcpJson(Document document) {
        return McpJson.CODEC.serializeToString(document);
    }

    private static List<String> strings(Document list) {
        return list.asList().stream().map(Document::asString).toList();
    }

    @Test
    void jsonNameMemberIsReadByWireNameWithoutAdaptedTypes() {
        var result = adapter.toSmithy(parseMcp("{\"tag_names\":[\"a\"]}"), TAGGED);
        assertEquals(List.of("a"), strings(result.getMember("tagNames")));
        assertNull(result.getMember("tag_names"));
    }

    @Test
    void memberNameAliasIsAccepted() {
        var result = adapter.toSmithy(parseMcp("{\"tagNames\":[\"a\"]}"), TAGGED);
        assertEquals(List.of("a"), strings(result.getMember("tagNames")));
    }

    @Test
    void wireNameWinsOverAlias() {
        var result = adapter.toSmithy(parseMcp("{\"tag_names\":[\"w\"],\"tagNames\":[\"m\"]}"), TAGGED);
        assertEquals(List.of("w"), strings(result.getMember("tagNames")));
    }

    private static void assertNotAvailable(String key, String shape, Executable call) {
        var error = assertThrows(IllegalArgumentException.class, call);
        assertEquals("`" + key + "` names a member of " + shape + " that is not available over MCP",
                error.getMessage());
    }

    @Test
    void membersWithOverlappingNamesAreNeitherBoundNorEmitted() {
        // "a" names one member in the model and another one on the wire. No reading of the key is right for
        // every caller, so both members are left out. The others are not affected.
        var expected = Document.ofObject(Map.of("itemName", "d", "note", "n"));
        var result = adapter.toSmithy(parseMcp("{\"item_name\":\"d\",\"note\":\"n\"}"), OVERLAPPED);
        assertTrue(Document.equals(expected, result), result.toString());

        // A key that names one of them is refused rather than dropped, whatever it holds.
        assertNotAvailable("a",
                "Overlapped",
                () -> adapter.toSmithy(parseMcp("{\"a\":\"x\",\"item_name\":\"d\"}"), OVERLAPPED));
        assertNotAvailable("b", "Overlapped", () -> adapter.toSmithy(parseMcp("{\"b\":null}"), OVERLAPPED));
        assertNotAvailable("c", "Overlapped", () -> adapter.toSmithy(parseMcp("{\"c\":\"y\"}"), OVERLAPPED));

        var typed = Document.of(struct(OVERLAPPED, s -> {
            s.writeString(OVERLAPPED.member("a"), "red");
            s.writeString(OVERLAPPED.member("c"), "blue");
            s.writeString(OVERLAPPED.member("itemName"), "d");
            s.writeString(OVERLAPPED.member("note"), "n");
        }));
        assertNotAvailable("a", "Overlapped", () -> adapter.toSmithy(typed, OVERLAPPED));
        // Also when the shape writes the member as null, like the JSON key that holds null.
        var typedNull = Document.of(struct(OVERLAPPED, s -> {
            s.writeNull(OVERLAPPED.member("c"));
            s.writeString(OVERLAPPED.member("note"), "n");
        }));
        assertNotAvailable("c", "Overlapped", () -> adapter.toSmithy(typedNull, OVERLAPPED));

        // Results of such a structure are not left to the codec, which would write every member.
        assertEquals("{\"item_name\":\"d\",\"note\":\"n\"}", toMcpJson(adapter.fromSmithy(typed, OVERLAPPED)));
        // The other members are written as the codec would have: one that the shape writes as null stays, and
        // one it does not write stays away.
        var withNull = Document.of(struct(OVERLAPPED, s -> {
            s.writeString(OVERLAPPED.member("itemName"), "d");
            s.writeNull(OVERLAPPED.member("note"));
        }));
        assertEquals("{\"item_name\":\"d\",\"note\":null}", toMcpJson(adapter.fromSmithy(withNull, OVERLAPPED)));
        assertEquals(toMcpJson(withNull), toMcpJson(adapter.fromSmithy(withNull, OVERLAPPED)));
        var withoutNote = Document.of(struct(OVERLAPPED, s -> s.writeString(OVERLAPPED.member("itemName"), "d")));
        assertEquals("{\"item_name\":\"d\"}", toMcpJson(adapter.fromSmithy(withoutNote, OVERLAPPED)));

        // Nested, in a list, in a map, and inside a structure that has no renamed member of its own.
        var list = Schema.listBuilder(ShapeId.from("test#OverlappedList")).putMember("member", OVERLAPPED).build();
        var map = Schema.mapBuilder(ShapeId.from("test#OverlappedMap"))
                .putMember("key", PreludeSchemas.STRING)
                .putMember("value", OVERLAPPED)
                .build();
        var holder = Schema.structureBuilder(ShapeId.from("test#OverlappedHolder"))
                .putMember("one", OVERLAPPED)
                .putMember("many", list)
                .putMember("byKey", map)
                .build();
        var held = adapter.toSmithy(
                parseMcp(
                        "{\"one\":{\"note\":\"n\"},\"many\":[{\"item_name\":\"d\"}],\"byKey\":{\"k\":{\"note\":\"m\"}}}"),
                holder);
        assertTrue(Document.equals(
                Document.ofObject(Map.of(
                        "one",
                        Map.of("note", "n"),
                        "many",
                        List.of(Map.of("itemName", "d")),
                        "byKey",
                        Map.of("k", Map.of("note", "m")))),
                held), held.toString());
        assertNotAvailable("b",
                "Overlapped",
                () -> adapter.toSmithy(parseMcp("{\"one\":{\"b\":\"red\",\"note\":\"n\"}}"), holder));
        assertNotAvailable("a",
                "Overlapped",
                () -> adapter.toSmithy(parseMcp("{\"many\":[{\"item_name\":\"d\"},{\"a\":\"blue\"}]}"), holder));
        assertNotAvailable("c",
                "Overlapped",
                () -> adapter.toSmithy(parseMcp("{\"byKey\":{\"k\":{\"c\":null}}}"), holder));
        // A map may also be given as a typed object whose members are its entries.
        var entries = Schema.structureBuilder(ShapeId.from("test#OverlappedEntries"))
                .putMember("k", OVERLAPPED)
                .build();
        var typedEntries = Document.of(struct(entries,
                s -> s.writeStruct(entries.member("k"),
                        struct(OVERLAPPED, n -> n.writeString(OVERLAPPED.member("c"), "y")))));
        assertNotAvailable("c",
                "Overlapped",
                () -> adapter.toSmithy(Document.of(Map.of("byKey", typedEntries)), holder));

        var typedHolder = Document.of(struct(holder, s -> {
            s.writeStruct(holder.member("one"), struct(OVERLAPPED, n -> {
                n.writeString(OVERLAPPED.member("a"), "red");
                n.writeString(OVERLAPPED.member("note"), "n");
            }));
        }));
        assertEquals("{\"one\":{\"note\":\"n\"}}", toMcpJson(adapter.fromSmithy(typedHolder, holder)));
    }

    @Test
    void membersWithTheSameWireNameAreNeitherBoundNorEmitted() {
        // Model validation refuses this, but not every model is validated before it is served.
        assertEquals(Set.of("first", "second"), SmithyDocumentAdapter.overlappingMembers(DUPLICATED));

        var result = adapter.toSmithy(parseMcp("{\"rank\":1}"), DUPLICATED);
        assertTrue(Document.equals(Document.ofObject(Map.of("rank", 1)), result), result.toString());
        for (var key : List.of("key", "first", "second")) {
            assertNotAvailable(key,
                    "Duplicated",
                    () -> adapter.toSmithy(parseMcp("{\"" + key + "\":\"v\",\"rank\":1}"), DUPLICATED));
        }

        var typed = Document.of(struct(DUPLICATED, s -> {
            s.writeString(DUPLICATED.member("first"), "f");
            s.writeString(DUPLICATED.member("second"), "s");
            s.writeInteger(DUPLICATED.member("rank"), 1);
        }));
        assertEquals("{\"rank\":1}", toMcpJson(adapter.fromSmithy(typed, DUPLICATED)));
    }

    @Test
    void unionMembersWithOverlappingNamesAreNeitherBoundNorEmitted() {
        // The key "archive" selected one member before members were named by their JSON name and would select
        // the other one now.
        for (var key : List.of("archive", "lookup", "fetch")) {
            assertNotAvailable(key,
                    "Crossed",
                    () -> adapter.toSmithy(parseMcp("{\"" + key + "\":\"id\"}"), CROSSED));
        }
        // Also next to a member that is available, and whatever it holds.
        assertNotAvailable("archive",
                "Crossed",
                () -> adapter.toSmithy(parseMcp("{\"archive\":\"x\",\"by_id\":\"y\"}"), CROSSED));
        assertNotAvailable("lookup",
                "Crossed",
                () -> adapter.toSmithy(parseMcp("{\"by_id\":\"y\",\"lookup\":null}"), CROSSED));

        var plain = adapter.toSmithy(parseMcp("{\"by_id\":\"i\"}"), CROSSED);
        assertTrue(Document.equals(Document.ofObject(Map.of("byId", "i")), plain), plain.toString());

        // A result that selects such a member cannot be expressed: an empty union would not say so.
        var typed = Document.of(struct(CROSSED, s -> s.writeString(CROSSED.member("archive"), "id")));
        var error = assertThrows(SmithyDocumentAdapter.UnavailableResultException.class,
                () -> adapter.fromSmithy(typed, CROSSED));
        assertEquals("Member `Crossed$archive` is not available over MCP", error.getMessage());
        assertThrows(SmithyDocumentAdapter.UnavailableResultException.class,
                () -> adapterFor(HOLDING, CROSSED).fromSmithy(
                        parseMcp("{\"__type\":\"test#Holding\",\"selector\":{\"fetch\":\"id\"}}"),
                        polymorphic("__type", member("holding", HOLDING.id()))));
        // Also when the member is there and holds null.
        assertEquals("Member `Crossed$archive` is not available over MCP",
                assertThrows(SmithyDocumentAdapter.UnavailableResultException.class,
                        () -> adapterFor(HOLDING, CROSSED).fromSmithy(
                                parseMcp("{\"__type\":\"test#Holding\",\"selector\":{\"archive\":null}}"),
                                polymorphic("__type", member("holding", HOLDING.id()))))
                        .getMessage());
        var typedNullArm = Document.of(struct(CROSSED, s -> s.writeNull(CROSSED.member("fetch"))));
        assertEquals("Member `Crossed$fetch` is not available over MCP",
                assertThrows(SmithyDocumentAdapter.UnavailableResultException.class,
                        () -> adapter.fromSmithy(typedNullArm, CROSSED)).getMessage());
        // And whichever member comes first: the one that is available is declared before the others here.
        var reversed = Schema.unionBuilder(ShapeId.from("test#Reversed"))
                .putMember("byId", PreludeSchemas.STRING, new JsonNameTrait("by_id"))
                .putMember("archive", PreludeSchemas.STRING, new JsonNameTrait("lookup"))
                .putMember("fetch", PreludeSchemas.STRING, new JsonNameTrait("archive"))
                .build();
        var holdingReversed = Schema.structureBuilder(ShapeId.from("test#HoldingReversed"))
                .putMember("selector", reversed)
                .build();
        assertEquals("Member `Reversed$archive` is not available over MCP",
                assertThrows(SmithyDocumentAdapter.UnavailableResultException.class,
                        () -> adapterFor(holdingReversed, reversed).fromSmithy(
                                parseMcp("{\"__type\":\"test#HoldingReversed\","
                                        + "\"selector\":{\"byId\":\"i\",\"archive\":null}}"),
                                polymorphic("__type", member("holding", holdingReversed.id()))))
                        .getMessage());
        var typedPlain = Document.of(struct(CROSSED, s -> s.writeString(CROSSED.member("byId"), "i")));
        assertEquals("{\"by_id\":\"i\"}", toMcpJson(adapter.fromSmithy(typedPlain, CROSSED)));
    }

    @Test
    void noKeyNamesTwoMembersThatAreExposed() {
        // What makes reading an untyped document safe: among the members that are not left out, a key is the
        // member name or the wire name of one member only.
        for (var schema : List.of(TAGGED,
                TAGGED_SINCE,
                ITEM,
                BOX,
                OVERLAPPED,
                SWAPPED,
                SELECTOR,
                CROSSED,
                CHAINED,
                DUPLICATED,
                STAMPED,
                LABELED,
                KINDED,
                REGIONAL)) {
            var omitted = SmithyDocumentAdapter.overlappingMembers(schema);
            var owners = new HashMap<String, String>();
            for (var member : schema.members()) {
                if (omitted.contains(member.memberName())) {
                    continue;
                }
                for (var key : new HashSet<>(List.of(member.memberName(), McpJson.wireName(member)))) {
                    var other = owners.put(key, member.memberName());
                    assertNull(other,
                            schema.id() + ": `" + key + "` names `" + other + "` and `"
                                    + member.memberName() + "`");
                }
            }
        }
        assertEquals(Set.of("a", "c"), SmithyDocumentAdapter.overlappingMembers(OVERLAPPED));
        assertEquals(Set.of("archive", "fetch"), SmithyDocumentAdapter.overlappingMembers(CROSSED));
        // A chain: each member is named on the wire like the next one in the model.
        assertEquals(Set.of("p", "q", "r"), SmithyDocumentAdapter.overlappingMembers(CHAINED));
        assertEquals(Set.of(), SmithyDocumentAdapter.overlappingMembers(ITEM));
    }

    @Test
    void jsonNameMemberIsReadByWireNameWithTimestampSibling() {
        var result = adapter.toSmithy(
                parseMcp("{\"tag_names\":[\"a\"],\"since\":\"2026-01-01T00:00:00Z\"}"),
                TAGGED_SINCE);
        assertEquals(List.of("a"), strings(result.getMember("tagNames")));
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), result.getMember("since").asTimestamp());
    }

    @Test
    void outputUsesWireNamesWithAndWithoutTimestamp() {
        var plain = Schema.structureBuilder(ShapeId.from("test#Status"))
                .putMember("isComplete", PreludeSchemas.BOOLEAN, new JsonNameTrait("is_complete"))
                .build();
        var timed = Schema.structureBuilder(ShapeId.from("test#TimedStatus"))
                .putMember("isComplete", PreludeSchemas.BOOLEAN, new JsonNameTrait("is_complete"))
                .putMember("generatedAt", PreludeSchemas.TIMESTAMP)
                .build();
        var plainOutput = Document.of(struct(plain, s -> s.writeBoolean(plain.member("isComplete"), true)));
        var timedOutput = Document.of(struct(timed, s -> {
            s.writeBoolean(timed.member("isComplete"), true);
            s.writeTimestamp(timed.member("generatedAt"), Instant.parse("2026-01-01T00:00:00Z"));
        }));
        assertEquals("{\"is_complete\":true}", toMcpJson(adapter.fromSmithy(plainOutput, plain)));
        assertEquals("{\"is_complete\":true,\"generatedAt\":\"2026-01-01T00:00:00Z\"}",
                toMcpJson(adapter.fromSmithy(timedOutput, timed)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"item_name", "itemName"})
    void nestedJsonNameUnderListMapAndUnion(String itemKey) {
        var itemId = ShapeId.from("test#Item");
        var item = Schema.structureBuilder(itemId)
                .putMember("itemName", PreludeSchemas.STRING, new JsonNameTrait("item_name"))
                .build();
        var itemList = Schema.listBuilder(ShapeId.from("test#ItemList")).putMember("member", item).build();
        var itemMap = Schema.mapBuilder(ShapeId.from("test#ItemMap"))
                .putMember("key", PreludeSchemas.STRING)
                .putMember("value", item)
                .build();
        var selector = Schema.unionBuilder(ShapeId.from("test#Selector"))
                .putMember("byName", item, new JsonNameTrait("by_name"))
                .putMember("byId", PreludeSchemas.STRING)
                .build();
        var polymorphic = Schema.createDocument(ShapeId.from("test#PolymorphicItem"),
                OneOfTrait.builder().discriminator("__type").members(List.of(member("item", itemId))).build());
        var holder = Schema.structureBuilder(ShapeId.from("test#ItemHolder"))
                .putMember("items", itemList)
                .putMember("itemsByKey", itemMap, new JsonNameTrait("items_by_key"))
                .putMember("selector", selector)
                .putMember("shape", polymorphic)
                .build();
        var itemAdapter = adapterFor(item);
        var selectorKey = itemKey.equals("item_name") ? "by_name" : "byName";
        var mapKey = itemKey.equals("item_name") ? "items_by_key" : "itemsByKey";
        var json = """
                {"items":[{"%1$s":"a"}],"%2$s":{"k":{"%1$s":"b"}},
                 "selector":{"%3$s":{"%1$s":"c"}},"shape":{"item":{"%1$s":"d"}}}
                """.formatted(itemKey, mapKey, selectorKey);

        var input = itemAdapter.toSmithy(parseMcp(json), holder);
        assertEquals("a", input.getMember("items").asList().get(0).getMember("itemName").asString());
        assertEquals("b", input.getMember("itemsByKey").getMember("k").getMember("itemName").asString());
        assertEquals("c", input.getMember("selector").getMember("byName").getMember("itemName").asString());
        assertEquals("d", input.getMember("shape").getMember("itemName").asString());
        assertEquals("test#Item", input.getMember("shape").getMember("__type").asString());

        // Outputs are typed documents; only content below a document member can be an untyped map.
        // Typed members that need no adaptation are passed through, so compare what the codec writes.
        var output = parseMcp(toMcpJson(itemAdapter.fromSmithy(StructDocument.of(holder, input), holder)));
        assertTrue(Document.equals(
                Document.ofObject(Map.of(
                        "items",
                        List.of(Map.of("item_name", "a")),
                        "items_by_key",
                        Map.of("k", Map.of("item_name", "b")),
                        "selector",
                        Map.of("by_name", Map.of("item_name", "c")),
                        "shape",
                        Map.of("item", Map.of("item_name", "d")))),
                output), output.toString());
    }

    @Test
    void recursiveStructureWithJsonName() {
        // The recursive member comes first so that the schema walk meets the cycle before the renamed member.
        var builder = Schema.structureBuilder(ShapeId.from("test#Node"));
        var node = builder
                .putMember("next", builder)
                .putMember("nodeLabel", PreludeSchemas.STRING, new JsonNameTrait("node_label"))
                .build();
        assertEquals(List.of("next", "nodeLabel"), node.members().stream().map(Schema::memberName).toList());
        var json = "{\"next\":{\"node_label\":\"two\"},\"node_label\":\"one\"}";
        var input = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> adapter.toSmithy(parseMcp(json), node));
        assertEquals("one", input.getMember("nodeLabel").asString());
        assertEquals("two", input.getMember("next").getMember("nodeLabel").asString());
        assertFalse(input.getMember("next").getMemberNames().contains("node_label"));

        var output = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> adapter.fromSmithy(StructDocument.of(node, input), node));
        assertEquals(json, toMcpJson(output));
    }

    @Test
    void recursiveStructureWithJsonNameBelowDocument() {
        var nodeId = ShapeId.from("test#Node");
        var builder = Schema.structureBuilder(nodeId);
        var node = builder
                .putMember("next", builder)
                .putMember("nodeLabel", PreludeSchemas.STRING, new JsonNameTrait("node_label"))
                .build();
        var polymorphic = Schema.createDocument(ShapeId.from("test#PolymorphicNode"),
                OneOfTrait.builder().discriminator("__type").members(List.of(member("node", nodeId))).build());
        var content = parseMcp("{\"__type\":\"test#Node\",\"next\":{\"nodeLabel\":\"two\"},\"nodeLabel\":\"one\"}");

        var output = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> adapterFor(node).fromSmithy(content, polymorphic));
        assertTrue(Document.equals(
                Document.ofObject(Map.of("node",
                        Map.of("next", Map.of("node_label", "two"), "node_label", "one"))),
                output), output.toString());
    }

    @Test
    void typedInputIsReadByMemberName() {
        var typed = Document.of(struct(RENAMED, s -> s.writeString(RENAMED.member("a"), "x")));
        var result = adapter.toSmithy(typed, RENAMED);
        assertEquals("x", result.getMember("a").asString());
        assertNull(result.getMember("c"));

        var dynamic = adapter.toSmithy(StructDocument.of(RENAMED, Document.ofObject(Map.of("a", "x"))), RENAMED);
        assertEquals("x", dynamic.getMember("a").asString());
        assertNull(dynamic.getMember("c"));
    }

    @Test
    void explicitNullUnderWireNameIsNotReplacedByAlias() {
        var result = adapter.toSmithy(parseMcp("{\"tag_names\":null,\"tagNames\":[\"old\"]}"), TAGGED);
        assertNull(result.getMember("tagNames"));

        var builtFromMap = new HashMap<String, Document>();
        builtFromMap.put("tag_names", null);
        builtFromMap.put("tagNames", Document.of(List.of(Document.of("old"))));
        assertNull(adapter.toSmithy(Document.of(builtFromMap), TAGGED).getMember("tagNames"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"byName\":\"n\",\"byId\":\"i\"}",
            "{\"by_name\":\"n\",\"byId\":\"i\"}",
            "{\"byId\":\"i\",\"byName\":\"n\"}"
    })
    void unionInputWithMoreThanOneVariantIsRejected(String json) {
        var holder = Schema.structureBuilder(ShapeId.from("test#SelectorHolder"))
                .putMember("selector", SELECTOR)
                .build();
        assertThrows(IllegalArgumentException.class, () -> adapter.toSmithy(parseMcp(json), SELECTOR));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.toSmithy(parseMcp("{\"selector\":" + json + "}"), holder));
    }

    @Test
    void unionVariantSentUnderBothSpellingsCountsOnce() {
        var result = adapter.toSmithy(parseMcp("{\"by_name\":\"wire\",\"byName\":\"alias\"}"), SELECTOR);
        assertEquals(Set.of("byName"), result.asStringMap().keySet());
        assertEquals("wire", result.getMember("byName").asString());

        // A member that is present counts, whatever it holds.
        assertThrows(IllegalArgumentException.class,
                () -> adapter.toSmithy(parseMcp("{\"by_name\":null,\"byId\":\"i\"}"), SELECTOR));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.toSmithy(parseMcp("{\"byId\":\"i\",\"byName\":null}"), SELECTOR));
        assertTrue(adapter.toSmithy(parseMcp("{\"by_name\":null}"), SELECTOR).asStringMap().isEmpty());
    }

    @Test
    void renameOnlyOutputIsSerializedByTheCodecUnchanged() {
        var inner = Schema.structureBuilder(ShapeId.from("test#Status"))
                .putMember("isComplete", PreludeSchemas.BOOLEAN, new JsonNameTrait("is_complete"))
                .build();
        var outer = Schema.structureBuilder(ShapeId.from("test#StatusPage"))
                .putMember("pageStatus", inner, new JsonNameTrait("page_status"))
                .putMember("nextToken", PreludeSchemas.STRING)
                .build();
        var shape = struct(outer, s -> {
            s.writeStruct(outer.member("pageStatus"),
                    struct(inner, n -> n.writeBoolean(inner.member("isComplete"), true)));
            s.writeString(outer.member("nextToken"), "t");
        });
        var typed = Document.of(shape);

        var output = adapter.fromSmithy(typed, outer);
        assertSame(typed, output);
        assertEquals("{\"page_status\":{\"is_complete\":true},\"nextToken\":\"t\"}", toMcpJson(output));
        assertEquals(McpJson.CODEC.serializeToString(Document.of(shape)), toMcpJson(output));
    }

    @ParameterizedTest
    @ValueSource(strings = {"item_name", "itemName"})
    void plainOuterStructureHoldingRenamedInnerStructure(String itemKey) {
        var item = Schema.structureBuilder(ShapeId.from("test#Item"))
                .putMember("itemName", PreludeSchemas.STRING, new JsonNameTrait("item_name"))
                .build();
        var outer = Schema.structureBuilder(ShapeId.from("test#PlainOuter"))
                .putMember("item", item)
                .putMember("count", PreludeSchemas.INTEGER)
                .build();
        var result = adapter.toSmithy(parseMcp("{\"item\":{\"" + itemKey + "\":\"a\"},\"count\":1}"), outer);
        assertEquals("a", result.getMember("item").getMember("itemName").asString());
        assertFalse(result.getMember("item").getMemberNames().contains("item_name"));
        assertEquals(1, result.getMember("count").asInteger());
    }

    @Test
    void jsonNameEqualToMemberNameBehavesAsNoTrait() {
        var noOp = Schema.structureBuilder(ShapeId.from("test#NoOpRename"))
                .putMember("plain", PreludeSchemas.STRING, new JsonNameTrait("plain"))
                .build();
        var input = parseMcp("{\"plain\":\"x\"}");
        assertSame(input, adapter.toSmithy(input, noOp));
        var output = Document.of(struct(noOp, s -> s.writeString(noOp.member("plain"), "x")));
        assertSame(output, adapter.fromSmithy(output, noOp));
    }

    @Test
    void untypedWireKeyedOneOfContentKeepsRenamedMembers() {
        var polymorphic = polymorphicItem();
        // What a proxied backend returns: an untyped document already keyed by wire names.
        var content = parseMcp("{\"__type\":\"test#Item\",\"item_name\":\"d\",\"count\":2}");
        var expected = Document.ofObject(Map.of("item", Map.of("item_name", "d", "count", 2)));

        var output = adapterFor(ITEM).fromSmithy(content, polymorphic);
        assertTrue(Document.equals(expected, output), output.toString());

        var holder = Schema.structureBuilder(ShapeId.from("test#ItemHolder"))
                .putMember("shape", polymorphic)
                .build();
        var held = adapterFor(ITEM).fromSmithy(holding(holder, content), holder);
        assertTrue(Document.equals(expected, held.getMember("shape")), held.toString());
    }

    @Test
    void typedOneOfContentUsesWireNames() {
        var typed = Document.of(struct(ITEM, s -> {
            s.writeString(ITEM.member("itemName"), "d");
            s.writeInteger(ITEM.member("count"), 2);
        }));
        var output = adapterFor(ITEM).fromSmithy(typed, polymorphicItem());
        assertTrue(Document.equals(
                Document.ofObject(Map.of("item", Map.of("item_name", "d", "count", 2))),
                output), output.toString());
    }

    @Test
    void renamedOneOfContentRoundTripsExactly() {
        var polymorphic = polymorphic("__type", member("box", BOX.id()));
        var adapting = adapterFor(BOX, ITEM);
        var input = parseMcp("{\"box\":{\"box_item\":{\"item_name\":\"d\",\"count\":2}}}");

        // The handler sees member names.
        var flattened = adapting.toSmithy(input, polymorphic);
        assertTrue(Document.equals(
                Document.ofObject(Map.of("__type", "test#Box", "boxItem", Map.of("itemName", "d", "count", 2))),
                flattened), flattened.toString());

        var output = adapting.fromSmithy(flattened, polymorphic);
        assertTrue(Document.equals(input, output), toMcpJson(output));
    }

    @Test
    void untypedResultPrefersMemberNameOverWireAlias() {
        var polymorphic = polymorphic("__type", member("item", ITEM_ID));
        var adapting = adapterFor(ITEM);

        var both = parseMcp("{\"__type\":\"test#Item\",\"itemName\":\"member\",\"item_name\":\"wire\"}");
        var output = adapting.fromSmithy(both, polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("item", Map.of("item_name", "member"))), output),
                toMcpJson(output));

        var explicitNull = parseMcp("{\"__type\":\"test#Item\",\"itemName\":null,\"item_name\":\"wire\"}");
        // The member name is present, so the alias is not read; what it holds is kept, see below.
        var withoutName = adapting.fromSmithy(explicitNull, polymorphic).getMember("item").asStringMap();
        assertEquals(Set.of("item_name"), withoutName.keySet());
        assertNull(withoutName.get("item_name"));
    }

    @Test
    void variantMembersWithOverlappingNamesAreNeitherBoundNorEmitted() {
        // "a" names one member in the model and another one on the wire, so an untyped document that holds it
        // cannot be read. Both members are left out; the others are not affected.
        var polymorphic = polymorphic("__type", member("overlapped", OVERLAPPED.id()));
        var adapting = adapterFor(OVERLAPPED);
        var expected = Document.ofObject(Map.of("overlapped", Map.of("item_name", "d", "note", "n")));

        var flattened = adapting.toSmithy(parseMcp("{\"overlapped\":{\"item_name\":\"d\",\"note\":\"n\"}}"),
                polymorphic);
        assertTrue(Document.equals(
                Document.ofObject(Map.of("__type", "test#Overlapped", "itemName", "d", "note", "n")),
                flattened), flattened.toString());
        assertNotAvailable("b",
                "Overlapped",
                () -> adapting.toSmithy(parseMcp("{\"overlapped\":{\"b\":\"red\",\"note\":\"n\"}}"), polymorphic));
        assertNotAvailable("a",
                "Overlapped",
                () -> adapting.toSmithy(parseMcp("{\"overlapped\":{\"a\":null}}"), polymorphic));

        for (var content : List.of(
                // Keyed by member name, as a handler writes it, and by wire name, as a backend may.
                "{\"__type\":\"test#Overlapped\",\"a\":\"red\",\"c\":\"blue\",\"itemName\":\"d\",\"note\":\"n\"}",
                "{\"__type\":\"test#Overlapped\",\"b\":\"red\",\"a\":\"blue\",\"item_name\":\"d\",\"note\":\"n\"}")) {
            var output = adapting.fromSmithy(parseMcp(content), polymorphic);
            assertTrue(Document.equals(expected, output), toMcpJson(output));
        }

        var typed = Document.of(struct(OVERLAPPED, s -> {
            s.writeString(OVERLAPPED.member("a"), "red");
            s.writeString(OVERLAPPED.member("c"), "blue");
            s.writeString(OVERLAPPED.member("itemName"), "d");
            s.writeString(OVERLAPPED.member("note"), "n");
        }));
        var fromTyped = adapting.fromSmithy(typed, polymorphic);
        assertTrue(Document.equals(expected, fromTyped), toMcpJson(fromTyped));
        assertNotAvailable("a",
                "Overlapped",
                () -> adapting.toSmithy(Document.of(Map.of("overlapped", typed)), polymorphic));
    }

    @Test
    void discriminatorNamedMemberAlsoCountsForOverlaps() {
        // "type" is left out for its name, and "x" because the wire name of "type" is its member name.
        var mixed = Schema.structureBuilder(ShapeId.from("test#Mixed"))
                .putMember("type", PreludeSchemas.STRING, new JsonNameTrait("x"))
                .putMember("x", PreludeSchemas.STRING, new JsonNameTrait("y"))
                .putMember("rank", PreludeSchemas.INTEGER)
                .build();
        var polymorphic = polymorphic("type", member("mixed", mixed.id()));
        var adapting = adapterFor(mixed);
        assertEquals(Set.of("type", "x"), SmithyDocumentAdapter.omittedMembers(mixed, "type"));

        var flattened = adapting.toSmithy(parseMcp("{\"mixed\":{\"rank\":1}}"), polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("type", "test#Mixed", "rank", 1)), flattened),
                flattened.toString());
        for (var key : List.of("x", "y")) {
            assertNotAvailable(key,
                    "Mixed",
                    () -> adapting.toSmithy(parseMcp("{\"mixed\":{\"" + key + "\":\"v\",\"rank\":1}}"), polymorphic));
        }

        // In a typed document a member named like the discriminator is a member, not the discriminator.
        var typedType = Document.of(struct(mixed, s -> {
            s.writeString(mixed.member("type"), "t");
            s.writeInteger(mixed.member("rank"), 1);
        }));
        assertNotAvailable("type", "Mixed", () -> adapting.toSmithy(typedType, polymorphic));
        assertNotAvailable("type",
                "Mixed",
                () -> adapting.toSmithy(Document.of(Map.of("mixed", typedType)), polymorphic));

        var expected = Document.ofObject(Map.of("mixed", Map.of("rank", 1)));
        for (var content : List.of(
                "{\"type\":\"test#Mixed\",\"x\":\"v\",\"rank\":1}",
                "{\"type\":\"test#Mixed\",\"x\":\"t\",\"y\":\"v\",\"rank\":1}")) {
            var output = adapting.fromSmithy(parseMcp(content), polymorphic);
            assertTrue(Document.equals(expected, output), toMcpJson(output));
        }
        var typed = Document.of(struct(mixed, s -> {
            s.writeString(mixed.member("type"), "t");
            s.writeString(mixed.member("x"), "v");
            s.writeInteger(mixed.member("rank"), 1);
        }));
        var fromTyped = adapting.fromSmithy(typed, polymorphic);
        assertTrue(Document.equals(expected, fromTyped), toMcpJson(fromTyped));
    }

    @Test
    void explicitNullIsKeptWhereContentIsPassedThrough() {
        // A handler can tell a member that was sent as null from one that was not sent, and so can a client.
        var polymorphic = polymorphic("__type", member("item", ITEM_ID), member("stamped", STAMPED.id()));
        var adapting = adapterFor(ITEM, STAMPED);

        var received = adapting.toSmithy(parseMcp("{\"item\":{\"item_name\":null}}"), polymorphic).asStringMap();
        assertEquals(Set.of("__type", "itemName"), received.keySet());
        assertNull(received.get("itemName"));
        var absent = adapting.toSmithy(parseMcp("{\"item\":{\"count\":1}}"), polymorphic).asStringMap();
        assertEquals(Set.of("__type", "count"), absent.keySet());

        var returned = adapting.fromSmithy(parseMcp("{\"__type\":\"test#Item\",\"itemName\":null}"), polymorphic);
        assertEquals("{\"item\":{\"item_name\":null}}", toMcpJson(returned));
        var echoed = adapting.fromSmithy(Document.of(received), polymorphic);
        assertEquals("{\"item\":{\"item_name\":null}}", toMcpJson(echoed));

        // A typed result that writes the member as null.
        var typed = Document.of(struct(ITEM, s -> s.writeNull(ITEM.member("itemName"))));
        assertEquals("{\"item\":{\"item_name\":null}}", toMcpJson(adapting.fromSmithy(typed, polymorphic)));

        // Where types are adapted, content has always been projected onto the members that hold a value.
        var stamped = adapting.toSmithy(parseMcp("{\"stamped\":{\"item_name\":null}}"), polymorphic);
        assertEquals(Set.of("__type"), stamped.asStringMap().keySet());
        assertEquals("{\"stamped\":{}}",
                toMcpJson(adapting.fromSmithy(parseMcp("{\"__type\":\"test#Stamped\",\"itemName\":null}"),
                        polymorphic)));
        var typedStamped = Document.of(struct(STAMPED, s -> s.writeNull(STAMPED.member("itemName"))));
        assertEquals("{\"stamped\":{}}", toMcpJson(adapting.fromSmithy(typedStamped, polymorphic)));
        assertEquals("{}", toMcpJson(adapting.fromSmithy(typedStamped, STAMPED)));
    }

    @Test
    void passedOnContentChecksEverySpelling() {
        // Flattened content is passed on as it is, and a handler reads it by member name: what the member
        // name holds is checked even when the wire name is there as well.
        var renest = Schema.structureBuilder(ShapeId.from("test#Renest"))
                .putMember("inner", OVERLAPPED, new JsonNameTrait("inner_w"))
                .build();
        var polymorphic = polymorphic("__type", member("renest", renest.id()));
        var adapting = adapterFor(OVERLAPPED, renest);

        assertNotAvailable("a",
                "Overlapped",
                () -> adapting.toSmithy(
                        parseMcp("{\"__type\":\"test#Renest\",\"inner_w\":{\"note\":\"n\"},\"inner\":{\"a\":\"x\"}}"),
                        polymorphic));
    }

    @Test
    void boundContentReadsTheWireName() {
        var renest = Schema.structureBuilder(ShapeId.from("test#Renest"))
                .putMember("inner", OVERLAPPED, new JsonNameTrait("inner_w"))
                .build();
        var polymorphic = polymorphic("__type", member("renest", renest.id()));
        var adapting = adapterFor(OVERLAPPED, renest);

        // Wrapped content and arguments are converted, and the handler gets what was bound: the wire name
        // wins and the other name is not read, as for any member, whatever it holds.
        var wrapped = adapting.toSmithy(
                parseMcp("{\"renest\":{\"inner_w\":{\"note\":\"n\"},\"inner\":{\"c\":null}}}"),
                polymorphic);
        assertTrue(Document.equals(
                Document.ofObject(Map.of("__type", "test#Renest", "inner", Map.of("note", "n"))),
                wrapped), wrapped.toString());
        var bound = adapter.toSmithy(parseMcp("{\"inner_w\":{\"note\":\"n\"},\"inner\":\"ignored\"}"), renest);
        assertTrue(Document.equals(Document.ofObject(Map.of("inner", Map.of("note", "n"))), bound),
                bound.toString());
    }

    @Test
    void variantNamedByADiscriminatorIsCheckedNextToAWrapperName() {
        // The inner document is content of an outer one that is flattened already, so it reaches the handler
        // as it is: with a discriminator, and a property that happens to be called like a variant.
        var inner = polymorphic("__type", member("overlapped", OVERLAPPED.id()), member("item", ITEM_ID));
        var outer = Schema.structureBuilder(ShapeId.from("test#Outer"))
                .putMember("inner", inner)
                .build();
        var polymorphic = Schema.createDocument(ShapeId.from("test#PolymorphicOuter"),
                OneOfTrait.builder().discriminator("__type").members(List.of(member("outer", outer.id()))).build());
        var adapting = adapterFor(OVERLAPPED, ITEM, outer);

        assertNotAvailable("a",
                "Overlapped",
                () -> adapting.toSmithy(
                        parseMcp("{\"__type\":\"test#Outer\",\"inner\":"
                                + "{\"__type\":\"test#Overlapped\",\"item\":\"x\",\"a\":\"red\"}}"),
                        polymorphic));

        // A document that is not passed on but bound is read one way only: what its wrapper holds is bound and
        // the handler gets nothing else of it.
        var bound = adapting.toSmithy(
                parseMcp("{\"__type\":\"test#Overlapped\",\"item\":{\"count\":1},\"c\":\"red\"}"),
                inner);
        assertTrue(Document.equals(Document.ofObject(Map.of("__type", "test#Item", "count", 1)), bound),
                bound.toString());
    }

    @Test
    void flattenedContentIsValidatedLikeWrappedContent() {
        // An outer variant that is flattened already is passed on as it is, with everything below it. What the
        // wrapped form would refuse below it is refused all the same.
        var inner = polymorphic("__type", member("item", ITEM_ID), member("stamped", STAMPED.id()));
        var outer = Schema.structureBuilder(ShapeId.from("test#Outer"))
                .putMember("selector", SELECTOR)
                .putMember("inner", inner)
                .build();
        var polymorphic = Schema.createDocument(ShapeId.from("test#PolymorphicOuter"),
                OneOfTrait.builder().discriminator("__type").members(List.of(member("outer", outer.id()))).build());
        var adapting = adapterFor(ITEM, STAMPED, outer);

        var twoMembers = "Union TagSelector accepts exactly one member, but found `by_name` and `byId`";
        for (var refused : List.of(
                // A union with two members.
                Map.entry("\"selector\":{\"byName\":\"n\",\"byId\":\"i\"}", twoMembers),
                Map.entry("\"selector\":{\"by_name\":\"n\",\"byId\":null}", twoMembers),
                // A wrapper with two variants, and one whose discriminator names another variant.
                Map.entry("\"inner\":{\"item\":{\"count\":1},\"stamped\":null}",
                        "Document Polymorphic accepts exactly one member, but found `item` and `stamped`"),
                Map.entry("\"inner\":{\"item\":{\"__type\":\"test#Stamped\",\"count\":1}}",
                        "`__type` is the discriminator of Polymorphic and can only be `test#Item` here"))) {
            var flattened = parseMcp("{\"__type\":\"test#Outer\"," + refused.getKey() + "}");
            assertEquals(refused.getValue(),
                    assertThrows(IllegalArgumentException.class, () -> adapting.toSmithy(flattened, polymorphic))
                            .getMessage());
            var wrapped = parseMcp("{\"outer\":{" + refused.getKey() + "}}");
            assertEquals(refused.getValue(),
                    assertThrows(IllegalArgumentException.class, () -> adapting.toSmithy(wrapped, polymorphic))
                            .getMessage());
        }

        var valid = parseMcp("{\"__type\":\"test#Outer\",\"selector\":{\"byId\":\"i\"},"
                + "\"inner\":{\"__type\":\"test#Item\",\"itemName\":\"d\"}}");
        assertSame(valid, adapting.toSmithy(valid, polymorphic));
    }

    @Test
    void plainFlattenedVariantIsPassedOnUntouched() {
        // Nothing can be refused in a variant without renamed, adapted or left-out members, so it is not read.
        var plain = Schema.structureBuilder(ShapeId.from("test#Plain"))
                .putMember("side", PreludeSchemas.INTEGER)
                .putMember("tags", STRING_LIST)
                .build();
        var polymorphic = polymorphic("__type", member("item", ITEM_ID), member("plain", plain.id()));
        var read = new HashSet<ShapeId>();
        var adapting = new SmithyDocumentAdapter(new SchemaIndex() {
            @Override
            public Schema getSchema(ShapeId id) {
                read.add(id);
                return id.equals(plain.id()) ? plain : ITEM;
            }

            @Override
            public void visit(Consumer<Schema> visitor) {
                visitor.accept(plain);
            }
        });

        var flattened = new CountingDocument(parseMcp("{\"__type\":\"test#Plain\",\"side\":1,\"tags\":\"x\"}"));

        assertSame(flattened, adapting.toSmithy(flattened, polymorphic));
        assertEquals(Set.of(plain.id()), read);
        assertEquals(1, flattened.reads, "the document is read once, to find what it names");
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 16, 64})
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void nestedFlattenedContentIsReadOnce(int depth) {
        // A structure that holds itself, with members that are named on the wire as in the model, one that
        // is adapted so that every level is converted, and one that is called like the wrapper of the
        // document, so that a level can hold a discriminator next to a wrapper name.
        var builder = Schema.structureBuilder(ShapeId.from("test#Node"));
        var node = builder.putMember("child", builder)
                .putMember("when", PreludeSchemas.TIMESTAMP)
                .putMember("link", polymorphicNode())
                .putMember("node", polymorphicNode())
                .build();
        var polymorphic = polymorphicNode();
        var adapting = adapterFor(node);
        var reads = new AtomicInteger();

        // Flattened, and with a flattened document below every level as well.
        Document content = null;
        for (int level = 0; level < depth; level++) {
            var members = new HashMap<String, Document>();
            members.put("__type", Document.of("test#Node"));
            members.put("when", Document.of("2026-01-01T00:00:00Z"));
            if (content != null) {
                // A level held under "link" is read as a document, and holds the next one under "node".
                members.put(List.of("child", "node", "link").get(level % 3), content);
            }
            content = new CountingDocument(Document.of(members), reads);
        }
        var flattened = content;

        assertSame(flattened, adapting.toSmithy(flattened, polymorphic));
        assertTrue(reads.get() <= 3 * depth, depth + " levels were read " + reads.get() + " times");

        // The same content in a wrapper is converted, once as well.
        reads.set(0);
        var wrapped = adapting.toSmithy(Document.of(Map.of("node", flattened)), polymorphic);
        assertEquals("test#Node", wrapped.getMember("__type").asString());
        assertTrue(reads.get() <= 3 * depth, depth + " levels were read " + reads.get() + " times");
    }

    private static Schema polymorphicNode() {
        return polymorphic("__type", member("node", ShapeId.from("test#Node")));
    }

    @Test
    void passedOnVariantMayHaveMembersNamedLikeWrappers() {
        // The inner document names its variant, and that structure has ordinary members that happen to be
        // called like two variants of the document. It is not a wrapper, and is passed on with the outer one.
        var both = Schema.structureBuilder(ShapeId.from("test#Both"))
                .putMember("item", PreludeSchemas.STRING)
                .putMember("both", PreludeSchemas.STRING)
                .putMember("since", PreludeSchemas.TIMESTAMP)
                .build();
        var inner = polymorphic("__type", member("item", ITEM_ID), member("both", both.id()));
        var outer = Schema.structureBuilder(ShapeId.from("test#Outer"))
                .putMember("inner", inner)
                .build();
        var polymorphic = Schema.createDocument(ShapeId.from("test#PolymorphicOuter"),
                OneOfTrait.builder().discriminator("__type").members(List.of(member("outer", outer.id()))).build());
        var adapting = adapterFor(ITEM, both, outer);

        var flattened = parseMcp("{\"__type\":\"test#Outer\",\"inner\":"
                + "{\"__type\":\"test#Both\",\"item\":\"x\",\"both\":\"y\"}}");
        assertSame(flattened, adapting.toSmithy(flattened, polymorphic));

        // A document that is read as a wrapper still takes one variant.
        var wrappers = parseMcp("{\"__type\":\"test#Outer\",\"inner\":{\"item\":{\"count\":1},\"both\":{}}}");
        assertEquals("Document Polymorphic accepts exactly one member, but found `item` and `both`",
                assertThrows(IllegalArgumentException.class, () -> adapting.toSmithy(wrappers, polymorphic))
                        .getMessage());
    }

    @Test
    void unionWithoutAnyAvailableMemberTakesNoValue() {
        // Each member is named on the wire like the other one in the model, so neither is available and the
        // schema of the union accepts nothing: a value for it is refused, whatever it holds.
        var blocked = Schema.unionBuilder(ShapeId.from("test#Blocked"))
                .putMember("archive", PreludeSchemas.STRING, new JsonNameTrait("fetch"))
                .putMember("fetch", PreludeSchemas.STRING, new JsonNameTrait("archive"))
                .build();
        var holder = Schema.structureBuilder(ShapeId.from("test#BlockedHolder"))
                .putMember("blocked", blocked)
                .putMember("note", PreludeSchemas.STRING)
                .build();
        var polymorphic = polymorphic("__type", member("holder", holder.id()));
        var adapting = adapterFor(holder);
        var message = "Union Blocked has no member that is available over MCP";

        assertEquals(message,
                assertThrows(IllegalArgumentException.class,
                        () -> adapter.toSmithy(parseMcp("{\"blocked\":{}}"), holder)).getMessage());
        assertEquals(message,
                assertThrows(IllegalArgumentException.class,
                        () -> adapting.toSmithy(parseMcp("{\"holder\":{\"blocked\":{}}}"), polymorphic))
                        .getMessage());
        assertEquals(message,
                assertThrows(IllegalArgumentException.class,
                        () -> adapting.toSmithy(parseMcp("{\"__type\":\"test#BlockedHolder\",\"blocked\":{}}"),
                                polymorphic))
                        .getMessage());
        var typed = Document.of(struct(blocked, s -> {}));
        assertEquals(message,
                assertThrows(IllegalArgumentException.class, () -> adapter.toSmithy(typed, blocked)).getMessage());

        // A key for it is a value as well, whatever it holds.
        assertEquals(message,
                assertThrows(IllegalArgumentException.class,
                        () -> adapter.toSmithy(parseMcp("{\"blocked\":null,\"note\":\"n\"}"), holder))
                        .getMessage());
        assertEquals(message,
                assertThrows(IllegalArgumentException.class,
                        () -> adapting.toSmithy(
                                parseMcp("{\"__type\":\"test#BlockedHolder\",\"blocked\":null}"),
                                polymorphic))
                        .getMessage());

        // Also as the member a union selects, in a wrapper and in content that is flattened already.
        var choice = Schema.unionBuilder(ShapeId.from("test#Choice"))
                .putMember("blocked", blocked)
                .putMember("plain", PreludeSchemas.STRING)
                .build();
        var choosing = Schema.structureBuilder(ShapeId.from("test#Choosing"))
                .putMember("choice", choice)
                .build();
        var choosingDocument = polymorphic("__type", member("choosing", choosing.id()));
        for (var json : List.of(
                "{\"choosing\":{\"choice\":{\"blocked\":null}}}",
                "{\"__type\":\"test#Choosing\",\"choice\":{\"blocked\":null}}")) {
            assertEquals(message,
                    assertThrows(IllegalArgumentException.class,
                            () -> adapterFor(choosing).toSmithy(parseMcp(json), choosingDocument)).getMessage());
        }

        assertEquals(message,
                assertThrows(SmithyDocumentAdapter.UnavailableResultException.class,
                        () -> adapterFor(choosing).fromSmithy(
                                parseMcp("{\"__type\":\"test#Choosing\",\"choice\":{\"blocked\":null}}"),
                                choosingDocument))
                        .getMessage());

        // A union member that is a plain document can be selected with null, and stays selected both ways.
        var loaded = Schema.unionBuilder(ShapeId.from("test#Loaded"))
                .putMember("payload", PreludeSchemas.DOCUMENT)
                .putMember("plain", PreludeSchemas.STRING)
                .build();
        var loading = Schema.structureBuilder(ShapeId.from("test#Loading"))
                .putMember("choice", loaded)
                .build();
        var loadingDocument = polymorphic("__type", member("loading", loading.id()));
        var sent = parseMcp("{\"loading\":{\"choice\":{\"payload\":null}}}");
        var received = adapterFor(loading).toSmithy(sent, loadingDocument);
        assertEquals(Set.of("payload"), received.getMember("choice").asStringMap().keySet());
        assertNull(received.getMember("choice").asStringMap().get("payload"));
        assertEquals("{\"loading\":{\"choice\":{\"payload\":null}}}",
                toMcpJson(adapterFor(loading).fromSmithy(received, loadingDocument)));

        // And a result cannot hold one, not even an empty one.
        assertEquals(message,
                assertThrows(SmithyDocumentAdapter.UnavailableResultException.class,
                        () -> adapter.fromSmithy(typed, blocked)).getMessage());
        var typedHolder =
                Document.of(struct(holder, s -> s.writeStruct(holder.member("blocked"), struct(blocked, n -> {}))));
        assertEquals(message,
                assertThrows(SmithyDocumentAdapter.UnavailableResultException.class,
                        () -> adapter.fromSmithy(typedHolder, holder)).getMessage());
        var withoutUnion = Document.of(struct(holder, s -> s.writeString(holder.member("note"), "n")));
        assertEquals("{\"note\":\"n\"}", toMcpJson(adapter.fromSmithy(withoutUnion, holder)));

        // A union that is not there is not a value.
        var absent = adapter.toSmithy(parseMcp("{\"note\":\"n\"}"), holder);
        assertTrue(Document.equals(Document.ofObject(Map.of("note", "n")), absent), absent.toString());
        var wrapped = adapting.toSmithy(parseMcp("{\"holder\":{\"note\":\"n\"}}"), polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("__type", "test#BlockedHolder", "note", "n")), wrapped),
                wrapped.toString());
    }

    @Test
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void schemaIsWalkedOncePerQuestion() {
        // A structure that holds itself in many ways and has nothing to adapt: whether it does is found by
        // looking at each shape once. Trying every path through its members would not end.
        var builder = Schema.structureBuilder(ShapeId.from("test#Branching"));
        for (int i = 0; i < 60; i++) {
            builder.putMember("self" + i, builder);
        }
        var branching = builder
                .putMember("many",
                        Schema.listBuilder(ShapeId.from("test#BranchingList")).putMember("member", builder).build())
                .putMember("byKey",
                        Schema.mapBuilder(ShapeId.from("test#BranchingMap"))
                                .putMember("key", PreludeSchemas.STRING)
                                .putMember("value", builder)
                                .build())
                .putMember("note", PreludeSchemas.STRING)
                .build();
        var polymorphic = polymorphic("__type", member("branching", branching.id()));
        var value = parseMcp("{\"note\":\"n\"}");
        var flattened = parseMcp("{\"__type\":\"test#Branching\",\"note\":\"n\"}");
        var wrapped = parseMcp("{\"branching\":{\"note\":\"n\"}}");

        // Each question on an adapter that has not answered it yet: arguments, results, flattened and wrapped
        // content, and content in a result.
        assertSame(value, adapterFor(branching).toSmithy(value, branching));
        assertSame(value, adapterFor(branching).fromSmithy(value, branching));
        assertSame(flattened, adapterFor(branching).toSmithy(flattened, polymorphic));
        assertEquals("n", adapterFor(branching).toSmithy(wrapped, polymorphic).getMember("note").asString());
        assertEquals("{\"branching\":{\"note\":\"n\"}}",
                toMcpJson(adapterFor(branching).fromSmithy(flattened, polymorphic)));

        // Members that share a shape share the answer: a call that holds many of them asks once.
        var fieldsBuilder = Schema.structureBuilder(ShapeId.from("test#Fields"));
        for (int i = 0; i < 200; i++) {
            fieldsBuilder.putMember("field" + i,
                    Schema.structureBuilder(ShapeId.from("test#Leaf" + i))
                            .putMember("value", PreludeSchemas.STRING)
                            .build());
        }
        var fieldsList = Schema.listBuilder(ShapeId.from("test#FieldsList"))
                .putMember("member", fieldsBuilder.build())
                .build();
        var sharingBuilder = Schema.structureBuilder(ShapeId.from("test#Sharing"))
                .putMember("renamed", PreludeSchemas.STRING, new JsonNameTrait("re_named"));
        var supplied = new HashMap<String, Document>();
        for (int i = 0; i < 200; i++) {
            sharingBuilder.putMember("list" + i, fieldsList);
            supplied.put("list" + i, Document.of(List.<Document>of()));
        }
        var sharing = sharingBuilder.build();
        var asking = adapterFor(sharing);
        assertEquals(200, asking.toSmithy(Document.of(supplied), sharing).asStringMap().size());
        // The shared list and the 200 shapes below it are looked at once, not once for each member.
        assertTrue(asking.shapesSearched() <= 1500, "shapes were looked at " + asking.shapesSearched() + " times");

        var branchingAdapter = adapterFor(branching);
        branchingAdapter.toSmithy(value, branching);
        branchingAdapter.fromSmithy(value, branching);
        assertTrue(branchingAdapter.shapesSearched() <= 200,
                "shapes were looked at " + branchingAdapter.shapesSearched() + " times");

        // And with something to find at the end of it.
        var deepBuilder = Schema.structureBuilder(ShapeId.from("test#BranchingAdapted"));
        for (int i = 0; i < 60; i++) {
            deepBuilder.putMember("self" + i, deepBuilder);
        }
        var adapted = deepBuilder.putMember("when", PreludeSchemas.TIMESTAMP).build();
        var converted = adapterFor(adapted).toSmithy(parseMcp("{\"when\":\"2026-01-01T00:00:00Z\"}"), adapted);
        assertEquals(ShapeType.TIMESTAMP, converted.getMember("when").type());
        assertEquals("{\"when\":\"2026-01-01T00:00:00Z\"}",
                toMcpJson(adapterFor(adapted).fromSmithy(converted, adapted)));
    }

    @Test
    void variantsWithOneNameAreMatchedByWhatTheyAdvertise() {
        // The second definition requires a member that is left out, which nobody can send, and the third one
        // has a member that is mirrored into a header and named in the model like the first one's member.
        var byX = Schema.structureBuilder(ShapeId.from("test#ByX"))
                .putMember("x", PreludeSchemas.STRING, new RequiredTrait())
                .build();
        var byY = Schema.structureBuilder(ShapeId.from("test#ByY"))
                .putMember("y", PreludeSchemas.STRING, new RequiredTrait())
                .putMember("marker", PreludeSchemas.STRING, new JsonNameTrait("__type"), new RequiredTrait())
                .build();
        var byHeader = Schema.structureBuilder(ShapeId.from("test#ByHeader"))
                .putMember("x",
                        PreludeSchemas.STRING,
                        new JsonNameTrait("x_value"),
                        new McpHeaderTrait("X"),
                        new RequiredTrait())
                .putMember("z", PreludeSchemas.STRING, new RequiredTrait())
                .build();
        var polymorphic = polymorphic("__type",
                member("entry", byX.id()),
                member("entry", byY.id()),
                member("entry", byHeader.id()));
        var adapting = adapterFor(byX, byY, byHeader);

        var second = adapting.toSmithy(parseMcp("{\"entry\":{\"y\":\"yes\"}}"), polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("__type", "test#ByY", "y", "yes")), second),
                second.toString());

        // Looking at a definition that is not chosen refuses nothing.
        var first = adapting.toSmithy(parseMcp("{\"entry\":{\"x\":\"yes\"}}"), polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("__type", "test#ByX", "x", "yes")), first),
                first.toString());

        // A document member that holds null has a value: null is one of the values a document can be.
        var byPayload = Schema.structureBuilder(ShapeId.from("test#ByPayload"))
                .putMember("payload", PreludeSchemas.DOCUMENT, new RequiredTrait())
                .build();
        var withPayload = adapterFor(byX, byPayload).toSmithy(
                parseMcp("{\"entry\":{\"payload\":null}}"),
                polymorphic("__type", member("entry", byX.id()), member("entry", byPayload.id())));
        assertEquals("test#ByPayload", withPayload.getMember("__type").asString());
        assertEquals(Set.of("__type", "payload"), withPayload.asStringMap().keySet());
        assertNull(withPayload.asStringMap().get("payload"));

        // It comes back as null as well, in what is written.
        var echoed = adapterFor(byX, byPayload).fromSmithy(withPayload,
                polymorphic("__type", member("entry", byX.id()), member("entry", byPayload.id())));
        assertEquals("{\"entry\":{\"payload\":null}}", toMcpJson(echoed));
        assertTrue(echoed.getMember("entry").asStringMap().containsKey("payload"));

        // Only a plain document can be null: one that takes variants holds a variant or is not there.
        var nested = polymorphic("__type", member("x", byX.id()));
        var byVariant = Schema.structureBuilder(ShapeId.from("test#ByVariant"))
                .putMember("payload", nested, new RequiredTrait())
                .build();
        var plainWins = adapterFor(byX, byVariant, byPayload).toSmithy(
                parseMcp("{\"entry\":{\"payload\":null}}"),
                polymorphic("__type", member("entry", byVariant.id()), member("entry", byPayload.id())));
        assertEquals("test#ByPayload", plainWins.getMember("__type").asString());

        // A name that is there shadows the other one, as when the member is bound, also when it holds null.
        var byCode = Schema.structureBuilder(ShapeId.from("test#ByCode"))
                .putMember("code", PreludeSchemas.STRING, new JsonNameTrait("apiCode"), new RequiredTrait())
                .build();
        var byId = Schema.structureBuilder(ShapeId.from("test#ById"))
                .putMember("id", PreludeSchemas.STRING, new RequiredTrait())
                .build();
        var shadowed = adapterFor(byCode, byId).toSmithy(
                parseMcp("{\"entry\":{\"apiCode\":null,\"code\":\"x\",\"id\":\"y\"}}"),
                polymorphic("__type", member("entry", byCode.id()), member("entry", byId.id())));
        assertEquals("test#ById", shadowed.getMember("__type").asString());
        assertEquals("y", shadowed.getMember("id").asString());

        // The chosen one is converted like any other, with everything that refuses.
        var third = adapting.toSmithy(parseMcp("{\"entry\":{\"x_value\":\"h\",\"z\":\"yes\"}}"), polymorphic);
        assertEquals("test#ByHeader", third.getMember("__type").asString());
        assertNotAvailable("marker",
                "ByY",
                () -> adapting.toSmithy(parseMcp("{\"entry\":{\"y\":\"yes\",\"marker\":\"m\"}}"), polymorphic));
    }

    @Test
    void variantsWithOneNameAreToldApartByWhatTheyRequire() {
        var byX = Schema.structureBuilder(ShapeId.from("test#ByX"))
                .putMember("x", PreludeSchemas.STRING, new RequiredTrait())
                .putMember("note", PreludeSchemas.STRING)
                .build();
        var byY = Schema.structureBuilder(ShapeId.from("test#ByY"))
                .putMember("y", PreludeSchemas.STRING, new JsonNameTrait("y_value"), new RequiredTrait())
                .build();
        var polymorphic = polymorphic("__type", member("entry", byX.id()), member("entry", byY.id()));
        var adapting = adapterFor(byX, byY);

        var first = adapting.toSmithy(parseMcp("{\"entry\":{\"x\":\"yes\"}}"), polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("__type", "test#ByX", "x", "yes")), first),
                first.toString());
        var second = adapting.toSmithy(parseMcp("{\"entry\":{\"y_value\":\"yes\"}}"), polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("__type", "test#ByY", "y", "yes")), second),
                second.toString());

        // Content that fits no variant is the first one's, as it always was.
        var neither = adapting.toSmithy(parseMcp("{\"entry\":{\"note\":\"n\"}}"), polymorphic);
        assertEquals("test#ByX", neither.getMember("__type").asString());

        // And so is content that fits several.
        var both = adapting.toSmithy(parseMcp("{\"entry\":{\"x\":\"yes\",\"y_value\":\"yes\"}}"), polymorphic);
        assertEquals("test#ByX", both.getMember("__type").asString());
        assertEquals("yes", both.getMember("x").asString());
    }

    @Test
    void typedWrapperIsReadLikeAnUntypedOne() {
        // A typed structure whose members are named like the variants, and that is no variant itself.
        var wrapper = Schema.structureBuilder(ShapeId.from("test#Wrapper"))
                .putMember("overlapped", OVERLAPPED)
                .putMember("item", ITEM)
                .build();
        var polymorphic = polymorphic("__type", member("overlapped", OVERLAPPED.id()), member("item", ITEM_ID));
        var adapting = adapterFor(OVERLAPPED, ITEM);

        var omitted = Document.of(struct(wrapper,
                s -> s.writeStruct(wrapper.member("overlapped"),
                        struct(OVERLAPPED, n -> n.writeString(OVERLAPPED.member("a"), "red")))));
        assertNotAvailable("a", "Overlapped", () -> adapting.toSmithy(omitted, polymorphic));

        // A second variant counts when it is there, whatever it holds.
        var two = Document.of(struct(wrapper, s -> {
            s.writeStruct(wrapper.member("item"), struct(ITEM, n -> n.writeInteger(ITEM.member("count"), 1)));
            s.writeNull(wrapper.member("overlapped"));
        }));
        var error = assertThrows(IllegalArgumentException.class, () -> adapting.toSmithy(two, polymorphic));
        assertEquals("Document Polymorphic accepts exactly one member, but found `overlapped` and `item`",
                error.getMessage());

        var one = Document.of(struct(wrapper,
                s -> s.writeStruct(wrapper.member("item"),
                        struct(ITEM, n -> n.writeInteger(ITEM.member("count"), 1)))));
        var flattened = adapting.toSmithy(one, polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("__type", "test#Item", "count", 1)), flattened),
                flattened.toString());
    }

    @Test
    void typedStructurePassedAsTheDocumentIsChecked() {
        var polymorphic = polymorphic("__type", member("overlapped", OVERLAPPED.id()), member("gone", MISSING_ID));
        var typed = Document.of(struct(OVERLAPPED, s -> s.writeString(OVERLAPPED.member("a"), "red")));
        // An index that throws for a shape it does not have.
        var adapting = new SmithyDocumentAdapter(new SchemaIndex() {
            @Override
            public Schema getSchema(ShapeId id) {
                if (id.equals(OVERLAPPED.id())) {
                    return OVERLAPPED;
                }
                throw new IllegalArgumentException("No schema found for id `" + id + "`");
            }

            @Override
            public void visit(Consumer<Schema> visitor) {
                visitor.accept(OVERLAPPED);
            }
        });

        assertNotAvailable("a", "Overlapped", () -> adapting.toSmithy(typed, polymorphic));

        var clean = Document.of(struct(OVERLAPPED, s -> s.writeString(OVERLAPPED.member("note"), "n")));
        assertSame(clean, adapting.toSmithy(clean, polymorphic));

        // A variant the index does not have cannot be checked, and is passed on as before.
        var missing = Schema.structureBuilder(MISSING_ID).putMember("a", PreludeSchemas.STRING).build();
        var unknown = Document.of(struct(missing, s -> s.writeString(missing.member("a"), "x")));
        assertSame(unknown, adapting.toSmithy(unknown, polymorphic));
        var flattened = parseMcp("{\"__type\":\"test#Missing\",\"a\":\"x\"}");
        assertSame(flattened, adapting.toSmithy(flattened, polymorphic));
    }

    @Test
    void flattenedOneOfInputIsCheckedLikeWrappedInput() {
        var nesting = Schema.structureBuilder(ShapeId.from("test#Nesting"))
                .putMember("inner", OVERLAPPED)
                .putMember("note", PreludeSchemas.STRING)
                .build();
        var polymorphic = polymorphic("__type",
                member("overlapped", OVERLAPPED.id()),
                member("nesting", nesting.id()));
        var adapting = adapterFor(OVERLAPPED, nesting);

        // Already flattened: the variant is named by the discriminator only, with or without a wrapper key
        // that holds nothing.
        for (var json : List.of(
                "{\"__type\":\"test#Overlapped\",\"a\":\"red\",\"note\":\"n\"}",
                "{\"__type\":\"test#Overlapped\",\"overlapped\":null,\"a\":\"red\"}",
                "{\"__type\":\"test#Nesting\",\"inner\":{\"a\":null,\"note\":\"n\"}}",
                "{\"nesting\":{\"inner\":{\"a\":\"red\"}}}")) {
            assertNotAvailable("a", "Overlapped", () -> adapting.toSmithy(parseMcp(json), polymorphic));
        }

        // Without such a key, and for documents that name no known variant, the document is passed on as it is.
        for (var json : List.of(
                "{\"__type\":\"test#Overlapped\",\"note\":\"n\",\"extra\":1}",
                "{\"__type\":\"test#Nesting\",\"inner\":{\"note\":\"n\",\"extra\":1}}",
                "{\"__type\":\"test#Unknown\",\"a\":\"red\"}",
                "{\"a\":\"red\",\"c\":\"kept\"}")) {
            var document = parseMcp(json);
            assertSame(document, adapting.toSmithy(document, polymorphic));
        }
    }

    @Test
    void typedVariantCannotReuseAnIndexedName() {
        // Between two versions of the model the wire name "old_key" moved from one member to another. The
        // server advertises it for "current", so what the newer member holds is not written under it.
        // The same happened in a nested structure that has no renamed member in the indexed model, so that its
        // typed values would otherwise be left to the codec.
        var indexedPart = Schema.structureBuilder(ShapeId.from("test#VersionedPart"))
                .putMember("current", PreludeSchemas.STRING)
                .build();
        var newerPart = Schema.structureBuilder(indexedPart.id())
                .putMember("extra", PreludeSchemas.STRING, new JsonNameTrait("current"))
                .putMember("fresh", PreludeSchemas.STRING)
                .build();
        var indexedHolder = Schema.structureBuilder(ShapeId.from("test#VersionedHolder"))
                .putMember("part", indexedPart)
                .build();
        var indexed = Schema.structureBuilder(ShapeId.from("test#Versioned"))
                .putMember("current", PreludeSchemas.STRING, new JsonNameTrait("old_key"))
                .putMember("rank", PreludeSchemas.INTEGER)
                .putMember("part", indexedPart)
                .putMember("parts",
                        Schema.listBuilder(ShapeId.from("test#VersionedParts"))
                                .putMember("member", indexedPart)
                                .build())
                .putMember("holder", indexedHolder)
                .putMember("byKey",
                        Schema.mapBuilder(ShapeId.from("test#VersionedPartMap"))
                                .putMember("key", PreludeSchemas.STRING)
                                .putMember("value", indexedPart)
                                .build())
                .build();
        var newerParts = Schema.listBuilder(ShapeId.from("test#VersionedParts"))
                .putMember("member", newerPart)
                .build();
        var newer = Schema.structureBuilder(indexed.id())
                .putMember("current", PreludeSchemas.STRING, new JsonNameTrait("new_key"))
                .putMember("rank", PreludeSchemas.INTEGER)
                .putMember("extra", PreludeSchemas.STRING, new JsonNameTrait("old_key"))
                .putMember("fresh", PreludeSchemas.STRING)
                .putMember("part", newerPart)
                .putMember("parts", newerParts)
                .build();
        var polymorphic = polymorphic("__type", member("versioned", indexed.id()));
        var part = struct(newerPart, s -> {
            s.writeString(newerPart.member("extra"), "from-extra");
            s.writeString(newerPart.member("fresh"), "g");
        });
        var typed = Document.of(struct(newer, s -> {
            s.writeString(newer.member("extra"), "e");
            s.writeString(newer.member("fresh"), "f");
            s.writeStruct(newer.member("part"), part);
            s.writeList(newer.member("parts"),
                    part,
                    1,
                    (item, list) -> list.writeStruct(newerParts.member("member"), item));
        }));

        var output = adapterFor(indexed, indexedPart).fromSmithy(typed, polymorphic);

        assertTrue(Document.equals(
                Document.ofObject(Map.of("versioned",
                        Map.of("fresh", "f", "part", Map.of("fresh", "g"), "parts", List.of(Map.of("fresh", "g"))))),
                output), toMcpJson(output));
        assertFalse(toMcpJson(output).contains("from-extra"), toMcpJson(output));

        // The same through maps that have no schema of their own: a result a handler put together by hand
        // around a typed part.
        var byHand = Document.of(Map.of(
                "__type",
                Document.of("test#Versioned"),
                "holder",
                Document.of(Map.of("part", Document.of(part)))));
        var fromHand = adapterFor(indexed, indexedPart, indexedHolder).fromSmithy(byHand, polymorphic);
        assertEquals("{\"versioned\":{\"holder\":{\"part\":{\"fresh\":\"g\"}}}}", toMcpJson(fromHand));

        // And through a map that is given as a typed object whose members are its entries.
        // The keys of the map are what that object is called on the wire, as the codec would write them.
        var entries = Schema.structureBuilder(ShapeId.from("test#VersionedEntries"))
                .putMember("k", newerPart)
                .putMember("local", newerPart, new JsonNameTrait("customer-key"))
                .build();
        var asObject = Document.of(Map.of(
                "__type",
                Document.of("test#Versioned"),
                "byKey",
                Document.of(struct(entries, s -> {
                    s.writeStruct(entries.member("k"), part);
                    s.writeStruct(entries.member("local"), part);
                }))));
        var fromObject = adapterFor(indexed, indexedPart, indexedHolder).fromSmithy(asObject, polymorphic);
        assertEquals(
                "{\"versioned\":{\"byKey\":{\"k\":{\"fresh\":\"g\"},\"customer-key\":{\"fresh\":\"g\"}}}}",
                toMcpJson(fromObject));
    }

    @Test
    void omittedMemberIsNotConvertedBeforeItIsSkipped() {
        // The member cannot hold the shape ID that the discriminator key repeats.
        var counted = Schema.structureBuilder(ShapeId.from("test#Counted"))
                .putMember("total", PreludeSchemas.BIG_INTEGER, new JsonNameTrait("type"))
                .putMember("rank", PreludeSchemas.INTEGER)
                .build();
        var polymorphic = polymorphic("type", member("counted", counted.id()));

        var flattened = adapterFor(counted)
                .toSmithy(parseMcp("{\"counted\":{\"type\":\"test#Counted\",\"rank\":1}}"), polymorphic);

        assertTrue(Document.equals(Document.ofObject(Map.of("type", "test#Counted", "rank", 1)), flattened),
                flattened.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"item\":{\"__type\":\"test#Other\",\"item_name\":\"d\"}}",
            // Projecting a variant with an adapted type would drop the key before it could be checked.
            "{\"stamped\":{\"__type\":\"test#Item\",\"since\":\"2026-01-01T00:00:00Z\"}}",
            "{\"stamped\":{\"__type\":7}}"
    })
    void variantContentCannotOverwriteTheDiscriminator(String json) {
        var polymorphic = polymorphic("__type", member("item", ITEM_ID), member("stamped", STAMPED.id()));
        var error = assertThrows(IllegalArgumentException.class,
                () -> adapterFor(ITEM, STAMPED).toSmithy(parseMcp(json), polymorphic));
        var variant = json.startsWith("{\"item\"") ? "test#Item" : "test#Stamped";
        assertEquals("`__type` is the discriminator of Polymorphic and can only be `" + variant + "` here",
                error.getMessage());
    }

    @Test
    void variantContentMayRepeatTheDiscriminator() {
        var polymorphic = polymorphic("__type", member("item", ITEM_ID), member("stamped", STAMPED.id()));
        var adapting = adapterFor(ITEM, STAMPED);

        var item = adapting.toSmithy(parseMcp("{\"item\":{\"__type\":\"test#Item\",\"count\":1}}"), polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("__type", "test#Item", "count", 1)), item),
                item.toString());

        var stamped = adapting.toSmithy(
                parseMcp("{\"stamped\":{\"__type\":\"test#Stamped\",\"item_name\":\"d\"}}"),
                polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("__type", "test#Stamped", "itemName", "d")), stamped),
                stamped.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"labeled\":{\"type\":\"blue\"}}",
            "{\"kinded\":{\"type\":\"blue\"}}"
    })
    void variantMemberWithTheNameOfTheDiscriminatorCannotOverwriteIt(String json) {
        var polymorphic = polymorphic("type", member("labeled", LABELED.id()), member("kinded", KINDED.id()));
        var error = assertThrows(IllegalArgumentException.class,
                () -> adapterFor(LABELED, KINDED).toSmithy(parseMcp(json), polymorphic));
        var variant = json.contains("kinded") ? "test#Kinded" : "test#Labeled";
        assertEquals("`type` is the discriminator of Polymorphic and can only be `" + variant + "` here",
                error.getMessage());
    }

    @Test
    void variantMemberWithTheNameOfTheDiscriminatorIsNotBound() {
        var polymorphic = polymorphic("type", member("labeled", LABELED.id()), member("kinded", KINDED.id()));
        var adapting = adapterFor(LABELED, KINDED);

        // The discriminator key holds the shape ID, which is accepted, but it is not the member.
        var flattened = adapting.toSmithy(parseMcp("{\"kinded\":{\"type\":\"test#Kinded\",\"rank\":1}}"),
                polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("type", "test#Kinded", "rank", 1)), flattened),
                flattened.toString());
        assertNull(flattened.getMember("kind"));

        // Any other name of such a member is refused, whatever it holds.
        assertNotAvailable("kind",
                "Kinded",
                () -> adapting.toSmithy(parseMcp("{\"kinded\":{\"kind\":\"k\",\"rank\":1}}"), polymorphic));
        assertNotAvailable("kind",
                "Kinded",
                () -> adapting.toSmithy(
                        parseMcp("{\"kinded\":{\"type\":\"test#Kinded\",\"kind\":null}}"),
                        polymorphic));
        assertNotAvailable("label",
                "Labeled",
                () -> adapting.toSmithy(parseMcp("{\"labeled\":{\"label\":\"test#Labeled\"}}"), polymorphic));
    }

    @Test
    void variantMemberWithTheNameOfTheDiscriminatorIsNotEmitted() {
        var polymorphic = polymorphic("type", member("labeled", LABELED.id()), member("kinded", KINDED.id()));
        var adapting = adapterFor(LABELED, KINDED);
        var expected = Document.ofObject(Map.of("labeled", Map.of("size", 1)));

        var flattened = adapting.toSmithy(parseMcp("{\"labeled\":{\"size\":1}}"), polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("type", "test#Labeled", "size", 1)), flattened),
                flattened.toString());
        var echoed = adapting.fromSmithy(flattened, polymorphic);
        assertTrue(Document.equals(expected, echoed), toMcpJson(echoed));

        var typed = Document.of(struct(LABELED, s -> {
            s.writeString(LABELED.member("type"), "blue");
            s.writeInteger(LABELED.member("size"), 1);
        }));
        var fromTyped = adapting.fromSmithy(typed, polymorphic);
        assertTrue(Document.equals(expected, fromTyped), toMcpJson(fromTyped));

        // The member is named like the discriminator only in JSON.
        var kinded = Document.of(struct(KINDED, s -> s.writeString(KINDED.member("kind"), "k")));
        var fromKinded = adapting.fromSmithy(kinded, polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("kinded", Map.of())), fromKinded),
                toMcpJson(fromKinded));
        var untypedKinded = adapting.fromSmithy(parseMcp("{\"type\":\"test#Kinded\",\"kind\":\"k\"}"),
                polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("kinded", Map.of())), untypedKinded),
                toMcpJson(untypedKinded));
    }

    @Test
    void typedVariantKeepsMembersTheServerDoesNotModel() {
        // The handler was built against a newer model than the one the server indexes.
        var newer = Schema.structureBuilder(ITEM_ID)
                .putMember("itemName", PreludeSchemas.STRING, new JsonNameTrait("item_name"))
                .putMember("count", PreludeSchemas.INTEGER)
                .putMember("addedField", PreludeSchemas.STRING, new JsonNameTrait("added_field"))
                // Added members that go by the name of the discriminator or of a modeled member are not content.
                .putMember("marker", PreludeSchemas.STRING, new JsonNameTrait("__type"))
                .putMember("__type", PreludeSchemas.STRING, new JsonNameTrait("tag"))
                .putMember("item_name", PreludeSchemas.STRING, new JsonNameTrait("other_name"))
                .build();
        var typed = Document.of(struct(newer, s -> {
            s.writeString(newer.member("itemName"), "d");
            s.writeString(newer.member("addedField"), "x");
            s.writeString(newer.member("marker"), "m");
            s.writeString(newer.member("__type"), "t");
            s.writeString(newer.member("item_name"), "o");
        }));

        var output = adapterFor(ITEM).fromSmithy(typed, polymorphicItem());
        assertTrue(Document.equals(
                Document.ofObject(Map.of("item", Map.of("item_name", "d", "added_field", "x"))),
                output), toMcpJson(output));
        assertEquals("{\"item\":{\"item_name\":\"d\",\"added_field\":\"x\"}}", toMcpJson(output));

        var dynamic = StructDocument.of(newer,
                Document.ofObject(Map.of("itemName", "d", "addedField", "x", "marker", "m", "__type", "t")));
        var fromDynamic = adapterFor(ITEM).fromSmithy(dynamic, polymorphicItem());
        assertTrue(Document.equals(output, fromDynamic), toMcpJson(fromDynamic));

        // Arguments: the handler side is keyed by member name.
        var flattened = adapterFor(ITEM).toSmithy(Document.of(Map.of("item", typed)), polymorphicItem());
        assertTrue(Document.equals(
                Document.ofObject(Map.of("__type", "test#Item", "itemName", "d", "addedField", "x")),
                flattened), flattened.toString());
    }

    @Test
    void documentMemberWrapperIsReadAsTheValueItWraps() {
        // A dynamic structure gives the value of a modeled document member a wrapper that reports DOCUMENT.
        var typed = wrapped(Document.of(struct(RENAMED, s -> s.writeString(RENAMED.member("a"), "x"))));
        assertEquals(ShapeType.DOCUMENT, typed.type());
        var fromTyped = adapter.toSmithy(typed, RENAMED);
        assertEquals("x", fromTyped.getMember("a").asString());
        assertNull(fromTyped.getMember("c"));

        var fromUntyped = adapter.toSmithy(wrapped(parseMcp("{\"b\":\"x\"}")), RENAMED);
        assertEquals("x", fromUntyped.getMember("a").asString());
        assertNull(fromUntyped.getMember("c"));
    }

    @Test
    void wrappedVariantKeepsItsVariantKey() {
        var expected = Document.ofObject(Map.of("item", Map.of("item_name", "d")));

        var typed = wrapped(Document.of(struct(ITEM, s -> s.writeString(ITEM.member("itemName"), "d"))));
        var fromTyped = adapterFor(ITEM).fromSmithy(typed, polymorphicItem());
        assertTrue(Document.equals(expected, fromTyped), toMcpJson(fromTyped));

        var untyped = wrapped(parseMcp("{\"__type\":\"test#Item\",\"itemName\":\"d\"}"));
        var fromUntyped = adapterFor(ITEM).fromSmithy(untyped, polymorphicItem());
        assertTrue(Document.equals(expected, fromUntyped), toMcpJson(fromUntyped));
    }

    @Test
    void unknownPropertiesOfRenameOnlyOneOfContentSurvive() {
        var polymorphic = polymorphic("__type", member("box", BOX.id()));
        var adapting = adapterFor(BOX, ITEM);
        var input = parseMcp("{\"box\":{\"box_item\":{\"item_name\":\"d\",\"inner\":[1]},\"extra\":{\"k\":true}}}");

        var flattened = adapting.toSmithy(input, polymorphic);
        assertTrue(Document.equals(
                Document.ofObject(Map.of(
                        "__type",
                        "test#Box",
                        "boxItem",
                        Map.of("itemName", "d", "inner", List.of(1)),
                        "extra",
                        Map.of("k", true))),
                flattened), flattened.toString());

        var output = adapting.fromSmithy(flattened, polymorphic);
        assertTrue(Document.equals(input, output), toMcpJson(output));

        // What a proxied backend returns: wire names and a property the model does not know.
        var backend = parseMcp("{\"__type\":\"test#Box\",\"box_item\":{\"item_name\":\"d\",\"inner\":[1]},"
                + "\"extra\":{\"k\":true}}");
        var fromBackend = adapting.fromSmithy(backend, polymorphic);
        assertTrue(Document.equals(input, fromBackend), toMcpJson(fromBackend));
    }

    @Test
    void ignoredAliasIsNotCopiedAsUnknownProperty() {
        var polymorphic = polymorphicItem();
        var adapting = adapterFor(ITEM);

        var flattened = adapting.toSmithy(parseMcp("{\"item\":{\"item_name\":\"wire\",\"itemName\":\"member\"}}"),
                polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("__type", "test#Item", "itemName", "wire")), flattened),
                flattened.toString());

        var output = adapting.fromSmithy(
                parseMcp("{\"__type\":\"test#Item\",\"itemName\":\"member\",\"item_name\":\"wire\"}"),
                polymorphic);
        assertTrue(Document.equals(Document.ofObject(Map.of("item", Map.of("item_name", "member"))), output),
                toMcpJson(output));
    }

    @Test
    void unknownPropertiesAreStillDroppedWhereTypesAreAdapted() {
        var polymorphic = polymorphic("__type", member("stamped", STAMPED.id()));
        var adapting = adapterFor(STAMPED);

        var flattened = adapting.toSmithy(
                parseMcp("{\"stamped\":{\"item_name\":\"d\",\"since\":\"2026-01-01T00:00:00Z\",\"extra\":1}}"),
                polymorphic);
        assertEquals(Set.of("__type", "itemName", "since"), flattened.asStringMap().keySet());

        var output = adapting.fromSmithy(
                parseMcp("{\"__type\":\"test#Stamped\",\"item_name\":\"d\",\"since\":1767225600,\"extra\":1}"),
                polymorphic);
        assertEquals(Set.of("item_name", "since"), output.getMember("stamped").asStringMap().keySet());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"item\":{\"item_name\":\"d\"},\"swapped\":{\"b\":\"x\"}}",
            "{\"swapped\":{\"b\":\"x\"},\"item\":{\"item_name\":\"d\"}}"
    })
    void oneOfInputWithMoreThanOneVariantIsRejected(String json) {
        var polymorphic = polymorphic("__type", member("item", ITEM_ID), member("swapped", SWAPPED.id()));
        var holder = Schema.structureBuilder(ShapeId.from("test#PolymorphicHolder"))
                .putMember("shape", polymorphic)
                .build();
        var adapting = adapterFor(ITEM, SWAPPED);

        assertThrows(IllegalArgumentException.class, () -> adapting.toSmithy(parseMcp(json), polymorphic));
        assertThrows(IllegalArgumentException.class,
                () -> adapting.toSmithy(parseMcp("{\"shape\":" + json + "}"), holder));
    }

    @Test
    void oneOfInputCountsAVariantThatHoldsNull() {
        var polymorphic = polymorphic("__type", member("item", ITEM_ID), member("swapped", SWAPPED.id()));
        var adapting = adapterFor(ITEM, SWAPPED);

        assertThrows(IllegalArgumentException.class,
                () -> adapting.toSmithy(parseMcp("{\"item\":null,\"swapped\":{\"b\":\"x\"}}"), polymorphic));
        assertThrows(IllegalArgumentException.class,
                () -> adapting.toSmithy(parseMcp("{\"swapped\":{\"b\":\"x\"},\"item\":null}"), polymorphic));

        // A single variant that holds null names no content and is passed on as it is.
        var single = parseMcp("{\"item\":null}");
        assertSame(single, adapting.toSmithy(single, polymorphic));
    }

    @Test
    void headerBoundMemberIsOnlyAcceptedUnderItsWireName() {
        var wire = adapter.toSmithy(parseMcp("{\"tag_region\":\"r\"}"), REGIONAL);
        assertEquals("r", wire.getMember("tagRegion").asString());

        var error = assertThrows(IllegalArgumentException.class,
                () -> adapter.toSmithy(parseMcp("{\"tagRegion\":\"r\"}"), REGIONAL));
        assertEquals("`tagRegion` is mirrored into an HTTP header and is only accepted as `tag_region`",
                error.getMessage());

        var both = adapter.toSmithy(parseMcp("{\"tag_region\":\"wire\",\"tagRegion\":\"alias\"}"), REGIONAL);
        assertEquals("wire", both.getMember("tagRegion").asString());

        assertNull(adapter.toSmithy(parseMcp("{\"tagRegion\":null}"), REGIONAL).getMember("tagRegion"));

        var typed = Document.of(struct(REGIONAL, s -> s.writeString(REGIONAL.member("tagRegion"), "r")));
        assertEquals("r", adapter.toSmithy(typed, REGIONAL).getMember("tagRegion").asString());

        // Members that are not mirrored into a header keep their alias.
        var alias = adapter.toSmithy(parseMcp("{\"tagNames\":[\"a\"]}"), REGIONAL);
        assertEquals(List.of("a"), strings(alias.getMember("tagNames")));
    }

    @Test
    void wireKeyedProjectsTypedDocumentsWithoutChangingValues() {
        var untyped = parseMcp("{\"tag_region\":\"r\"}");
        assertSame(untyped, SmithyDocumentAdapter.wireKeyed(untyped));
        assertEquals("r", SmithyDocumentAdapter.wireKeyed(wrapped(untyped)).getMember("tag_region").asString());
        assertNull(SmithyDocumentAdapter.wireKeyed(null));

        var exact = new BigDecimal("1.0000000000000001");
        var inner = Schema.structureBuilder(ShapeId.from("test#Measure"))
                .putMember("ratioValue", PreludeSchemas.BIG_DECIMAL, new JsonNameTrait("ratio_value"))
                .build();
        var innerList = Schema.listBuilder(ShapeId.from("test#MeasureList")).putMember("member", inner).build();
        var outer = Schema.structureBuilder(ShapeId.from("test#Measured"))
                .putMember("tagRegion", PreludeSchemas.STRING, new JsonNameTrait("tag_region"))
                .putMember("ratioValue", PreludeSchemas.BIG_DECIMAL, new JsonNameTrait("ratio_value"))
                .putMember("allMeasures", innerList, new JsonNameTrait("all_measures"))
                .build();
        var source = Document.ofObject(Map.of(
                "tagRegion",
                "r",
                "ratioValue",
                exact,
                "allMeasures",
                List.of(Map.of("ratioValue", exact))));
        var dynamic = StructDocument.of(outer, source);
        var typed = Document.of(dynamic);
        for (var document : List.of(dynamic, typed, wrapped(dynamic))) {
            var wire = SmithyDocumentAdapter.wireKeyed(document);
            assertEquals(ShapeType.MAP, wire.type());
            assertEquals(Set.of("tag_region", "ratio_value", "all_measures"), wire.asStringMap().keySet());
            assertEquals("r", wire.getMember("tag_region").asString());
            assertEquals(exact, wire.getMember("ratio_value").asBigDecimal());
            var nested = wire.getMember("all_measures").asList().getFirst();
            assertEquals(Set.of("ratio_value"), nested.asStringMap().keySet());
            assertEquals(exact, nested.getMember("ratio_value").asBigDecimal());
        }
    }

    private static final Schema LABELED = Schema.structureBuilder(ShapeId.from("test#Labeled"))
            .putMember("type", PreludeSchemas.STRING, new JsonNameTrait("label"))
            .putMember("size", PreludeSchemas.INTEGER)
            .build();
    private static final Schema KINDED = Schema.structureBuilder(ShapeId.from("test#Kinded"))
            .putMember("kind", PreludeSchemas.STRING, new JsonNameTrait("type"))
            .putMember("rank", PreludeSchemas.INTEGER)
            .build();
    private static final Schema STAMPED = Schema.structureBuilder(ShapeId.from("test#Stamped"))
            .putMember("itemName", PreludeSchemas.STRING, new JsonNameTrait("item_name"))
            .putMember("since", PreludeSchemas.TIMESTAMP)
            .build();
    private static final Schema REGIONAL = Schema.structureBuilder(ShapeId.from("test#Regional"))
            .putMember("tagRegion",
                    PreludeSchemas.STRING,
                    new JsonNameTrait("tag_region"),
                    new McpHeaderTrait("Tag-Region"))
            .putMember("tagNames", STRING_LIST, new JsonNameTrait("tag_names"))
            .build();

    private static Schema polymorphic(String discriminator, OneOfMember... members) {
        return Schema.createDocument(ShapeId.from("test#Polymorphic"),
                OneOfTrait.builder().discriminator(discriminator).members(List.of(members)).build());
    }

    private static Document wrapped(Document value) {
        var holder = Schema.structureBuilder(ShapeId.from("test#DocumentHolder"))
                .putMember("value", PreludeSchemas.DOCUMENT)
                .build();
        return StructDocument.of(holder, Document.of(Map.of("value", value))).getMember("value");
    }

    private static final ShapeId ITEM_ID = ShapeId.from("test#Item");
    private static final Schema ITEM = Schema.structureBuilder(ITEM_ID)
            .putMember("itemName", PreludeSchemas.STRING, new JsonNameTrait("item_name"))
            .putMember("count", PreludeSchemas.INTEGER)
            .build();
    private static final Schema OVERLAPPED = Schema.structureBuilder(ShapeId.from("test#Overlapped"))
            .putMember("a", PreludeSchemas.STRING, new JsonNameTrait("b"))
            .putMember("c", PreludeSchemas.STRING, new JsonNameTrait("a"))
            .putMember("itemName", PreludeSchemas.STRING, new JsonNameTrait("item_name"))
            .putMember("note", PreludeSchemas.STRING)
            .build();
    private static final Schema BOX = Schema.structureBuilder(ShapeId.from("test#Box"))
            .putMember("boxItem", ITEM, new JsonNameTrait("box_item"))
            .build();
    private static final Schema RENAMED = Schema.structureBuilder(ShapeId.from("test#Renamed"))
            .putMember("a", PreludeSchemas.STRING, new JsonNameTrait("b"))
            .putMember("c", PreludeSchemas.STRING, new JsonNameTrait("d"))
            .build();
    private static final Schema CROSSED = Schema.unionBuilder(ShapeId.from("test#Crossed"))
            .putMember("archive", PreludeSchemas.STRING, new JsonNameTrait("lookup"))
            .putMember("fetch", PreludeSchemas.STRING, new JsonNameTrait("archive"))
            .putMember("byId", PreludeSchemas.STRING, new JsonNameTrait("by_id"))
            .build();
    private static final Schema HOLDING = Schema.structureBuilder(ShapeId.from("test#Holding"))
            .putMember("selector", CROSSED)
            .build();
    private static final Schema DUPLICATED = Schema.structureBuilder(ShapeId.from("test#Duplicated"))
            .putMember("first", PreludeSchemas.STRING, new JsonNameTrait("key"))
            .putMember("second", PreludeSchemas.STRING, new JsonNameTrait("key"))
            .putMember("rank", PreludeSchemas.INTEGER)
            .build();
    private static final ShapeId MISSING_ID = ShapeId.from("test#Missing");

    /**
     * A map document that counts how often its entries are asked for.
     */
    private static final class CountingDocument implements Document {
        private final Document delegate;
        private final AtomicInteger total;
        private int reads;

        private CountingDocument(Document delegate) {
            this(delegate, new AtomicInteger());
        }

        private CountingDocument(Document delegate, AtomicInteger total) {
            this.delegate = delegate;
            this.total = total;
        }

        @Override
        public ShapeType type() {
            return delegate.type();
        }

        @Override
        public Map<String, Document> asStringMap() {
            reads++;
            total.incrementAndGet();
            return delegate.asStringMap();
        }

        @Override
        public Document getMember(String memberName) {
            reads++;
            total.incrementAndGet();
            return delegate.getMember(memberName);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            delegate.serializeContents(serializer);
        }
    }

    private static final Schema CHAINED = Schema.structureBuilder(ShapeId.from("test#Chained"))
            .putMember("p", PreludeSchemas.STRING, new JsonNameTrait("q"))
            .putMember("q", PreludeSchemas.STRING, new JsonNameTrait("r"))
            .putMember("r", PreludeSchemas.STRING, new JsonNameTrait("s"))
            .putMember("t", PreludeSchemas.STRING)
            .build();
    private static final Schema SWAPPED = Schema.structureBuilder(ShapeId.from("test#Swapped"))
            .putMember("a", PreludeSchemas.STRING, new JsonNameTrait("b"))
            .putMember("c", PreludeSchemas.STRING, new JsonNameTrait("a"))
            .build();
    private static final Schema SELECTOR = Schema.unionBuilder(ShapeId.from("test#TagSelector"))
            .putMember("byName", PreludeSchemas.STRING, new JsonNameTrait("by_name"))
            .putMember("byId", PreludeSchemas.STRING)
            .build();

    private static Schema polymorphicItem() {
        return Schema.createDocument(ShapeId.from("test#PolymorphicItem"),
                OneOfTrait.builder().discriminator("__type").members(List.of(member("item", ITEM_ID))).build());
    }

    private static SmithyDocumentAdapter adapterFor(Schema... schemas) {
        return new SmithyDocumentAdapter(new SchemaIndex() {
            @Override
            public Schema getSchema(ShapeId id) {
                for (var schema : schemas) {
                    if (schema.id().equals(id)) {
                        return schema;
                    }
                }
                return null;
            }

            @Override
            public void visit(Consumer<Schema> visitor) {
                List.of(schemas).forEach(visitor);
            }
        });
    }
}
