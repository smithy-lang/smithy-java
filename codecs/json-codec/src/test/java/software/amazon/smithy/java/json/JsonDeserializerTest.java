/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.model.traits.Trait;

public class JsonDeserializerTest extends ProviderTestBase {
    @PerProvider
    public void detectsUnclosedStructureObject(JsonSerdeProvider provider) {
        Set<String> members = new LinkedHashSet<>();

        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useJsonName(true).build()) {
                var de = codec.createDeserializer("{\"name\":\"Sam\"".getBytes(StandardCharsets.UTF_8));
                de.readStruct(JsonTestData.BIRD, members, (memberResult, member, deser) -> {
                    memberResult.add(member.memberName());
                });
            }
        });

        assertThat(members, contains("name"));
    }

    @PerProvider
    public void deserializesByte(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readByte(PreludeSchemas.BYTE), is((byte) 1));
        }
    }

    @PerProvider
    public void deserializesShort(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readShort(PreludeSchemas.SHORT), is((short) 1));
        }
    }

    @PerProvider
    public void deserializesInteger(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readInteger(PreludeSchemas.INTEGER), is(1));
        }
    }

    @PerProvider
    public void deserializesLong(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readLong(PreludeSchemas.LONG), is(1L));
        }
    }

    @PerProvider
    public void deserializesFloat(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readFloat(PreludeSchemas.FLOAT), is(1.0f));
            de = codec.createDeserializer("\"NaN\"".getBytes(StandardCharsets.UTF_8));
            assertTrue(Float.isNaN(de.readFloat(PreludeSchemas.FLOAT)));
            de = codec.createDeserializer("\"Infinity\"".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readFloat(PreludeSchemas.FLOAT), is(Float.POSITIVE_INFINITY));
            de = codec.createDeserializer("\"-Infinity\"".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readFloat(PreludeSchemas.FLOAT), is(Float.NEGATIVE_INFINITY));
        }
    }

    @PerProvider
    public void normalFloatsCannotBeStrings(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("\"1\"".getBytes(StandardCharsets.UTF_8));
            Assertions.assertThrows(SerializationException.class, () -> {
                de.readFloat(PreludeSchemas.FLOAT);
            });
        }
    }

    @PerProvider
    public void deserializesDouble(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readDouble(PreludeSchemas.DOUBLE), is(1.0));
            de = codec.createDeserializer("\"NaN\"".getBytes(StandardCharsets.UTF_8));
            assertTrue(Double.isNaN(de.readDouble(PreludeSchemas.DOUBLE)));
            de = codec.createDeserializer("\"Infinity\"".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readDouble(PreludeSchemas.DOUBLE), is(Double.POSITIVE_INFINITY));
            de = codec.createDeserializer("\"-Infinity\"".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readDouble(PreludeSchemas.DOUBLE), is(Double.NEGATIVE_INFINITY));
        }
    }

    @PerProvider
    public void normalDoublesCannotBeStrings(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("\"1\"".getBytes(StandardCharsets.UTF_8));
            Assertions.assertThrows(SerializationException.class, () -> {
                de.readDouble(PreludeSchemas.DOUBLE);
            });
        }
    }

    @PerProvider
    public void deserializesBigInteger(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readBigInteger(PreludeSchemas.BIG_INTEGER), is(BigInteger.ONE));
        }
    }

    @PerProvider
    public void deserializesBigIntegerOnlyFromRawNumbersByDefault(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("\"1\"".getBytes(StandardCharsets.UTF_8));
            Assertions.assertThrows(SerializationException.class, () -> de.readBigInteger(PreludeSchemas.BIG_INTEGER));
        }
    }

    @PerProvider
    public void deserializesBigDecimal(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readBigDecimal(PreludeSchemas.BIG_DECIMAL), is(BigDecimal.ONE));
        }
    }

    @PerProvider
    public void deserializesBigDecimalOnlyFromRawNumbersByDefault(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("\"1\"".getBytes(StandardCharsets.UTF_8));
            Assertions.assertThrows(SerializationException.class, () -> de.readBigDecimal(PreludeSchemas.BIG_DECIMAL));
        }
    }

    @PerProvider
    public void deserializesTimestamp(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var sink = new ByteArrayOutputStream();
            try (var ser = codec.createSerializer(sink)) {
                ser.writeTimestamp(PreludeSchemas.TIMESTAMP, Instant.EPOCH);
            }

            var de = codec.createDeserializer(sink.toByteArray());
            assertThat(de.readTimestamp(PreludeSchemas.TIMESTAMP), equalTo(Instant.EPOCH));
        }
    }

    @PerProvider
    public void deserializesBlob(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var str = "foo";
            var expected = Base64.getEncoder().encodeToString(str.getBytes());
            var de = codec.createDeserializer(("\"" + expected + "\"").getBytes(StandardCharsets.UTF_8));
            assertThat(de.readBlob(PreludeSchemas.BLOB).array(), equalTo(str.getBytes(StandardCharsets.UTF_8)));
        }
    }

    @PerProvider
    public void deserializesBoolean(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("true".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readBoolean(PreludeSchemas.BOOLEAN), is(true));
        }
    }

    @PerProvider
    public void deserializesString(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("\"foo\"".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readString(PreludeSchemas.STRING), equalTo("foo"));
        }
    }

    @PerProvider
    public void deserializesList(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("[\"foo\",\"bar\"]".getBytes(StandardCharsets.UTF_8));
            List<String> values = new ArrayList<>();

            de.readList(PreludeSchemas.DOCUMENT, null, (ignore, firstList) -> {
                values.add(firstList.readString(PreludeSchemas.STRING));
            });

            assertThat(values, equalTo(List.of("foo", "bar")));
        }
    }

    @PerProvider
    public void deserializesEmptyList(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("[]".getBytes(StandardCharsets.UTF_8));
            List<String> values = new ArrayList<>();

            de.readList(PreludeSchemas.DOCUMENT, null, (ignore, listDe) -> {
                values.add(listDe.readString(PreludeSchemas.STRING));
            });

            assertThat(values, hasSize(0));
        }
    }

    @PerProvider
    public void throwsWhenReadListGetsObject(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("{\"foo\":\"bar\"}".getBytes(StandardCharsets.UTF_8));
            List<String> values = new ArrayList<>();

            var e = Assertions.assertThrows(SerializationException.class, () -> {
                de.readList(PreludeSchemas.DOCUMENT, values, (list, listDe) -> {
                    list.add(listDe.readString(PreludeSchemas.STRING));
                });
            });

            assertThat(e.getMessage(), containsString("Expected a list"));
        }
    }

    @PerProvider
    public void throwsWhenReadListGetsString(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("\"not a list\"".getBytes(StandardCharsets.UTF_8));
            List<String> values = new ArrayList<>();

            var e = Assertions.assertThrows(SerializationException.class, () -> {
                de.readList(PreludeSchemas.DOCUMENT, values, (list, listDe) -> {
                    list.add(listDe.readString(PreludeSchemas.STRING));
                });
            });

            assertThat(e.getMessage(), containsString("Expected a list"));
        }
    }

    @PerProvider
    public void throwsWhenReadListGetsEmptyObject(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("{}".getBytes(StandardCharsets.UTF_8));
            List<String> values = new ArrayList<>();

            var e = Assertions.assertThrows(SerializationException.class, () -> {
                de.readList(PreludeSchemas.DOCUMENT, values, (list, listDe) -> {
                    list.add(listDe.readString(PreludeSchemas.STRING));
                });
            });

            assertThat(e.getMessage(), containsString("Expected a list"));
        }
    }

    @PerProvider
    public void throwsOnUnfinishedList(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("[{}".getBytes(StandardCharsets.UTF_8));
            List<String> values = new ArrayList<>();

            var e = Assertions.assertThrows(SerializationException.class, () -> {
                de.readList(PreludeSchemas.DOCUMENT, values, (list, listDe) -> {
                    list.add(listDe.readString(PreludeSchemas.STRING));
                });
            });

            assertThat(e.getMessage(),
                    anyOf(
                            equalTo("Expected end of list, but found Object value"),
                            containsString("Expected")));
        }
    }

    @PerProvider
    public void deserializesMap(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("{\"foo\":\"bar\",\"baz\":\"bam\"}".getBytes(StandardCharsets.UTF_8));
            Map<String, String> result = new LinkedHashMap<>();

            de.readStringMap(PreludeSchemas.DOCUMENT, result, (map, key, mapde) -> {
                map.put(key, mapde.readString(PreludeSchemas.STRING));
            });

            assertThat(result.values(), hasSize(2));
            assertThat(result, hasKey("foo"));
            assertThat(result, hasKey("baz"));
            assertThat(result.get("foo"), equalTo("bar"));
            assertThat(result.get("baz"), equalTo("bam"));
        }
    }

    @PerProvider
    public void deserializesStruct(JsonSerdeProvider provider) {
        try (var codec = codecBuilder(provider).useJsonName(true).build()) {
            var de = codec.createDeserializer("{\"name\":\"Sam\",\"Color\":\"red\"}".getBytes(StandardCharsets.UTF_8));
            Set<String> members = new LinkedHashSet<>();

            de.readStruct(JsonTestData.BIRD, members, (memberResult, member, deser) -> {
                memberResult.add(member.memberName());
                switch (member.memberName()) {
                    case "name" -> assertThat(deser.readString(JsonTestData.BIRD.member("name")), equalTo("Sam"));
                    case "color" -> assertThat(deser.readString(JsonTestData.BIRD.member("color")), equalTo("red"));
                    default -> throw new IllegalStateException("Unexpected member: " + member);
                }
            });

            assertThat(members, contains("name", "color"));
        }
    }

    @PerProvider
    public void deserializesUnion(JsonSerdeProvider provider) {
        try (var codec = codecBuilder(provider).useJsonName(true).build()) {
            var de = codec.createDeserializer("{\"booleanValue\":true}".getBytes(StandardCharsets.UTF_8));
            Set<String> members = new LinkedHashSet<>();

            de.readStruct(JsonTestData.UNION, members, new ShapeDeserializer.StructMemberConsumer<>() {
                @Override
                public void accept(Set<String> memberResult, Schema member, ShapeDeserializer deser) {
                    memberResult.add(member.memberName());
                    if (member.memberName().equals("booleanValue")) {
                        assertThat(deser.readBoolean(JsonTestData.UNION.member("booleanValue")), equalTo(true));
                    } else {
                        throw new IllegalStateException("Unexpected member: " + member);
                    }
                }

                @Override
                public void unknownMember(Set<String> state, String memberName) {
                    Assertions.fail("Should not have detected an unknown member: " + memberName);
                }
            });

            assertThat(members, contains("booleanValue"));
        }
    }

    @PerProvider
    public void deserializesUnknownUnion(JsonSerdeProvider provider) {
        try (var codec = codecBuilder(provider).useJsonName(true).build()) {
            var de = codec.createDeserializer("{\"totallyUnknown!\":3.14}".getBytes(StandardCharsets.UTF_8));
            Set<String> members = new LinkedHashSet<>();

            AtomicReference<String> unknownSet = new AtomicReference<>();
            de.readStruct(JsonTestData.UNION, members, new ShapeDeserializer.StructMemberConsumer<>() {
                @Override
                public void accept(Set<String> state, Schema memberSchema, ShapeDeserializer memberDeserializer) {
                    Assertions.fail("Unexpected member: " + memberSchema);
                }

                @Override
                public void unknownMember(Set<String> state, String memberName) {
                    unknownSet.set(memberName);
                }
            });

            assertThat(unknownSet.get(), equalTo("totallyUnknown!"));
        }
    }

    @PerProvider
    public void skipsUnknownMembers(JsonSerdeProvider provider) {
        try (var codec = codecBuilder(provider).useJsonName(true).build()) {
            var de = codec.createDeserializer(
                    "{\"name\":\"Sam\",\"Ignore\":[1,2,3],\"Color\":\"rainbow\"}".getBytes(StandardCharsets.UTF_8));
            Set<String> members = new LinkedHashSet<>();

            de.readStruct(JsonTestData.BIRD, members, (memberResult, member, deser) -> {
                memberResult.add(member.memberName());
                switch (member.memberName()) {
                    case "name" -> assertThat(deser.readString(JsonTestData.BIRD.member("name")), equalTo("Sam"));
                    case "color" -> assertThat(deser.readString(JsonTestData.BIRD.member("color")), equalTo("rainbow"));
                    default -> throw new IllegalStateException("Unexpected member: " + member);
                }
            });

            assertThat(members, contains("name", "color"));
        }
    }

    @ParameterizedTest
    @MethodSource("deserializesBirdWithJsonNameOrNotSource")
    public void deserializesBirdWithJsonNameOrNot(JsonSerdeProvider provider, boolean useJsonName, String input) {
        try (var codec = codecBuilder(provider).useJsonName(useJsonName).build()) {
            var de = codec.createDeserializer(input.getBytes(StandardCharsets.UTF_8));
            Set<String> members = new LinkedHashSet<>();
            de.readStruct(JsonTestData.BIRD, members, (memberResult, member, deser) -> {
                memberResult.add(member.memberName());
                switch (member.memberName()) {
                    case "name" -> assertThat(deser.readString(JsonTestData.BIRD.member("name")), equalTo("Sam"));
                    case "color" -> assertThat(deser.readString(JsonTestData.BIRD.member("color")), equalTo("red"));
                    default -> throw new IllegalStateException("Unexpected member: " + member);
                }
            });
            assertThat(members, contains("name", "color"));
        }
    }

    public static List<Arguments> deserializesBirdWithJsonNameOrNotSource() {
        var testCases = List.of(
                Arguments.of(true, "{\"name\":\"Sam\",\"Color\":\"red\"}"),
                Arguments.of(false, "{\"name\":\"Sam\",\"color\":\"red\"}"));

        // Cross-product with providers
        var result = new ArrayList<Arguments>();
        for (var provider : providers()) {
            for (var testCase : testCases) {
                result.add(Arguments.of(
                        provider.get()[0],
                        testCase.get()[0],
                        testCase.get()[1]));
            }
        }
        return result;
    }

    @PerProvider
    public void readsDocuments(JsonSerdeProvider provider) {
        var json = "{\"name\":\"Sam\",\"color\":\"red\"}".getBytes(StandardCharsets.UTF_8);

        try (var codec = codec(provider)) {
            var de = codec.createDeserializer(json);
            var document = de.readDocument();

            assertThat(document.type(), is(ShapeType.MAP));
            var map = document.asStringMap();
            assertThat(map.values(), hasSize(2));
            assertThat(map.get("name").asString(), equalTo("Sam"));
            assertThat(map.get("color").asString(), equalTo("red"));
        }
    }

    @ParameterizedTest
    @MethodSource("deserializesWithTimestampFormatSource")
    public void deserializesWithTimestampFormat(
            JsonSerdeProvider provider,
            boolean useTrait,
            TimestampFormatTrait trait,
            TimestampFormatter defaultFormat,
            String json
    ) {
        Trait[] traits = trait == null ? new Trait[0] : new Trait[] {trait};
        var schema = Schema.createTimestamp(ShapeId.from("smithy.foo#Time"), traits);

        var builder = codecBuilder(provider).useTimestampFormat(useTrait);
        if (defaultFormat != null) {
            builder.defaultTimestampFormat(defaultFormat);
        }

        try (var codec = builder.build()) {
            var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
            assertThat(de.readTimestamp(schema), equalTo(Instant.EPOCH));
        }
    }

    public static List<Arguments> deserializesWithTimestampFormatSource() {
        var epochSeconds = Double.toString(((double) Instant.EPOCH.toEpochMilli()) / 1000);

        var testCases = List.of(
                // boolean useTrait, TimestampFormatTrait trait, TimestampFormatter defaultFormat, String json
                Arguments.of(false, null, null, epochSeconds),
                Arguments.of(false, new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS), null, epochSeconds),
                Arguments.of(
                        false,
                        new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS),
                        TimestampFormatter.Prelude.EPOCH_SECONDS,
                        epochSeconds),
                Arguments.of(
                        true,
                        new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS),
                        TimestampFormatter.Prelude.EPOCH_SECONDS,
                        epochSeconds),
                Arguments.of(true, new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS), null, epochSeconds),
                Arguments.of(
                        true,
                        new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS),
                        TimestampFormatter.Prelude.DATE_TIME,
                        epochSeconds),
                Arguments.of(
                        false,
                        new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS),
                        TimestampFormatter.Prelude.DATE_TIME,
                        "\"" + Instant.EPOCH + "\""),
                Arguments.of(
                        true,
                        new TimestampFormatTrait(TimestampFormatTrait.DATE_TIME),
                        TimestampFormatter.Prelude.EPOCH_SECONDS,
                        "\"" + Instant.EPOCH + "\""));

        // Cross-product with providers
        var result = new ArrayList<Arguments>();
        for (var provider : providers()) {
            for (var testCase : testCases) {
                result.add(Arguments.of(
                        provider.get()[0],
                        testCase.get()[0],
                        testCase.get()[1],
                        testCase.get()[2],
                        testCase.get()[3]));
            }
        }
        return result;
    }

    @PerProvider
    public void throwsWhenTimestampIsWrongType(JsonSerdeProvider provider) {
        var schema = Schema.createTimestamp(ShapeId.from("smithy.foo#Time"));

        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("true".getBytes(StandardCharsets.UTF_8));
            var e = Assertions.assertThrows(SerializationException.class, () -> de.readTimestamp(schema));
            assertThat(e.getMessage(), containsString("Expected a timestamp"));
        }
    }

    @PerProvider
    public void ignoresTypeOnUnions(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer(
                    "{\"__type\":\"foo\", \"booleanValue\":true}".getBytes(StandardCharsets.UTF_8));
            Set<String> members = new LinkedHashSet<>();

            de.readStruct(JsonTestData.UNION, members, new ShapeDeserializer.StructMemberConsumer<>() {
                @Override
                public void accept(Set<String> memberResult, Schema member, ShapeDeserializer deser) {
                    memberResult.add(member.memberName());
                    if (member.memberName().equals("booleanValue")) {
                        assertThat(deser.readBoolean(JsonTestData.UNION.member("booleanValue")), equalTo(true));
                    } else {
                        throw new IllegalStateException("Unexpected member: " + member);
                    }
                }

                @Override
                public void unknownMember(Set<String> state, String memberName) {
                    Assertions.fail("Should not have detected an unknown member: " + memberName);
                }
            });

            assertThat(members, contains("booleanValue"));
        }
    }
}
