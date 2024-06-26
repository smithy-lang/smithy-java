/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import static java.nio.ByteBuffer.wrap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

public class JsonDocumentTest {

    private static final String FOO_B64 = "Zm9v";

    @Test
    public void convertsNumberToNumber() {
        var codec = JsonCodec.builder().build();
        var de = codec.createDeserializer("120".getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.INTEGER));
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
        var codec = JsonCodec.builder().build();
        var de = codec.createDeserializer("1.1".getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.DOUBLE));
        assertThat(document.asFloat(), is(1.1f));
        assertThat(document.asDouble(), is(1.1));
    }

    @Test
    public void convertsToBoolean() {
        var codec = JsonCodec.builder().build();
        var de = codec.createDeserializer("true".getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.BOOLEAN));
        assertThat(document.asBoolean(), is(true));
    }

    @Test
    public void convertsToTimestampWithEpochSeconds() {
        var codec = JsonCodec.builder().build();
        var de = codec.createDeserializer("0".getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.INTEGER));
        assertThat(document.asTimestamp(), equalTo(Instant.EPOCH));
    }

    @Test
    public void convertsToTimestampWithDefaultStringFormat() {
        var now = Instant.now();
        var codec = JsonCodec.builder().defaultTimestampFormat(TimestampFormatter.Prelude.DATE_TIME).build();
        var de = codec.createDeserializer(("\"" + now + "\"").getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.STRING));
        assertThat(document.asTimestamp(), equalTo(now));
    }

    @Test
    public void convertsToTimestampFailsOnUnknownType() {
        var codec = JsonCodec.builder().build();
        var de = codec.createDeserializer("true".getBytes(StandardCharsets.UTF_8));
        var document = de.readDocument();

        var e = Assertions.assertThrows(SerializationException.class, document::asTimestamp);
        assertThat(e.getMessage(), containsString("Expected a timestamp, but found boolean"));
    }

    @Test
    public void convertsToBlob() {
        var codec = JsonCodec.builder().build();
        var de = codec.createDeserializer(("\"" + FOO_B64 + "\"").getBytes(StandardCharsets.UTF_8));
        var document = de.readDocument();

        assertThat(document.type(), is(ShapeType.STRING));

        // Reading here as a blob will base64 decode the value.
        assertThat(document.asBlob(), equalTo(wrap("foo".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void convertsToList() {
        var codec = JsonCodec.builder().build();
        var de = codec.createDeserializer("[1, 2, 3]".getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.LIST));

        var list = document.asList();
        assertThat(list, hasSize(3));
        assertThat(list.get(0).type(), is(ShapeType.INTEGER));
        assertThat(list.get(0).asInteger(), is(1));
        assertThat(list.get(1).asInteger(), is(2));
        assertThat(list.get(2).asInteger(), is(3));
    }

    @Test
    public void convertsToMap() {
        var codec = JsonCodec.builder().build();
        var de = codec.createDeserializer("{\"a\":1,\"b\":true}".getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.MAP));

        var map = document.asStringMap();
        assertThat(map.keySet(), hasSize(2));
        assertThat(map.get("a").type(), is(ShapeType.INTEGER));
        assertThat(map.get("a").asInteger(), is(1));
        assertThat(document.getMember("a").type(), is(ShapeType.INTEGER));

        assertThat(map.get("b").type(), is(ShapeType.BOOLEAN));
        assertThat(map.get("b").asBoolean(), is(true));
        assertThat(document.getMember("b").type(), is(ShapeType.BOOLEAN));
    }

    @Test
    public void nullAndMissingMapMembersReturnsNull() {
        var codec = JsonCodec.builder().build();
        var de = codec.createDeserializer("{\"a\":null}".getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.getMember("c"), nullValue());
        assertThat(document.getMember("d"), nullValue());
    }

    @ParameterizedTest
    @MethodSource("failToConvertSource")
    public void failToConvert(String json, Consumer<Document> consumer) {
        var codec = JsonCodec.builder().build();
        var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
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
            Arguments.of("\"1\"", (Consumer<Document>) Document::asBigDecimal)
        );
    }

    @ParameterizedTest
    @MethodSource("serializeContentSource")
    public void serializeContent(String json) {
        var codec = JsonCodec.builder().build();
        var sink = new ByteArrayOutputStream();
        var se = codec.createSerializer(sink);
        var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
        var document = de.readDocument();
        document.serializeContents(se);
        se.flush();

        assertThat(sink.toString(StandardCharsets.UTF_8), equalTo(json));
    }

    @ParameterizedTest
    @MethodSource("serializeContentSource")
    public void serializeDocument(String json) {
        var codec = JsonCodec.builder().build();
        var sink = new ByteArrayOutputStream();
        var se = codec.createSerializer(sink);
        var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
        var document = de.readDocument();
        document.serialize(se);
        se.flush();

        assertThat(sink.toString(StandardCharsets.UTF_8), equalTo(json));
    }

    public static List<Arguments> serializeContentSource() {
        return List.of(
            Arguments.of("true"),
            Arguments.of("false"),
            Arguments.of("1"),
            Arguments.of("1.1"),
            Arguments.of("[1,2,3]"),
            Arguments.of("{\"a\":1,\"b\":[1,true,-20,\"hello\"]}")
        );
    }

    @Test
    public void deserializesIntoBuilderWithJsonNameAndTimestampFormat() {
        String date = Instant.EPOCH.toString();
        var json = "{\"name\":\"Hank\",\"BINARY\":\"" + FOO_B64 + "\",\"date\":\"" + date + "\",\"numbers\":[1,2,3]}";
        var codec = JsonCodec.builder().useTimestampFormat(true).useJsonName(true).build();
        var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
        var document = de.readDocument();

        var builder = new TestPojo.Builder();
        document.deserializeInto(builder);
        var pojo = builder.build();

        assertThat(pojo.name, equalTo("Hank"));
        assertThat(pojo.binary, equalTo(wrap("foo".getBytes(StandardCharsets.UTF_8))));
        assertThat(pojo.date, equalTo(Instant.EPOCH));
        assertThat(pojo.numbers, equalTo(List.of(1, 2, 3)));
    }

    @Test
    public void deserializesIntoBuilder() {
        var json = "{\"name\":\"Hank\",\"binary\":\"" + FOO_B64 + "\",\"date\":0,\"numbers\":[1,2,3]}";
        var codec = JsonCodec.builder().build();
        var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
        var document = de.readDocument();

        var builder = new TestPojo.Builder();
        document.deserializeInto(builder);
        var pojo = builder.build();

        assertThat(pojo.name, equalTo("Hank"));
        assertThat(pojo.binary, equalTo(wrap("foo".getBytes(StandardCharsets.UTF_8))));
        assertThat(pojo.date, equalTo(Instant.EPOCH));
        assertThat(pojo.numbers, equalTo(List.of(1, 2, 3)));
    }

    private static final class TestPojo implements SerializableShape {

        private static final ShapeId ID = ShapeId.from("smithy.example#Foo");

        private static final Schema NAME = Schema.memberBuilder("name", PreludeSchemas.STRING)
            .id(ID)
            .build();

        private static final Schema BINARY = Schema.memberBuilder("binary", PreludeSchemas.BLOB)
            .id(ID)
            .traits(new JsonNameTrait("BINARY"))
            .build();

        private static final Schema DATE = Schema.memberBuilder("date", PreludeSchemas.TIMESTAMP)
            .id(ID)
            .traits(new TimestampFormatTrait(TimestampFormatTrait.DATE_TIME))
            .build();

        private static final Schema NUMBERS_LIST = Schema.builder()
            .type(ShapeType.LIST)
            .id("smithy.example#Numbers")
            .members(Schema.memberBuilder("member", PreludeSchemas.INTEGER))
            .build();

        private static final Schema NUMBERS = Schema.memberBuilder("numbers", NUMBERS_LIST)
            .id(ID)
            .build();

        private static final Schema SCHEMA = Schema.builder()
            .id(ID)
            .type(ShapeType.STRUCTURE)
            .members(NAME, BINARY, DATE, NUMBERS)
            .build();

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
        public void serialize(ShapeSerializer encoder) {
            throw new UnsupportedOperationException();
        }

        private static final class Builder implements ShapeBuilder<TestPojo> {

            private String name;
            private ByteBuffer binary;
            private Instant date;
            private final List<Integer> numbers = new ArrayList<>();

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
        var codec = JsonCodec.builder().build();

        var de1 = codec.createDeserializer(left.getBytes(StandardCharsets.UTF_8));
        var leftValue = de1.readDocument();

        var de2 = codec.createDeserializer(right.getBytes(StandardCharsets.UTF_8));
        var rightValue = de2.readDocument();

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
            Arguments.of("{\"foo\":\"foo\"}", "{\"foo\":\"bar\"}", false)
        );
    }

    @Test
    public void onlyEqualIfBothUseTimestampFormat() {
        var de1 = JsonCodec.builder()
            .useTimestampFormat(true)
            .build()
            .createDeserializer("1".getBytes(StandardCharsets.UTF_8));
        var de2 = JsonCodec.builder()
            .useTimestampFormat(false)
            .build()
            .createDeserializer("1".getBytes(StandardCharsets.UTF_8));

        var leftValue = de1.readDocument();
        var rightValue = de2.readDocument();

        assertThat(leftValue, not(equalTo(rightValue)));
    }

    @Test
    public void onlyEqualIfBothUseJsonName() {
        var de1 = JsonCodec.builder()
            .useJsonName(true)
            .build()
            .createDeserializer("1".getBytes(StandardCharsets.UTF_8));
        var de2 = JsonCodec.builder()
            .useJsonName(false)
            .build()
            .createDeserializer("1".getBytes(StandardCharsets.UTF_8));

        var leftValue = de1.readDocument();
        var rightValue = de2.readDocument();

        assertThat(leftValue, not(equalTo(rightValue)));
    }

    @Test
    public void canNormalizeJsonDocuments() {
        var codec = JsonCodec.builder().build();
        var de = codec.createDeserializer("true".getBytes(StandardCharsets.UTF_8));
        var json = de.readDocument();

        assertThat(Document.equals(json, Document.createBoolean(true)), is(true));
    }
}
