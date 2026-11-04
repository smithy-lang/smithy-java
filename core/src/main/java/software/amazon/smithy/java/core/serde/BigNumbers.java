/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * Limits and bounded conversions for {@link BigInteger} and {@link BigDecimal} values at serialization boundaries.
 *
 * <p>Methods throw {@link SerializationException} when input or conversions exceed their limits.
 */
public final class BigNumbers {

    /**
     * Maximum significant decimal digits of a binary value and of bounded plain-notation output.
     *
     * <p>Text input uses {@link #MAX_TEXT_DIGITS} to allow exponent digits and leading zeros.
     */
    public static final int MAX_DIGITS = 1000;

    /**
     * Maximum digits in textual input, including exponent digits and leading zeros.
     *
     * <p>Signs, decimal points, and exponent markers do not count.
     * Parsed values must also satisfy the {@link #MAX_DIGITS} significant-digit limit.
     */
    public static final int MAX_TEXT_DIGITS = MAX_DIGITS + 10;

    /**
     * Maximum significant magnitude length, in bytes, for a binary {@code BigInteger}.
     *
     * <p>Leading zero bytes do not count toward this limit.
     * Values within this byte limit can still exceed the digit limit.
     * Apply {@link #checkBigIntegerDigits} after decoding.
     */
    public static final int MAX_BIG_INTEGER_BYTES = 416;

    /**
     * Maximum positive scale for conversion from {@code BigDecimal} to {@code BigInteger}.
     *
     * <p>Applies only to values with a nonzero integer part.
     * Negative scales must produce an integer with at most {@link #MAX_DIGITS} digits.
     */
    public static final int MAX_BIG_INTEGER_SCALE = 100_000;

    private static final int MAX_COMPACT_LENGTH = 21;

    private static final long COMPACT_ACCUMULATOR_LIMIT = (Long.MAX_VALUE - 9) / 10;

    private static final long NOT_COMPACT = Long.MIN_VALUE;

    private static final BigInteger TEN_POW_MAX_DIGITS = BigInteger.TEN.pow(MAX_DIGITS);

    private static final int MAX_DIGITS_BIT_LENGTH = TEN_POW_MAX_DIGITS.bitLength();

    private BigNumbers() {}

    /**
     * Rejects a textual digit count that exceeds {@link #MAX_TEXT_DIGITS}.
     *
     * @throws SerializationException if the count exceeds the limit.
     */
    public static void checkDigits(int digitCount) {
        if (digitCount > MAX_TEXT_DIGITS) {
            throw tooManyDigits();
        }
    }

    /**
     * Rejects a textual number with more than {@link #MAX_TEXT_DIGITS} digits.
     *
     * <p>Counts ASCII and non-ASCII decimal digits without validating number syntax.
     *
     * @throws SerializationException if the text contains more digits than the limit.
     */
    public static void checkDigits(CharSequence value) {
        int length = value.length();
        if (length <= MAX_TEXT_DIGITS) {
            return;
        }
        int digits = 0;
        for (int i = 0; i < length; i++) {
            if (isDigit(value.charAt(i)) && ++digits > MAX_TEXT_DIGITS) {
                throw tooManyDigits();
            }
        }
    }

    /**
     * Rejects an ASCII byte span with more than {@link #MAX_TEXT_DIGITS} digits.
     *
     * <p>Counts ASCII digits without validating number syntax.
     *
     * @param start Index of the first byte of the number.
     * @param length Number of bytes in the number.
     * @throws SerializationException if the span contains more digits than the limit.
     */
    public static void checkDigits(byte[] buf, int start, int length) {
        if (length <= MAX_TEXT_DIGITS) {
            return;
        }
        int digits = 0;
        for (int i = start, end = start + length; i < end; i++) {
            byte b = buf[i];
            if (b >= '0' && b <= '9' && ++digits > MAX_TEXT_DIGITS) {
                throw tooManyDigits();
            }
        }
    }

