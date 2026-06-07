/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import ch.randelshofer.fastdoubleparser.JavaDoubleParser;
import ch.randelshofer.fastdoubleparser.JavaFloatParser;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Low-level utilities for reading/writing numeric primitives directly from/to byte arrays.
 */
@SmithyInternalApi
public final class NumberCodec {

    private NumberCodec() {}

    private static final VarHandle INT_HANDLE =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle LONG_HANDLE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
    private static final byte[] INT_MAX_DIGITS = "2147483647".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INT_MIN_DIGITS = "2147483648".getBytes(StandardCharsets.UTF_8);

    private static final byte[] TRUE = {'t', 'r', 'u', 'e'};
    private static final byte[] FALSE = {'f', 'a', 'l', 's', 'e'};

    private static final byte[] NAN = {'N', 'a', 'N'};
    private static final byte[] INF = {'I', 'n', 'f', 'i', 'n', 'i', 't', 'y'};
    private static final byte[] NEG_INF = {'-', 'I', 'n', 'f', 'i', 'n', 'i', 't', 'y'};
    private static final byte[] QUOTED_NAN = {'"', 'N', 'a', 'N', '"'};
    private static final byte[] QUOTED_INF = {'"', 'I', 'n', 'f', 'i', 'n', 'i', 't', 'y', '"'};
    private static final byte[] QUOTED_NEG_INF = {'"', '-', 'I', 'n', 'f', 'i', 'n', 'i', 't', 'y', '"'};
    private static final long[] POWERS_OF_10 = {
            1L,
            10L,
            100L,
            1000L,
            10000L,
            100000L,
            1000000L,
            10000000L,
            100000000L,
            1000000000L,
            10000000000L,
            100000000000L,
            1000000000000L,
            10000000000000L,
            100000000000000L,
            1000000000000000L,
            10000000000000000L,
            100000000000000000L,
            1000000000000000000L
    };

    /**
     * Parse an int from a byte array span. Handles optional leading minus sign.
     */
    public static int parseInt(byte[] buf, int start, int len) {
        if (len == 0) {
            throw new NumberFormatException("Empty input");
        }

        boolean negative = false;
        int i = start;
        int end = start + len;

        if (buf[i] == '-') {
            negative = true;
            i++;
            if (i == end)
                throw new NumberFormatException("Just a minus sign");
        } else if (buf[i] == '+') {
            i++;
            if (i == end)
                throw new NumberFormatException("Just a plus sign");
        }

        while (i < end - 1 && buf[i] == '0') {
            i++;
        }

        int digitLen = end - i;
        if (digitLen > 10) {
            throw new NumberFormatException("Integer overflow");
        }

        if (digitLen == 10) {
            byte[] limit = negative ? INT_MIN_DIGITS : INT_MAX_DIGITS;
            for (int j = 0; j < 10; j++) {
                int cmp = (buf[i + j] & 0xFF) - (limit[j] & 0xFF);
                if (cmp < 0)
                    break;
                if (cmp > 0)
                    throw new NumberFormatException("Integer overflow");
            }
        }

        int result = 0;

        while (i + 4 <= end) {
            int v = parse4Digits(buf, i);
            if (v < 0)
                break;
            result = result * 10000 - v;
            i += 4;
        }

        while (i < end) {
            int d = buf[i++] - '0';
            if (d < 0 || d > 9) {
                throw new NumberFormatException("Invalid digit at position " + (i - 1 - start));
            }
            result = result * 10 - d;
        }

        return negative ? result : -result;
    }

    /**
     * Parse a long from a byte array span. Handles optional leading minus sign.
     */
    public static long parseLong(byte[] buf, int start, int len) {
        if (len == 0) {
            throw new NumberFormatException("Empty input");
        }

        boolean negative = false;
        int i = start;
        int end = start + len;

        if (buf[i] == '-') {
            negative = true;
            i++;
            if (i == end)
                throw new NumberFormatException("Just a minus sign");
        } else if (buf[i] == '+') {
            i++;
            if (i == end)
                throw new NumberFormatException("Just a plus sign");
        }

        while (i < end - 1 && buf[i] == '0') {
            i++;
        }

        int digitLen = end - i;
        if (digitLen > 19) {
            throw new NumberFormatException("Long overflow");
        }

        long result = 0;

        while (i + 8 <= end) {
            long v = parse8Digits(buf, i);
            if (v < 0)
                break;
            result = result * 100000000L - v;
            i += 8;
        }

        while (i + 4 <= end) {
            int v = parse4Digits(buf, i);
            if (v < 0)
                break;
            result = result * 10000 - v;
            i += 4;
        }

        while (i < end) {
            int d = buf[i++] - '0';
            if (d < 0 || d > 9) {
                throw new NumberFormatException("Invalid digit at position " + (i - 1 - start));
            }
            long next = result * 10 - d;
            if (next > result) {
                throw new NumberFormatException("Long overflow");
            }
            result = next;
        }

        if (!negative) {
            result = -result;
            if (result < 0) {
                throw new NumberFormatException("Long overflow");
            }
        }
        return result;
    }

