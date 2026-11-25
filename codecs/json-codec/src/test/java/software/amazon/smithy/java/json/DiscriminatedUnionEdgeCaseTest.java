/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.DiscriminatedTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;

/**
 * Edge cases of {@code @discriminated} unions on both JSON backends: member types other than strings, codec
 * settings, a member whose serialized name collides with the discriminator, malformed input, and writer misuse.
 *
 * <p>Values are read into and written from plain maps by a schema-driven reader and writer.
 */
public class DiscriminatedUnionEdgeCaseTest extends ProviderTestBase {

    private static final String NS = "smithy.example#";

    // Record := structure { s, b: Byte, i: Integer, d: BigDecimal, t: Timestamp, blob: Blob, flag: Boolean,
    //                       list: list<String>, map: map<String, Integer>, choice: Choice }
    // Choice := union { a: String, n: Integer }  (a standard union)
    // Event := @discriminated("type") union { record: Record, other: Other }
    // Other := structure { v: String }
    private static final Schema EVENT;

    // Renamed := @discriminated("type") union { renamed: Named @jsonName("R") }
    // Named := structure { name: String @jsonName("Name"), count: Integer @jsonName("Count") }
    private static final Schema RENAMED;

    // Clash := @discriminated("type") union { v: Clashing }
    // Clashing := structure { type: String @jsonName("kind"), size: String }
    private static final Schema CLASH;

    // Holder := structure { event: Event }
    private static final Schema HOLDER;

    static {
        var list = Schema.listBuilder(ShapeId.from(NS + "StringList"));
        list.putMember("member", PreludeSchemas.STRING);
        var map = Schema.mapBuilder(ShapeId.from(NS + "IntMap"));
        map.putMember("key", PreludeSchemas.STRING);
        map.putMember("value", PreludeSchemas.INTEGER);
        var choice = Schema.unionBuilder(ShapeId.from(NS + "Choice"));
        choice.putMember("a", PreludeSchemas.STRING);
        choice.putMember("n", PreludeSchemas.INTEGER);

        var record = Schema.structureBuilder(ShapeId.from(NS + "Record"));
        record.putMember("s", PreludeSchemas.STRING);
        record.putMember("b", PreludeSchemas.BYTE);
        record.putMember("i", PreludeSchemas.INTEGER);
        record.putMember("d", PreludeSchemas.BIG_DECIMAL);
        record.putMember("t", PreludeSchemas.TIMESTAMP);
        record.putMember("blob", PreludeSchemas.BLOB);
        record.putMember("flag", PreludeSchemas.BOOLEAN);
        record.putMember("list", list);
        record.putMember("map", map);
        record.putMember("choice", choice);
        var other = Schema.structureBuilder(ShapeId.from(NS + "Other"));
        other.putMember("v", PreludeSchemas.STRING);
        var event = Schema.unionBuilder(ShapeId.from(NS + "Event"), new DiscriminatedTrait("type"));
        event.putMember("record", record);
        event.putMember("other", other);
        EVENT = event.build();

        var holder = Schema.structureBuilder(ShapeId.from(NS + "Holder"));
        holder.putMember("event", EVENT);
        HOLDER = holder.build();

        var named = Schema.structureBuilder(ShapeId.from(NS + "Named"));
        named.putMember("name", PreludeSchemas.STRING, new JsonNameTrait("Name"));
        named.putMember("count", PreludeSchemas.INTEGER, new JsonNameTrait("Count"));
        var renamed = Schema.unionBuilder(ShapeId.from(NS + "Renamed"), new DiscriminatedTrait("type"));
        renamed.putMember("renamed", named, new JsonNameTrait("R"));
        RENAMED = renamed.build();

        var clashing = Schema.structureBuilder(ShapeId.from(NS + "Clashing"));
        clashing.putMember("type", PreludeSchemas.STRING, new JsonNameTrait("kind"));
        clashing.putMember("size", PreludeSchemas.STRING);
        var clash = Schema.unionBuilder(ShapeId.from(NS + "Clash"), new DiscriminatedTrait("type"));
        clash.putMember("v", clashing);
        CLASH = clash.build();
    }

    // ----- Member types other than strings, in front of and behind the discriminator -----