    private static boolean isDigit(char c) {
        return (c >= '0' && c <= '9') || (c > 0x7F && Character.isDigit(c));
    }

    /**
     * Parses a {@code BigInteger} within the digit limits.
     *
     * @throws SerializationException if the text exceeds {@link #MAX_TEXT_DIGITS} digit characters or
     * {@link #MAX_DIGITS} significant digits.
     * @throws NumberFormatException if the text is not a valid {@code BigInteger}.
     */
    public static BigInteger parseBigInteger(String value) {
        int length = value.length();
        if (length <= MAX_COMPACT_LENGTH) {
            BigInteger compact = compactInteger(value, length);
            if (compact != null) {
                return compact;
            }
        }
        checkDigits(value);
        BigInteger result = new BigInteger(value);
        return length > MAX_DIGITS ? checkBigIntegerDigits(result) : result;
    }

    /**
     * Parses a {@code BigDecimal} within the digit limits.
     *
     * @throws SerializationException if the text exceeds {@link #MAX_TEXT_DIGITS} digit characters or
     * {@link #MAX_DIGITS} significant digits.
     * @throws NumberFormatException if the text is not a valid {@code BigDecimal}.
     */
    public static BigDecimal parseBigDecimal(String value) {
        int length = value.length();
        if (length <= MAX_COMPACT_LENGTH) {
            BigDecimal compact = compactDecimal(value, length);
            if (compact != null) {
                return compact;
            }
        }
        checkDigits(value);
        BigDecimal result = new BigDecimal(value);
        return length > MAX_DIGITS ? checkBigDecimalDigits(result) : result;
    }

    /**
     * Parses a {@code BigInteger} from an ASCII byte span within the digit limits.
     *
     * @param start Index of the first byte of the number.
     * @param length Number of bytes in the number.
     * @throws SerializationException if the span exceeds {@link #MAX_TEXT_DIGITS} digit characters or
     * {@link #MAX_DIGITS} significant digits.
     * @throws NumberFormatException if the span is not a valid {@code BigInteger}.
     */
    public static BigInteger parseBigInteger(byte[] buf, int start, int length) {
        if (length <= MAX_COMPACT_LENGTH) {
            BigInteger compact = compactInteger(buf, start, length);
            if (compact != null) {
                return compact;
            }
        }
        checkDigits(buf, start, length);
        BigInteger result = new BigInteger(new String(buf, start, length, StandardCharsets.US_ASCII));
        return length > MAX_DIGITS ? checkBigIntegerDigits(result) : result;
    }

    /**
     * Parses a {@code BigDecimal} from an ASCII byte span within the digit limits.
     *
     * @param start Index of the first byte of the number.
     * @param length Number of bytes in the number.
     * @throws SerializationException if the span exceeds {@link #MAX_TEXT_DIGITS} digit characters or
     * {@link #MAX_DIGITS} significant digits.
     * @throws NumberFormatException if the span is not a valid {@code BigDecimal}.
     */
    public static BigDecimal parseBigDecimal(byte[] buf, int start, int length) {
        if (length <= MAX_COMPACT_LENGTH) {
            BigDecimal compact = compactDecimal(buf, start, length);
            if (compact != null) {
                return compact;
            }
        }
        checkDigits(buf, start, length);
        BigDecimal result = new BigDecimal(new String(buf, start, length, StandardCharsets.US_ASCII));
        return length > MAX_DIGITS ? checkBigDecimalDigits(result) : result;
    }

    // Return null for unsupported or malformed text so the JDK parsers preserve syntax and error behavior.
    private static BigInteger compactInteger(byte[] buf, int start, int length) {
        int end = start + length;
        int i = start;
        boolean negative = false;
        if (i < end && (buf[i] == '-' || buf[i] == '+')) {
            negative = buf[i] == '-';
            i++;
        }
        if (i == end) {
            return null;
        }
        long magnitude = accumulateDigits(buf, i, end, 0);
        if (magnitude == NOT_COMPACT) {
            return null;
        }
        return BigInteger.valueOf(negative ? -magnitude : magnitude);
    }