    /**
     * Parses 4 ASCII digit bytes at buf[i..i+4) into a value 0..9999.
     * Returns -1 if any byte is not a digit.
     */
    private static int parse4Digits(byte[] buf, int i) {
        int word = (int) INT_HANDLE.get(buf, i);
        int sub = word - 0x30303030;
        // (sub + 0x76767676) & 0x80808080 catches bytes > 9;
        // sub & 0x80808080 catches bytes < 0 (original byte < '0')
        if ((((sub + 0x76767676) | sub) & 0x80808080) != 0) {
            return -1;
        }
        int d0 = (sub >>> 24) & 0xFF;
        int d1 = (sub >>> 16) & 0xFF;
        int d2 = (sub >>> 8) & 0xFF;
        int d3 = sub & 0xFF;
        return d0 * 1000 + d1 * 100 + d2 * 10 + d3;
    }

    /**
     * Parses 8 ASCII digit bytes at buf[i..i+8) into a value 0..99999999.
     * Returns -1 if any byte is not a digit.
     */
    private static long parse8Digits(byte[] buf, int i) {
        long word = (long) LONG_HANDLE.get(buf, i);
        long sub = word - 0x3030303030303030L;
        if ((((sub + 0x7676767676767676L) | sub) & 0x8080808080808080L) != 0) {
            return -1;
        }
        // SWAR: combine pairs -> quads -> final 8-digit value
        long lo = sub & 0x000F000F000F000FL;
        long hi = (sub >>> 8) & 0x000F000F000F000FL;
        long pairs = hi * 10 + lo;
        long lo2 = pairs & 0x0000007F0000007FL;
        long hi2 = (pairs >>> 16) & 0x0000007F0000007FL;
        long quads = hi2 * 100 + lo2;
        int upper = (int) (quads >>> 32);
        int lower = (int) quads;
        return (long) upper * 10000 + lower;
    }

    /**
     * Parse a double from a byte array span using FastDoubleParser (no String allocation).
     */
    public static double parseDouble(byte[] buf, int offset, int length) {
        return JavaDoubleParser.parseDouble(buf, offset, length);
    }

    /**
     * Parse a float from a byte array span using FastDoubleParser (no String allocation).
     */
    public static float parseFloat(byte[] buf, int offset, int length) {
        return JavaFloatParser.parseFloat(buf, offset, length);
    }

    public static int writeBoolean(byte[] buf, int pos, boolean value) {
        byte[] bytes = value ? TRUE : FALSE;
        System.arraycopy(bytes, 0, buf, pos, bytes.length);
        return pos + bytes.length;
    }

    private static final byte[] DIGIT_PAIRS = new byte[200];

    static {
        for (int i = 0; i < 100; i++) {
            DIGIT_PAIRS[i * 2] = (byte) ('0' + i / 10);
            DIGIT_PAIRS[i * 2 + 1] = (byte) ('0' + i % 10);
        }
    }

    private static final BigInteger TEN_TO_18 = BigInteger.valueOf(1_000_000_000_000_000_000L);

    public static int writeInt(byte[] buf, int pos, int value) {
        if (value == 0) {
            buf[pos] = '0';
            return pos + 1;
        }

        if (value < 0) {
            buf[pos++] = '-';
            if (value == Integer.MIN_VALUE) {
                return writePositiveLong(buf, pos, 2147483648L);
            }
            value = -value;
        }

        return writePositiveInt(buf, pos, value);
    }

    public static int writeLong(byte[] buf, int pos, long value) {
        if (value == 0) {
            buf[pos] = '0';
            return pos + 1;
        }

        if (value < 0) {
            buf[pos++] = '-';
            if (value == Long.MIN_VALUE) {
                byte[] minBytes = "9223372036854775808".getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(minBytes, 0, buf, pos, minBytes.length);
                return pos + minBytes.length;
            }
            value = -value;
        }

        return writePositiveLong(buf, pos, value);
    }

