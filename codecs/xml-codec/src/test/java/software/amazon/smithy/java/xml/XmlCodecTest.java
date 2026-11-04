/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.BigNumbers;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.model.traits.XmlAttributeTrait;

public class XmlCodecTest extends ProviderTestBase {

    @Test
    public void defaultsToSmithyProvider() {
        try (var codec = XmlCodec.builder().build();
                var serializer = codec.createSerializer(new ByteArrayOutputStream())) {
            assertInstanceOf(LazyXmlSerializer.class, serializer);
        }
    }

    @PerProvider
    public void deserializesXml(boolean useNative) {
        var xml = """
                <Foo>
                    <date>2006-03-01T00:00:00Z</date>
                    <name>Hello</name>
                    <numbers>
                        <member>1</member>
                        <member>2</member>
                        <member>3</member>
                    </numbers>
                </Foo>
                """;

        try (var codec = XmlCodec.builder().useNative(useNative).build()) {
            var pojo = codec.deserializeShape(xml, new TestPojo.Builder());
            assertThat(pojo.name, equalTo("Hello"));
            assertThat(pojo.date, equalTo(Instant.parse("2006-03-01T00:00:00Z")));
            assertThat(pojo.numbers, contains(1, 2, 3));
        }
    }

    // The shape's root element is <Foo>; this document wraps the same members in a different root name.
    private static final String WRONG_ROOT = """
            <CopyObjectResult>
                <name>Hello</name>
                <numbers>
                    <member>1</member>
                    <member>2</member>
                </numbers>
            </CopyObjectResult>
            """;

    @PerProvider
    public void strictRootElementRejectsMismatchedRoot(boolean useNative) {
        // Default (strict) — a root name that doesn't match the shape is rejected. Server behavior.
        try (var codec = XmlCodec.builder().useNative(useNative).build()) {
            assertThrows(SerializationException.class,
                    () -> codec.deserializeShape(WRONG_ROOT, new TestPojo.Builder()));
        }
    }

    @PerProvider
    public void lenientRootElementToleratesMismatchedRoot(boolean useNative) {
        // strictRootElement(false) — the mismatched root is ignored and the children are read. Client
        // behavior, e.g. S3's <CopyObjectResult> for a CopyObjectOutput shape.
        try (var codec = XmlCodec.builder().useNative(useNative).strictRootElement(false).build()) {
            var pojo = codec.deserializeShape(WRONG_ROOT, new TestPojo.Builder());
            assertThat(pojo.name, equalTo("Hello"));
            assertThat(pojo.numbers, contains(1, 2));
        }
    }

    @PerProvider
    public void matchingRootStillWorksInBothModes(boolean useNative) {
        // A correct root name (<Foo>) deserializes regardless of the strict setting.
        var xml = "<Foo><name>Hi</name></Foo>";
        try (var strict = XmlCodec.builder().useNative(useNative).build();
                var lenient = XmlCodec.builder().useNative(useNative).strictRootElement(false).build()) {
            assertThat(strict.deserializeShape(xml, new TestPojo.Builder()).name, equalTo("Hi"));
            assertThat(lenient.deserializeShape(xml, new TestPojo.Builder()).name, equalTo("Hi"));
        }
    }

    @PerProvider
    public void deserializesEmptyBody(boolean useNative) {
        try (var codec = XmlCodec.builder().useNative(useNative).build()) {
            var pojo = codec.deserializeShape(ByteBuffer.allocate(0), new TestPojo.Builder());
            assertThat(pojo.name, equalTo(null));
            assertThat(pojo.date, equalTo(null));
            assertThat(pojo.numbers, equalTo(List.of()));
        }
    }