    private static BigDecimal compactDecimal(byte[] buf, int start, int length) {
        int end = start + length;
        int i = start;
        boolean negative = false;
        if (i < end && (buf[i] == '-' || buf[i] == '+')) {
            negative = buf[i] == '-';
            i++;
        }
        int dot = i;
        while (dot < end && buf[dot] != '.') {
            dot++;
        }
        int scale = dot < end ? end - dot - 1 : 0;
        if (dot - i + scale == 0) {
            return null;
        }
        long magnitude = accumulateDigits(buf, i, dot, 0);
        if (magnitude != NOT_COMPACT && scale > 0) {
            magnitude = accumulateDigits(buf, dot + 1, end, magnitude);
        }
        if (magnitude == NOT_COMPACT) {
            return null;
        }
        return BigDecimal.valueOf(negative ? -magnitude : magnitude, scale);
    }

    private static long accumulateDigits(byte[] buf, int from, int to, long acc) {
        for (int i = from; i < to; i++) {
            int d = buf[i] - '0';
            if (d < 0 || d > 9 || acc > COMPACT_ACCUMULATOR_LIMIT) {
                return NOT_COMPACT;
            }
            acc = acc * 10 + d;
        }
        return acc;
    }

    private static BigInteger compactInteger(CharSequence text, int length) {
        int i = 0;
        boolean negative = false;
        if (i < length && (text.charAt(i) == '-' || text.charAt(i) == '+')) {
            negative = text.charAt(i) == '-';
            i++;
        }
        if (i == length) {
            return null;
        }
        long magnitude = accumulateDigits(text, i, length, 0);
        if (magnitude == NOT_COMPACT) {
            return null;
        }
        return BigInteger.valueOf(negative ? -magnitude : magnitude);
    }

    private static BigDecimal compactDecimal(CharSequence text, int length) {
        int i = 0;
        boolean negative = false;
        if (i < length && (text.charAt(i) == '-' || text.charAt(i) == '+')) {
            negative = text.charAt(i) == '-';
            i++;
        }
        int dot = i;
        while (dot < length && text.charAt(dot) != '.') {
            dot++;
        }
        int scale = dot < length ? length - dot - 1 : 0;
        if (dot - i + scale == 0) {
            return null;
        }
        long magnitude = accumulateDigits(text, i, dot, 0);
        if (magnitude != NOT_COMPACT && scale > 0) {
            magnitude = accumulateDigits(text, dot + 1, length, magnitude);
        }
        if (magnitude == NOT_COMPACT) {
            return null;
        }
        return BigDecimal.valueOf(negative ? -magnitude : magnitude, scale);
    }

    private static long accumulateDigits(CharSequence text, int from, int to, long acc) {
        for (int i = from; i < to; i++) {
            int d = text.charAt(i) - '0';
            if (d < 0 || d > 9 || acc > COMPACT_ACCUMULATOR_LIMIT) {
                return NOT_COMPACT;
            }
            acc = acc * 10 + d;
        }
        return acc;
    }

    /**
     * Rejects a binary magnitude with more significant bytes than {@link #MAX_BIG_INTEGER_BYTES}.
     *
     * <p>Check the byte limit before decoding.
     * Apply {@link #checkBigIntegerDigits} to the decoded value.
     *
     * @param length Significant magnitude length in bytes, excluding leading zero bytes.
     * @throws SerializationException if the length exceeds the limit.
     */
    public static void checkBigIntegerBytes(int length) {
        if (length > MAX_BIG_INTEGER_BYTES) {
            throw new SerializationException(
                    "Binary BigInteger of " + length + " bytes exceeds the maximum of " + MAX_BIG_INTEGER_BYTES
                            + " bytes");
        }
    }