    public static int writeBigInteger(byte[] buf, int pos, BigInteger value) {
        if (value.signum() < 0) {
            buf[pos++] = '-';
            value = value.negate();
        }

        if (value.compareTo(TEN_TO_18) < 0) {
            return writePositiveLong(buf, pos, value.longValue());
        }

        BigInteger[] qr = value.divideAndRemainder(TEN_TO_18);
        BigInteger high = qr[0];
        long low = qr[1].longValue();

        if (high.compareTo(TEN_TO_18) < 0) {
            pos = writePositiveLong(buf, pos, high.longValue());
            return writePaddedLong18(buf, pos, low);
        }

        BigInteger[] qr2 = high.divideAndRemainder(TEN_TO_18);
        long mid = qr2[1].longValue();

        if (qr2[0].compareTo(TEN_TO_18) < 0) {
            pos = writePositiveLong(buf, pos, qr2[0].longValue());
            pos = writePaddedLong18(buf, pos, mid);
            return writePaddedLong18(buf, pos, low);
        }

        String s = value.toString();
        return writeAsciiString(buf, pos, s);
    }

    public static int writeDouble(byte[] buf, int pos, double value) {
        long longValue = (long) value;
        if (value == (double) longValue) {
            return writeLong(buf, pos, longValue);
        }
        return Schubfach.writeDouble(buf, pos, value);
    }

    public static int writeFloat(byte[] buf, int pos, float value) {
        int intValue = (int) value;
        if (value == (float) intValue) {
            return writeInt(buf, pos, intValue);
        }
        return Schubfach.writeFloat(buf, pos, value);
    }

    public static int writeNonFiniteFloat(byte[] buf, int pos, float value) {
        byte[] bytes;
        if (Float.isNaN(value)) {
            bytes = NAN;
        } else {
            bytes = value > 0 ? INF : NEG_INF;
        }
        System.arraycopy(bytes, 0, buf, pos, bytes.length);
        return pos + bytes.length;
    }

    public static int writeNonFiniteDouble(byte[] buf, int pos, double value) {
        byte[] bytes;
        if (Double.isNaN(value)) {
            bytes = NAN;
        } else {
            bytes = value > 0 ? INF : NEG_INF;
        }
        System.arraycopy(bytes, 0, buf, pos, bytes.length);
        return pos + bytes.length;
    }

    public static int writeNonFiniteFloatQuoted(byte[] buf, int pos, float value) {
        byte[] bytes;
        if (Float.isNaN(value)) {
            bytes = QUOTED_NAN;
        } else {
            bytes = value > 0 ? QUOTED_INF : QUOTED_NEG_INF;
        }
        System.arraycopy(bytes, 0, buf, pos, bytes.length);
        return pos + bytes.length;
    }

    public static int writeNonFiniteDoubleQuoted(byte[] buf, int pos, double value) {
        byte[] bytes;
        if (Double.isNaN(value)) {
            bytes = QUOTED_NAN;
        } else {
            bytes = value > 0 ? QUOTED_INF : QUOTED_NEG_INF;
        }
        System.arraycopy(bytes, 0, buf, pos, bytes.length);
        return pos + bytes.length;
    }

    public static int writeFloatFull(byte[] buf, int pos, float value) {
        if (Float.isFinite(value)) {
            return writeFloat(buf, pos, value);
        }
        return writeNonFiniteFloat(buf, pos, value);
    }

    public static int writeDoubleFull(byte[] buf, int pos, double value) {
        if (Double.isFinite(value)) {
            return writeDouble(buf, pos, value);
        }
        return writeNonFiniteDouble(buf, pos, value);
    }

    public static int writeFloatFullQuoted(byte[] buf, int pos, float value) {
        if (Float.isFinite(value)) {
            return writeFloat(buf, pos, value);
        }
        return writeNonFiniteFloatQuoted(buf, pos, value);
    }

    public static int writeDoubleFullQuoted(byte[] buf, int pos, double value) {
        if (Double.isFinite(value)) {
            return writeDouble(buf, pos, value);
        }
        return writeNonFiniteDoubleQuoted(buf, pos, value);
    }

    public static int writeBigDecimal(byte[] buf, int pos, BigDecimal value) {
        int scale = value.scale();
        if (value.unscaledValue().bitLength() < 64) {
            if (scale == 0) {
                return writeLong(buf, pos, value.longValueExact());
            }
            if (scale > 0 && scale < POWERS_OF_10.length) {
                long unscaled = value.unscaledValue().longValue();
                if (unscaled < 0) {
                    buf[pos++] = '-';
                    unscaled = -unscaled;
                }
                long divisor = POWERS_OF_10[scale];
                long intPart = unscaled / divisor;
                long fracPart = unscaled - intPart * divisor;
                pos = writeLong(buf, pos, intPart);
                buf[pos++] = '.';
                for (int i = scale - 1; i >= 0; i--) {
                    long p10 = POWERS_OF_10[i];
                    int d = (int) (fracPart / p10);
                    buf[pos++] = (byte) ('0' + d);
                    fracPart -= d * p10;
                }
                return pos;
            }
        }
        return writeAsciiString(buf, pos, value.toPlainString());
    }