    private static final List<String> RECORD_FIELDS = List.of(
            "\"s\":\"text\"",
            "\"b\":-7",
            "\"i\":123456",
            "\"d\":1.5",
            "\"t\":1700000000.5",
            "\"blob\":\"aGVsbG8=\"",
            "\"flag\":true",
            "\"list\":[\"x\",\"y\"]",
            "\"map\":{\"one\":1,\"two\":2}",
            "\"choice\":{\"n\":9}");

    private static Map<String, Object> expectedRecord() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("s", "text");
        record.put("b", (byte) -7);
        record.put("i", 123456);
        record.put("d", new BigDecimal("1.5"));
        record.put("t", Instant.ofEpochSecond(1700000000L, 500_000_000L));
        record.put("blob", ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)));
        record.put("flag", true);
        record.put("list", List.of("x", "y"));
        record.put("map", Map.of("one", 1, "two", 2));
        record.put("choice", Map.of("n", 9));
        return Map.of("record", record);
    }

    static List<Arguments> discriminatorPositions() {
        var arguments = new ArrayList<Arguments>();
        for (var provider : providerInstances()) {
            // First, after one field, in the middle, and last (ten fields in front of it).
            for (int position : new int[] {0, 1, 5, RECORD_FIELDS.size()}) {
                arguments.add(Arguments.of(provider, position));
            }
        }
        return arguments;
    }

    private static String recordJson(int discriminatorPosition) {
        var fields = new ArrayList<>(RECORD_FIELDS);
        fields.add(discriminatorPosition, "\"type\":\"record\"");
        return "{" + String.join(",", fields) + "}";
    }

    @ParameterizedTest
    @MethodSource("discriminatorPositions")
    public void everyMemberTypeDecodesTheSameWhereverTheDiscriminatorIs(JsonSerdeProvider provider, int position) {
        var json = recordJson(position);
        assertThat(json, read(provider, EVENT, json), equalTo(expectedRecord()));
    }

    @ParameterizedTest
    @MethodSource("discriminatorPositions")
    public void nestedDiscriminatedUnionDecodesTheSameWhereverTheDiscriminatorIs(
            JsonSerdeProvider provider,
            int position
    ) {
        var json = "{\"event\":" + recordJson(position) + "}";
        assertThat(json, read(provider, HOLDER, json), equalTo(Map.of("event", expectedRecord())));
    }

    // ----- Codec settings -----

    @ParameterizedTest
    @MethodSource("providers")
    public void jsonNameAppliesToVariantFieldsButNotToTheDiscriminatorValue(JsonSerdeProvider provider) {
        UnaryOperator<JsonCodec.Builder> useJsonName = builder -> builder.useJsonName(true);
        Map<String, Object> expected = Map.of("renamed", Map.of("name", "n1", "count", 3));
        for (var json : List.of(
                "{\"type\":\"renamed\",\"Name\":\"n1\",\"Count\":3}",
                "{\"Name\":\"n1\",\"Count\":3,\"type\":\"renamed\"}")) {
            assertThat(json, read(provider, RENAMED, json, useJsonName), equalTo(expected));
        }
        assertThat(
                write(provider, RENAMED, expected, useJsonName),
                equalTo("{\"type\":\"renamed\",\"Name\":\"n1\",\"Count\":3}"));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void unknownDiscriminatorValueIsAnErrorWhenUnknownUnionMembersAreForbidden(JsonSerdeProvider provider) {
        UnaryOperator<JsonCodec.Builder> forbid = builder -> builder.forbidUnknownUnionMembers(true);
        for (var json : List.of(
                "{\"type\":\"triangle\",\"v\":\"x\"}",
                "{\"v\":\"x\",\"type\":\"triangle\"}")) {
            var e = assertThrows(SerializationException.class, () -> read(provider, EVENT, json, forbid), json);
            assertThat(json, e.getMessage(), containsString("triangle"));
        }
        assertThat(
                read(provider, EVENT, "{\"v\":\"x\",\"type\":\"other\"}", forbid),
                equalTo(Map.of("other", Map.of("v", "x"))));
    }

    // ----- A member whose serialized name collides with the discriminator -----

    // A suppressible collision: the member is named like the discriminator but renamed with @jsonName, so the
    // names only collide when the codec ignores @jsonName. The discriminator must never be read as the member.

    @ParameterizedTest
    @MethodSource("providers")
    public void collidingMemberDoesNotReceiveTheDiscriminator(JsonSerdeProvider provider) {
        for (var json : List.of(
                "{\"type\":\"v\",\"size\":\"1\"}",
                "{\"size\":\"1\",\"type\":\"v\"}")) {
            assertThat(json, read(provider, CLASH, json), equalTo(Map.of("v", Map.of("size", "1"))));
            assertThat(json, readThroughDocument(provider, CLASH, json), equalTo(Map.of("v", Map.of("size", "1"))));
        }
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void renamedMemberIsReadWhenTheCodecUsesJsonName(JsonSerdeProvider provider) {
        UnaryOperator<JsonCodec.Builder> useJsonName = builder -> builder.useJsonName(true);
        for (var json : List.of(
                "{\"type\":\"v\",\"kind\":\"k\",\"size\":\"1\"}",
                "{\"kind\":\"k\",\"size\":\"1\",\"type\":\"v\"}")) {
            assertThat(json,
                    read(provider, CLASH, json, useJsonName),
                    equalTo(Map.of("v", Map.of("type", "k", "size", "1"))));
        }
    }

    // ----- Unknown members -----

    @ParameterizedTest
    @MethodSource("providers")
    public void unknownMembersAreReportedTheSameWhereverTheDiscriminatorIs(JsonSerdeProvider provider) {
        var first = unknownMembersOfVariant(provider, "{\"type\":\"other\",\"extra\":null,\"more\":1,\"v\":\"x\"}");
        var last = unknownMembersOfVariant(provider, "{\"extra\":null,\"more\":1,\"v\":\"x\",\"type\":\"other\"}");
        assertThat(last, equalTo(first));
    }

    private static List<String> unknownMembersOfVariant(JsonSerdeProvider provider, String json) {
        List<String> unknown = new ArrayList<>();
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
            de.readStruct(EVENT, unknown, (state, member, memberDe) -> {
                memberDe.readStruct(member, state, new ShapeDeserializer.StructMemberConsumer<List<String>>() {
                    @Override
                    public void accept(List<String> s, Schema m, ShapeDeserializer d) {
                        d.readString(m);
                    }

                    @Override
                    public void unknownMember(List<String> s, String memberName) {
                        s.add(memberName);
                    }
                });
            });
            de.close();
        }
        return unknown;
    }

    // ----- Malformed input -----

    @ParameterizedTest
    @MethodSource("malformedInputs")
    public void malformedInputIsRejected(JsonSerdeProvider provider, String json) {
        assertThrows(SerializationException.class, () -> read(provider, EVENT, json), json);
    }

    static List<Arguments> malformedInputs() {
        var inputs = List.of(
                // Around a discriminator that comes first.
                "{\"type\":\"other\" \"v\":\"x\"}",
                "{\"type\":\"other\",,\"v\":\"x\"}",
                "{\"type\":\"other\",\"v\":\"x\",}",
                "{\"type\":\"other\",\"v\":}",
                "{\"type\":\"other\",\"v\":\"x\"",
                "{\"type\":\"other\",\"v\":\"x\"}}",
                "{\"type\" \"other\",\"v\":\"x\"}",
                "{\"type\":\"other\",\"bogus\":tru,\"v\":\"x\"}",
                // Before a discriminator that comes later.
                "{\"v\":\"x\" \"type\":\"other\"}",
                "{\"v\":\"x\",,\"type\":\"other\"}",
                "{\"bogus\":tru,\"type\":\"other\",\"v\":\"x\"}",
                "{\"bogus\":[1},\"type\":\"other\",\"v\":\"x\"}",
                "{\"bogus\":{\"a\":1,},\"type\":\"other\",\"v\":\"x\"}",
                "{\"bogus\":\"\\q\",\"type\":\"other\",\"v\":\"x\"}",
                "{\"v\":\"x\",\"type\":\"other\"",
                "{\"v\":\"x\",\"type\":\"other\",}",
                "{\"v\":\"x\",\"type\":}");
        return providerInstances().stream()
                .flatMap(provider -> inputs.stream().map(json -> Arguments.of(provider, json)))
                .toList();
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void variantNotReadAsAStructureIsAnError(JsonSerdeProvider provider) {
        for (var json : List.of("{\"type\":\"other\",\"v\":\"x\"}", "{\"v\":\"x\",\"type\":\"other\"}")) {
            var e = assertThrows(SerializationException.class, () -> {
                try (var codec = codec(provider)) {
                    var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
                    // The consumer resolves the variant but never reads it.
                    de.readStruct(EVENT, null, (state, member, memberDe) -> {});
                }
            }, json);
            assertThat(json, e.getMessage(), containsString("not read as a structure"));
        }
    }

    // ----- Writer misuse -----

    @ParameterizedTest
    @MethodSource("providers")
    public void writingAUnionWithNoKnownMemberIsAnError(JsonSerdeProvider provider) {
        // Like the unknown variant of a generated union, which has no member to write.
        Map<String, Object> holder = Map.of("event", Map.of());
        var e = assertThrows(SerializationException.class, () -> write(provider, HOLDER, holder));
        assertThat(e.getMessage(), containsString("without a known member set"));
    }

    @ParameterizedTest
    @MethodSource("providers")
    public void writingAUnionWithTwoMembersIsAnError(JsonSerdeProvider provider) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("record", Map.of("s", "text"));
        event.put("other", Map.of("v", "x"));
        var e = assertThrows(SerializationException.class, () -> write(provider, EVENT, event));
        assertThat(e.getMessage(), containsString("only one member of a union can be set"));
    }

    // ----- Schema-driven reading and writing of plain maps -----

    private static Object read(JsonSerdeProvider provider, Schema schema, String json) {
        return read(provider, schema, json, UnaryOperator.identity());
    }

    private static Object read(
            JsonSerdeProvider provider,
            Schema schema,
            String json,
            UnaryOperator<JsonCodec.Builder> configure
    ) {
        try (var codec = configure.apply(codecBuilder(provider)).build()) {
            var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
            var result = readValue(de, schema);
            de.close();
            return result;
        }
    }

    private static Object readThroughDocument(JsonSerdeProvider provider, Schema schema, String json) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
            var document = de.readDocument();
            de.close();
            return readValue(document.createDeserializer(), schema);
        }
    }

    private static Object readValue(ShapeDeserializer de, Schema schema) {
        return switch (schema.type()) {
            case STRING -> de.readString(schema);
            case BYTE -> de.readByte(schema);
            case INTEGER -> de.readInteger(schema);
            case BOOLEAN -> de.readBoolean(schema);
            case BIG_DECIMAL -> de.readBigDecimal(schema);
            case TIMESTAMP -> de.readTimestamp(schema);
            case BLOB -> de.readBlob(schema);
            case LIST -> {
                List<Object> result = new ArrayList<>();
                de.readList(schema, result, (state, d) -> state.add(readValue(d, schema.listMember())));
                yield result;
            }
            case MAP -> {
                Map<String, Object> result = new LinkedHashMap<>();
                de.readStringMap(schema,
                        result,
                        (state, key, d) -> state.put(key, readValue(d, schema.mapValueMember())));
                yield result;
            }
            case STRUCTURE, UNION -> {
                Map<String, Object> result = new LinkedHashMap<>();
                de.readStruct(schema, result, (state, member, memberDe) -> {
                    state.put(member.memberName(), readValue(memberDe, member));
                });
                yield result;
            }
            default -> throw new IllegalArgumentException("Unsupported type " + schema.type());
        };
    }

    private static String write(JsonSerdeProvider provider, Schema schema, Map<String, Object> value) {
        return write(provider, schema, value, UnaryOperator.identity());
    }

    private static String write(
            JsonSerdeProvider provider,
            Schema schema,
            Map<String, Object> value,
            UnaryOperator<JsonCodec.Builder> configure
    ) {
        try (
                var codec = configure.apply(codecBuilder(provider)).build();
                var output = new ByteArrayOutputStream();
                var serializer = codec.createSerializer(output)) {
            new MapStruct(schema, value).serialize(serializer);
            serializer.flush();
            return output.toString(StandardCharsets.UTF_8);
        } catch (SerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Serializes a map according to a schema, in schema member order. Supports strings and nested shapes. */
    private record MapStruct(Schema schema, Map<String, Object> values) implements SerializableStruct {
        @Override
        @SuppressWarnings("unchecked")
        public void serializeMembers(ShapeSerializer serializer) {
            for (Schema member : schema.members()) {
                Object value = values.get(member.memberName());
                if (value == null) {
                    continue;
                }
                switch (member.type()) {
                    case STRING -> serializer.writeString(member, (String) value);
                    case INTEGER -> serializer.writeInteger(member, (Integer) value);
                    case STRUCTURE, UNION -> serializer.writeStruct(
                            member,
                            new MapStruct(member.memberTarget(), (Map<String, Object>) value));
                    default -> throw new IllegalArgumentException("Unsupported type " + member.type());
                }
            }
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }
}
