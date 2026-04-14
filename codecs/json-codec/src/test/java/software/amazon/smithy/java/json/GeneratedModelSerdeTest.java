/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.bench.model.BenchUnion;
import software.amazon.smithy.java.json.bench.model.BlobStruct;
import software.amazon.smithy.java.json.bench.model.Color;
import software.amazon.smithy.java.json.bench.model.ComplexStruct;
import software.amazon.smithy.java.json.bench.model.InnerStruct;
import software.amazon.smithy.java.json.bench.model.JsonNameStruct;
import software.amazon.smithy.java.json.bench.model.NestedStruct;
import software.amazon.smithy.java.json.bench.model.NumericStruct;
import software.amazon.smithy.java.json.bench.model.RecursiveStruct;
import software.amazon.smithy.java.json.bench.model.SimpleStruct;
import software.amazon.smithy.java.json.bench.model.StringStruct;
import software.amazon.smithy.java.json.bench.model.TimestampStruct;

/**
 * Tests roundtrip serialization/deserialization of generated model classes with both providers.
 * Also tests cross-provider compatibility (serialize with one, deserialize with the other).
 */
public class GeneratedModelSerdeTest extends ProviderTestBase {

    private static <T extends SerializableShape> T roundtrip(
            JsonSerdeProvider provider,
            SerializableShape original,
            ShapeBuilder<T> builder
    ) {
        return roundtrip(provider, provider, original, builder);
    }

    private static <T extends SerializableShape> T roundtrip(
            JsonSerdeProvider ser,
            JsonSerdeProvider de,
            SerializableShape original,
            ShapeBuilder<T> builder
    ) {
        try (var serCodec = JsonCodec.builder()
                .overrideSerdeProvider(ser)
                .useJsonName(true)
                .useTimestampFormat(true)
                .build();
                var deCodec = JsonCodec.builder()
                        .overrideSerdeProvider(de)
                        .useJsonName(true)
                        .useTimestampFormat(true)
                        .build()) {
            ByteBuffer serialized = serCodec.serialize(original);
            byte[] bytes = new byte[serialized.remaining()];
            serialized.get(bytes);
            return deCodec.deserializeShape(bytes, builder);
        }
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
        var sparseMap = new HashMap<String, String>();
        sparseMap.put("x", "1");
        sparseMap.put("y", "2");
        sparseMap.put("z", null);
        return ComplexStruct.builder()
                .id("bench-001")
                .count(999)
                .enabled(true)
                .ratio(1.618)
                .score(2.718f)
                .bigCount(1_000_000L)
                .optionalString("optional-value")
                .optionalInt(42)
                .createdAt(Instant.parse("2025-01-15T10:30:00Z"))
                .updatedAt(Instant.parse("2025-06-01T12:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T00:00:00Z"))
                .payload(ByteBuffer.wrap("binary-payload-data".getBytes(StandardCharsets.UTF_8)))
                .tags(List.of("alpha", "beta", "gamma", "delta"))
                .intList(List.of(10, 20, 30, 40, 50))
                .metadata(Map.of("key1", "value1", "key2", "value2", "key3", "value3"))
                .intMap(Map.of("a", 1, "b", 2, "c", 3))
                .nested(nested)
                .optionalNested(NestedStruct.builder()
                        .field1("opt-nested")
                        .field2(200)
                        .build())
                .structList(List.of(nested, nested))
                .structMap(Map.of("first", nested, "second", nested))
                .choice(new BenchUnion.StringValueMember("union-string"))
                .color(Color.GREEN)
                .colorList(List.of(Color.RED, Color.BLUE, Color.YELLOW))
                .sparseStrings(Arrays.asList("a", null, "c"))
                .sparseMap(sparseMap)
                .bigIntValue(new BigInteger("123456789012345678901234567890"))
                .bigDecValue(new BigDecimal("99999.99999"))
                .freeformData(Document.of(Map.of("key", Document.of("value"), "num", Document.of(42))))
                .build();
    }

    @ParameterizedTest
    @MethodSource("crossProviders")
    void complexStructRoundtrip(JsonSerdeProvider ser, JsonSerdeProvider de) {
        var original = buildComplexStruct();
        assertThat(roundtrip(ser, de, original, ComplexStruct.builder())).isEqualTo(original);
    }

