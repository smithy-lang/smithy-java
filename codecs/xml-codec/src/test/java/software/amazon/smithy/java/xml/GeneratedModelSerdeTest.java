/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import smithy.java.xml.test.model.AllListsStruct;
import smithy.java.xml.test.model.BlobStruct;
import smithy.java.xml.test.model.Color;
import smithy.java.xml.test.model.ComplexStruct;
import smithy.java.xml.test.model.FlattenedListStruct;
import smithy.java.xml.test.model.FlattenedMapStruct;
import smithy.java.xml.test.model.InnerStruct;
import smithy.java.xml.test.model.NamespacedStruct;
import smithy.java.xml.test.model.NestedStruct;
import smithy.java.xml.test.model.NumericStruct;
import smithy.java.xml.test.model.RecursiveStruct;
import smithy.java.xml.test.model.SimpleStruct;
import smithy.java.xml.test.model.StringStruct;
import smithy.java.xml.test.model.TimestampStruct;
import smithy.java.xml.test.model.XmlAttributeStruct;
import smithy.java.xml.test.model.XmlNameStruct;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.SerializationException;

public class GeneratedModelSerdeTest extends ProviderTestBase {

    private static <T extends SerializableShape> T roundtrip(
            boolean useNative,
            SerializableShape original,
            ShapeBuilder<T> builder
    ) {
        return roundtrip(useNative, useNative, original, builder);
    }

    private static <T extends SerializableShape> T roundtrip(
            boolean ser,
            boolean de,
            SerializableShape original,
            ShapeBuilder<T> builder
    ) {
        try (var serCodec = XmlCodec.builder().useNative(ser).build();
                var deCodec = XmlCodec.builder().useNative(de).build()) {
            ByteBuffer serialized = serCodec.serialize(original);
            return deCodec.deserializeShape(serialized, builder);
        }
    }

    // --- Simple Struct ---

    @PerProvider
    void simpleStructRoundtrip(boolean useNative) {
        var original = SimpleStruct.builder()
                .name("test")
                .age(42)
                .active(true)
                .score(98.6)
                .createdAt(Instant.parse("2025-01-15T10:30:00Z"))
                .build();
        assertThat(roundtrip(useNative, original, SimpleStruct.builder())).isEqualTo(original);
    }

    // --- Complex Struct ---

    private static ComplexStruct buildComplexStruct() {
        var inner = InnerStruct.builder()
                .value("inner-value")
                .numbers(List.of(1, 2, 3, 4, 5))
                .build();
        var nested = NestedStruct.builder()
                .field1("nested-field")
                .field2(100)
                .inner(inner)
                .build();
        return ComplexStruct.builder()
                .id("bench-001")
                .count(999)
                .enabled(true)
                .ratio(1.618)
                .score(2.718f)
                .bigCount(1_000_000L)
                .optionalString("optional-value")
                .optionalInt(42)
                .createdAt(Instant.parse("2025-06-01T12:00:00Z"))
                .payload(ByteBuffer.wrap("binary-payload-data".getBytes(StandardCharsets.UTF_8)))
                .tags(List.of("alpha", "beta", "gamma", "delta"))
                .intList(List.of(10, 20, 30, 40, 50))
                .metadata(Map.of("key1", "value1", "key2", "value2"))
                .intMap(Map.of("a", 1, "b", 2))
                .nested(nested)
                .optionalNested(NestedStruct.builder()
                        .field1("opt-nested")
                        .field2(200)
                        .build())
                .structList(List.of(nested, nested))
                .structMap(Map.of("first", nested))
                .color(Color.GREEN)
                .colorList(List.of(Color.RED, Color.BLUE, Color.YELLOW))
                .bigIntValue(new BigInteger("123456789012345678901234567890"))
                .bigDecValue(new BigDecimal("99999.99999"))
                .build();
    }

