/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(10)
class BigNumbersTest {

    private static final String MAX_DIGITS = "7".repeat(BigNumbers.MAX_DIGITS);
    private static final int TEXT_SLACK = BigNumbers.MAX_TEXT_DIGITS - BigNumbers.MAX_DIGITS;
    private static final String MAX_TEXT_DIGITS = "0".repeat(TEXT_SLACK) + MAX_DIGITS;
    private static final String TOO_MANY_DIGITS = MAX_TEXT_DIGITS + "7";
    private static final String TOO_MANY_SIGNIFICANT_DIGITS = "7".repeat(BigNumbers.MAX_TEXT_DIGITS);
    private static final String FULLWIDTH_SEVEN = "\uFF17";
    private static final String FULLWIDTH_ZERO = "\uFF10";

    @Test
    void binaryBoundHoldsEveryMaxDigitValue() {
        var largest = BigInteger.TEN.pow(BigNumbers.MAX_DIGITS).subtract(BigInteger.ONE);
        int magnitudeBytes = (largest.bitLength() + 7) / 8;
        assertEquals(BigNumbers.MAX_BIG_INTEGER_BYTES, magnitudeBytes, "bound must be the smallest sufficient size");
        BigNumbers.checkBigIntegerBytes(BigNumbers.MAX_BIG_INTEGER_BYTES);
        assertThrows(SerializationException.class,
                () -> BigNumbers.checkBigIntegerBytes(BigNumbers.MAX_BIG_INTEGER_BYTES + 1));
    }

    @Test
    void decodedBigIntegerDigitsAreBoundedExactly() {
        var largest = BigInteger.TEN.pow(BigNumbers.MAX_DIGITS).subtract(BigInteger.ONE);
        assertEquals(largest, BigNumbers.checkBigIntegerDigits(largest));
        assertEquals(largest.negate(), BigNumbers.checkBigIntegerDigits(largest.negate()));
        assertEquals(BigInteger.ZERO, BigNumbers.checkBigIntegerDigits(BigInteger.ZERO));
        var oneTooMany = BigInteger.TEN.pow(BigNumbers.MAX_DIGITS);
        assertThrows(SerializationException.class, () -> BigNumbers.checkBigIntegerDigits(oneTooMany));
        assertThrows(SerializationException.class, () -> BigNumbers.checkBigIntegerDigits(oneTooMany.negate()));
        // This magnitude fits the byte limit but has 1002 decimal digits.
        var allOnes = BigInteger.ONE.shiftLeft(BigNumbers.MAX_BIG_INTEGER_BYTES * 8).subtract(BigInteger.ONE);
        assertThrows(SerializationException.class, () -> BigNumbers.checkBigIntegerDigits(allOnes));
    }

    static List<String> acceptedText() {
        return List.of(
                MAX_DIGITS,
                "-" + MAX_DIGITS,
                MAX_TEXT_DIGITS,
                "-" + MAX_TEXT_DIGITS,
                "-" + "1".repeat(BigNumbers.MAX_DIGITS - 1) + ".1",
                "1".repeat(BigNumbers.MAX_DIGITS - 2) + ".1e+1",
                "1".repeat(BigNumbers.MAX_DIGITS - 2) + ".1E-1",
                MAX_DIGITS + "E+9999",
                "0." + "0".repeat(TEXT_SLACK - 1) + MAX_DIGITS);
    }

    @ParameterizedTest
    @MethodSource("acceptedText")
    void acceptsUpToMaxDigits(String text) {
        var expected = new BigDecimal(text);
        assertEquals(expected, BigNumbers.parseBigDecimal(text));
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        assertEquals(expected, BigNumbers.parseBigDecimal(bytes, 0, bytes.length));
        if (text.matches("-?\\d+")) {
            assertEquals(expected.toBigInteger(), BigNumbers.parseBigInteger(text));
            assertEquals(expected.toBigInteger(), BigNumbers.parseBigInteger(bytes, 0, bytes.length));
        }
    }