    /**
     * Rejects a decoded {@code BigInteger} with more than {@link #MAX_DIGITS} decimal digits.
     *
     * @return the unchanged value.
     * @throws SerializationException if the value has more digits than the limit.
     */
    public static BigInteger checkBigIntegerDigits(BigInteger value) {
        if (value.bitLength() >= MAX_DIGITS_BIT_LENGTH && value.abs().compareTo(TEN_POW_MAX_DIGITS) >= 0) {
            throw tooManySignificantDigits();
        }
        return value;
    }

    /**
     * Rejects a {@code BigDecimal} with more than {@link #MAX_DIGITS} significant digits.
     *
     * @return the unchanged value.
     * @throws SerializationException if the unscaled value has more digits than the limit.
     */
    public static BigDecimal checkBigDecimalDigits(BigDecimal value) {
        if (value.precision() > MAX_DIGITS) {
            throw tooManySignificantDigits();
        }
        return value;
    }

    /**
     * Converts a {@code BigDecimal} to a {@code BigInteger} and discards the fractional part.
     *
     * <p>Zero and values with magnitude below one convert to zero at any scale without computing {@code 10^scale}.
     *
     * @throws SerializationException if the result would have more than {@link #MAX_DIGITS} digits, or if the
     * scale exceeds {@link #MAX_BIG_INTEGER_SCALE} and the value has a non-zero integer part.
     */
    public static BigInteger toBigInteger(BigDecimal value) {
        int scale = value.scale();
        if (scale < 0) {
            checkConvertedDigits(value, scale);
            return value.signum() == 0 ? BigInteger.ZERO : value.toBigInteger();
        }
        // |unscaled| < 10^precision <= 10^scale means |value| < 1, so skip the 10^scale division entirely.
        if (scale > 0 && value.precision() <= scale) {
            return BigInteger.ZERO;
        }
        if (scale > MAX_BIG_INTEGER_SCALE) {
            throw bigIntegerScaleExceeded(scale);
        }
        return value.toBigInteger();
    }

    /**
     * Converts a {@code BigDecimal} to a {@code BigInteger} and requires a zero fractional part.
     *
     * <p>Zero converts for any scale.
     *
     * @throws SerializationException if the result would have more than {@link #MAX_DIGITS} digits, or if the
     * scale exceeds {@link #MAX_BIG_INTEGER_SCALE} and the value has a non-zero integer part.
     * @throws ArithmeticException if the value has a non-zero fractional part.
     */
    public static BigInteger toBigIntegerExact(BigDecimal value) {
        int scale = value.scale();
        if (scale < 0) {
            checkConvertedDigits(value, scale);
            return value.signum() == 0 ? BigInteger.ZERO : value.toBigIntegerExact();
        }
        // |unscaled| < 10^precision <= 10^scale means |value| < 1, so skip the 10^scale division entirely.
        if (scale > 0 && value.precision() <= scale) {
            if (value.signum() == 0) {
                return BigInteger.ZERO;
            }
            throw new ArithmeticException("Rounding necessary");
        }
        if (scale > MAX_BIG_INTEGER_SCALE) {
            throw bigIntegerScaleExceeded(scale);
        }
        return value.toBigIntegerExact();
    }

    private static void checkConvertedDigits(BigDecimal value, int scale) {
        if (value.signum() != 0 && value.precision() - (long) scale > MAX_DIGITS) {
            throw tooManySignificantDigits();
        }
    }

    private static SerializationException bigIntegerScaleExceeded(int scale) {
        return new SerializationException(
                "BigDecimal scale " + scale + " exceeds the maximum magnitude of " + MAX_BIG_INTEGER_SCALE
                        + " supported for conversion to BigInteger");
    }

    private static SerializationException tooManyDigits() {
        return new SerializationException(
                "Arbitrary-precision number exceeds the maximum of " + MAX_TEXT_DIGITS + " digit characters");
    }

    private static SerializationException tooManySignificantDigits() {
        return new SerializationException(
                "Arbitrary-precision number exceeds the maximum of " + MAX_DIGITS + " significant digits");
    }
}