    @Test
    void complexStructRoundtrip() {
        var original = buildComplexStruct();
        assertThat(roundtrip(NATIVE, original, ComplexStruct.builder())).isEqualTo(original);
    }

    // --- Numeric Boundary Roundtrips ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void numericMinValuesRoundtrip(boolean ser, boolean de) {
        var original = NumericStruct.builder()
                .byteVal(Byte.MIN_VALUE)
                .shortVal(Short.MIN_VALUE)
                .intVal(Integer.MIN_VALUE)
                .longVal(Long.MIN_VALUE)
                .floatVal(Float.MIN_VALUE)
                .doubleVal(Double.MIN_VALUE)
                .bigIntVal(new BigInteger("-99999999999999999999999999999"))
                .bigDecVal(new BigDecimal("-99999.99999"))
                .build();
        assertThat(roundtrip(ser, de, original, NumericStruct.builder())).isEqualTo(original);
    }

    @ParameterizedTest
    @MethodSource("crossProviders")
    void numericMaxValuesRoundtrip(boolean ser, boolean de) {
        var original = NumericStruct.builder()
                .byteVal(Byte.MAX_VALUE)
                .shortVal(Short.MAX_VALUE)
                .intVal(Integer.MAX_VALUE)
                .longVal(Long.MAX_VALUE)
                .floatVal(Float.MAX_VALUE)
                .doubleVal(Double.MAX_VALUE)
                .bigIntVal(new BigInteger("99999999999999999999999999999"))
                .bigDecVal(new BigDecimal("123456789.123456789"))
                .build();
        assertThat(roundtrip(ser, de, original, NumericStruct.builder())).isEqualTo(original);
    }

    @PerProvider
    void numericZeroValues(boolean useNative) {
        var original = NumericStruct.builder()
                .byteVal((byte) 0)
                .shortVal((short) 0)
                .intVal(0)
                .longVal(0L)
                .floatVal(0.0f)
                .doubleVal(0.0)
                .bigIntVal(BigInteger.ZERO)
                .bigDecVal(BigDecimal.ZERO)
                .build();
        assertThat(roundtrip(useNative, original, NumericStruct.builder())).isEqualTo(original);
    }

    @PerProvider
    void bigDecimalVariousScales(boolean useNative) {
        for (var bd : List.of(
                new BigDecimal("42"),
                new BigDecimal("1.005"),
                new BigDecimal("1E+10"),
                new BigDecimal("12345678901234567890.12345"))) {
            var original = NumericStruct.builder().bigDecVal(bd).build();
            assertThat(roundtrip(useNative, original, NumericStruct.builder())
                    .getBigDecVal()).isEqualByComparingTo(bd);
        }
    }

    @PerProvider
    void bigIntegerSmallAndLarge(boolean useNative) {
        for (var bi : List.of(
                BigInteger.valueOf(42),
                BigInteger.valueOf(-42),
                new BigInteger("123456789012345678901234567890123456789"))) {
            var original = NumericStruct.builder().bigIntVal(bi).build();
            assertThat(roundtrip(useNative, original, NumericStruct.builder())).isEqualTo(original);
        }
    }

    // --- String Edge Cases ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void stringEdgeCasesRoundtrip(boolean ser, boolean de) {
        // Note: \r is excluded because XML spec Section 2.11 normalizes CR to LF
        for (var s : List.of(
                "",
                "abcdefghijklmnopqrstuvwxyz0123456789",
                "quote:\" backslash:\\ tab:\t newline:\n",
                "é中ü",
                "😀🎉",
                "hello\tworld\né😀\"quoted\"")) {
            var original = StringStruct.builder().value(s).build();
            assertThat(roundtrip(ser, de, original, StringStruct.builder())).isEqualTo(original);
        }
    }

    @ParameterizedTest
    @MethodSource("crossProviders")
    void xmlSpecialCharactersRoundtrip(boolean ser, boolean de) {
        for (var s : List.of(
                "<tag>value</tag>",
                "a & b",
                "1 < 2 > 0",
                "'single' and \"double\" quotes",
                "<![CDATA[not actually cdata]]>")) {
            var original = StringStruct.builder().value(s).build();
            assertThat(roundtrip(ser, de, original, StringStruct.builder())).isEqualTo(original);
        }
    }

    // --- Timestamp Format Roundtrips ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void timestampAllFormatsRoundtrip(boolean ser, boolean de) {
        var epoch = Instant.EPOCH;
        var original = TimestampStruct.builder()
                .epochSeconds(epoch)
                .dateTime(epoch)
                .httpDate(epoch)
                .build();
        assertThat(roundtrip(ser, de, original, TimestampStruct.builder())).isEqualTo(original);
    }

    @ParameterizedTest
    @MethodSource("crossProviders")
    void timestampWithNanoseconds(boolean ser, boolean de) {
        var withNanos = Instant.parse("2025-01-15T10:30:00.123456789Z");
        var original = TimestampStruct.builder().dateTime(withNanos).build();
        assertThat(roundtrip(ser, de, original, TimestampStruct.builder())).isEqualTo(original);
    }

    @PerProvider
    void timestampPreEpoch(boolean useNative) {
        var preEpoch = Instant.parse("1969-12-31T23:59:59Z");
        var original = TimestampStruct.builder()
                .epochSeconds(preEpoch)
                .dateTime(preEpoch)
                .httpDate(preEpoch)
                .build();
        assertThat(roundtrip(useNative, original, TimestampStruct.builder())).isEqualTo(original);
    }

    @PerProvider
    void timestampLeapYear(boolean useNative) {
        var leapDay = LocalDate.of(2024, 2, 29).atStartOfDay(ZoneOffset.UTC).toInstant();
        var original = TimestampStruct.builder().dateTime(leapDay).build();
        assertThat(roundtrip(useNative, original, TimestampStruct.builder())).isEqualTo(original);
    }

    @PerProvider
    void timestampAllMonthsHttpDate(boolean useNative) {
        for (int month = 1; month <= 12; month++) {
            var instant = LocalDate.of(2025, month, 15).atStartOfDay(ZoneOffset.UTC).toInstant();
            var original = TimestampStruct.builder().httpDate(instant).build();
            assertThat(roundtrip(useNative, original, TimestampStruct.builder())).isEqualTo(original);
        }
    }

    // --- Collection Roundtrips ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void emptyCollections(boolean ser, boolean de) {
        var original = ComplexStruct.builder()
                .id("empty")
                .count(0)
                .nested(NestedStruct.builder().field1("f").field2(0).build())
                .tags(List.of())
                .intList(List.of())
                .metadata(Map.of())
                .intMap(Map.of())
                .build();
        assertThat(roundtrip(ser, de, original, ComplexStruct.builder())).isEqualTo(original);
    }

    @Test
    void nestedStructCollections() {
        var nested1 = NestedStruct.builder().field1("first").field2(1).build();
        var nested2 = NestedStruct.builder().field1("second").field2(2).build();
        var original = ComplexStruct.builder()
                .id("nested-collections")
                .count(0)
                .nested(nested1)
                .structList(List.of(nested1, nested2))
                .structMap(Map.of("a", nested1, "b", nested2))
                .build();
        assertThat(roundtrip(NATIVE, original, ComplexStruct.builder())).isEqualTo(original);
    }

    // --- Enum Roundtrips ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void enumAllVariantsRoundtrip(boolean ser, boolean de) {
        for (var color : List.of(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)) {
            var original = ComplexStruct.builder()
                    .id("enum")
                    .count(0)
                    .nested(NestedStruct.builder().field1("f").field2(0).build())
                    .color(color)
                    .build();
            assertThat(roundtrip(ser, de, original, ComplexStruct.builder())).isEqualTo(original);
        }
    }

    // --- XML-specific: xmlName Trait ---

    @PerProvider
    void xmlNameTraitRoundtrip(boolean useNative) {
        var original = XmlNameStruct.builder()
                .id("test-id")
                .displayName("Test Name")
                .normalField("normal")
                .build();
        assertThat(roundtrip(useNative, original, XmlNameStruct.builder())).isEqualTo(original);
    }

    @PerProvider
    void xmlNameTraitSerialization(boolean useNative) {
        var original = XmlNameStruct.builder()
                .id("test-id")
                .displayName("Test Name")
                .normalField("normal")
                .build();

        try (var codec = XmlCodec.builder().useNative(useNative).build()) {
            String xml = codec.serializeToString(original);
            assertThat(xml).contains("<CustomRoot>");
            assertThat(xml).contains("<ID>");
            assertThat(xml).contains("<DisplayName>");
            assertThat(xml).contains("<normalField>");
        }
    }

    // --- XML-specific: xmlAttribute Trait ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void xmlAttributeRoundtrip(boolean ser, boolean de) {
        var original = XmlAttributeStruct.builder()
                .version("1.0")
                .identifier("abc-123")
                .content("hello world")
                .build();
        assertThat(roundtrip(ser, de, original, XmlAttributeStruct.builder())).isEqualTo(original);
    }

    @PerProvider
    void xmlAttributeSerialization(boolean useNative) {
        var original = XmlAttributeStruct.builder()
                .version("1.0")
                .identifier("abc-123")
                .content("hello world")
                .build();

        try (var codec = XmlCodec.builder().useNative(useNative).build()) {
            String xml = codec.serializeToString(original);
            assertThat(xml).contains("version=\"1.0\"");
            assertThat(xml).contains("id=\"abc-123\"");
            assertThat(xml).contains("<content>hello world</content>");
        }
    }

    // --- XML-specific: xmlFlattened Trait ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void flattenedListRoundtrip(boolean ser, boolean de) {
        var original = FlattenedListStruct.builder()
                .items(List.of("a", "b", "c"))
                .numbers(List.of(1, 2, 3))
                .normalList(List.of("x", "y"))
                .build();
        assertThat(roundtrip(ser, de, original, FlattenedListStruct.builder())).isEqualTo(original);
    }

    @PerProvider
    void flattenedListSerialization(boolean useNative) {
        var original = FlattenedListStruct.builder()
                .items(List.of("a", "b", "c"))
                .normalList(List.of("x", "y"))
                .build();

        try (var codec = XmlCodec.builder().useNative(useNative).build()) {
            String xml = codec.serializeToString(original);
            assertThat(xml).contains("<items>a</items>");
            assertThat(xml).contains("<items>b</items>");
            assertThat(xml).contains("<items>c</items>");
            assertThat(xml).contains("<normalList><member>x</member><member>y</member></normalList>");
        }
    }

    @ParameterizedTest
    @MethodSource("crossProviders")
    void flattenedMapRoundtrip(boolean ser, boolean de) {
        var original = FlattenedMapStruct.builder()
                .entries(Map.of("k1", "v1", "k2", "v2"))
                .normalMap(Map.of("a", "b"))
                .build();
        assertThat(roundtrip(ser, de, original, FlattenedMapStruct.builder())).isEqualTo(original);
    }

    // --- XML-specific: xmlNamespace Trait ---

    @PerProvider
    void namespacedStructRoundtrip(boolean useNative) {
        var original = NamespacedStruct.builder()
                .name("test")
                .value(42)
                .build();
        assertThat(roundtrip(useNative, original, NamespacedStruct.builder())).isEqualTo(original);
    }

    @PerProvider
    void namespacedStructSerialization(boolean useNative) {
        var original = NamespacedStruct.builder()
                .name("test")
                .value(42)
                .build();

        try (var codec = XmlCodec.builder().useNative(useNative).build()) {
            String xml = codec.serializeToString(original);
            assertThat(xml).contains("xmlns=\"https://example.com/test\"");
        }
    }

    // --- Recursive / Nested Depth ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void recursiveStructRoundtrip(boolean ser, boolean de) {
        RecursiveStruct current = RecursiveStruct.builder().value("leaf").build();
        for (int i = 9; i >= 1; i--) {
            current = RecursiveStruct.builder().value("level-" + i).child(current).build();
        }
        assertThat(roundtrip(ser, de, current, RecursiveStruct.builder())).isEqualTo(current);
    }

    // --- Blob Roundtrips ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void blobRoundtrip(boolean ser, boolean de) {
        var empty = BlobStruct.builder().data(ByteBuffer.wrap(new byte[0])).build();
        assertThat(roundtrip(ser, de, empty, BlobStruct.builder())).isEqualTo(empty);

        var small = BlobStruct.builder()
                .data(ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)))
                .build();
        assertThat(roundtrip(ser, de, small, BlobStruct.builder())).isEqualTo(small);

        byte[] largeData = new byte[1000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }
        var large = BlobStruct.builder().data(ByteBuffer.wrap(largeData)).build();
        assertThat(roundtrip(ser, de, large, BlobStruct.builder())).isEqualTo(large);
    }

    // --- Null optional members ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void nullOptionalMembersRoundtrip(boolean ser, boolean de) {
        var original = NumericStruct.builder().build();
        assertThat(roundtrip(ser, de, original, NumericStruct.builder())).isEqualTo(original);
    }

    // --- All-types list roundtrip ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void allListTypesRoundtrip(boolean ser, boolean de) {
        var original = AllListsStruct.builder()
                .booleans(List.of(true, false, true))
                .bytes(List.of((byte) 1, (byte) -128, (byte) 127))
                .shorts(List.of((short) 1, (short) -32768, (short) 32767))
                .ints(List.of(1, Integer.MIN_VALUE, Integer.MAX_VALUE))
                .longs(List.of(1L, Long.MIN_VALUE, Long.MAX_VALUE))
                .floats(List.of(1.5f, Float.MIN_VALUE, Float.MAX_VALUE))
                .doubles(List.of(1.5, Double.MIN_VALUE, Double.MAX_VALUE))
                .bigInts(List.of(BigInteger.ZERO, new BigInteger("99999999999999999999")))
                .bigDecs(List.of(BigDecimal.ZERO, new BigDecimal("99999.99999")))
                .strings(List.of("hello", "world"))
                .blobs(List.of(
                        ByteBuffer.wrap("a".getBytes(StandardCharsets.UTF_8)),
                        ByteBuffer.wrap("b".getBytes(StandardCharsets.UTF_8))))
                .timestamps(List.of(Instant.EPOCH, Instant.parse("2025-01-15T10:30:00Z")))
                .build();
        assertThat(roundtrip(ser, de, original, AllListsStruct.builder())).isEqualTo(original);
    }

    // --- Malformed XML validation (parity between native and StAX) ---

    private static <T extends SerializableShape> T deserialize(
            boolean useNative,
            String xml,
            ShapeBuilder<T> builder
    ) {
        try (var codec = XmlCodec.builder().useNative(useNative).build()) {
            return codec.deserializeShape(xml, builder);
        }
    }

    private static <T extends SerializableShape> T deserialize(
            boolean useNative,
            byte[] xml,
            ShapeBuilder<T> builder
    ) {
        try (var codec = XmlCodec.builder().useNative(useNative).build()) {
            return codec.deserializeShape(ByteBuffer.wrap(xml), builder);
        }
    }

    @PerProvider
    void invalidUtf8IsRejected(boolean useNative) {
        byte[] input = new byte[] {
                '<',
                'S',
                'i',
                'm',
                'p',
                'l',
                'e',
                'S',
                't',
                'r',
                'u',
                'c',
                't',
                '>',
                '<',
                'n',
                'a',
                'm',
                'e',
                '>',
                'h',
                'i',
                '<',
                '/',
                'n',
                (byte) 0x97,
                'm',
                'e',
                '>',
                '<',
                '/',
                'S',
                'i',
                'm',
                'p',
                'l',
                'e',
                'S',
                't',
                'r',
                'u',
                'c',
                't',
                '>'
        };
        assertThatThrownBy(() -> deserialize(useNative, input, SimpleStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @PerProvider
    void invalidUtf8ContinuationByteAlone(boolean useNative) {
        byte[] input = new byte[] {
                '<',
                'S',
                'i',
                'm',
                'p',
                'l',
                'e',
                'S',
                't',
                'r',
                'u',
                'c',
                't',
                '>',
                '<',
                'n',
                'a',
                'm',
                'e',
                '>',
                (byte) 0x80,
                '<',
                '/',
                'n',
                'a',
                'm',
                'e',
                '>',
                '<',
                '/',
                'S',
                'i',
                'm',
                'p',
                'l',
                'e',
                'S',
                't',
                'r',
                'u',
                'c',
                't',
                '>'
        };
        assertThatThrownBy(() -> deserialize(useNative, input, SimpleStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @PerProvider
    void nullByteInContentIsRejected(boolean useNative) {
        byte[] input = "<SimpleStruct><name>hi\0there</name></SimpleStruct>".getBytes();
        assertThatThrownBy(() -> deserialize(useNative, input, SimpleStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @PerProvider
    void numericCharRefToControlCharIsRejected(boolean useNative) {
        String xml = "<SimpleStruct><name>hi&#1;there</name></SimpleStruct>";
        assertThatThrownBy(() -> deserialize(useNative, xml, SimpleStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @PerProvider
    void mismatchedEndTagOnScalar(boolean useNative) {
        String xml = "<SimpleStruct><name>hello</val></SimpleStruct>";
        assertThatThrownBy(() -> deserialize(useNative, xml, SimpleStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @PerProvider
    void mismatchedOuterEndTag(boolean useNative) {
        String xml = "<SimpleStruct><name>hi</name></WrongName>";
        assertThatThrownBy(() -> deserialize(useNative, xml, SimpleStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @PerProvider
    void mismatchedEndTagInList(boolean useNative) {
        String xml = "<ComplexStruct><id>1</id><count>1</count><enabled>true</enabled>"
                + "<ratio>1.0</ratio><score>1.0</score><bigCount>1</bigCount>"
                + "<nested><field1>a</field1><field2>1</field2></nested>"
                + "<tags><member>hello</wrong></tags></ComplexStruct>";
        assertThatThrownBy(() -> deserialize(useNative, xml, ComplexStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @PerProvider
    void mismatchedEndTagInMapKey(boolean useNative) {
        String xml = "<ComplexStruct><id>1</id><count>1</count><enabled>true</enabled>"
                + "<ratio>1.0</ratio><score>1.0</score><bigCount>1</bigCount>"
                + "<nested><field1>a</field1><field2>1</field2></nested>"
                + "<metadata><entry><key>k</wrong><value>v</value></entry></metadata></ComplexStruct>";
        assertThatThrownBy(() -> deserialize(useNative, xml, ComplexStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @Test
    void emptyListMembersHandledConsistently() {
        String xml = "<ComplexStruct><id>1</id><count>1</count><enabled>true</enabled>"
                + "<ratio>1.0</ratio><score>1.0</score><bigCount>1</bigCount>"
                + "<nested><field1>a</field1><field2>1</field2></nested>"
                + "<tags><member></member><member>hello</member><member></member><member>world</member></tags>"
                + "</ComplexStruct>";

        var staxResult = deserialize(STAX, xml, ComplexStruct.builder());
        var nativeResult = deserialize(NATIVE, xml, ComplexStruct.builder());
        assertThat(nativeResult.getTags()).isEqualTo(staxResult.getTags());
    }

    @PerProvider
    void selfClosingElementDeserializesAsEmpty(boolean useNative) {
        String xml = "<SimpleStruct><name/><age>5</age></SimpleStruct>";
        var result = deserialize(useNative, xml, SimpleStruct.builder());
        assertThat(result.getName()).isEmpty();
        assertThat(result.getAge()).isEqualTo(5);
    }

    @PerProvider
    void selfClosingElementDeserializesAsEmptyInStruct(boolean useNative) {
        String xml = "<SimpleStruct><name/><age>5</age></SimpleStruct>";
        var result = deserialize(useNative, xml, SimpleStruct.builder());
        assertThat(result.getAge()).isEqualTo(5);
    }

    @Test
    void selfClosingElementsInListSkippedAsNull() {
        // Self-closing and empty elements in lists are treated as null (skipped) by the generated code
        String xml = "<ComplexStruct><id>1</id><count>1</count><enabled>true</enabled>"
                + "<ratio>1.0</ratio><score>1.0</score><bigCount>1</bigCount>"
                + "<nested><field1>a</field1><field2>1</field2></nested>"
                + "<tags><member/><member>hello</member><member/></tags>"
                + "</ComplexStruct>";
        var staxResult = deserialize(STAX, xml, ComplexStruct.builder());
        var nativeResult = deserialize(NATIVE, xml, ComplexStruct.builder());
        assertThat(nativeResult.getTags()).isEqualTo(staxResult.getTags());
        assertThat(nativeResult.getTags()).containsExactly("hello");
    }

    @PerProvider
    void trailingContentAfterRootIsRejected(boolean useNative) {
        String xml = "<SimpleStruct><name>hi</name><age>1</age></SimpleStruct>extra";
        assertThatThrownBy(() -> deserialize(useNative, xml, SimpleStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @PerProvider
    void unknownElementWithMismatchedInnerTags(boolean useNative) {
        String xml = "<SimpleStruct><garbled></SimpleStruct>";
        assertThatThrownBy(() -> deserialize(useNative, xml, SimpleStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @PerProvider
    void unclosedElementRejectsAtEof(boolean useNative) {
        String xml = "<SimpleStruct><unknown>text";
        assertThatThrownBy(() -> deserialize(useNative, xml, SimpleStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @PerProvider
    void truncatedEndTagRejectsAtEof(boolean useNative) {
        String xml = "<SimpleStruct></SimpleStruct";
        assertThatThrownBy(() -> deserialize(useNative, xml, SimpleStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @PerProvider
    void unterminatedEntityReferenceIsRejected(boolean useNative) {
        String xml = "<SimpleStruct><name>hello&world</name></SimpleStruct>";
        assertThatThrownBy(() -> deserialize(useNative, xml, SimpleStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @PerProvider
    void undeclaredEntityReferenceIsRejected(boolean useNative) {
        String xml = "<SimpleStruct><name>&foo;</name></SimpleStruct>";
        assertThatThrownBy(() -> deserialize(useNative, xml, SimpleStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @PerProvider
    void cdataCloseInTextContentIsRejected(boolean useNative) {
        String xml = "<SimpleStruct><name>a]]>b</name></SimpleStruct>";
        assertThatThrownBy(() -> deserialize(useNative, xml, SimpleStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @PerProvider
    void invalidMarkupDeclarationIsRejected(boolean useNative) {
        String xml = "<SimpleStruct><!ELEMENT foo></SimpleStruct>";
        assertThatThrownBy(() -> deserialize(useNative, xml, SimpleStruct.builder()))
                .isInstanceOf(SerializationException.class);
    }

    @Test
    void infinityParsedCorrectlyNotFalsePositive() {
        String xml = "<SimpleStruct><name>x</name><age>1</age><score>ISO-885</score></SimpleStruct>";
        assertThatThrownBy(() -> deserialize(STAX, xml, SimpleStruct.builder())).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> deserialize(NATIVE, xml, SimpleStruct.builder())).isInstanceOf(Exception.class);
    }

    @Test
    void actualInfinityParsedCorrectly() {
        String xml = "<SimpleStruct><name>x</name><age>1</age><score>Infinity</score></SimpleStruct>";
        var stax = deserialize(STAX, xml, SimpleStruct.builder());
        var nat = deserialize(NATIVE, xml, SimpleStruct.builder());
        assertThat(nat.getScore()).isEqualTo(stax.getScore());
        assertThat(nat.getScore()).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    void carriageReturnNormalization() {
        String xml = "<SimpleStruct><name>a\rb\r\nc</name><age>1</age></SimpleStruct>";
        var staxResult = deserialize(STAX, xml, SimpleStruct.builder());
        var nativeResult = deserialize(NATIVE, xml, SimpleStruct.builder());
        assertThat(nativeResult.getName()).isEqualTo(staxResult.getName());
        assertThat(nativeResult.getName()).isEqualTo("a\nb\nc");
    }

    @Test
    void crossCodecRoundtripWithListsAndMaps() {
        String xml = "<ComplexStruct><id>x</id><count>5</count><enabled>false</enabled>"
                + "<ratio>2.5</ratio><score>1.5</score><bigCount>100</bigCount>"
                + "<nested><field1>n1</field1><field2>10</field2></nested>"
                + "<tags><member>a</member><member>b</member></tags>"
                + "<metadata><entry><key>k1</key><value>v1</value></entry></metadata>"
                + "</ComplexStruct>";

        var staxResult = deserialize(STAX, xml, ComplexStruct.builder());
        var nativeResult = deserialize(NATIVE, xml, ComplexStruct.builder());

        assertThat(nativeResult.getId()).isEqualTo(staxResult.getId());
        assertThat(nativeResult.getTags()).isEqualTo(staxResult.getTags());
        assertThat(nativeResult.getMetadata()).isEqualTo(staxResult.getMetadata());
    }

    // --- Error code parsing ---

    @PerProvider
    void parseErrorCodeFromErrorResponse(boolean useNative) {
        String xml = "<ErrorResponse><Error><Type>Sender</Type><Code>InvalidGreeting</Code>"
                + "<Message>Hi</Message></Error></ErrorResponse>";
        var codec = XmlCodec.builder().useNative(useNative).build();
        var deser = codec.createDeserializer(ByteBuffer.wrap(xml.getBytes(StandardCharsets.UTF_8)));
        String code = XmlUtil.parseErrorCodeName(deser);
        assertThat(code).isEqualTo("InvalidGreeting");
    }

    @PerProvider
    void parseErrorCodeFromBareError(boolean useNative) {
        String xml = "<Error><Code>ComplexError</Code><Message>Something</Message></Error>";
        var codec = XmlCodec.builder().useNative(useNative).build();
        var deser = codec.createDeserializer(ByteBuffer.wrap(xml.getBytes(StandardCharsets.UTF_8)));
        String code = XmlUtil.parseErrorCodeName(deser);
        assertThat(code).isEqualTo("ComplexError");
    }

    @PerProvider
    void parseErrorCodeFromEc2Response(boolean useNative) {
        String xml = "<Response><Errors><Error><Code>AuthFailure</Code>"
                + "<Message>Unauthorized</Message></Error></Errors></Response>";
        var codec = XmlCodec.builder().useNative(useNative).build();
        var deser = codec.createDeserializer(ByteBuffer.wrap(xml.getBytes(StandardCharsets.UTF_8)));
        String code = XmlUtil.parseErrorCodeName(deser);
        assertThat(code).isEqualTo("AuthFailure");
    }

    @Test
    void errorStructDeserializationThroughErrorResponse() {
        String xml = "<ErrorResponse><Error><name>test</name><age>42</age></Error></ErrorResponse>";
        var result = deserialize(NATIVE, xml, SimpleStruct.builder());
        assertThat(result.getName()).isEqualTo("test");
        assertThat(result.getAge()).isEqualTo(42);
    }
}