    @PerProvider
    void simpleStructRoundtrip(JsonSerdeProvider provider) {
        var original = SimpleStruct.builder()
                .name("test")
                .age(42)
                .active(true)
                .score(98.6)
                .createdAt(Instant.parse("2025-01-15T10:30:00Z"))
                .build();
        assertThat(roundtrip(provider, original, SimpleStruct.builder())).isEqualTo(original);
    }

    // --- Numeric Boundary Roundtrips ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void numericMinValuesRoundtrip(JsonSerdeProvider ser, JsonSerdeProvider de) {
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
    void numericMaxValuesRoundtrip(JsonSerdeProvider ser, JsonSerdeProvider de) {
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
    void numericSpecialFloatingPoint(JsonSerdeProvider provider) {
        // NaN requires special comparison since NaN != NaN
        var nan = NumericStruct.builder().floatVal(Float.NaN).doubleVal(Double.NaN).build();
        NumericStruct nanResult = roundtrip(provider, nan, NumericStruct.builder());
        assertThat(nanResult.getFloatVal()).isNaN();
        assertThat(nanResult.getDoubleVal()).isNaN();

        // Infinity values can use equals
        var posInf = NumericStruct.builder()
                .floatVal(Float.POSITIVE_INFINITY)
                .doubleVal(Double.POSITIVE_INFINITY)
                .build();
        assertThat(roundtrip(provider, posInf, NumericStruct.builder())).isEqualTo(posInf);

        var negInf = NumericStruct.builder()
                .floatVal(Float.NEGATIVE_INFINITY)
                .doubleVal(Double.NEGATIVE_INFINITY)
                .build();
        assertThat(roundtrip(provider, negInf, NumericStruct.builder())).isEqualTo(negInf);
    }

    @PerProvider
    void numericZeroValues(JsonSerdeProvider provider) {
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
        assertThat(roundtrip(provider, original, NumericStruct.builder())).isEqualTo(original);
    }

    @PerProvider
    void bigDecimalVariousScales(JsonSerdeProvider provider) {
        for (var bd : List.of(
                new BigDecimal("42"), // scale=0
                new BigDecimal("1.005"), // leading zeros in fraction
                new BigDecimal("1E+10"), // negative scale
                new BigDecimal("12345678901234567890.12345") // >64 bit unscaled
        )) {
            var original = NumericStruct.builder().bigDecVal(bd).build();
            assertThat(roundtrip(provider, original, NumericStruct.builder())
                    .getBigDecVal()).isEqualByComparingTo(bd);
        }
    }

    @PerProvider
    void bigIntegerSmallAndLarge(JsonSerdeProvider provider) {
        for (var bi : List.of(
                BigInteger.valueOf(42),
                BigInteger.valueOf(-42),
                new BigInteger("123456789012345678901234567890123456789"))) {
            var original = NumericStruct.builder().bigIntVal(bi).build();
            assertThat(roundtrip(provider, original, NumericStruct.builder())).isEqualTo(original);
        }
    }

    // --- String Edge Cases ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void stringEdgeCasesRoundtrip(JsonSerdeProvider ser, JsonSerdeProvider de) {
        for (var s : List.of(
                "", // empty
                "abcdefghijklmnopqrstuvwxyz0123456789", // long ASCII (>8 bytes, SWAR)
                "quote:\" backslash:\\ tab:\t newline:\n cr:\r", // JSON escapes
                "\u00e9\u4e2d\u00fc", // BMP unicode
                "\uD83D\uDE00\uD83C\uDF89", // SMP emoji
                "hello\tworld\n\u00e9\uD83D\uDE00\"quoted\"" // mixed
        )) {
            var original = StringStruct.builder().value(s).build();
            assertThat(roundtrip(ser, de, original, StringStruct.builder())).isEqualTo(original);
        }
    }

    // --- Timestamp Format Roundtrips ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void timestampAllFormatsRoundtrip(JsonSerdeProvider ser, JsonSerdeProvider de) {
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
    void timestampWithNanoseconds(JsonSerdeProvider ser, JsonSerdeProvider de) {
        var withNanos = Instant.parse("2025-01-15T10:30:00.123456789Z");
        var original = TimestampStruct.builder().dateTime(withNanos).build();
        assertThat(roundtrip(ser, de, original, TimestampStruct.builder())).isEqualTo(original);
    }