    @PerProvider
    public void serializesXml(boolean useNative) {
        try (var codec = XmlCodec.builder().useNative(useNative).build()) {
            var builder = new TestPojo.Builder();
            builder.name = "Hello";
            builder.date = Instant.parse("2006-03-01T00:00:00Z");
            builder.numbers.addAll(List.of(1, 2, 3));
            var pojo = builder.build();

            var xml = codec.serializeToString(pojo);
            assertThat(
                    xml,
                    equalTo(
                            "<Foo><name>Hello</name><date>2006-03-01T00:00:00Z</date><numbers><member>1</member><member>2</member><member>3</member></numbers></Foo>"));
        }
    }

    @PerProvider
    public void deserializesMapWithEmptyBlobValue(boolean useNative) {
        var xml = "<MapPojo><value><entry><key>k1</key><value></value></entry>"
                + "<entry><key>k2</key><value>AQID</value></entry></value></MapPojo>";

        try (var codec = XmlCodec.builder().useNative(useNative).build()) {
            var pojo = codec.deserializeShape(xml, new MapPojo.Builder());
            assertThat(pojo.value.size(), equalTo(2));
            assertThat(pojo.value.get("k1"), equalTo(ByteBuffer.wrap(new byte[0])));
            assertThat(pojo.value.get("k2"), equalTo(ByteBuffer.wrap(new byte[] {1, 2, 3})));
        }
    }

    @Test
    public void nativeAndStaxProduceSameResultForBlobMap() {
        var xml = "<MapPojo><value>"
                + "<entry><key>k1</key><value></value></entry>"
                + "<entry><key>k2</key><value>AQID</value></entry>"
                + "<entry><key>k3</key><value>BAUG</value></entry>"
                + "<entry><key>k4</key><value>BwgJ</value></entry>"
                + "</value></MapPojo>";

        try (var stax = XmlCodec.builder().useNative(false).build();
                var native_ = XmlCodec.builder().useNative(true).build()) {
            var staxResult = stax.deserializeShape(xml, new MapPojo.Builder());
            var nativeResult = native_.deserializeShape(xml, new MapPojo.Builder());
            assertEquals(staxResult.value.size(),
                    nativeResult.value.size(),
                    "Map sizes differ: stax=" + staxResult.value + " native=" + nativeResult.value);
            for (var entry : staxResult.value.entrySet()) {
                assertEquals(entry.getValue(),
                        nativeResult.value.get(entry.getKey()),
                        "Mismatch for key=" + entry.getKey());
            }
        }
    }

    @Test
    public void failedSerializationDoesNotPoisonThePooledSerializer() {
        var poison = new SerializableStruct() {
            @Override
            public Schema schema() {
                return TestPojo.SCHEMA;
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                throw new IllegalStateException("boom");
            }

            @Override
            public <T> T getMemberValue(Schema member) {
                throw new UnsupportedOperationException();
            }
        };
        var builder = new TestPojo.Builder();
        builder.name = "Hello";
        var good = builder.build();

        try (var codec = XmlCodec.builder().useNative(true).build()) {
            assertThrows(IllegalStateException.class, () -> codec.serializeToDataStream(poison, "application/xml"));
            var leased = codec.serializeToDataStream(good, "application/xml");
            assertThat(StandardCharsets.UTF_8.decode(leased.asByteBuffer()).toString(),
                    equalTo("<Foo><name>Hello</name><numbers></numbers></Foo>"));

            assertThrows(IllegalStateException.class, () -> codec.serialize(poison));
            assertThat(codec.serializeToString(good), equalTo("<Foo><name>Hello</name><numbers></numbers></Foo>"));
        }
    }

