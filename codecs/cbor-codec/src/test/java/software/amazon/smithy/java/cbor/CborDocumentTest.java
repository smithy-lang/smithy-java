/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

public class CborDocumentTest {
    private static ShapeDeserializer toCbor(String json) {
        return toCbor(json, JsonSettings.builder().build(), CborSettings.defaultSettings());
    }

    private static ShapeDeserializer toCbor(String json, JsonSettings jsonSettings, CborSettings settings) {
        try (var jsonCodec = JsonCodec.builder().settings(jsonSettings).build()) {
            var de = jsonCodec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
            return toCbor(de.readDocument(), settings);
        }
    }

    private static ShapeDeserializer toCbor(Document d) {
        return toCbor(d, CborSettings.defaultSettings());
    }

    private static ShapeDeserializer toCbor(Document d, CborSettings settings) {
        try (var cborCodec = Rpcv2CborCodec.builder().settings(settings).build()) {
            var doc = cborCodec.serialize(d);
            return Rpcv2CborCodec.builder().settings(settings).build().createDeserializer(doc);
        }
    }

    @Test
    public void convertsNumberToNumber() {
        var de = toCbor("120");

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.LONG));
        assertThat(document.asByte(), is((byte) 120));
        assertThat(document.asShort(), is((short) 120));
        assertThat(document.asInteger(), is(120));
        assertThat(document.asLong(), is(120L));
        assertThat(document.asFloat(), is(120f));
        assertThat(document.asDouble(), is(120.0));
        assertThat(document.asBigInteger(), equalTo(BigInteger.valueOf(120)));
        assertThat(document.asBigDecimal(), comparesEqualTo(BigDecimal.valueOf(120.0)));
    }

    @Test
    public void convertsDoubleToNumber() {
        var de = toCbor("1.1");

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.DOUBLE));
        assertThat(document.asFloat(), is(1.1f));
        assertThat(document.asDouble(), is(1.1));
    }

    @Test
    public void convertsTrueToBoolean() {
        var de = toCbor("true");

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.BOOLEAN));
        assertThat(document.asBoolean(), is(true));
    }

    @Test
    public void convertsFalseToBoolean() {
        var de = toCbor("false");

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.BOOLEAN));
        assertThat(document.asBoolean(), is(false));
    }

    @Test
    public void convertsToTimestampWithEpochSeconds() {
        var de = toCbor(Document.of(Instant.EPOCH));

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.TIMESTAMP));
        assertThat(document.asTimestamp(), equalTo(Instant.EPOCH));
    }

    @Test
    public void convertsToTimestampFailsOnUnknownType() {
        var de = toCbor("true");
        var document = de.readDocument();

        var e = Assertions.assertThrows(SerializationException.class, document::asTimestamp);
        e.printStackTrace();
        assertThat(e.getMessage(), containsString("Expected a timestamp document, but found boolean"));
    }

    @Test
    public void convertsToList() {
        var de = toCbor("[1, 2, 3]");

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.LIST));
        assertThat(document.size(), is(3));

        var list = document.asList();
        assertThat(list, hasSize(3));
        assertThat(list.get(0).type(), is(ShapeType.LONG));
        assertThat(list.get(0).asInteger(), is(1));
        assertThat(list.get(1).asInteger(), is(2));
        assertThat(list.get(2).asInteger(), is(3));
    }

    @Test
    public void convertsToMap() {
        var de = toCbor("{\"a\":1,\"b\":true}");

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.MAP));
        assertThat(document.size(), is(2));

        var map = document.asStringMap();
        assertThat(map.keySet(), hasSize(2));
        assertThat(map.get("a").type(), is(ShapeType.LONG));
        assertThat(map.get("a").asInteger(), is(1));
        assertThat(document.getMember("a").type(), is(ShapeType.LONG));

        assertThat(map.get("b").type(), is(ShapeType.BOOLEAN));
        assertThat(map.get("b").asBoolean(), is(true));
        assertThat(document.getMember("b").type(), is(ShapeType.BOOLEAN));
    }

    @Test
    public void otherDocumentsReturnSizeOfNegativeOne() {
        var de = toCbor("1");
        var document = de.readDocument();

        assertThat(document.size(), is(-1));
    }

    @Test
    public void nullAndMissingMapMembersReturnsNull() {
        var de = toCbor("{\"a\":null}");

        var document = de.readDocument();
        assertThat(document.getMember("a"), nullValue());
        assertThat(document.getMember("d"), nullValue());
    }

    @ParameterizedTest
    @MethodSource("failToConvertSource")
    public void failToConvert(String json, Consumer<Document> consumer) {
        var de = toCbor(json);
        var document = de.readDocument();

        Assertions.assertThrows(SerializationException.class, () -> consumer.accept(document));
    }

    public static List<Arguments> failToConvertSource() {
        return List.of(
                Arguments.of("1", (Consumer<Document>) Document::asBoolean),
                Arguments.of("1", (Consumer<Document>) Document::asBlob),
                Arguments.of("1", (Consumer<Document>) Document::asString),
                Arguments.of("1", (Consumer<Document>) Document::asList),
                Arguments.of("1", (Consumer<Document>) Document::asStringMap),
                Arguments.of("1", (Consumer<Document>) Document::asBlob),

                Arguments.of("\"1\"", (Consumer<Document>) Document::asBoolean),
                Arguments.of("\"1\"", (Consumer<Document>) Document::asList),
                Arguments.of("\"1\"", (Consumer<Document>) Document::asStringMap),
                Arguments.of("\"1\"", (Consumer<Document>) Document::asBlob),
                Arguments.of("\"1\"", (Consumer<Document>) Document::asBoolean),
                Arguments.of("\"1\"", (Consumer<Document>) Document::asByte),
                Arguments.of("\"1\"", (Consumer<Document>) Document::asShort),
                Arguments.of("\"1\"", (Consumer<Document>) Document::asInteger),
                Arguments.of("\"1\"", (Consumer<Document>) Document::asLong),
                Arguments.of("\"1\"", (Consumer<Document>) Document::asFloat),
                Arguments.of("\"1\"", (Consumer<Document>) Document::asDouble),
                Arguments.of("\"1\"", (Consumer<Document>) Document::asBigInteger),
                Arguments.of("\"1\"", (Consumer<Document>) Document::asBigDecimal));
    }

    @ParameterizedTest
    @MethodSource("serializeContentSource")
    public void serializeContent(String json) {
        var de = toCbor(json);
        var document = de.readDocument();
        var codec = Rpcv2CborCodec.builder().build();
        var ser = codec.serialize(document);
        var roundtrip = codec.createDeserializer(ser).readDocument();
        assertEquals(roundtrip, document);
    }

    public static List<Arguments> serializeContentSource() {
        return List.of(
                Arguments.of("true"),
                Arguments.of("false"),
                Arguments.of("1"),
                Arguments.of("1.1"),
                Arguments.of("[1,2,3]"),
                Arguments.of("{\"a\":1,\"b\":[1,true,-20,\"hello\"]}"));
    }

    @Test
    public void deserializesIntoBuilder() {
        var base = new TestPojo.Builder();
        base.name = "Hank";
        base.binary = ByteBuffer.wrap("foo".getBytes(StandardCharsets.UTF_8));
        base.date = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        base.numbers.addAll(List.of(1, 2, 3));
        var codec = Rpcv2CborCodec.builder().build();
        var ser = codec.serialize(base.build());
        var de = codec.createDeserializer(ser);
        var document = de.readDocument();

        var builder = new TestPojo.Builder();
        document.deserializeInto(builder);
        var pojo = builder.build();

        assertThat(pojo.name, equalTo(base.name));
        assertThat(pojo.binary, equalTo(base.binary));
        assertThat(pojo.date, equalTo(base.date));
        assertThat(pojo.numbers, equalTo(base.numbers));
    }

    private static final class TestPojo implements SerializableStruct {

        private static final ShapeId ID = ShapeId.from("smithy.example#Foo");

        private static final Schema NUMBERS_LIST = Schema.listBuilder(ShapeId.from("smithy.example#Numbers"))
                .putMember("member", PreludeSchemas.INTEGER)
                .build();

        private static final Schema SCHEMA = Schema.structureBuilder(ID)
                .putMember("name", PreludeSchemas.STRING)
                .putMember("binary", PreludeSchemas.BLOB, new JsonNameTrait("BINARY"))
                .putMember("date", PreludeSchemas.TIMESTAMP, new TimestampFormatTrait(TimestampFormatTrait.DATE_TIME))
                .putMember("numbers", NUMBERS_LIST)
                .build();

        private static final Schema NAME = SCHEMA.member("name");
        private static final Schema BINARY = SCHEMA.member("binary");
        private static final Schema DATE = SCHEMA.member("date");
        private static final Schema NUMBERS = SCHEMA.member("numbers");

        private final String name;
        private final ByteBuffer binary;
        private final Instant date;
        private final List<Integer> numbers;

        TestPojo(Builder builder) {
            this.name = builder.name;
            this.binary = builder.binary;
            this.date = builder.date;
            this.numbers = builder.numbers;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (name != null) {
                serializer.writeString(NAME, name);
            }

            if (binary != null) {
                serializer.writeBlob(BINARY, binary);
            }

            if (date != null) {
                serializer.writeTimestamp(DATE, date);
            }

            serializer.writeList(NUMBERS, numbers, numbers.size(), (elements, ser) -> {
                for (var e : elements) {
                    ser.writeInteger(PreludeSchemas.INTEGER, e);
                }
            });
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return switch (member.memberIndex()) {
                case 0 -> (T) name;
                case 1 -> (T) binary;
                case 2 -> (T) date;
                case 3 -> (T) numbers;
                default -> throw new UnsupportedOperationException(member.toString());
            };
        }

        private static final class Builder implements ShapeBuilder<TestPojo> {

            private String name;
            private ByteBuffer binary;
            private Instant date;
            private final List<Integer> numbers = new ArrayList<>();

            @Override
            public Schema schema() {
                return SCHEMA;
            }

            @Override
            public Builder deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, (pojo, member, deser) -> {
                    switch (member.memberName()) {
                        case "name" -> pojo.name = deser.readString(NAME);
                        case "binary" -> pojo.binary = deser.readBlob(BINARY);
                        case "date" -> pojo.date = deser.readTimestamp(DATE);
                        case "numbers" -> {
                            deser.readList(NUMBERS, pojo.numbers, (values, de) -> {
                                values.add(de.readInteger(NUMBERS_LIST.member("member")));
                            });
                        }
                        default -> throw new UnsupportedOperationException(member.toString());
                    }
                });
                return this;
            }

            @Override
            public TestPojo build() {
                return new TestPojo(this);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("checkEqualitySource")
    public void checkEquality(String left, String right, boolean equal) {
        var de1 = toCbor(left);
        var leftValue = de1.readDocument();

        var de2 = toCbor(right);
        var rightValue = de2.readDocument();

        System.out.println(leftValue);
        System.out.println(rightValue);
        assertThat(leftValue.equals(rightValue), is(equal));
    }

    public static List<Arguments> checkEqualitySource() {
        return List.of(
                Arguments.of("1", "1", true),
                Arguments.of("1", "1.1", false),
                Arguments.of("true", "true", true),
                Arguments.of("true", "false", false),
                Arguments.of("1", "false", false),
                Arguments.of("1", "\"1\"", false),
                Arguments.of("\"foo\"", "\"foo\"", true),
                Arguments.of("[\"foo\"]", "[\"foo\"]", true),
                Arguments.of("{\"foo\":\"foo\"}", "{\"foo\":\"foo\"}", true),
                Arguments.of("{\"foo\":\"foo\"}", "{\"foo\":\"bar\"}", false));
    }

    @Test
    public void canNormalizeCborDocuments() {
        var de = toCbor("true");
        var json = de.readDocument();

        assertThat(Document.equals(json, Document.of(true)), is(true));
    }

    @Test
    public void returnsNullWhenGettingDisciminatorOfWrongType() {
        var de = toCbor("\"hi\"");
        var cbor = de.readDocument();

        assertThat(cbor.discriminator(), nullValue());
    }

    @Test
    public void findsDiscriminatorForAbsoluteShapeId() {
        var de = toCbor("{\"__type\":\"com.example#Foo\"}");
        var json = de.readDocument();

        assertThat(json.discriminator(), equalTo(ShapeId.from("com.example#Foo")));
    }

    @Test
    public void findsDiscriminatorForRelativeShapeId() {
        var ns = "com.foo";
        var de = toCbor("{\"__type\":\"Foo\"}",
                JsonSettings.builder().defaultNamespace(ns).build(),
                CborSettings.builder().defaultNamespace(ns).build());
        var doc = de.readDocument();

        assertThat(doc.discriminator(), equalTo(ShapeId.from("com.foo#Foo")));
    }

    @Test
    public void failsToParseRelativeDiscriminatorWithNoDefaultNamespace() {
        toCbor("{\"__type\":\"Foo\"}").readDocument();
    }
}