    public static int maxBigDecimalLength(BigDecimal value) {
        int scale = value.scale();
        int bitLength = value.unscaledValue().bitLength();
        if (bitLength < 64 && scale >= 0 && scale < POWERS_OF_10.length) {
            return 1 + 20 + 1 + scale;
        }
        int unscaledDigits = (int) (bitLength * 0.302) + 2;
        if (scale <= 0) {
            return 1 + unscaledDigits + (-scale);
        }
        return 1 + unscaledDigits + 1 + scale;
    }

    @SuppressWarnings("deprecation")
    private static int writeAsciiString(byte[] buf, int pos, String s) {
        int len = s.length();
        s.getBytes(0, len, buf, pos);
        return pos + len;
    }

    private static int writePositiveInt(byte[] buf, int pos, int value) {
        int digits = digitCount(value);
        int end = pos + digits;
        int p = end;

        while (value >= 10000 && p - 4 >= pos) {
            int q = value / 10000;
            int r = value - q * 10000;
            value = q;
            p -= 4;
            write4Digits(buf, p, r);
        }

        while (value >= 100) {
            int q = value / 100;
            int r = (value - q * 100) * 2;
            value = q;
            buf[--p] = DIGIT_PAIRS[r + 1];
            buf[--p] = DIGIT_PAIRS[r];
        }

        if (value >= 10) {
            int r = value * 2;
            buf[--p] = DIGIT_PAIRS[r + 1];
            buf[--p] = DIGIT_PAIRS[r];
        } else {
            buf[--p] = (byte) ('0' + value);
        }

        return end;
    }

    private static final int DIGITS_10_8 = 100_000_000;

    private static int writePositiveLong(byte[] buf, int pos, long value) {
        if (value <= Integer.MAX_VALUE) {
            return writePositiveInt(buf, pos, (int) value);
        }

        // Split into 32-bit chunks to avoid repeated 64-bit division
        if (value < (long) DIGITS_10_8 * DIGITS_10_8) {
            int lo = (int) (value % DIGITS_10_8);
            int hi = (int) (value / DIGITS_10_8);
            pos = writePositiveInt(buf, pos, hi);
            return writePaddedInt8(buf, pos, lo);
        } else {
            long tmp = value / DIGITS_10_8;
            int lo = (int) (value - tmp * DIGITS_10_8);
            int mid = (int) (tmp % DIGITS_10_8);
            int hi = (int) (tmp / DIGITS_10_8);
            pos = writePositiveInt(buf, pos, hi);
            pos = writePaddedInt8(buf, pos, mid);
            return writePaddedInt8(buf, pos, lo);
        }
    }

    /**
     * Writes exactly 8 digits (zero-padded) using 32-bit arithmetic only.
     */
    private static int writePaddedInt8(byte[] buf, int pos, int value) {
        int hi4 = value / 10000;
        int lo4 = value - hi4 * 10000;
        write4Digits(buf, pos, hi4);
        write4Digits(buf, pos + 4, lo4);
        return pos + 8;
    }

    /**
     * Writes 4 decimal digits (0000-9999) as a single int store (big-endian).
     */
    private static void write4Digits(byte[] buf, int pos, int value) {
        int q = (value * 5243) >>> 19; // value / 100
        int r = value - q * 100;
        int d01 = DIGIT_PAIRS[q * 2] << 24 | (DIGIT_PAIRS[q * 2 + 1] & 0xFF) << 16;
        int d23 = (DIGIT_PAIRS[r * 2] & 0xFF) << 8 | (DIGIT_PAIRS[r * 2 + 1] & 0xFF);
        INT_HANDLE.set(buf, pos, d01 | d23);
    }

    private static int writePaddedLong18(byte[] buf, int pos, long value) {
        int end = pos + 18;
        int p = end;
        for (int i = 0; i < 9; i++) {
            int r = (int) (value % 100) * 2;
            value /= 100;
            buf[--p] = DIGIT_PAIRS[r + 1];
            buf[--p] = DIGIT_PAIRS[r];
        }
        return end;
    }

    public static int digitCount(int value) {
        if (value < 10)
            return 1;
        if (value < 100)
            return 2;
        if (value < 1000)
            return 3;
        if (value < 10000)
            return 4;
        if (value < 100000)
            return 5;
        if (value < 1000000)
            return 6;
        if (value < 10000000)
            return 7;
        if (value < 100000000)
            return 8;
        if (value < 1000000000)
            return 9;
        return 10;
    }

}
