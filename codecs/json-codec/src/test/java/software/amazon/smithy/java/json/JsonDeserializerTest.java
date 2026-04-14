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
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.junit.jupiter.api.Disabled;
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

    @PerProvider
    public void rejectsInvalidEscapeInSkippedValue(JsonSerdeProvider provider) {
        // Unknown field "x" has value with invalid escape \e
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                var de = codec.createDeserializer(
                        "{\"x\":\"hello\\eworld\",\"name\":\"Sam\"}".getBytes(StandardCharsets.UTF_8));
                de.readStruct(JsonTestData.BIRD, new LinkedHashSet<>(), (s, member, deser) -> {
                    deser.readString(member);
                });
            }
        });
    }

    @PerProvider
    public void rejectsControlCharInSkippedValue(JsonSerdeProvider provider) {
        // Unknown field "x" has value with unescaped null byte
        byte[] input = "{\"x\":\"ab\u0000cd\",\"name\":\"Sam\"}".getBytes(StandardCharsets.UTF_8);
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                var de = codec.createDeserializer(input);
                de.readStruct(JsonTestData.BIRD, new LinkedHashSet<>(), (s, member, deser) -> {
                    deser.readString(member);
                });
            }
        });
    }

    @PerProvider
    public void rejectsLeadingZeroInSkippedNumber(JsonSerdeProvider provider) {
        // Unknown field "x" has value with leading zero (00.5)
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                var de = codec.createDeserializer(
                        "{\"x\":00.5,\"name\":\"Sam\"}".getBytes(StandardCharsets.UTF_8));
                de.readStruct(JsonTestData.BIRD, new LinkedHashSet<>(), (s, member, deser) -> {
                    deser.readString(member);
                });
            }
        });
    }

    @PerProvider
    public void rejectsDoubleExponentInSkippedNumber(JsonSerdeProvider provider) {
        // Unknown field "x" has malformed number with double exponent
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                var de = codec.createDeserializer(
                        "{\"x\":1e2e3,\"name\":\"Sam\"}".getBytes(StandardCharsets.UTF_8));
                de.readStruct(JsonTestData.BIRD, new LinkedHashSet<>(), (s, member, deser) -> {
                    deser.readString(member);
                });
            }
        });
    }

    @PerProvider
    public void rejectsControlCharInUnknownFieldName(JsonSerdeProvider provider) {
        // Field name contains unescaped null byte
        byte[] input = new byte[] {
                '{',
                '"',
                'a',
                0x00,
                'b',
                '"',
                ':',
                '1',
                ',',
                '"',
                'n',
                'a',
                'm',
                'e',
                '"',
                ':',
                '"',
                'S',
                '"',
                '}'
        };
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                var de = codec.createDeserializer(input);
                de.readStruct(JsonTestData.BIRD, new LinkedHashSet<>(), (s, member, deser) -> {
                    deser.readString(member);
                });
            }
        });
    }

    @PerProvider
    public void rejectsInvalidEscapeInUnknownFieldName(JsonSerdeProvider provider) {
        // Field name contains invalid escape \e
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                var de = codec.createDeserializer(
                        "{\"a\\eb\":1,\"name\":\"Sam\"}".getBytes(StandardCharsets.UTF_8));
                de.readStruct(JsonTestData.BIRD, new LinkedHashSet<>(), (s, member, deser) -> {
                    deser.readString(member);
                });
            }
        });
    }

    @PerProvider
    public void rejectsInvalidEscapeInSkippedObjectKey(JsonSerdeProvider provider) {
        // Unknown field "x" has an object value whose key contains invalid escape
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                var de = codec.createDeserializer(
                        "{\"x\":{\"a\\eb\":1},\"name\":\"Sam\"}".getBytes(StandardCharsets.UTF_8));
                de.readStruct(JsonTestData.BIRD, new LinkedHashSet<>(), (s, member, deser) -> {
                    deser.readString(member);
                });
            }
        });
    }

    @PerProvider
    public void stillSkipsValidUnknownFields(JsonSerdeProvider provider) {
        // Valid unknown fields should still be skipped successfully
        try (var codec = codecBuilder(provider).useJsonName(true).build()) {
            var de = codec.createDeserializer(
                    "{\"unknown\":{\"a\":1,\"b\":[true,null,\"x\"]},\"name\":\"Sam\",\"extra\":1.5e10}"
                            .getBytes(StandardCharsets.UTF_8));
            Set<String> members = new LinkedHashSet<>();
            de.readStruct(JsonTestData.BIRD, members, (memberResult, member, deser) -> {
                memberResult.add(member.memberName());
                if (member.memberName().equals("name")) {
                    assertThat(deser.readString(JsonTestData.BIRD.member("name")), equalTo("Sam"));
                }
            });
            assertThat(members, contains("name"));
        }
    }

    @PerProvider
    public void deserializesLongMinValue(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("-9223372036854775808".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readLong(PreludeSchemas.LONG), is(Long.MIN_VALUE));
        }
    }

    @PerProvider
    public void deserializesLongMaxValue(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("9223372036854775807".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readLong(PreludeSchemas.LONG), is(Long.MAX_VALUE));
        }
    }

    @PerProvider
    public void rejectsLongOverflowPositive(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("9223372036854775808".getBytes(StandardCharsets.UTF_8));
            Assertions.assertThrows(SerializationException.class, () -> de.readLong(PreludeSchemas.LONG));
        }
    }

    @PerProvider
    public void rejectsLongOverflowNegative(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("-9223372036854775809".getBytes(StandardCharsets.UTF_8));
            Assertions.assertThrows(SerializationException.class, () -> de.readLong(PreludeSchemas.LONG));
        }
    }

    @PerProvider
    public void deserializesIntegerMinMaxValues(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            assertThat(codec.createDeserializer("-2147483648".getBytes(StandardCharsets.UTF_8))
                    .readInteger(PreludeSchemas.INTEGER), is(Integer.MIN_VALUE));
            assertThat(codec.createDeserializer("2147483647".getBytes(StandardCharsets.UTF_8))
                    .readInteger(PreludeSchemas.INTEGER), is(Integer.MAX_VALUE));
        }
    }

    @PerProvider
    public void rejectsIntegerOverflow(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("2147483648".getBytes(StandardCharsets.UTF_8))
                            .readInteger(PreludeSchemas.INTEGER));
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("-2147483649".getBytes(StandardCharsets.UTF_8))
                            .readInteger(PreludeSchemas.INTEGER));
        }
    }

    @PerProvider
    public void rejectsLeadingZerosInLong(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("01".getBytes(StandardCharsets.UTF_8))
                            .readLong(PreludeSchemas.LONG));
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("007".getBytes(StandardCharsets.UTF_8))
                            .readLong(PreludeSchemas.LONG));
        }
    }

    @PerProvider
    public void parsesNegativeZero(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            assertThat(codec.createDeserializer("-0".getBytes(StandardCharsets.UTF_8))
                    .readLong(PreludeSchemas.LONG), is(0L));
        }
    }

    @PerProvider
    public void rejectsBareMinusSign(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("-".getBytes(StandardCharsets.UTF_8))
                            .readLong(PreludeSchemas.LONG));
        }
    }

    @PerProvider
    public void deserializesByteMinMax(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            assertThat(codec.createDeserializer("-128".getBytes(StandardCharsets.UTF_8))
                    .readByte(PreludeSchemas.BYTE), is(Byte.MIN_VALUE));
            assertThat(codec.createDeserializer("127".getBytes(StandardCharsets.UTF_8))
                    .readByte(PreludeSchemas.BYTE), is(Byte.MAX_VALUE));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsByteOverflow(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("128".getBytes(StandardCharsets.UTF_8))
                            .readByte(PreludeSchemas.BYTE));
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("-129".getBytes(StandardCharsets.UTF_8))
                            .readByte(PreludeSchemas.BYTE));
        }
    }

    @PerProvider
    public void deserializesShortMinMax(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            assertThat(codec.createDeserializer("-32768".getBytes(StandardCharsets.UTF_8))
                    .readShort(PreludeSchemas.SHORT), is(Short.MIN_VALUE));
            assertThat(codec.createDeserializer("32767".getBytes(StandardCharsets.UTF_8))
                    .readShort(PreludeSchemas.SHORT), is(Short.MAX_VALUE));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsShortOverflow(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("32768".getBytes(StandardCharsets.UTF_8))
                            .readShort(PreludeSchemas.SHORT));
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("-32769".getBytes(StandardCharsets.UTF_8))
                            .readShort(PreludeSchemas.SHORT));
        }
    }

    @PerProvider
    public void parsesDoubleWithLeadingZeroBeforeDecimal(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            assertThat(codec.createDeserializer("0.5".getBytes(StandardCharsets.UTF_8))
                    .readDouble(PreludeSchemas.DOUBLE), is(0.5));
        }
    }

    @PerProvider
    public void rejectsDoubleWithDoubleLeadingZero(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("00.5".getBytes(StandardCharsets.UTF_8))
                            .readDouble(PreludeSchemas.DOUBLE));
        }
    }

    @PerProvider
    public void parsesDoubleWithExponent(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            assertThat(codec.createDeserializer("1e10".getBytes(StandardCharsets.UTF_8))
                    .readDouble(PreludeSchemas.DOUBLE), is(1e10));
            assertThat(codec.createDeserializer("1E-10".getBytes(StandardCharsets.UTF_8))
                    .readDouble(PreludeSchemas.DOUBLE), is(1E-10));
            assertThat(codec.createDeserializer("1.5e+3".getBytes(StandardCharsets.UTF_8))
                    .readDouble(PreludeSchemas.DOUBLE), is(1.5e+3));
        }
    }

    @PerProvider
    public void parsesAllEscapeSequences(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer(
                    "\"\\\" \\\\ \\/ \\b \\f \\n \\r \\t\"".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readString(PreludeSchemas.STRING), equalTo("\" \\ / \b \f \n \r \t"));
        }
    }

    @PerProvider
    public void parsesUnicodeEscapes(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            // Basic unicode escape
            assertThat(codec.createDeserializer("\"\\u0041\"".getBytes(StandardCharsets.UTF_8))
                    .readString(PreludeSchemas.STRING), equalTo("A"));
            // Lowercase hex
            assertThat(codec.createDeserializer("\"\\u00e9\"".getBytes(StandardCharsets.UTF_8))
                    .readString(PreludeSchemas.STRING), equalTo("\u00e9"));
            // Uppercase hex
            assertThat(codec.createDeserializer("\"\\u00E9\"".getBytes(StandardCharsets.UTF_8))
                    .readString(PreludeSchemas.STRING), equalTo("\u00e9"));
        }
    }

    @PerProvider
    public void parsesSurrogatePair(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            // U+1F600 (grinning face emoji)
            var de = codec.createDeserializer("\"\\uD83D\\uDE00\"".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readString(PreludeSchemas.STRING), equalTo("\uD83D\uDE00"));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsLoneSurrogateHigh(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("\"\\uD83D\"".getBytes(StandardCharsets.UTF_8))
                            .readString(PreludeSchemas.STRING));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsLoneSurrogateLow(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("\"\\uDE00\"".getBytes(StandardCharsets.UTF_8))
                            .readString(PreludeSchemas.STRING));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsHighSurrogateFollowedByNonLow(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("\"\\uD83D\\u0041\"".getBytes(StandardCharsets.UTF_8))
                            .readString(PreludeSchemas.STRING));
        }
    }

    static List<Arguments> smithyOnly() {
        return List.of(Arguments.of(SMITHY));
    }

    @PerProvider
    public void rejectsInvalidEscapeSequence(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("\"\\x\"".getBytes(StandardCharsets.UTF_8))
                            .readString(PreludeSchemas.STRING));
        }
    }

    @PerProvider
    public void rejectsControlCharInString(JsonSerdeProvider provider) {
        byte[] input = new byte[] {'"', 0x01, '"'};
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer(input).readString(PreludeSchemas.STRING));
        }
    }

    @PerProvider
    public void rejectsUnterminatedString(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("\"hello".getBytes(StandardCharsets.UTF_8))
                            .readString(PreludeSchemas.STRING));
        }
    }

    @PerProvider
    public void rejectsIncompleteUnicodeEscape(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("\"\\u00\"".getBytes(StandardCharsets.UTF_8))
                            .readString(PreludeSchemas.STRING));
        }
    }

    @PerProvider
    public void rejectsInvalidHexInUnicodeEscape(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("\"\\uGGGG\"".getBytes(StandardCharsets.UTF_8))
                            .readString(PreludeSchemas.STRING));
        }
    }

    @PerProvider
    public void parsesLongAsciiString(JsonSerdeProvider provider) {
        // > 8 bytes to exercise SWAR scanning path
        String longStr = "abcdefghijklmnopqrstuvwxyz0123456789";
        try (var codec = codec(provider)) {
            assertThat(codec.createDeserializer(("\"" + longStr + "\"").getBytes(StandardCharsets.UTF_8))
                    .readString(PreludeSchemas.STRING), equalTo(longStr));
        }
    }

    @PerProvider
    public void parsesUtf8MultiByte(JsonSerdeProvider provider) {
        // 2-byte UTF-8: e-acute (\u00e9)
        // 3-byte UTF-8: CJK character (\u4e2d)
        // 4-byte UTF-8: emoji (U+1F600)
        // Embed directly as UTF-8 bytes with an escape to trigger slow path
        String input = "\"\u00e9\\n\u4e2d\"";
        try (var codec = codec(provider)) {
            assertThat(codec.createDeserializer(input.getBytes(StandardCharsets.UTF_8))
                    .readString(PreludeSchemas.STRING), equalTo("\u00e9\n\u4e2d"));
        }
    }

    @PerProvider
    public void rejectsTruncatedTrue(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("tru".getBytes(StandardCharsets.UTF_8))
                            .readBoolean(PreludeSchemas.BOOLEAN));
        }
    }

    @PerProvider
    public void rejectsTruncatedFalse(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("fals".getBytes(StandardCharsets.UTF_8))
                            .readBoolean(PreludeSchemas.BOOLEAN));
        }
    }

    @PerProvider
    public void rejectsCorruptLiteral(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("trux".getBytes(StandardCharsets.UTF_8))
                            .readBoolean(PreludeSchemas.BOOLEAN));
        }
    }

    @PerProvider
    public void readsNullLiteral(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("null".getBytes(StandardCharsets.UTF_8));
            assertTrue(de.isNull());
            assertNull(de.readNull());
        }
    }

    @PerProvider
    public void rejectsTruncatedNull(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                var de = codec.createDeserializer("nul".getBytes(StandardCharsets.UTF_8));
                de.readNull();
            }
        });
    }

    @PerProvider
    public void handlesLeadingWhitespace(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            assertThat(codec.createDeserializer("  \t\n\r 42".getBytes(StandardCharsets.UTF_8))
                    .readInteger(PreludeSchemas.INTEGER), is(42));
        }
    }

    @PerProvider
    public void handlesWhitespaceBetweenTokens(JsonSerdeProvider provider) {
        try (var codec = codecBuilder(provider).useJsonName(true).build()) {
            var de = codec.createDeserializer(
                    "  {  \"name\"  :  \"Sam\"  ,  \"Color\"  :  \"red\"  }  ".getBytes(StandardCharsets.UTF_8));
            Set<String> members = new LinkedHashSet<>();
            de.readStruct(JsonTestData.BIRD, members, (memberResult, member, deser) -> {
                memberResult.add(member.memberName());
                deser.readString(member);
            });
            assertThat(members, contains("name", "color"));
        }
    }

    @PerProvider
    public void documentParsesLargeInteger(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            // Long range (exceeds int)
            var de = codec.createDeserializer("3000000000".getBytes(StandardCharsets.UTF_8));
            var doc = de.readDocument();
            assertThat(doc.type(), is(ShapeType.LONG));
            assertThat(doc.asLong(), is(3000000000L));
        }
    }

    @PerProvider
    public void documentParsesBigInteger(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("99999999999999999999".getBytes(StandardCharsets.UTF_8));
            var doc = de.readDocument();
            // Should be BigInteger since it exceeds Long range
            assertThat(doc.asBigInteger(), equalTo(new BigInteger("99999999999999999999")));
        }
    }

    @PerProvider
    public void documentParsesExponentAsDouble(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("1e5".getBytes(StandardCharsets.UTF_8));
            var doc = de.readDocument();
            assertThat(doc.type(), is(ShapeType.DOUBLE));
            assertThat(doc.asDouble(), is(1e5));
        }
    }

    @PerProvider
    public void documentParsesNegativeNumber(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("-42".getBytes(StandardCharsets.UTF_8));
            var doc = de.readDocument();
            assertThat(doc.type(), is(ShapeType.INTEGER));
            assertThat(doc.asInteger(), is(-42));
        }
    }

    @PerProvider
    public void rejectsTrailingContentAfterClose(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("42 extra".getBytes(StandardCharsets.UTF_8));
            de.readInteger(PreludeSchemas.INTEGER);
            Assertions.assertThrows(SerializationException.class, () -> de.close());
        }
    }

    @PerProvider
    public void skipsDeepNestedUnknownFields(JsonSerdeProvider provider) {
        try (var codec = codecBuilder(provider).useJsonName(true).build()) {
            var de = codec.createDeserializer(
                    ("{\"unknown\":{\"a\":{\"b\":[1,{\"c\":true}]},"
                            + "\"d\":\"test\"},\"name\":\"Sam\"}")
                            .getBytes(StandardCharsets.UTF_8));
            Set<String> members = new LinkedHashSet<>();
            de.readStruct(JsonTestData.BIRD, members, (memberResult, member, deser) -> {
                memberResult.add(member.memberName());
                if (member.memberName().equals("name")) {
                    assertThat(deser.readString(JsonTestData.BIRD.member("name")), equalTo("Sam"));
                }
            });
            assertThat(members, contains("name"));
        }
    }

    @PerProvider
    public void rejectsInvalidEscapeInSkippedNestedObjectKey(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                var de = codec.createDeserializer(
                        "{\"x\":{\"a\\qb\":1},\"name\":\"Sam\"}".getBytes(StandardCharsets.UTF_8));
                de.readStruct(JsonTestData.BIRD, new LinkedHashSet<>(), (s, member, deser) -> {
                    deser.readString(member);
                });
            }
        });
    }

    @PerProvider
    public void skipsStringWithValidEscapesInUnknownValue(JsonSerdeProvider provider) {
        try (var codec = codecBuilder(provider).useJsonName(true).build()) {
            // Use JSON escapes that are valid: \n, \t, \\, \", \u0041
            var json = "{\"x\":\"hello\\nworld\\t\\u0041\",\"name\":\"Sam\"}";
            var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
            Set<String> members = new LinkedHashSet<>();
            de.readStruct(JsonTestData.BIRD, members, (memberResult, member, deser) -> {
                memberResult.add(member.memberName());
                deser.readString(member);
            });
            assertThat(members, contains("name"));
        }
    }

    @PerProvider
    public void rejectsCorruptFalse(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                codec.createDeserializer("faxxx".getBytes(StandardCharsets.UTF_8))
                        .readBoolean(PreludeSchemas.BOOLEAN);
            }
        });
    }

    @PerProvider
    public void rejectsInvalidBase64InBlob(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                codec.createDeserializer("\"!!!not-base64!!!\"".getBytes(StandardCharsets.UTF_8))
                        .readBlob(PreludeSchemas.BLOB);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsEpochSecondsOutOfRange(JsonSerdeProvider provider) {
        // Instant.MAX.getEpochSecond() is 31556889864403199; one beyond that overflows
        long outOfRange = Instant.MAX.getEpochSecond() + 1;
        var schema = Schema.createTimestamp(ShapeId.from("smithy.foo#Time"));
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                codec.createDeserializer(
                        Long.toString(outOfRange).getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
    }

    @PerProvider
    public void timestampFallbackForOffsetTimezone(JsonSerdeProvider provider) {
        var schema = Schema.createTimestamp(
                ShapeId.from("smithy.foo#Time"),
                new TimestampFormatTrait(TimestampFormatTrait.DATE_TIME));
        try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
            // Offset timezone causes fast-path parseIso8601 to return null, falling back to DateTimeFormatter
            var de = codec.createDeserializer(
                    "\"2025-01-15T10:30:00+00:00\"".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readTimestamp(schema), equalTo(Instant.parse("2025-01-15T10:30:00Z")));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsNonObjectForStruct(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                codec.createDeserializer("123".getBytes(StandardCharsets.UTF_8))
                        .readStruct(JsonTestData.BIRD, new LinkedHashSet<>(), (s, m, d) -> {});
            }
        });
    }

    @PerProvider
    public void rejectsNonQuotedFieldName(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                codec.createDeserializer("{123:1}".getBytes(StandardCharsets.UTF_8))
                        .readStruct(JsonTestData.BIRD, new LinkedHashSet<>(), (s, m, d) -> {});
            }
        });
    }

    @PerProvider
    public void rejectsUnterminatedFieldName(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                codec.createDeserializer("{\"unterminated".getBytes(StandardCharsets.UTF_8))
                        .readStruct(JsonTestData.BIRD, new LinkedHashSet<>(), (s, m, d) -> {});
            }
        });
    }

    @PerProvider
    public void rejectsMissingColonInStruct(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                codec.createDeserializer("{\"name\" \"Sam\"}".getBytes(StandardCharsets.UTF_8))
                        .readStruct(JsonTestData.BIRD, new LinkedHashSet<>(), (s, m, d) -> {});
            }
        });
    }

    @PerProvider
    public void nullMemberValueSkippedInStruct(JsonSerdeProvider provider) {
        try (var codec = codecBuilder(provider).useJsonName(true).build()) {
            var de = codec.createDeserializer(
                    "{\"name\":null,\"Color\":\"red\"}".getBytes(StandardCharsets.UTF_8));
            Set<String> members = new LinkedHashSet<>();
            de.readStruct(JsonTestData.BIRD, members, (memberResult, member, deser) -> {
                memberResult.add(member.memberName());
                deser.readString(member);
            });
            // "name" was null so consumer was never called for it
            assertThat(members, contains("color"));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsNonObjectForMap(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                codec.createDeserializer("[1]".getBytes(StandardCharsets.UTF_8))
                        .readStringMap(PreludeSchemas.DOCUMENT, null, (s, k, d) -> {});
            }
        });
    }

    @PerProvider
    public void rejectsMissingCommaInMap(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                Map<String, String> result = new LinkedHashMap<>();
                codec.createDeserializer("{\"a\":\"1\" \"b\":\"2\"}".getBytes(StandardCharsets.UTF_8))
                        .readStringMap(PreludeSchemas.DOCUMENT,
                                result,
                                (m, k, d) -> m.put(k, d.readString(PreludeSchemas.STRING)));
            }
        });
    }

    @PerProvider
    public void rejectsMissingColonInMap(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                Map<String, String> result = new LinkedHashMap<>();
                codec.createDeserializer("{\"a\" \"1\"}".getBytes(StandardCharsets.UTF_8))
                        .readStringMap(PreludeSchemas.DOCUMENT,
                                result,
                                (m, k, d) -> m.put(k, d.readString(PreludeSchemas.STRING)));
            }
        });
    }

    @PerProvider
    public void skipsFalseLiteralInUnknownValue(JsonSerdeProvider provider) {
        try (var codec = codecBuilder(provider).useJsonName(true).build()) {
            var de = codec.createDeserializer(
                    "{\"x\":false,\"name\":\"Sam\"}".getBytes(StandardCharsets.UTF_8));
            Set<String> members = new LinkedHashSet<>();
            de.readStruct(JsonTestData.BIRD, members, (memberResult, member, deser) -> {
                memberResult.add(member.memberName());
                deser.readString(member);
            });
            assertThat(members, contains("name"));
        }
    }

    @PerProvider
    public void skipsEmptyObjectInUnknownValue(JsonSerdeProvider provider) {
        try (var codec = codecBuilder(provider).useJsonName(true).build()) {
            var de = codec.createDeserializer(
                    "{\"x\":{},\"name\":\"Sam\"}".getBytes(StandardCharsets.UTF_8));
            Set<String> members = new LinkedHashSet<>();
            de.readStruct(JsonTestData.BIRD, members, (memberResult, member, deser) -> {
                memberResult.add(member.memberName());
                deser.readString(member);
            });
            assertThat(members, contains("name"));
        }
    }

    @PerProvider
    public void skipsEmptyArrayInUnknownValue(JsonSerdeProvider provider) {
        try (var codec = codecBuilder(provider).useJsonName(true).build()) {
            var de = codec.createDeserializer(
                    "{\"x\":[],\"name\":\"Sam\"}".getBytes(StandardCharsets.UTF_8));
            Set<String> members = new LinkedHashSet<>();
            de.readStruct(JsonTestData.BIRD, members, (memberResult, member, deser) -> {
                memberResult.add(member.memberName());
                deser.readString(member);
            });
            assertThat(members, contains("name"));
        }
    }

    @PerProvider
    public void rejectsUnterminatedStringInSkippedValue(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                codec.createDeserializer("{\"x\":\"unterminated".getBytes(StandardCharsets.UTF_8))
                        .readStruct(JsonTestData.BIRD, new LinkedHashSet<>(), (s, m, d) -> {});
            }
        });
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsInvalidMonthInHttpDate(JsonSerdeProvider provider) {
        var schema = Schema.createTimestamp(
                ShapeId.from("smithy.foo#Time"),
                new TimestampFormatTrait(TimestampFormatTrait.HTTP_DATE));
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"Thu, 01 Xyz 2025 00:00:00 GMT\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
    }

    @PerProvider
    public void rejectsMissingCommaInList(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                codec.createDeserializer("[1 2]".getBytes(StandardCharsets.UTF_8))
                        .readList(PreludeSchemas.DOCUMENT, null, (s, d) -> d.readInteger(PreludeSchemas.INTEGER));
            }
        });
    }

    @PerProvider
    public void rejectsNullAsTimestamp(JsonSerdeProvider provider) {
        var schema = Schema.createTimestamp(ShapeId.from("smithy.foo#Time"));
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("null".getBytes(StandardCharsets.UTF_8));
            var e = Assertions.assertThrows(SerializationException.class, () -> de.readTimestamp(schema));
            assertThat(e.getMessage(), containsString("Expected a timestamp"));
        }
    }

    @PerProvider
    public void rejectsArrayAsTimestamp(JsonSerdeProvider provider) {
        var schema = Schema.createTimestamp(ShapeId.from("smithy.foo#Time"));
        try (var codec = codec(provider)) {
            var de = codec.createDeserializer("[1]".getBytes(StandardCharsets.UTF_8));
            var e = Assertions.assertThrows(SerializationException.class, () -> de.readTimestamp(schema));
            assertThat(e.getMessage(), containsString("Expected a timestamp"));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void parseLongRejectsEmptyInput(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer(new byte[0]).readLong(PreludeSchemas.LONG));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void parseLongRejectsNonDigitInput(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("abc".getBytes(StandardCharsets.UTF_8))
                            .readLong(PreludeSchemas.LONG));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void parseDoubleRejectsBareMinusSign(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer("-".getBytes(StandardCharsets.UTF_8))
                            .readDouble(PreludeSchemas.DOUBLE));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsOverlong2ByteUtf8InString(JsonSerdeProvider provider) {
        // 0xC0 0x80 is an overlong encoding of U+0000 (RFC 3629 forbids this)
        byte[] input = {'"', '\\', 'n', (byte) 0xC0, (byte) 0x80, '"'};
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer(input).readString(PreludeSchemas.STRING));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsTruncated2ByteUtf8InString(JsonSerdeProvider provider) {
        // 0xC2 is a 2-byte sequence lead but the continuation byte is missing
        byte[] input = {'"', '\\', 'n', (byte) 0xC2, '"'};
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer(input).readString(PreludeSchemas.STRING));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsOverlong3ByteUtf8InString(JsonSerdeProvider provider) {
        // 0xE0 0x80 0x80 is an overlong encoding of U+0000
        byte[] input = {'"', '\\', 'n', (byte) 0xE0, (byte) 0x80, (byte) 0x80, '"'};
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer(input).readString(PreludeSchemas.STRING));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsTruncated3ByteUtf8InString(JsonSerdeProvider provider) {
        // 0xE4 0xB8 is a 3-byte lead + first continuation, missing the third byte
        byte[] input = {'"', '\\', 'n', (byte) 0xE4, (byte) 0xB8, '"'};
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer(input).readString(PreludeSchemas.STRING));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsTruncated4ByteUtf8InString(JsonSerdeProvider provider) {
        // 0xF0 0x9F 0x98 is a 4-byte sequence missing the last continuation byte
        byte[] input = {'"', '\\', 'n', (byte) 0xF0, (byte) 0x9F, (byte) 0x98, '"'};
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer(input).readString(PreludeSchemas.STRING));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsInvalidUtf8StartByteInString(JsonSerdeProvider provider) {
        // 0xFF is never a valid UTF-8 start byte
        byte[] input = {'"', '\\', 'n', (byte) 0xFF, '"'};
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer(input).readString(PreludeSchemas.STRING));
        }
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsControlCharAfterEscapeInString(JsonSerdeProvider provider) {
        // RFC 8259 requires control chars (U+0000..U+001F) to be escaped
        byte[] input = {'"', '\\', 'n', 0x01, '"'};
        try (var codec = codec(provider)) {
            Assertions.assertThrows(SerializationException.class,
                    () -> codec.createDeserializer(input).readString(PreludeSchemas.STRING));
        }
    }

    @Disabled("DateTimeFormatter fallback accepts trailing garbage after Z")
    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void iso8601WithExtraCharsAfterZShouldBeRejected(JsonSerdeProvider provider) {
        var schema = Schema.createTimestamp(
                ShapeId.from("smithy.foo#Time"),
                new TimestampFormatTrait(TimestampFormatTrait.DATE_TIME));
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"2025-01-15T10:30:00Zextra\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
    }

    @Disabled("DateTimeFormatter fallback accepts missing space after comma")
    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void httpDateShouldRejectMissingSpaceAfterComma(JsonSerdeProvider provider) {
        // RFC 7231 requires "day-name, SP" format
        var schema = Schema.createTimestamp(
                ShapeId.from("smithy.foo#Time"),
                new TimestampFormatTrait(TimestampFormatTrait.HTTP_DATE));
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"Thu,01 Jan 2025 00:00:00 GMT\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
    }

    @Disabled("DateTimeFormatter fallback accepts UTC instead of GMT")
    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void httpDateShouldRejectNonGmtTimezone(JsonSerdeProvider provider) {
        // RFC 7231 HTTP-date requires "GMT"
        var schema = Schema.createTimestamp(
                ShapeId.from("smithy.foo#Time"),
                new TimestampFormatTrait(TimestampFormatTrait.HTTP_DATE));
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"Thu, 01 Jan 2025 00:00:00 UTC\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
    }

    @Disabled("DateTimeFormatter fallback accepts dash delimiters")
    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void httpDateShouldRejectDashInsteadOfSpace(JsonSerdeProvider provider) {
        var schema = Schema.createTimestamp(
                ShapeId.from("smithy.foo#Time"),
                new TimestampFormatTrait(TimestampFormatTrait.HTTP_DATE));
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"Thu, 01-Jan 2025 00:00:00 GMT\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
    }

    @Disabled("DateTimeFormatter fallback accepts dash instead of colon in time")
    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void httpDateShouldRejectDashInsteadOfColonInTime(JsonSerdeProvider provider) {
        var schema = Schema.createTimestamp(
                ShapeId.from("smithy.foo#Time"),
                new TimestampFormatTrait(TimestampFormatTrait.HTTP_DATE));
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"Thu, 01 Jan 2025 00-00:00 GMT\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void httpDateRejectsInvalidMonthStartingWithMa(JsonSerdeProvider provider) {
        // "Max" is neither "Mar" nor "May"
        var schema = Schema.createTimestamp(
                ShapeId.from("smithy.foo#Time"),
                new TimestampFormatTrait(TimestampFormatTrait.HTTP_DATE));
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"Thu, 01 Max 2025 00:00:00 GMT\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void httpDateRejectsInvalidMonthStartingWithJu(JsonSerdeProvider provider) {
        // "Jux" is neither "Jun" nor "Jul"
        var schema = Schema.createTimestamp(
                ShapeId.from("smithy.foo#Time"),
                new TimestampFormatTrait(TimestampFormatTrait.HTTP_DATE));
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"Thu, 01 Jux 2025 00:00:00 GMT\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsInvalidMonthInIso8601(JsonSerdeProvider provider) {
        var schema = Schema.createTimestamp(
                ShapeId.from("smithy.foo#Time"),
                new TimestampFormatTrait(TimestampFormatTrait.DATE_TIME));
        // Month 13
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"2025-13-01T00:00:00Z\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
        // Month 0
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"2025-00-01T00:00:00Z\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsInvalidDayInIso8601(JsonSerdeProvider provider) {
        var schema = Schema.createTimestamp(
                ShapeId.from("smithy.foo#Time"),
                new TimestampFormatTrait(TimestampFormatTrait.DATE_TIME));
        // Day 32
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"2025-01-32T00:00:00Z\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
        // Day 0
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"2025-01-00T00:00:00Z\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsInvalidTimeInIso8601(JsonSerdeProvider provider) {
        var schema = Schema.createTimestamp(
                ShapeId.from("smithy.foo#Time"),
                new TimestampFormatTrait(TimestampFormatTrait.DATE_TIME));
        // Hour 25
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"2025-01-01T25:00:00Z\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
        // Minute 60
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"2025-01-01T00:60:00Z\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
        // Second 60
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useTimestampFormat(true).build()) {
                codec.createDeserializer(
                        "\"2025-01-01T00:00:60Z\"".getBytes(StandardCharsets.UTF_8))
                        .readTimestamp(schema);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsInvalidContinuationByteInUtf8(JsonSerdeProvider provider) {
        // 2-byte lead 0xC2 followed by ASCII 'A' (0x41) instead of valid continuation byte.
        // Prefix with "\n" to force entry into the slow path (escape triggers decodeUtf8Char for following bytes).
        byte[] json = new byte[] {'"', '\\', 'n', (byte) 0xC2, 0x41, '"'};
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                codec.createDeserializer(json).readString(PreludeSchemas.STRING);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void rejectsSurrogateCodePointInUtf8(JsonSerdeProvider provider) {
        // 3-byte sequence [0xED, 0xA0, 0x80] encodes U+D800 (high surrogate) as UTF-8.
        // Prefix with "\n" to force slow path.
        byte[] json = new byte[] {'"', '\\', 'n', (byte) 0xED, (byte) 0xA0, (byte) 0x80, '"'};
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                codec.createDeserializer(json).readString(PreludeSchemas.STRING);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void readBigIntegerRejectsDecimalInput(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                codec.createDeserializer("1.5".getBytes(StandardCharsets.UTF_8))
                        .readBigInteger(PreludeSchemas.BIG_INTEGER);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void readBigIntegerRejectsExponentInput(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                codec.createDeserializer("1e5".getBytes(StandardCharsets.UTF_8))
                        .readBigInteger(PreludeSchemas.BIG_INTEGER);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("smithyOnly")
    public void readBigDecimalRejectsInvalidFormat(JsonSerdeProvider provider) {
        // findNumberEnd accepts "++1" but BigDecimal constructor rejects it
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codec(provider)) {
                // Start with valid digit prefix so findNumberEnd is entered, but overall format is invalid
                codec.createDeserializer("1+1".getBytes(StandardCharsets.UTF_8))
                        .readBigDecimal(PreludeSchemas.BIG_DECIMAL);
            }
        });
    }

    @PerProvider
    public void forbidUnknownUnionMembersThrows(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).forbidUnknownUnionMembers(true).build()) {
                var de = codec.createDeserializer(
                        "{\"totallyUnknown\":42}".getBytes(StandardCharsets.UTF_8));
                Set<String> members = new LinkedHashSet<>();
                de.readStruct(JsonTestData.UNION, members, new ShapeDeserializer.StructMemberConsumer<Set<String>>() {
                    @Override
                    public void accept(Set<String> state, Schema member, ShapeDeserializer deser) {}

                    @Override
                    public void unknownMember(Set<String> state, String memberName) {}
                });
            }
        });
    }

    @PerProvider
    public void rejectsUnterminatedObject(JsonSerdeProvider provider) {
        Assertions.assertThrows(SerializationException.class, () -> {
            try (var codec = codecBuilder(provider).useJsonName(true).build()) {
                // After reading first field, no '}' or ',' -> unterminated
                var de = codec.createDeserializer("{\"name\":\"Sam\"".getBytes(StandardCharsets.UTF_8));
                Set<String> members = new LinkedHashSet<>();
                de.readStruct(JsonTestData.BIRD, members, (memberResult, member, deser) -> {
                    memberResult.add(member.memberName());
                    deser.readString(member);
                });
            }
        });
    }

}