    @ParameterizedTest
    @MethodSource("crossProviders")
    void timestampEpochSecondsWithMillis(JsonSerdeProvider ser, JsonSerdeProvider de) {
        // Exercises all three branches in writeEpochSeconds
        for (var millis : List.of(
                Instant.ofEpochSecond(1000, 100_000_000), // millis%100==0
                Instant.ofEpochSecond(1000, 120_000_000), // millis%10==0
                Instant.ofEpochSecond(1000, 123_000_000) // 3-digit
        )) {
            var original = TimestampStruct.builder().epochSeconds(millis).build();
            assertThat(roundtrip(ser, de, original, TimestampStruct.builder())).isEqualTo(original);
        }
    }

    @PerProvider
    void timestampPreEpoch(JsonSerdeProvider provider) {
        var preEpoch = Instant.parse("1969-12-31T23:59:59Z");
        var original = TimestampStruct.builder()
                .epochSeconds(preEpoch)
                .dateTime(preEpoch)
                .httpDate(preEpoch)
                .build();
        assertThat(roundtrip(provider, original, TimestampStruct.builder())).isEqualTo(original);
    }

    @PerProvider
    void timestampLeapYear(JsonSerdeProvider provider) {
        var leapDay = LocalDate.of(2024, 2, 29).atStartOfDay(ZoneOffset.UTC).toInstant();
        var original = TimestampStruct.builder().dateTime(leapDay).build();
        assertThat(roundtrip(provider, original, TimestampStruct.builder())).isEqualTo(original);
    }

    @PerProvider
    void timestampAllMonthsHttpDate(JsonSerdeProvider provider) {
        for (int month = 1; month <= 12; month++) {
            var instant = LocalDate.of(2025, month, 15).atStartOfDay(ZoneOffset.UTC).toInstant();
            var original = TimestampStruct.builder().httpDate(instant).build();
            assertThat(roundtrip(provider, original, TimestampStruct.builder())).isEqualTo(original);
        }
    }

    @PerProvider
    void timestampAllDaysOfWeekHttpDate(JsonSerdeProvider provider) {
        // 2025-01-06 = Monday, 06-12 covers Mon-Sun
        for (int day = 6; day <= 12; day++) {
            var instant = LocalDate.of(2025, 1, day).atStartOfDay(ZoneOffset.UTC).toInstant();
            var original = TimestampStruct.builder().httpDate(instant).build();
            assertThat(roundtrip(provider, original, TimestampStruct.builder())).isEqualTo(original);
        }
    }

    // --- Collection Roundtrips ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void emptyCollections(JsonSerdeProvider ser, JsonSerdeProvider de) {
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

    @ParameterizedTest
    @MethodSource("crossProviders")
    void sparseCollectionsRoundtrip(JsonSerdeProvider ser, JsonSerdeProvider de) {
        var sparseMap = new HashMap<String, String>();
        sparseMap.put("present", "value");
        sparseMap.put("absent", null);
        var original = ComplexStruct.builder()
                .id("sparse")
                .count(0)
                .nested(NestedStruct.builder().field1("f").field2(0).build())
                .sparseStrings(Arrays.asList("a", null, "c", null, "e"))
                .sparseMap(sparseMap)
                .build();
        assertThat(roundtrip(ser, de, original, ComplexStruct.builder())).isEqualTo(original);
    }

    @ParameterizedTest
    @MethodSource("crossProviders")
    void nestedStructCollections(JsonSerdeProvider ser, JsonSerdeProvider de) {
        var nested1 = NestedStruct.builder().field1("first").field2(1).build();
        var nested2 = NestedStruct.builder().field1("second").field2(2).build();
        var original = ComplexStruct.builder()
                .id("nested-collections")
                .count(0)
                .nested(nested1)
                .structList(List.of(nested1, nested2))
                .structMap(Map.of("a", nested1, "b", nested2))
                .build();
        assertThat(roundtrip(ser, de, original, ComplexStruct.builder())).isEqualTo(original);
    }

    // --- Union Roundtrips ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void unionVariantsRoundtrip(JsonSerdeProvider ser, JsonSerdeProvider de) {
        var base = ComplexStruct.builder()
                .id("u")
                .count(0)
                .nested(NestedStruct.builder().field1("f").field2(0).build());