    @Test
    public void closingTheLazySerializerTwiceReleasesItsDelegateOnce() throws Exception {
        try (var codec = XmlCodec.builder().useNative(true).build()) {
            var builder = new TestPojo.Builder();
            builder.name = "Hello";
            var pojo = builder.build();

            var out = new ByteArrayOutputStream();
            var serializer = codec.createSerializer(out);
            pojo.serialize(serializer);
            serializer.close();
            serializer.close();
            assertThat(out.toString(StandardCharsets.UTF_8),
                    equalTo("<Foo><name>Hello</name><numbers></numbers></Foo>"));
            assertThrows(IllegalStateException.class, () -> pojo.serialize(serializer));

            var first = (LazyXmlSerializer) codec.createSerializer(new ByteArrayOutputStream());
            var second = (LazyXmlSerializer) codec.createSerializer(new ByteArrayOutputStream());
            pojo.serialize(first);
            pojo.serialize(second);
            assertThat(first.delegateForTest() == second.delegateForTest(), equalTo(false));
            first.close();
            second.close();
        }
    }

    private static final class MapPojo implements SerializableStruct {

        private static final ShapeId ID = ShapeId.from("smithy.example#MapPojo");

        private static final Schema BLOB_MAP = Schema.mapBuilder(ShapeId.from("smithy.example#BlobMap"))
                .putMember("key", PreludeSchemas.STRING)
                .putMember("value", PreludeSchemas.BLOB)
                .build();

        private static final Schema SCHEMA = Schema.structureBuilder(ID)
                .putMember("value", BLOB_MAP)
                .build();

        private static final Schema VALUE = SCHEMA.member("value");

        private final Map<String, ByteBuffer> value;

        MapPojo(Builder builder) {
            this.value = builder.value;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (value != null) {
                serializer.writeMap(VALUE, value, value.size(), (map, ms) -> {
                    for (var e : map.entrySet()) {
                        ms.writeEntry(BLOB_MAP.mapKeyMember(),
                                e.getKey(),
                                e.getValue(),
                                (v, s) -> s.writeBlob(BLOB_MAP.mapValueMember(), v));
                    }
                });
            }
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }

        private static final class Builder implements ShapeBuilder<MapPojo> {
            private Map<String, ByteBuffer> value = new LinkedHashMap<>();

            @Override
            public Schema schema() {
                return SCHEMA;
            }

            @Override
            public Builder deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, (pojo, member, deser) -> {
                    if (member.memberName().equals("value")) {
                        deser.readStringMap(VALUE, pojo.value, (map, key, de) -> {
                            map.put(key, de.readBlob(BLOB_MAP.mapValueMember()));
                        });
                    }
                });
                return this;
            }