    static List<String> rejectedText() {
        return List.of(
                TOO_MANY_DIGITS,
                "-" + TOO_MANY_DIGITS,
                MAX_TEXT_DIGITS + ".1",
                "1".repeat(BigNumbers.MAX_TEXT_DIGITS - 1) + ".1e+12",
                "1".repeat(2_000_000),
                "1".repeat(2_000_000) + ".5E-3",
                TOO_MANY_SIGNIFICANT_DIGITS,
                "-" + TOO_MANY_SIGNIFICANT_DIGITS,
                MAX_DIGITS + "7",
                "7".repeat(BigNumbers.MAX_DIGITS + 6) + "E+9999",
                MAX_DIGITS + ".5");
    }

    @ParameterizedTest
    @MethodSource("rejectedText")
    void rejectsMoreThanMaxDigits(String text) {
        boolean integral = text.matches("-?\\d+");
        if (integral) {
            assertThrows(SerializationException.class, () -> BigNumbers.parseBigInteger(text));
        }
        assertThrows(SerializationException.class, () -> BigNumbers.parseBigDecimal(text));
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        if (integral) {
            assertThrows(SerializationException.class, () -> BigNumbers.parseBigInteger(bytes, 0, bytes.length));
        }
        assertThrows(SerializationException.class, () -> BigNumbers.parseBigDecimal(bytes, 0, bytes.length));
    }

    @Test
    void significantDigitBoundIsExactAndCitesItself() {
        var e = assertThrows(SerializationException.class,
                () -> BigNumbers.parseBigInteger(TOO_MANY_SIGNIFICANT_DIGITS));
        assertTrue(e.getMessage().contains(BigNumbers.MAX_DIGITS + " significant digits"), e.getMessage());
        e = assertThrows(SerializationException.class, () -> BigNumbers.parseBigDecimal(MAX_DIGITS + ".5"));
        assertTrue(e.getMessage().contains(BigNumbers.MAX_DIGITS + " significant digits"), e.getMessage());
        e = assertThrows(SerializationException.class,
                () -> BigNumbers.checkBigIntegerDigits(new BigInteger("7".repeat(BigNumbers.MAX_DIGITS + 5))));
        assertTrue(e.getMessage().contains(BigNumbers.MAX_DIGITS + " significant digits"), e.getMessage());
        e = assertThrows(SerializationException.class, () -> BigNumbers.parseBigDecimal(TOO_MANY_DIGITS));
        assertTrue(e.getMessage().contains(BigNumbers.MAX_TEXT_DIGITS + " digit characters"), e.getMessage());

        var largest = new BigDecimal(BigInteger.TEN.pow(BigNumbers.MAX_DIGITS).subtract(BigInteger.ONE), 7);
        assertEquals(largest, BigNumbers.checkBigDecimalDigits(largest));
        assertThrows(SerializationException.class,
                () -> BigNumbers.checkBigDecimalDigits(new BigDecimal(BigInteger.TEN.pow(BigNumbers.MAX_DIGITS), 7)));
        assertEquals(new BigInteger(MAX_DIGITS), BigNumbers.parseBigInteger("0".repeat(TEXT_SLACK) + MAX_DIGITS));
    }

    @Test
    void byteSpanBoundsOnlyTheSpan() {
        byte[] bytes = ("xx" + TOO_MANY_DIGITS + "yy").getBytes(StandardCharsets.US_ASCII);
        assertEquals(new BigInteger(MAX_DIGITS),
                BigNumbers.parseBigInteger(bytes, 2, BigNumbers.MAX_TEXT_DIGITS));
        assertThrows(SerializationException.class,
                () -> BigNumbers.parseBigInteger(bytes, 2, BigNumbers.MAX_TEXT_DIGITS + 1));
    }