        for (var choice : List.of(
                new BenchUnion.StringValueMember("hello"),
                new BenchUnion.IntValueMember(42),
                new BenchUnion.StructValueMember(
                        NestedStruct.builder().field1("in-union").field2(99).build()))) {
            var original = base.choice(choice).build();
            assertThat(roundtrip(ser, de, original, ComplexStruct.builder())).isEqualTo(original);
        }
    }

    // --- Enum Roundtrips ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void enumAllVariantsRoundtrip(JsonSerdeProvider ser, JsonSerdeProvider de) {
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

    // --- JsonName Trait ---

    @PerProvider
    void jsonNameTraitSerialization(JsonSerdeProvider provider) {
        var original = JsonNameStruct.builder()
                .id("test-id")
                .displayName("Test Name")
                .normalField("normal")
                .build();

        // With useJsonName=true: verify field names use trait values
        try (var codec = JsonCodec.builder()
                .overrideSerdeProvider(provider)
                .useJsonName(true)
                .useTimestampFormat(true)
                .build()) {
            ByteBuffer serialized = codec.serialize(original);
            String json = StandardCharsets.UTF_8.decode(serialized.duplicate()).toString();
            assertThat(json).contains("\"ID\"").contains("\"DisplayName\"").contains("\"normalField\"");
            byte[] bytes = new byte[serialized.remaining()];
            serialized.get(bytes);
            assertThat(codec.deserializeShape(bytes, JsonNameStruct.builder())).isEqualTo(original);
        }

        // With useJsonName=false: verify field names use member names
        try (var codec = JsonCodec.builder()
                .overrideSerdeProvider(provider)
                .useJsonName(false)
                .useTimestampFormat(true)
                .build()) {
            ByteBuffer serialized = codec.serialize(original);
            String json = StandardCharsets.UTF_8.decode(serialized.duplicate()).toString();
            assertThat(json).contains("\"id\"").contains("\"displayName\"");
            byte[] bytes = new byte[serialized.remaining()];
            serialized.get(bytes);
            assertThat(codec.deserializeShape(bytes, JsonNameStruct.builder())).isEqualTo(original);
        }
    }

    // --- Recursive / Nested Depth ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void recursiveStructRoundtrip(JsonSerdeProvider ser, JsonSerdeProvider de) {
        RecursiveStruct current = RecursiveStruct.builder().value("leaf").build();
        for (int i = 9; i >= 1; i--) {
            current = RecursiveStruct.builder().value("level-" + i).child(current).build();
        }
        assertThat(roundtrip(ser, de, current, RecursiveStruct.builder())).isEqualTo(current);
    }

    // --- Blob Roundtrips ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void blobRoundtrip(JsonSerdeProvider ser, JsonSerdeProvider de) {
        // Empty blob
        var empty = BlobStruct.builder().data(ByteBuffer.wrap(new byte[0])).build();
        assertThat(roundtrip(ser, de, empty, BlobStruct.builder())).isEqualTo(empty);

        // Small blob
        var small = BlobStruct.builder()
                .data(ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)))
                .build();
        assertThat(roundtrip(ser, de, small, BlobStruct.builder())).isEqualTo(small);

        // Large blob (1000 bytes)
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
    void nullOptionalMembersRoundtrip(JsonSerdeProvider ser, JsonSerdeProvider de) {
        var original = NumericStruct.builder().build();
        assertThat(roundtrip(ser, de, original, NumericStruct.builder())).isEqualTo(original);
    }

    // --- Fields in non-schema order (exercises hash lookup slow path) ---

    @PerProvider
    void fieldsInReverseOrder(JsonSerdeProvider provider) {
        String json = "{\"normalField\":\"c\",\"DisplayName\":\"b\",\"ID\":\"a\"}";
        try (var codec = JsonCodec.builder()
                .overrideSerdeProvider(provider)
                .useJsonName(true)
                .useTimestampFormat(true)
                .build()) {
            JsonNameStruct result = codec.deserializeShape(
                    json.getBytes(StandardCharsets.UTF_8),
                    JsonNameStruct.builder());
            assertThat(result).isEqualTo(JsonNameStruct.builder()
                    .id("a")
                    .displayName("b")
                    .normalField("c")
                    .build());
        }
    }

    // --- Unknown fields skipped ---

    @PerProvider
    void unknownFieldsSkipped(JsonSerdeProvider provider) {
        String json = "{\"value\":\"hello\",\"unknownField\":42,\"anotherUnknown\":[1,2,3]}";
        try (var codec = JsonCodec.builder()
                .overrideSerdeProvider(provider)
                .useJsonName(true)
                .useTimestampFormat(true)
                .build()) {
            assertThat(codec.deserializeShape(json.getBytes(StandardCharsets.UTF_8), StringStruct.builder()))
                    .isEqualTo(StringStruct.builder().value("hello").build());
        }
    }

    // --- All-types list roundtrip (exercises ListElementSerializer for each type) ---

    @ParameterizedTest
    @MethodSource("crossProviders")
    void allListTypesRoundtrip(JsonSerdeProvider ser, JsonSerdeProvider de) {
        var original = software.amazon.smithy.java.json.bench.model.AllListsStruct.builder()
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
        assertThat(roundtrip(ser,
                de,
                original,
                software.amazon.smithy.java.json.bench.model.AllListsStruct.builder())).isEqualTo(original);
    }

    private static String serializeToJson(SerializableShape shape) {
        try (var codec = JsonCodec.builder()
                .overrideSerdeProvider(SMITHY)
                .useJsonName(true)
                .useTimestampFormat(true)
                .build()) {
            ByteBuffer serialized = codec.serialize(shape);
            byte[] bytes = new byte[serialized.remaining()];
            serialized.get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    @Test
    void epochSecondsNegativeWithNanos() {
        // Instant.ofEpochSecond(-1, 500_000_000) = -0.5 seconds from epoch (1969-12-31T23:59:59.5Z)
        // The wire format must be -0.5, NOT -1.5
        var original = TimestampStruct.builder()
                .epochSeconds(Instant.ofEpochSecond(-1, 500_000_000))
                .build();
        String json = serializeToJson(original);
        assertThat(json).contains("-0.5");
        assertThat(json).doesNotContain("-1.5");

        // Roundtrip must recover the original Instant
        assertThat(roundtrip(SMITHY, original, TimestampStruct.builder())).isEqualTo(original);
    }

    @Test
    void epochSecondsNegativeWithVariousNanos() {
        // -1 epoch + 100_000_000 nanos = -0.9 seconds
        var neg09 = TimestampStruct.builder()
                .epochSeconds(Instant.ofEpochSecond(-1, 100_000_000))
                .build();
        assertThat(serializeToJson(neg09)).contains("-0.9");
        assertThat(roundtrip(SMITHY, neg09, TimestampStruct.builder())).isEqualTo(neg09);

        // -1 epoch + 999_000_000 nanos = -0.001 seconds
        var neg0001 = TimestampStruct.builder()
                .epochSeconds(Instant.ofEpochSecond(-1, 999_000_000))
                .build();
        assertThat(serializeToJson(neg0001)).contains("-0.001");
        assertThat(roundtrip(SMITHY, neg0001, TimestampStruct.builder())).isEqualTo(neg0001);

        // -2 epoch + 500_000_000 nanos = -1.5 seconds
        var neg15 = TimestampStruct.builder()
                .epochSeconds(Instant.ofEpochSecond(-2, 500_000_000))
                .build();
        assertThat(serializeToJson(neg15)).contains("-1.5");
        assertThat(roundtrip(SMITHY, neg15, TimestampStruct.builder())).isEqualTo(neg15);
    }

    @Test
    void epochSecondsPreservesSubMillisecondPrecision() {
        // Full nanosecond precision: 123_456_789 nanos should NOT be truncated to 123 millis
        var fullNano = TimestampStruct.builder()
                .epochSeconds(Instant.ofEpochSecond(1000, 123_456_789))
                .build();
        String json = serializeToJson(fullNano);
        assertThat(json).contains("1000.123456789");
        assertThat(roundtrip(SMITHY, fullNano, TimestampStruct.builder())).isEqualTo(fullNano);

        // Microsecond precision: trailing zeros stripped
        var microSec = TimestampStruct.builder()
                .epochSeconds(Instant.ofEpochSecond(1000, 123_456_000))
                .build();
        assertThat(serializeToJson(microSec)).contains("1000.123456");
        assertThat(roundtrip(SMITHY, microSec, TimestampStruct.builder())).isEqualTo(microSec);

        // Single nanosecond
        var oneNano = TimestampStruct.builder()
                .epochSeconds(Instant.ofEpochSecond(1000, 1))
                .build();
        assertThat(serializeToJson(oneNano)).contains("1000.000000001");
        assertThat(roundtrip(SMITHY, oneNano, TimestampStruct.builder())).isEqualTo(oneNano);
    }

    @ParameterizedTest
    @MethodSource
    void nonStructDocumentRoundtrip(JsonSerdeProvider ser, JsonSerdeProvider de, Document original) {
        try (var serCodec = JsonCodec.builder()
                .overrideSerdeProvider(ser)
                .useJsonName(true)
                .useTimestampFormat(true)
                .build();
                var deCodec = JsonCodec.builder()
                        .overrideSerdeProvider(de)
                        .useJsonName(true)
                        .useTimestampFormat(true)
                        .build()) {
            ByteBuffer serialized = serCodec.serialize(original);
            Document result = deCodec.createDeserializer(serialized).readDocument();
            assertThat(Document.equals(result, original)).isTrue();
        }
    }

    static Stream<Arguments> nonStructDocumentRoundtrip() {
        List<Document> documents = List.of(
                Document.of("hello world"),
                Document.of(true),
                Document.of(false),
                Document.of(42),
                Document.of(Long.MAX_VALUE),
                Document.of(3.14),
                Document.of(List.of()),
                Document.of(List.of(Document.of(1), Document.of("two"), Document.of(true))),
                Document.of(Map.of()),
                Document.of(Map.of("key1", Document.of("value1"), "key2", Document.of(42))),
                Document.of(Map.of(
                        "list",
                        Document.of(List.of(Document.of(1), Document.of(2))),
                        "nested",
                        Document.of(Map.of("inner", Document.of("value"))),
                        "scalar",
                        Document.of("hello"))));
        return crossProviders().stream()
                .flatMap(cp -> documents.stream()
                        .map(doc -> Arguments.of(cp.get()[0], cp.get()[1], doc)));
    }

    @ParameterizedTest
    @MethodSource
    void nonStructDocumentInStructRoundtrip(JsonSerdeProvider ser, JsonSerdeProvider de, Document freeform) {
        var original = ComplexStruct.builder()
                .id("doc-test")
                .count(1)
                .nested(NestedStruct.builder().field1("f").field2(0).build())
                .freeformData(freeform)
                .build();
        var result = roundtrip(ser, de, original, ComplexStruct.builder());
        assertThat(Document.equals(result.getFreeformData(), freeform)).isTrue();
    }

    static Stream<Arguments> nonStructDocumentInStructRoundtrip() {
        List<Document> documents = List.of(
                Document.of("a string"),
                Document.of(true),
                Document.of(42),
                Document.of(Long.MAX_VALUE),
                Document.of(3.14),
                Document.of(List.of(Document.of(1), Document.of("two"))),
                Document.of(Map.of("key", Document.of("value"))),
                Document.of(Map.of(
                        "list",
                        Document.of(List.of(Document.of(1), Document.of(2))),
                        "nested",
                        Document.of(Map.of("inner", Document.of("value"))))));
        return crossProviders().stream()
                .flatMap(cp -> documents.stream()
                        .map(doc -> Arguments.of(cp.get()[0], cp.get()[1], doc)));
    }

    @ParameterizedTest
    @MethodSource
    void invalidInputs(JsonSerdeProvider provider, String input, boolean isB64) {
        byte[] bytes;
        if (isB64) {
            bytes = Base64.getDecoder()
                    .decode(input);
        } else {
            bytes = input.getBytes(StandardCharsets.UTF_8);
        }
        var codec = codec(provider);
        assertThrows(SerializationException.class,
                () -> codec.deserializeShape(bytes, NestedStruct.builder()));
    }

    static Stream<Arguments> invalidInputs() {
        List<Arguments> arguments = new ArrayList<>();
        for (var provider : List.of(JACKSON, SMITHY)) {
            arguments.add(Arguments.of(provider,
                    "eyJ2YWx1ZSI6e2JpdWUiOntiaW50VmE6e2JpbnRWYXJpnm50VmE6e2JpbnRWYXJpnpF0IjotMjA3fQ==",
                    true));
            arguments.add(Arguments.of(provider, "{\"value\":[true", false));
        }
        return arguments.stream();
    }
}
