/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static java.nio.ByteBuffer.wrap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

public class JsonDocumentTest extends ProviderTestBase {

    private static final String FOO_B64 = "Zm9v";

    @PerProvider
    public void convertsNumberToNumber(JsonSerdeProvider provider) {
        var codec = codec(provider);
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

    @PerProvider
    public void convertsDoubleToNumber(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var de = codec.createDeserializer("1.1".getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.DOUBLE));
        assertThat(document.asFloat(), is(1.1f));
        assertThat(document.asDouble(), is(1.1));
    }

    @PerProvider
    public void convertsToBoolean(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var de = codec.createDeserializer("true".getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.BOOLEAN));
        assertThat(document.asBoolean(), is(true));
    }

    @PerProvider
    public void convertsToTimestampWithEpochSeconds(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var de = codec.createDeserializer("0".getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.INTEGER));
        assertThat(document.asTimestamp(), equalTo(Instant.EPOCH));
    }

    @PerProvider
    public void convertsToTimestampWithDefaultStringFormat(JsonSerdeProvider provider) {
        var now = Instant.now();
        var codec = codecBuilder(provider).defaultTimestampFormat(TimestampFormatter.Prelude.DATE_TIME).build();
        var de = codec.createDeserializer(("\"" + now + "\"").getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.STRING));
        assertThat(document.asTimestamp(), equalTo(now));
    }

    @PerProvider
    public void convertsToTimestampFailsOnUnknownType(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var de = codec.createDeserializer("true".getBytes(StandardCharsets.UTF_8));
        var document = de.readDocument();

        var e = Assertions.assertThrows(SerializationException.class, document::asTimestamp);
        assertThat(e.getMessage(), containsString("Expected a timestamp document"));
    }

    @PerProvider
    public void convertsToBlob(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var de = codec.createDeserializer(("\"" + FOO_B64 + "\"").getBytes(StandardCharsets.UTF_8));
        var document = de.readDocument();

        assertThat(document.type(), is(ShapeType.STRING));

        // Reading here as a blob will base64 decode the value.
        assertThat(document.asBlob(), equalTo(wrap("foo".getBytes(StandardCharsets.UTF_8))));
    }

    @PerProvider
    public void convertsToList(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var de = codec.createDeserializer("[1, 2, 3]".getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.LIST));
        assertThat(document.size(), is(3));

        var list = document.asList();
        assertThat(list, hasSize(3));
        assertThat(list.get(0).type(), is(ShapeType.INTEGER));
        assertThat(list.get(0).asInteger(), is(1));
        assertThat(list.get(1).asInteger(), is(2));
        assertThat(list.get(2).asInteger(), is(3));
    }

    @PerProvider
    public void convertsToMap(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var de = codec.createDeserializer("{\"a\":1,\"b\":true}".getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.type(), is(ShapeType.MAP));
        assertThat(document.size(), is(2));

        var map = document.asStringMap();
        assertThat(map.keySet(), hasSize(2));
        assertThat(map.get("a").type(), is(ShapeType.INTEGER));
        assertThat(map.get("a").asInteger(), is(1));
        assertThat(document.getMember("a").type(), is(ShapeType.INTEGER));

        assertThat(map.get("b").type(), is(ShapeType.BOOLEAN));
        assertThat(map.get("b").asBoolean(), is(true));
        assertThat(document.getMember("b").type(), is(ShapeType.BOOLEAN));
    }

    @PerProvider
    public void otherDocumentsReturnSizeOfNegativeOne(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var de = codec.createDeserializer("1".getBytes(StandardCharsets.UTF_8));
        var document = de.readDocument();

        assertThat(document.size(), is(-1));
    }

    @PerProvider
    public void nullAndMissingMapMembersReturnsNull(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var de = codec.createDeserializer("{\"a\":null}".getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertThat(document.getMember("c"), nullValue());
        assertThat(document.getMember("d"), nullValue());
    }

    @PerProvider
    public void nullMapMemberRoundtrip(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var doc = codec.createDeserializer("{\"a\":null}".getBytes(StandardCharsets.UTF_8)).readDocument();
        var roundtrip = codec.createDeserializer(codec.serialize(doc)).readDocument();

        assertEquals(doc, roundtrip);
    }

    @PerProvider
    public void nullListMemberRoundtrip(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var doc = codec.createDeserializer("[null]".getBytes(StandardCharsets.UTF_8)).readDocument();
        var roundtrip = codec.createDeserializer(codec.serialize(doc)).readDocument();

        assertEquals(doc, roundtrip);
    }

    @PerProvider
    public void nullDocument(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var de = codec.createDeserializer("null".getBytes(StandardCharsets.UTF_8));

        var document = de.readDocument();
        assertNull(document);
    }

    @ParameterizedTest
    @MethodSource("failToConvertSource")
    public void failToConvert(JsonSerdeProvider provider, String json, Consumer<Document> consumer) {
        var codec = codec(provider);
        var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
        var document = de.readDocument();

        Assertions.assertThrows(SerializationException.class, () -> consumer.accept(document));
    }

    public static List<Arguments> failToConvertSource() {
        var tests = List.of(
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

        var result = new ArrayList<Arguments>();
        for (var provider : providers()) {
            for (var test : tests) {
                result.add(Arguments.of(provider.get()[0], test.get()[0], test.get()[1]));
            }
        }
        return result;
    }

    @ParameterizedTest
    @MethodSource("serializeContentSource")
    public void serializeContent(JsonSerdeProvider provider, String json) {
        var codec = codec(provider);
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
    public void serializeDocument(JsonSerdeProvider provider, String json) {
        var codec = codec(provider);
        var sink = new ByteArrayOutputStream();
        var se = codec.createSerializer(sink);
        var de = codec.createDeserializer(json.getBytes(StandardCharsets.UTF_8));
        var document = de.readDocument();
        document.serialize(se);
        se.flush();

        assertThat(sink.toString(StandardCharsets.UTF_8), equalTo(json));
    }

    public static List<Arguments> serializeContentSource() {
        var tests = List.of(
                Arguments.of("true"),
                Arguments.of("false"),
                Arguments.of("1"),
                Arguments.of("1.1"),
                Arguments.of("[1,2,3]"),
                Arguments.of("{\"a\":1,\"b\":[1,true,-20,\"hello\"]}"));

        var result = new ArrayList<Arguments>();
        for (var provider : providers()) {
            for (var test : tests) {
                result.add(Arguments.of(provider.get()[0], test.get()[0]));
            }
        }
        return result;
    }

    @PerProvider
    public void deserializesIntoBuilderWithJsonNameAndTimestampFormat(JsonSerdeProvider provider) {
        String date = Instant.EPOCH.toString();
        var json = "{\"name\":\"Hank\",\"BINARY\":\"" + FOO_B64 + "\",\"date\":\"" + date + "\",\"numbers\":[1,2,3]}";
        var codec = codecBuilder(provider).useTimestampFormat(true).useJsonName(true).build();
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

    @PerProvider
    public void deserializesIntoBuilder(JsonSerdeProvider provider) {
        var json = "{\"name\":\"Hank\",\"binary\":\"" + FOO_B64 + "\",\"date\":0,\"numbers\":[1,2,3]}";
        var codec = codec(provider);
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
        public void serialize(ShapeSerializer encoder) {
            throw new UnsupportedOperationException();
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
    public void checkEquality(JsonSerdeProvider provider, String left, String right, boolean equal) {
        var codec = codec(provider);

        var de1 = codec.createDeserializer(left.getBytes(StandardCharsets.UTF_8));
        var leftValue = de1.readDocument();

        var de2 = codec.createDeserializer(right.getBytes(StandardCharsets.UTF_8));
        var rightValue = de2.readDocument();

        assertThat(leftValue.equals(rightValue), is(equal));
    }

    public static List<Arguments> checkEqualitySource() {
        var tests = List.of(
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

        var result = new ArrayList<Arguments>();
        for (var provider : providers()) {
            for (var test : tests) {
                result.add(Arguments.of(provider.get()[0], test.get()[0], test.get()[1], test.get()[2]));
            }
        }
        return result;
    }

    @Test
    @Disabled //TODO revisit if this test makes sense. See https://github.com/smithy-lang/smithy-java/pull/629
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
    @Disabled //TODO revisit if this test makes sense. See https://github.com/smithy-lang/smithy-java/pull/629
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

    @PerProvider
    public void canNormalizeJsonDocuments(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var de = codec.createDeserializer("true".getBytes(StandardCharsets.UTF_8));
        var json = de.readDocument();

        assertThat(Document.equals(json, Document.of(true)), is(true));
    }

    @PerProvider
    public void returnsNullWhenGettingDisciminatorOfWrongType(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var de = codec.createDeserializer("\"hi\"".getBytes(StandardCharsets.UTF_8));
        var json = de.readDocument();

        assertThat(json.discriminator(), nullValue());
    }

    @PerProvider
    public void findsDiscriminatorForAbsoluteShapeId(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var de = codec.createDeserializer("{\"__type\":\"com.example#Foo\"}".getBytes(StandardCharsets.UTF_8));
        var json = de.readDocument();

        assertThat(json.discriminator(), equalTo(ShapeId.from("com.example#Foo")));
    }

    @PerProvider
    public void findsDiscriminatorForRelativeShapeId(JsonSerdeProvider provider) {
        var codec = codecBuilder(provider).defaultNamespace("com.foo").build();
        var de = codec.createDeserializer("{\"__type\":\"Foo\"}".getBytes(StandardCharsets.UTF_8));
        var json = de.readDocument();

        assertThat(json.discriminator(), equalTo(ShapeId.from("com.foo#Foo")));
    }

    @PerProvider
    public void failsToParseRelativeDiscriminatorWithNoDefaultNamespace(JsonSerdeProvider provider) {
        var codec = codec(provider);
        var de = codec.createDeserializer("{\"__type\":\"Foo\"}".getBytes(StandardCharsets.UTF_8));
        var json = de.readDocument();
    }
}