    @Test
    void countsNonAsciiDigitsLikeTheJdk() {
        String accepted = FULLWIDTH_ZERO.repeat(TEXT_SLACK) + FULLWIDTH_SEVEN.repeat(BigNumbers.MAX_DIGITS);
        assertEquals(new BigInteger(MAX_DIGITS), BigNumbers.parseBigInteger(accepted));
        assertEquals(new BigDecimal(MAX_DIGITS), BigNumbers.parseBigDecimal(accepted));
        String rejected = accepted + FULLWIDTH_SEVEN;
        assertThrows(SerializationException.class, () -> BigNumbers.parseBigInteger(rejected));
        assertThrows(SerializationException.class, () -> BigNumbers.parseBigDecimal(rejected));
        assertThrows(SerializationException.class, () -> BigNumbers.checkDigits(rejected));
        String tooSignificant = FULLWIDTH_SEVEN.repeat(BigNumbers.MAX_DIGITS + 1);
        assertThrows(SerializationException.class, () -> BigNumbers.parseBigInteger(tooSignificant));
        assertThrows(SerializationException.class, () -> BigNumbers.parseBigDecimal(tooSignificant));
        String mixed = "\u0663" + MAX_TEXT_DIGITS;
        assertThrows(SerializationException.class, () -> BigNumbers.parseBigDecimal(mixed));
        assertThrows(SerializationException.class, () -> BigNumbers.parseBigDecimal(mixed + "e5"));
        String huge = FULLWIDTH_SEVEN.repeat(300_000);
        assertThrows(SerializationException.class, () -> BigNumbers.parseBigDecimal(huge));
        assertThrows(SerializationException.class, () -> BigNumbers.parseBigInteger("-" + huge));
    }

    static Stream<BigDecimal> maxDigitValuesAtEveryNotation() {
        var largest = BigInteger.TEN.pow(BigNumbers.MAX_DIGITS).subtract(BigInteger.ONE);
        return Stream.of(0, 500, 999, 1000, 1001, 1005, 1006, -1, -5, -1000, Integer.MIN_VALUE + 1, Integer.MAX_VALUE)
                .map(scale -> new BigDecimal(largest, scale));
    }

    @ParameterizedTest
    @MethodSource("maxDigitValuesAtEveryNotation")
    void textBoundAcceptsEveryRenderingOfMaxDigitValues(BigDecimal value) {
        String text = value.toString();
        assertEquals(value, BigNumbers.parseBigDecimal(text));
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        assertEquals(value, BigNumbers.parseBigDecimal(bytes, 0, bytes.length));
        // Plain notation with a negative scale adds trailing zeros, which count as significant digits.
        int scale = value.scale();
        long plainDigits = scale <= 0 ? value.precision() - (long) scale : Math.max(value.precision(), scale + 1L);
        if (plainDigits <= BigNumbers.MAX_TEXT_DIGITS && scale >= 0) {
            assertEquals(value, BigNumbers.parseBigDecimal(value.toPlainString()));
        } else if (Math.abs(scale) <= BigNumbers.MAX_TEXT_DIGITS) {
            assertThrows(SerializationException.class, () -> BigNumbers.parseBigDecimal(value.toPlainString()));
        }
    }

    @Test
    void malformedTextStillThrowsNumberFormatException() {
        assertThrows(NumberFormatException.class, () -> BigNumbers.parseBigInteger("1.5"));
        assertThrows(NumberFormatException.class, () -> BigNumbers.parseBigDecimal("abc"));
        assertThrows(NumberFormatException.class, () -> BigNumbers.parseBigDecimal("1" + "x".repeat(5000)));
    }

