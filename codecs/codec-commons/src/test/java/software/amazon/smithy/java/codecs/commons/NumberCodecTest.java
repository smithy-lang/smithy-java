/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class NumberCodecTest {

    // --- parseInt ---

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "1",
            "-1",
            "9",
            "10",
            "99",
            "100",
            "999",
            "1000",
            "9999",
            // Exercises SWAR parse4Digits for exactly 4 digits
            "1234",
            // 5 digits: SWAR parses 4 then falls through to single-digit loop
            "12345",
            // 8 digits: two rounds of SWAR
            "12345678",
            "-12345678",
            // MAX and MIN boundaries
            "2147483647",
            "-2147483648",
            // Just below MAX (ensures we don't over-reject)
            "2147483646",
            "-2147483647",
            // Leading zeros must not trigger false overflow
            "0042",
            "00000000000000042",
            "-0042",
            "0000000000",
            // Plus sign prefix
            "+1",
            "+2147483647",
            "+0042"
    })
    void parseInt(String input) {
        int expected = Integer.parseInt(input);
        byte[] buf = input.getBytes(StandardCharsets.US_ASCII);
        assertEquals(expected, NumberCodec.parseInt(buf, 0, buf.length));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "-",
            "+",
            "abc",
            // Invalid digit in the middle (exercises SWAR fallback to single-digit validation)
            "12a4",
            // Invalid byte at position 0 in a 4-byte SWAR chunk
            "x234",
            // Invalid byte at position 3 in a 4-byte SWAR chunk
            "123x",
            // Overflow: one past MAX
            "2147483648",
            // Overflow: one past MIN magnitude
            "-2147483649",
            // Silent wraparound without length guard
            "99999999999",
            // 10-digit overflow that previously wrapped silently
            "9005150711",
            "3000000000",
            "-9005150711"
    })
    void parseIntRejects(String input) {
        byte[] buf = input.getBytes(StandardCharsets.US_ASCII);
        assertThrows(NumberFormatException.class, () -> NumberCodec.parseInt(buf, 0, buf.length));
    }

    // --- parseLong ---

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "-1",
            "12345678",
            // 8-digit SWAR boundary
            "99999999",
            "100000000",
            // 9 digits: 8-digit SWAR + 1 remaining
            "123456789",
            // 12 digits: one 8-digit SWAR + one 4-digit SWAR
            "123456789012",
            "-123456789012",
            // 16 digits: two rounds of 8-digit SWAR
            "1234567890123456",
            // MAX/MIN
            "9223372036854775807",
            "-9223372036854775808",
            "9223372036854775806",
            // Leading zeros
            "000000000000000000001",
            "-00042"
    })
    void parseLong(String input) {
        long expected = Long.parseLong(input);
        byte[] buf = input.getBytes(StandardCharsets.US_ASCII);
        assertEquals(expected, NumberCodec.parseLong(buf, 0, buf.length));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "-",
            // Invalid digit in single-digit loop after SWAR
            "12345678x",
            // Invalid byte at position 0 of 8-byte SWAR chunk
            "x2345678",
            // Invalid byte at position 7 of 8-byte SWAR chunk
            "1234567x",
            "9223372036854775808",
            "-9223372036854775809",
            "99999999999999999999"
    })
    void parseLongRejects(String input) {
        byte[] buf = input.getBytes(StandardCharsets.US_ASCII);
        assertThrows(NumberFormatException.class, () -> NumberCodec.parseLong(buf, 0, buf.length));
    }

    // --- parseInt with offset ---

    static Stream<Arguments> parseWithOffsetCases() {
        return Stream.of(
                Arguments.of("xxx42yyy", 3, 2),
                Arguments.of("  -7  ", 2, 2),
                Arguments.of("ab1234cd", 2, 4));
    }

    @ParameterizedTest
    @MethodSource("parseWithOffsetCases")
    void parseIntWithOffset(String input, int start, int len) {
        byte[] buf = input.getBytes(StandardCharsets.US_ASCII);
        String slice = input.substring(start, start + len);
        int expected = Integer.parseInt(slice);
        assertEquals(expected, NumberCodec.parseInt(buf, start, len));
    }

    // --- writeInt ---

    @ParameterizedTest
    @ValueSource(ints = {
            0,
            1,
            -1,
            9,
            10,
            99,
            100,
            999,
            9999,
            10000,
            10001,
            Integer.MAX_VALUE,
            Integer.MIN_VALUE,
            // Exercises digit-pair path for values in [100, 10000)
            123,
            1000,
            5678,
            // Value with remainder exactly 0 after /10000 (tests write4Digits with r=0)
            20000,
            // Exercises writePositiveInt 4-digit SWAR write path
            12345,
            // Value >= 100000000 forces two rounds of SWAR 4-digit writes
            123456789,
            100000000,
            1000000000
    })
    void writeInt(int value) {
        String expected = Integer.toString(value);
        byte[] buf = new byte[12];
        int end = NumberCodec.writeInt(buf, 0, value);
        assertEquals(expected, new String(buf, 0, end, StandardCharsets.US_ASCII));
    }

    // --- writeLong ---
    // Values chosen to exercise every branch of digitCountLong (11-19 digits)
    // and all three paths in writePositiveLong

    @ParameterizedTest
    @ValueSource(longs = {
            0L,
            -1L,
            Long.MAX_VALUE,
            Long.MIN_VALUE,
            // Exactly Integer.MAX_VALUE as long (boundary: writePositiveLong delegates to writePositiveInt)
            2147483647L,
            // Just above int range -> exercises long split path
            2147483648L,
            // Exactly at 10^16 boundary (writePositiveLong two-chunk vs three-chunk split)
            10000000000000000L,
            // 11 digits
            10000000000L,
            // 12 digits
            100000000001L,
            // 13 digits
            1000000000001L,
            // 14 digits
            10000000000001L,
            // 15 digits
            100000000000001L,
            // 16 digits
            1000000000000001L,
            // 17 digits -> three-chunk split in writePositiveLong
            10000000000000001L,
            // 18 digits
            100000000000000001L,
            // 19 digits
            1000000000000000001L
    })
    void writeLong(long value) {
        String expected = Long.toString(value);
        byte[] buf = new byte[21];
        int end = NumberCodec.writeLong(buf, 0, value);
        assertEquals(expected, new String(buf, 0, end, StandardCharsets.US_ASCII));
    }

    // --- writeBigInteger ---

    static Stream<Arguments> writeBigIntegerCases() {
        return Stream.of(
                BigInteger.ZERO,
                BigInteger.ONE,
                BigInteger.ONE.negate(),
                BigInteger.valueOf(Long.MAX_VALUE),
                // Exactly 10^18 (boundary: compareTo(TEN_TO_18) == 0)
                new BigInteger("1000000000000000000"),
                // Just above 10^18 (triggers two-chunk path: high < 10^18)
                new BigInteger("1000000000000000001"),
                // Exactly 10^36 (boundary: high == TEN_TO_18, triggers three-chunk)
                new BigInteger("1000000000000000000000000000000000000"),
                // Just above 10^36 (triggers three-chunk path: qr2[0] < 10^18)
                new BigInteger("1000000000000000000000000000000000000001"),
                // Exactly 10^54 (boundary: qr2[0] == TEN_TO_18, triggers toString fallback)
                new BigInteger("1" + "0".repeat(54)),
                // Way above 10^54 (deep in toString() fallback)
                new BigInteger("1" + "0".repeat(55)),
                new BigInteger("-99999999999999999999"))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("writeBigIntegerCases")
    void writeBigInteger(BigInteger value) {
        String expected = value.toString();
        byte[] buf = new byte[80];
        int end = NumberCodec.writeBigInteger(buf, 0, value);
        assertEquals(expected, new String(buf, 0, end, StandardCharsets.US_ASCII));
    }

    // --- writeDouble ---

    @ParameterizedTest
    @ValueSource(doubles = {
            0.0,
            1.0,
            -1.0,
            42.0,
            1000000.0,
            3.14,
            1.0E-7,
            1.7976931348623157E308,
            5.0E-324,
            -0.1
    })
    void writeDouble(double value) {
        byte[] buf = new byte[32];
        int end = NumberCodec.writeDouble(buf, 0, value);
        String actual = new String(buf, 0, end, StandardCharsets.US_ASCII);
        assertEquals(value, Double.parseDouble(actual));
    }

    // --- writeFloat ---

    @ParameterizedTest
    @ValueSource(floats = {
            0.0f,
            1.0f,
            -1.0f,
            100.0f,
            3.14f,
            Float.MAX_VALUE,
            Float.MIN_VALUE
    })
    void writeFloat(float value) {
        byte[] buf = new byte[20];
        int end = NumberCodec.writeFloat(buf, 0, value);
        String actual = new String(buf, 0, end, StandardCharsets.US_ASCII);
        assertEquals(value, Float.parseFloat(actual));
    }

    // --- writeNonFiniteFloat ---

    static Stream<Arguments> nonFiniteFloatCases() {
        return Stream.of(
                Arguments.of(Float.NaN, "NaN"),
                Arguments.of(Float.POSITIVE_INFINITY, "Infinity"),
                Arguments.of(Float.NEGATIVE_INFINITY, "-Infinity"));
    }

    @ParameterizedTest
    @MethodSource("nonFiniteFloatCases")
    void writeNonFiniteFloat(float value, String expected) {
        byte[] buf = new byte[24];
        int end = NumberCodec.writeNonFiniteFloat(buf, 0, value);
        assertEquals(expected, new String(buf, 0, end, StandardCharsets.US_ASCII));
    }

    @ParameterizedTest
    @MethodSource("nonFiniteFloatCases")
    void writeNonFiniteFloatQuoted(float value, String expected) {
        byte[] buf = new byte[24];
        int end = NumberCodec.writeNonFiniteFloatQuoted(buf, 0, value);
        assertEquals("\"" + expected + "\"", new String(buf, 0, end, StandardCharsets.US_ASCII));
    }

    // --- writeNonFiniteDouble ---

    static Stream<Arguments> nonFiniteDoubleCases() {
        return Stream.of(
                Arguments.of(Double.NaN, "NaN"),
                Arguments.of(Double.POSITIVE_INFINITY, "Infinity"),
                Arguments.of(Double.NEGATIVE_INFINITY, "-Infinity"));
    }

    @ParameterizedTest
    @MethodSource("nonFiniteDoubleCases")
    void writeNonFiniteDouble(double value, String expected) {
        byte[] buf = new byte[24];
        int end = NumberCodec.writeNonFiniteDouble(buf, 0, value);
        assertEquals(expected, new String(buf, 0, end, StandardCharsets.US_ASCII));
    }

    @ParameterizedTest
    @MethodSource("nonFiniteDoubleCases")
    void writeNonFiniteDoubleQuoted(double value, String expected) {
        byte[] buf = new byte[24];
        int end = NumberCodec.writeNonFiniteDoubleQuoted(buf, 0, value);
        assertEquals("\"" + expected + "\"", new String(buf, 0, end, StandardCharsets.US_ASCII));
    }

    // --- writeFloatFull / writeDoubleFull ---

    @ParameterizedTest
    @ValueSource(floats = {0.0f, 1.5f, -3.14f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void writeFloatFull(float value) {
        byte[] buf = new byte[24];
        int end = NumberCodec.writeFloatFull(buf, 0, value);
        String actual = new String(buf, 0, end, StandardCharsets.US_ASCII);
        if (Float.isNaN(value)) {
            assertEquals("NaN", actual);
        } else if (value == Float.POSITIVE_INFINITY) {
            assertEquals("Infinity", actual);
        } else if (value == Float.NEGATIVE_INFINITY) {
            assertEquals("-Infinity", actual);
        } else {
            assertEquals(value, Float.parseFloat(actual));
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.5, -3.14, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void writeDoubleFull(double value) {
        byte[] buf = new byte[24];
        int end = NumberCodec.writeDoubleFull(buf, 0, value);
        String actual = new String(buf, 0, end, StandardCharsets.US_ASCII);
        if (Double.isNaN(value)) {
            assertEquals("NaN", actual);
        } else if (value == Double.POSITIVE_INFINITY) {
            assertEquals("Infinity", actual);
        } else if (value == Double.NEGATIVE_INFINITY) {
            assertEquals("-Infinity", actual);
        } else {
            assertEquals(value, Double.parseDouble(actual));
        }
    }

    @ParameterizedTest
    @ValueSource(floats = {0.0f, 1.5f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void writeFloatFullQuoted(float value) {
        byte[] buf = new byte[24];
        int end = NumberCodec.writeFloatFullQuoted(buf, 0, value);
        String actual = new String(buf, 0, end, StandardCharsets.US_ASCII);
        if (Float.isFinite(value)) {
            assertEquals(value, Float.parseFloat(actual));
        } else if (Float.isNaN(value)) {
            assertEquals("\"NaN\"", actual);
        } else if (value > 0) {
            assertEquals("\"Infinity\"", actual);
        } else {
            assertEquals("\"-Infinity\"", actual);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.5, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void writeDoubleFullQuoted(double value) {
        byte[] buf = new byte[24];
        int end = NumberCodec.writeDoubleFullQuoted(buf, 0, value);
        String actual = new String(buf, 0, end, StandardCharsets.US_ASCII);
        if (Double.isFinite(value)) {
            assertEquals(value, Double.parseDouble(actual));
        } else if (Double.isNaN(value)) {
            assertEquals("\"NaN\"", actual);
        } else if (value > 0) {
            assertEquals("\"Infinity\"", actual);
        } else {
            assertEquals("\"-Infinity\"", actual);
        }
    }

}