            @Override
            public MapPojo build() {
                return new MapPojo(this);
            }
        }
    }

    private static final String MAX_DIGITS = "7".repeat(BigNumbers.MAX_DIGITS);
    private static final String TOO_MANY_DIGITS = "7".repeat(BigNumbers.MAX_TEXT_DIGITS + 1);
    private static final String TOO_MANY_SIGNIFICANT_DIGITS = "7".repeat(BigNumbers.MAX_TEXT_DIGITS);
    private static final String TOO_MANY_FULLWIDTH_DIGITS = "\uFF17".repeat(BigNumbers.MAX_TEXT_DIGITS + 1);
    private static final String FAR_TOO_MANY_DIGITS = "1".repeat(2_000_000);

    @PerProvider
    @Timeout(10)
    public void readsBigNumbersWithMaxDigits(boolean useNative) {
        var decimal = "-" + "1".repeat(BigNumbers.MAX_DIGITS - 1) + ".1";
        var xml = "<Foo bigAttribute=\"-" + MAX_DIGITS + "\"><bigInteger>" + MAX_DIGITS + "</bigInteger><bigDecimal>"
                + decimal + "</bigDecimal></Foo>";
        var pojo = deserializePojo(useNative, xml);
        assertThat(pojo.bigInteger, equalTo(new BigInteger(MAX_DIGITS)));
        assertThat(pojo.bigDecimal, equalTo(new BigDecimal(decimal)));
        assertThat(pojo.bigAttribute, equalTo(new BigInteger("-" + MAX_DIGITS)));
    }

    @PerProvider
    @Timeout(10)
    public void rejectsBigNumberElementsOverMaxDigits(boolean useNative) {
        for (var digits : List.of(TOO_MANY_DIGITS, FAR_TOO_MANY_DIGITS, TOO_MANY_SIGNIFICANT_DIGITS)) {
            assertThrows(SerializationException.class,
                    () -> deserializePojo(useNative, "<Foo><bigInteger>" + digits + "</bigInteger></Foo>"));
            assertThrows(SerializationException.class,
                    () -> deserializePojo(useNative, "<Foo><bigDecimal>" + digits + ".5</bigDecimal></Foo>"));
        }
    }

    @PerProvider
    @Timeout(10)
    public void rejectsBigNumberCdataOverMaxDigits(boolean useNative) {
        var ok = deserializePojo(useNative, "<Foo><bigInteger><![CDATA[" + MAX_DIGITS + "]]></bigInteger></Foo>");
        assertThat(ok.bigInteger, equalTo(new BigInteger(MAX_DIGITS)));
        assertThrows(SerializationException.class,
                () -> deserializePojo(useNative,
                        "<Foo><bigInteger><![CDATA[" + TOO_MANY_DIGITS + "]]></bigInteger></Foo>"));
        assertThrows(SerializationException.class,
                () -> deserializePojo(useNative,
                        "<Foo><bigDecimal><![CDATA[" + FAR_TOO_MANY_DIGITS + "]]></bigDecimal></Foo>"));
    }

    @PerProvider
    @Timeout(10)
    public void rejectsBigNumberAttributesOverMaxDigits(boolean useNative) {
        assertThrows(SerializationException.class,
                () -> deserializePojo(useNative, "<Foo bigAttribute=\"" + TOO_MANY_DIGITS + "\"/>"));
        assertThrows(SerializationException.class,
                () -> deserializePojo(useNative, "<Foo bigAttribute=\"" + FAR_TOO_MANY_DIGITS + "\"/>"));
        assertThrows(SerializationException.class,
                () -> deserializePojo(useNative, "<Foo bigAttribute=\"" + TOO_MANY_SIGNIFICANT_DIGITS + "\"/>"));
        var zeros = "0".repeat(BigNumbers.MAX_TEXT_DIGITS - BigNumbers.MAX_DIGITS);
        var padded = deserializePojo(useNative, "<Foo bigAttribute=\"" + zeros + MAX_DIGITS + "\"/>");
        assertThat(padded.bigAttribute, equalTo(new BigInteger(MAX_DIGITS)));
    }

    @PerProvider
    @Timeout(10)
    public void rejectsNonAsciiDigitsOverMaxDigits(boolean useNative) {
        assertThrows(SerializationException.class,
                () -> deserializePojo(useNative, "<Foo bigAttribute=\"" + TOO_MANY_FULLWIDTH_DIGITS + "\"/>"));
        assertThrows(SerializationException.class,
                () -> deserializePojo(useNative,
                        "<Foo><bigInteger><![CDATA[" + TOO_MANY_FULLWIDTH_DIGITS + "]]></bigInteger></Foo>"));
        assertThrows(SerializationException.class,
                () -> deserializePojo(useNative,
                        "<Foo><bigDecimal>" + TOO_MANY_FULLWIDTH_DIGITS + ".5</bigDecimal></Foo>"));
        var huge = "\uFF17".repeat(300_000);
        assertThrows(SerializationException.class,
                () -> deserializePojo(useNative, "<Foo bigAttribute=\"" + huge + "\"/>"));
    }

    @PerProvider
    public void malformedBigNumbersFailAsSerializationException(boolean useNative) {
        assertThrows(SerializationException.class,
                () -> deserializePojo(useNative, "<Foo><bigInteger>1.5</bigInteger></Foo>"));
        assertThrows(SerializationException.class,
                () -> deserializePojo(useNative, "<Foo><bigDecimal>abc</bigDecimal></Foo>"));
        assertThrows(SerializationException.class,
                () -> deserializePojo(useNative, "<Foo><bigInteger><![CDATA[1.5]]></bigInteger></Foo>"));
        assertThrows(SerializationException.class,
                () -> deserializePojo(useNative, "<Foo bigAttribute=\"abc\"/>"));
    }

    @PerProvider
    public void bigDecimalWithExtremeScaleRoundTripsWithoutExpansion(boolean useNative) throws Exception {
        var builder = new TestPojo.Builder();
        builder.bigDecimal = new BigDecimal("1812031E+900000000");
        var pojo = builder.build();
        try (var codec = codec(useNative); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                pojo.serialize(serializer);
            }
            var xml = output.toString(StandardCharsets.UTF_8);
            assertThat(xml, containsString("1.812031E+900000006"));
            assertThat(deserializePojo(useNative, xml).bigDecimal, equalTo(pojo.bigDecimal));
        }
    }

    private static TestPojo deserializePojo(boolean useNative, String xml) {
        try (var codec = codec(useNative)) {
            return codec.deserializeShape(xml, new TestPojo.Builder());
        }
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
                .putMember("bigInteger", PreludeSchemas.BIG_INTEGER)
                .putMember("bigDecimal", PreludeSchemas.BIG_DECIMAL)
                .putMember("bigAttribute", PreludeSchemas.BIG_INTEGER, new XmlAttributeTrait())
                .build();

        private static final Schema NAME = SCHEMA.member("name");
        private static final Schema BINARY = SCHEMA.member("binary");
        private static final Schema DATE = SCHEMA.member("date");
        private static final Schema NUMBERS = SCHEMA.member("numbers");
        private static final Schema BIG_INTEGER = SCHEMA.member("bigInteger");
        private static final Schema BIG_DECIMAL = SCHEMA.member("bigDecimal");
        private static final Schema BIG_ATTRIBUTE = SCHEMA.member("bigAttribute");

        private final String name;
        private final ByteBuffer binary;
        private final Instant date;
        private final List<Integer> numbers;
        private final BigInteger bigInteger;
        private final BigDecimal bigDecimal;
        private final BigInteger bigAttribute;

        TestPojo(Builder builder) {
            this.name = builder.name;
            this.binary = builder.binary;
            this.date = builder.date;
            this.numbers = builder.numbers;
            this.bigInteger = builder.bigInteger;
            this.bigDecimal = builder.bigDecimal;
            this.bigAttribute = builder.bigAttribute;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (bigAttribute != null) {
                serializer.writeBigInteger(BIG_ATTRIBUTE, bigAttribute);
            }
            if (name != null) {
                serializer.writeString(NAME, name);
            }
            if (binary != null) {
                serializer.writeBlob(BINARY, binary);
            }
            if (date != null) {
                serializer.writeTimestamp(DATE, date);
            }
            if (numbers != null) {
                serializer.writeList(NUMBERS, numbers, numbers.size(), (elements, ser) -> {
                    for (var e : elements) {
                        ser.writeInteger(PreludeSchemas.INTEGER, e);
                    }
                });
            }
            if (bigInteger != null) {
                serializer.writeBigInteger(BIG_INTEGER, bigInteger);
            }
            if (bigDecimal != null) {
                serializer.writeBigDecimal(BIG_DECIMAL, bigDecimal);
            }
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

        private static final class Builder implements ShapeBuilder<TestPojo> {

            private String name;
            private ByteBuffer binary;
            private Instant date;
            private final List<Integer> numbers = new ArrayList<>();
            private BigInteger bigInteger;
            private BigDecimal bigDecimal;
            private BigInteger bigAttribute;

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
                        case "bigInteger" -> pojo.bigInteger = deser.readBigInteger(BIG_INTEGER);
                        case "bigDecimal" -> pojo.bigDecimal = deser.readBigDecimal(BIG_DECIMAL);
                        case "bigAttribute" -> pojo.bigAttribute = deser.readBigInteger(BIG_ATTRIBUTE);
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
}