    @Test
    void checkDigitsCountsOnlyDigits() {
        BigNumbers.checkDigits(BigNumbers.MAX_DIGITS);
        BigNumbers.checkDigits(BigNumbers.MAX_TEXT_DIGITS);
        assertThrows(SerializationException.class, () -> BigNumbers.checkDigits(BigNumbers.MAX_TEXT_DIGITS + 1));
        BigNumbers.checkDigits("+" + MAX_TEXT_DIGITS + "e+");
        BigNumbers.checkDigits("-." + MAX_TEXT_DIGITS + "E-");
        assertThrows(SerializationException.class, () -> BigNumbers.checkDigits(TOO_MANY_DIGITS));
    }

    @Test
    void convertsBigDecimalWithinScaleBound() {
        assertEquals(BigInteger.TEN.pow(BigNumbers.MAX_DIGITS - 1),
                BigNumbers.toBigInteger(new BigDecimal("1E+" + (BigNumbers.MAX_DIGITS - 1))));
        assertEquals(BigInteger.TEN.pow(BigNumbers.MAX_DIGITS - 1),
                BigNumbers.toBigIntegerExact(new BigDecimal("1E+" + (BigNumbers.MAX_DIGITS - 1))));
        var largest = BigInteger.TEN.pow(BigNumbers.MAX_DIGITS).subtract(BigInteger.ONE);
        assertEquals(largest, BigNumbers.toBigInteger(new BigDecimal(largest, 0)));
        assertEquals(largest.divide(BigInteger.TEN.pow(5)), BigNumbers.toBigInteger(new BigDecimal(largest, 5)));
        assertEquals(largest.divide(BigInteger.TEN), BigNumbers.toBigInteger(new BigDecimal(largest, 1)));
        assertEquals(BigInteger.ZERO,
                BigNumbers.toBigInteger(new BigDecimal("1E-" + BigNumbers.MAX_BIG_INTEGER_SCALE)));
        assertEquals(BigInteger.ZERO, BigNumbers.toBigInteger(new BigDecimal("0E+" + BigNumbers.MAX_DIGITS)));
        assertEquals(BigInteger.ZERO, BigNumbers.toBigIntegerExact(new BigDecimal("0E+" + BigNumbers.MAX_DIGITS)));
        assertEquals(BigInteger.ONE, BigNumbers.toBigInteger(new BigDecimal("1.5")));
        assertEquals(BigInteger.valueOf(-1), BigNumbers.toBigInteger(new BigDecimal("-1.5")));
        assertEquals(BigInteger.valueOf(150), BigNumbers.toBigIntegerExact(new BigDecimal("1.50E+2")));
        assertThrows(ArithmeticException.class, () -> BigNumbers.toBigIntegerExact(new BigDecimal("1.5")));
    }

    static Stream<BigDecimal> triviallyZeroBeyondScaleBound() {
        return Stream.of(
                new BigDecimal("1E-" + (BigNumbers.MAX_BIG_INTEGER_SCALE + 1)),
                new BigDecimal("-123456E-2000000000"),
                new BigDecimal(BigInteger.ONE, Integer.MAX_VALUE),
                new BigDecimal("0E+2000000000"),
                new BigDecimal("0E-2000000000"));
    }

    @ParameterizedTest
    @MethodSource("triviallyZeroBeyondScaleBound")
    void convertsTriviallyZeroValuesBeyondScaleBound(BigDecimal value) {
        assertEquals(BigInteger.ZERO, BigNumbers.toBigInteger(value));
        assertEquals(0L, value.longValue());
        if (value.signum() == 0) {
            assertEquals(BigInteger.ZERO, BigNumbers.toBigIntegerExact(value));
        } else {
            assertThrows(ArithmeticException.class, () -> BigNumbers.toBigIntegerExact(value));
        }
    }

