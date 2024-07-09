/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import static java.nio.ByteBuffer.wrap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
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
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.model.traits.Trait;

public class JsonDeserializerTest {
    @Test
    public void deserializesByte() {
        try (var codec = JsonCodec.builder().build()) {
            var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readByte(PreludeSchemas.BYTE), is((byte) 1));
        }
    }

    @Test
    public void deserializesShort() {
        try (var codec = JsonCodec.builder().build()) {
            var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readShort(PreludeSchemas.SHORT), is((short) 1));
        }
    }

    @Test
    public void deserializesInteger() {
        try (var codec = JsonCodec.builder().build()) {
            var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readInteger(PreludeSchemas.INTEGER), is(1));
        }
    }

    @Test
    public void deserializesLong() {
        try (var codec = JsonCodec.builder().build()) {
            var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readLong(PreludeSchemas.LONG), is(1L));
        }
    }

    @Test
    public void deserializesFloat() {
        try (var codec = JsonCodec.builder().build()) {
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

    @Test
    public void deserializesDouble() {
        try (var codec = JsonCodec.builder().build()) {
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

    @Test
    public void deserializesBigInteger() {
        try (var codec = JsonCodec.builder().build()) {
            var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readBigInteger(PreludeSchemas.BIG_INTEGER), is(BigInteger.ONE));
        }
    }

    @Test
    public void deserializesBigIntegerOnlyFromRawNumbersByDefault() {
        try (var codec = JsonCodec.builder().build()) {
            var de = codec.createDeserializer("\"1\"".getBytes(StandardCharsets.UTF_8));
            Assertions.assertThrows(SerializationException.class, () -> de.readBigInteger(PreludeSchemas.BIG_INTEGER));
        }
    }

    @Test
    public void deserializesBigDecimal() {
        try (var codec = JsonCodec.builder().build()) {
            var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readBigDecimal(PreludeSchemas.BIG_DECIMAL), is(BigDecimal.ONE));
        }
    }

    @Test
    public void deserializesBigDecimalOnlyFromRawNumbersByDefault() {
        try (var codec = JsonCodec.builder().build()) {
            var de = codec.createDeserializer("\"1\"".getBytes(StandardCharsets.UTF_8));
            Assertions.assertThrows(SerializationException.class, () -> de.readBigDecimal(PreludeSchemas.BIG_DECIMAL));
        }
    }

    @Test
    public void deserializesTimestamp() {
        try (var codec = JsonCodec.builder().build()) {
            var sink = new ByteArrayOutputStream();
            try (var ser = codec.createSerializer(sink)) {
                ser.writeTimestamp(PreludeSchemas.TIMESTAMP, Instant.EPOCH);
            }

            var de = codec.createDeserializer(sink.toByteArray());
            assertThat(de.readTimestamp(PreludeSchemas.TIMESTAMP), equalTo(Instant.EPOCH));
        }
    }

    @Test
    public void deserializesBlob() {
        try (var codec = JsonCodec.builder().build()) {
            var str = "foo";
            var expected = Base64.getEncoder().encodeToString(str.getBytes());
            var de = codec.createDeserializer(("\"" + expected + "\"").getBytes(StandardCharsets.UTF_8));
            assertThat(de.readBlob(PreludeSchemas.BLOB), equalTo(wrap(str.getBytes(StandardCharsets.UTF_8))));
        }
    }

    @Test
    public void deserializesBoolean() {
        try (var codec = JsonCodec.builder().build()) {
            var de = codec.createDeserializer("true".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readBoolean(PreludeSchemas.BOOLEAN), is(true));
        }
    }

    @Test
    public void deserializesString() {
        try (var codec = JsonCodec.builder().build()) {
            var de = codec.createDeserializer("\"foo\"".getBytes(StandardCharsets.UTF_8));
            assertThat(de.readString(PreludeSchemas.STRING), equalTo("foo"));
        }
    }

    @Test
    public void deserializesList() {
        try (var codec = JsonCodec.builder().build()) {
            var de = codec.createDeserializer("[\"foo\",\"bar\"]".getBytes(StandardCharsets.UTF_8));
            List<String> values = new ArrayList<>();

            de.readList(PreludeSchemas.DOCUMENT, null, (ignore, firstList) -> {
                values.add(firstList.readString(PreludeSchemas.STRING));
            });

            assertThat(values, equalTo(List.of("foo", "bar")));
        }
    }

    @Test
    public void deserializesMap() {
        try (var codec = JsonCodec.builder().build()) {
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

    @Test
    public void deserializesStruct() {
        try (var codec = JsonCodec.builder().useJsonName(true).build()) {
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

    @Test
    public void deserializesUnion() {
        try (var codec = JsonCodec.builder().useJsonName(true).build()) {
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

    @Test
    public void deserializesUnknownUnion() {
        try (var codec = JsonCodec.builder().useJsonName(true).build()) {
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

    @Test
    public void skipsUnknownMembers() {
        try (var codec = JsonCodec.builder().useJsonName(true).build()) {
            var de = codec.createDeserializer(
                "{\"name\":\"Sam\",\"Ignore\":[1,2,3],\"Color\":\"rainbow\"}".getBytes(StandardCharsets.UTF_8)
            );
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
    public void deserializesBirdWithJsonNameOrNot(boolean useJsonName, String input) {
        try (var codec = JsonCodec.builder().useJsonName(useJsonName).build()) {
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
        return List.of(
            Arguments.of(true, "{\"name\":\"Sam\",\"Color\":\"red\"}"),
            Arguments.of(false, "{\"name\":\"Sam\",\"color\":\"red\"}")
        );
    }

    @Test
    public void readsDocuments() {
        var json = "{\"name\":\"Sam\",\"color\":\"red\"}".getBytes(StandardCharsets.UTF_8);

        try (var codec = JsonCodec.builder().build()) {
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
        boolean useTrait,
        TimestampFormatTrait trait,
        TimestampFormatter defaultFormat,
        String json
    ) {
        Trait[] traits = trait == null ? null : new Trait[]{trait};
        var schema = Schema.createTimestamp(ShapeId.from("smithy.foo#Time"), traits);

        var codecBuilder = JsonCodec.builder().useTimestampFormat(useTrait);
        if (defaultFormat != null) {
            codecBuilder.defaultTimestampFormat(defaultFormat);
        }

        try (var codec = codecBuilder.build()) {
            var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
            assertThat(de.readTimestamp(schema), equalTo(Instant.EPOCH));
        }
    }

    public static List<Arguments> deserializesWithTimestampFormatSource() {
        var epochSeconds = Double.toString(((double) Instant.EPOCH.toEpochMilli()) / 1000);

        return List.of(
            // boolean useTrait, TimestampFormatTrait trait, TimestampFormatter defaultFormat, String json
            Arguments.of(false, null, null, epochSeconds),
            Arguments.of(false, new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS), null, epochSeconds),
            Arguments.of(
                false,
                new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS),
                TimestampFormatter.Prelude.EPOCH_SECONDS,
                epochSeconds
            ),
            Arguments.of(
                true,
                new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS),
                TimestampFormatter.Prelude.EPOCH_SECONDS,
                epochSeconds
            ),
            Arguments.of(true, new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS), null, epochSeconds),
            Arguments.of(
                true,
                new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS),
                TimestampFormatter.Prelude.DATE_TIME,
                epochSeconds
            ),
            Arguments.of(
                false,
                new TimestampFormatTrait(TimestampFormatTrait.EPOCH_SECONDS),
                TimestampFormatter.Prelude.DATE_TIME,
                "\"" + Instant.EPOCH + "\""
            ),
            Arguments.of(
                true,
                new TimestampFormatTrait(TimestampFormatTrait.DATE_TIME),
                TimestampFormatter.Prelude.EPOCH_SECONDS,
                "\"" + Instant.EPOCH + "\""
            )
        );
    }

    @Test
    public void throwsWhenTimestampIsWrongType() {
        var schema = Schema.createTimestamp(ShapeId.from("smithy.foo#Time"));

        try (var codec = JsonCodec.builder().build()) {
            var de = codec.createDeserializer("true".getBytes(StandardCharsets.UTF_8));
            var e = Assertions.assertThrows(SerializationException.class, () -> de.readTimestamp(schema));
            assertThat(e.getMessage(), equalTo("Expected a timestamp, but found boolean"));
        }
    }
}