    @Test
    @Timeout(5)
    void convertsFractionsBelowOneWithoutScalingWork() {
        var largest = BigInteger.TEN.pow(BigNumbers.MAX_DIGITS).subtract(BigInteger.ONE);
        var fractions = List.of(
                new BigDecimal("1E-" + BigNumbers.MAX_BIG_INTEGER_SCALE),
                new BigDecimal("-7E-" + (BigNumbers.MAX_BIG_INTEGER_SCALE / 2)),
                new BigDecimal("0.5"),
                new BigDecimal(largest, BigNumbers.MAX_DIGITS + 500),
                new BigDecimal(largest.negate(), BigNumbers.MAX_BIG_INTEGER_SCALE));
        long start = System.nanoTime();
        for (int i = 0; i < 2_000; i++) {
            for (var value : fractions) {
                assertEquals(BigInteger.ZERO, BigNumbers.toBigInteger(value));
                assertThrows(ArithmeticException.class, () -> BigNumbers.toBigIntegerExact(value));
            }
        }
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMillis < 1_000, "fractions below one took " + elapsedMillis + " ms to convert");
        assertEquals(BigInteger.TEN, BigNumbers.toBigInteger(new BigDecimal(BigInteger.valueOf(105), 1)));
        assertEquals(BigInteger.valueOf(123),
                BigNumbers.toBigIntegerExact(new BigDecimal(BigInteger.valueOf(123).multiply(BigInteger.TEN.pow(400)),
                        400)));
    }

    static Stream<BigDecimal> integerFormBeyondMaxDigits() {
        var largest = BigInteger.TEN.pow(BigNumbers.MAX_DIGITS).subtract(BigInteger.ONE);
        return Stream.of(
                new BigDecimal("1E+" + BigNumbers.MAX_DIGITS),
                new BigDecimal("-1E+" + BigNumbers.MAX_DIGITS),
                new BigDecimal(largest, -1),
                new BigDecimal(largest, -BigNumbers.MAX_BIG_INTEGER_SCALE),
                new BigDecimal("1E+" + BigNumbers.MAX_BIG_INTEGER_SCALE),
                new BigDecimal("1E+" + (BigNumbers.MAX_BIG_INTEGER_SCALE + 1)),
                new BigDecimal("-7E+" + (BigNumbers.MAX_BIG_INTEGER_SCALE + 1)),
                new BigDecimal("1812031E+900000000"),
                new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE + 1),
                new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE));
    }

    @ParameterizedTest
    @MethodSource("integerFormBeyondMaxDigits")
    @Timeout(2)
    void rejectsConversionBeyondMaxDigits(BigDecimal value) {
        var e = assertThrows(SerializationException.class, () -> BigNumbers.toBigInteger(value));
        assertTrue(e.getMessage().contains(BigNumbers.MAX_DIGITS + " significant digits"), e.getMessage());
        e = assertThrows(SerializationException.class, () -> BigNumbers.toBigIntegerExact(value));
        assertTrue(e.getMessage().contains(BigNumbers.MAX_DIGITS + " significant digits"), e.getMessage());
    }

    @Test
    void rejectsConversionOutsideScaleBound() {
        var value = new BigDecimal(BigInteger.TEN.pow(BigNumbers.MAX_BIG_INTEGER_SCALE + 2),
                BigNumbers.MAX_BIG_INTEGER_SCALE + 1);
        var e = assertThrows(SerializationException.class, () -> BigNumbers.toBigInteger(value));
        assertTrue(e.getMessage().contains("scale"), e.getMessage());
        e = assertThrows(SerializationException.class, () -> BigNumbers.toBigIntegerExact(value));
        assertTrue(e.getMessage().contains("scale"), e.getMessage());
        var withinScale = new BigDecimal(BigInteger.TEN.pow(BigNumbers.MAX_BIG_INTEGER_SCALE + 1),
                BigNumbers.MAX_BIG_INTEGER_SCALE);
        assertEquals(BigInteger.TEN, BigNumbers.toBigInteger(withinScale));
        assertEquals(BigInteger.TEN, BigNumbers.toBigIntegerExact(withinScale));
    }

    static List<String> compactShapedText() {
        return List.of(
                "0",
                "-0",
                "+0",
                "7",
                "-7",
                "+7",
                "007",
                "-007.50",
                "1.",
                "-1.",
                ".5",
                "-.5",
                "+.5",
                "0.0",
                "-0.0",
                "0.000",
                "1.50",
                "1234567890.123456789",
                "-1234567890.123456789",
                "123456789012345678",
                "999999999999999999",
                "1234567890123456789",
                "-1234567890123456789",
                "9223372036854775807",
                "-9223372036854775807",
                "9223372036854775808",
                "-9223372036854775808",
                "9999999999999999999",
                "0000000000000000000001",
                "0000000000000000000001.5",
                "12345678901234567890",
                "1E5",
                "1e-5",
                "1.5E+3",
                "0E+5",
                "+1.5e3");
    }

    @ParameterizedTest
    @MethodSource("compactShapedText")
    void compactParsersMatchTheJdk(String text) {
        var expectedDecimal = new BigDecimal(text);
        assertEquals(expectedDecimal, BigNumbers.parseBigDecimal(text));
        assertEquals(expectedDecimal.scale(), BigNumbers.parseBigDecimal(text).scale());
        assertEquals(expectedDecimal.toString(), BigNumbers.parseBigDecimal(text).toString());
        byte[] bytes = ("<" + text + ">").getBytes(StandardCharsets.US_ASCII);
        assertEquals(expectedDecimal, BigNumbers.parseBigDecimal(bytes, 1, text.length()));
        assertEquals(expectedDecimal.scale(), BigNumbers.parseBigDecimal(bytes, 1, text.length()).scale());
        if (text.matches("[+-]?\\d+")) {
            var expectedInteger = new BigInteger(text);
            assertEquals(expectedInteger, BigNumbers.parseBigInteger(text));
            assertEquals(expectedInteger, BigNumbers.parseBigInteger(bytes, 1, text.length()));
        } else {
            assertThrows(NumberFormatException.class, () -> BigNumbers.parseBigInteger(text));
            assertThrows(NumberFormatException.class, () -> BigNumbers.parseBigInteger(bytes, 1, text.length()));
        }
    }

    static List<String> malformedCompactShapedText() {
        return List.of(
                "",
                "-",
                "+",
                ".",
                "-.",
                "+.",
                "..",
                "1..",
                "1.2.3",
                "+-5",
                "--5",
                "5-",
                "5+",
                " 5",
                "5 ",
                "1_000",
                "0x10",
                "1,5",
                "abc",
                "5e",
                "e5");
    }

    @Test
    void nonAsciiDigitsKeepTheirJdkBehavior() {
        String arabicIndic = "\u0661\u0662";
        assertEquals(new BigDecimal(arabicIndic), BigNumbers.parseBigDecimal(arabicIndic));
        assertEquals(new BigInteger(arabicIndic), BigNumbers.parseBigInteger(arabicIndic));
        byte[] bytes = arabicIndic.getBytes(StandardCharsets.UTF_8);
        assertThrows(NumberFormatException.class, () -> BigNumbers.parseBigDecimal(bytes, 0, bytes.length));
        assertThrows(NumberFormatException.class, () -> BigNumbers.parseBigInteger(bytes, 0, bytes.length));
    }

    @ParameterizedTest
    @MethodSource("malformedCompactShapedText")
    void compactParsersRejectWhatTheJdkRejects(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        assertThrows(NumberFormatException.class, () -> new BigDecimal(text));
        assertThrows(NumberFormatException.class, () -> BigNumbers.parseBigDecimal(text));
        assertThrows(NumberFormatException.class, () -> BigNumbers.parseBigDecimal(bytes, 0, bytes.length));
        assertThrows(NumberFormatException.class, () -> BigNumbers.parseBigInteger(text));
        assertThrows(NumberFormatException.class, () -> BigNumbers.parseBigInteger(bytes, 0, bytes.length));
    }
}
