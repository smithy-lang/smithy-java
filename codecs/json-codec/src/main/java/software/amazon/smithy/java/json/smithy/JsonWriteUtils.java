/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * Low-level utilities for writing JSON primitives directly to byte arrays.
 *
 * <p>All methods write UTF-8 encoded JSON bytes and return the new write position.
 */
final class JsonWriteUtils {

    private JsonWriteUtils() {}

    // Pre-computed byte arrays for JSON literals
    static final byte[] TRUE_BYTES = {'t', 'r', 'u', 'e'};
    static final byte[] FALSE_BYTES = {'f', 'a', 'l', 's', 'e'};
    static final byte[] NULL_BYTES = {'n', 'u', 'l', 'l'};
    static final byte[] NAN_BYTES = {'"', 'N', 'a', 'N', '"'};
    static final byte[] INF_BYTES = {'"', 'I', 'n', 'f', 'i', 'n', 'i', 't', 'y', '"'};
    static final byte[] NEG_INF_BYTES = {'"', '-', 'I', 'n', 'f', 'i', 'n', 'i', 't', 'y', '"'};

    // Pre-computed digit pairs: DIGIT_PAIRS[i*2] and DIGIT_PAIRS[i*2+1] give the two ASCII
    // digits for the number i (00-99).
    private static final byte[] DIGIT_PAIRS = new byte[200];

    static {
        for (int i = 0; i < 100; i++) {
            DIGIT_PAIRS[i * 2] = (byte) ('0' + i / 10);
            DIGIT_PAIRS[i * 2 + 1] = (byte) ('0' + i % 10);
        }
    }

    private static final byte[] HEX = {
            '0',
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            'a',
            'b',
            'c',
            'd',
            'e',
            'f'
    };

    // Pre-computed escape sequences for control characters and special chars.
    // null means "not a simple 2-char escape" (use \\uXXXX instead).
    private static final byte[] ESCAPE_TABLE = new byte[128];
    private static final boolean[] NEEDS_ESCAPE = new boolean[128];

    static {
        for (int i = 0; i < 0x20; i++) {
            NEEDS_ESCAPE[i] = true;
        }
        NEEDS_ESCAPE['"'] = true;
        NEEDS_ESCAPE['\\'] = true;

        ESCAPE_TABLE['"'] = '"';
        ESCAPE_TABLE['\\'] = '\\';
        ESCAPE_TABLE['\b'] = 'b';
        ESCAPE_TABLE['\f'] = 'f';
        ESCAPE_TABLE['\n'] = 'n';
        ESCAPE_TABLE['\r'] = 'r';
        ESCAPE_TABLE['\t'] = 't';
    }

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();

    /**
     * Writes an integer value as JSON number bytes. Returns new position.
     *
     * <p>Handles Integer.MIN_VALUE correctly.
     */
    static int writeInt(byte[] buf, int pos, int value) {
        if (value == 0) {
            buf[pos] = '0';
            return pos + 1;
        }

        if (value < 0) {
            buf[pos++] = '-';
            if (value == Integer.MIN_VALUE) {
                // -2147483648 — can't negate, write directly
                return writePositiveLong(buf, pos, 2147483648L);
            }
            value = -value;
        }

        return writePositiveInt(buf, pos, value);
    }

    private static int writePositiveInt(byte[] buf, int pos, int value) {
        int digits = digitCount(value);
        int end = pos + digits;
        int p = end;

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

    /**
     * Writes a long value as JSON number bytes. Returns new position.
     */
    static int writeLong(byte[] buf, int pos, long value) {
        if (value == 0) {
            buf[pos] = '0';
            return pos + 1;
        }

        if (value < 0) {
            buf[pos++] = '-';
            if (value == Long.MIN_VALUE) {
                // -9223372036854775808 — can't negate
                byte[] minBytes = "9223372036854775808".getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(minBytes, 0, buf, pos, minBytes.length);
                return pos + minBytes.length;
            }
            value = -value;
        }

        return writePositiveLong(buf, pos, value);
    }

    private static int writePositiveLong(byte[] buf, int pos, long value) {
        if (value <= Integer.MAX_VALUE) {
            return writePositiveInt(buf, pos, (int) value);
        }

        int digits = digitCountLong(value);
        int end = pos + digits;
        int p = end;

        while (value >= 100) {
            long q = value / 100;
            int r = (int) (value - q * 100) * 2;
            value = q;
            buf[--p] = DIGIT_PAIRS[r + 1];
            buf[--p] = DIGIT_PAIRS[r];
        }

        if (value >= 10) {
            int r = (int) value * 2;
            buf[--p] = DIGIT_PAIRS[r + 1];
            buf[--p] = DIGIT_PAIRS[r];
        } else {
            buf[--p] = (byte) ('0' + value);
        }

        return end;
    }

    private static int digitCount(int value) {
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

    private static int digitCountLong(long value) {
        if (value < 10000000000L) {
            return digitCount((int) Math.min(value, Integer.MAX_VALUE));
        }
        if (value < 100000000000L)
            return 11;
        if (value < 1000000000000L)
            return 12;
        if (value < 10000000000000L)
            return 13;
        if (value < 100000000000000L)
            return 14;
        if (value < 1000000000000000L)
            return 15;
        if (value < 10000000000000000L)
            return 16;
        if (value < 100000000000000000L)
            return 17;
        if (value < 1000000000000000000L)
            return 18;
        return 19;
    }

    private static final BigInteger TEN_TO_18 = BigInteger.valueOf(1_000_000_000_000_000_000L);

    /**
     * Writes a BigInteger directly to the byte buffer by splitting into 18-digit groups.
     * Avoids BigInteger.toString() which does expensive recursive division and String allocation.
     */
    static int writeBigInteger(byte[] buf, int pos, BigInteger value) {
        if (value.signum() < 0) {
            buf[pos++] = '-';
            value = value.negate();
        }

        // Split into groups of up to 18 decimal digits (each fits in a long)
        if (value.compareTo(TEN_TO_18) < 0) {
            return writePositiveLong(buf, pos, value.longValue());
        }

        BigInteger[] qr = value.divideAndRemainder(TEN_TO_18);
        BigInteger high = qr[0];
        long low = qr[1].longValue();

        if (high.compareTo(TEN_TO_18) < 0) {
            // Two groups: high (no padding) + low (18-digit padded)
            pos = writePositiveLong(buf, pos, high.longValue());
            return writePaddedLong18(buf, pos, low);
        }

        // Three or more groups (handles numbers up to ~54 digits)
        BigInteger[] qr2 = high.divideAndRemainder(TEN_TO_18);
        long mid = qr2[1].longValue();

        if (qr2[0].compareTo(TEN_TO_18) < 0) {
            pos = writePositiveLong(buf, pos, qr2[0].longValue());
            pos = writePaddedLong18(buf, pos, mid);
            return writePaddedLong18(buf, pos, low);
        }

        // Extremely large: fall back to toString for safety
        String s = value.toString();
        return writeAsciiString(buf, pos, s);
    }

    /**
     * Writes a long value zero-padded to exactly 18 digits.
     */
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

    /**
     * Writes a JSON quoted string. Returns new position.
     */
    @SuppressWarnings("deprecation")
    static int writeQuotedString(byte[] buf, int pos, String value) {
        int len = value.length();
        buf[pos++] = '"';

        if (len == 0) {
            buf[pos++] = '"';
            return pos;
        }

        // Single-pass: write safe ASCII chars directly to buf, bail to slow path
        // on the first char needing escaping or multi-byte UTF-8 encoding.
        // The JIT auto-vectorizes this loop on JDK 21.
        //
        // Note: we cannot use String.getBytes(int,int,byte[],int) + SWAR here because
        // that method truncates chars >= 0x100 to their low byte, which can produce
        // valid-looking ASCII bytes (e.g. U+0123 -> 0x23 '#') indistinguishable from
        // real ASCII via any byte-level check.
        int i = 0;
        for (; i < len; i++) {
            char c = value.charAt(i);
            if (c >= 0x80 || c < 0x20 || c == '"' || c == '\\') {
                pos = writeStringSlowPath(buf, pos, value, i, len);
                buf[pos++] = '"';
                return pos;
            }
            buf[pos++] = (byte) c;
        }

        buf[pos++] = '"';
        return pos;
    }

    private static int writeStringSlowPath(byte[] buf, int pos, String value, int startIdx, int len) {
        for (int i = startIdx; i < len; i++) {
            char c = value.charAt(i);

            if (c < 0x80) {
                // ASCII range
                if (c >= 0x20 && !NEEDS_ESCAPE[c]) {
                    buf[pos++] = (byte) c;
                } else if (ESCAPE_TABLE[c] != 0) {
                    // Two-character escape: \n, \t, \\, \", etc.
                    buf[pos++] = '\\';
                    buf[pos++] = ESCAPE_TABLE[c];
                } else {
                    // Unicode escape for control characters
                    pos = writeUnicodeEscape(buf, pos, c);
                }
            } else if (c < 0x800) {
                // 2-byte UTF-8
                buf[pos++] = (byte) (0xC0 | (c >> 6));
                buf[pos++] = (byte) (0x80 | (c & 0x3F));
            } else if (!Character.isSurrogate(c)) {
                // 3-byte UTF-8 (BMP, non-surrogate)
                buf[pos++] = (byte) (0xE0 | (c >> 12));
                buf[pos++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                buf[pos++] = (byte) (0x80 | (c & 0x3F));
            } else {
                // Surrogate pair -> 4-byte UTF-8
                if (Character.isHighSurrogate(c) && i + 1 < len) {
                    char low = value.charAt(++i);
                    if (Character.isLowSurrogate(low)) {
                        int cp = Character.toCodePoint(c, low);
                        buf[pos++] = (byte) (0xF0 | (cp >> 18));
                        buf[pos++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
                        buf[pos++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
                        buf[pos++] = (byte) (0x80 | (cp & 0x3F));
                    } else {
                        // Lone high surrogate followed by non-low — escape both
                        pos = writeUnicodeEscape(buf, pos, c);
                        i--; // re-process the non-low char
                    }
                } else {
                    // Lone surrogate — escape as unicode
                    pos = writeUnicodeEscape(buf, pos, c);
                }
            }
        }
        return pos;
    }

    private static int writeUnicodeEscape(byte[] buf, int pos, int c) {
        buf[pos++] = '\\';
        buf[pos++] = 'u';
        buf[pos++] = HEX[(c >> 12) & 0xF];
        buf[pos++] = HEX[(c >> 8) & 0xF];
        buf[pos++] = HEX[(c >> 4) & 0xF];
        buf[pos++] = HEX[c & 0xF];
        return pos;
    }

    /**
     * Writes a double value as JSON. Handles integer-valued doubles optimization.
     * Returns new position.
     */
    static int writeDouble(byte[] buf, int pos, double value) {
        // Avoid writing 1.0 when 1 suffices
        long longValue = (long) value;
        if (value == (double) longValue) {
            return writeLong(buf, pos, longValue);
        }
        return Schubfach.writeDouble(buf, pos, value);
    }

    /**
     * Writes a double using a reusable Schubfach instance to avoid per-call allocation.
     */
    static int writeDouble(byte[] buf, int pos, double value, Schubfach.DoubleToDecimal dtd) {
        long longValue = (long) value;
        if (value == (double) longValue) {
            return writeLong(buf, pos, longValue);
        }
        return Schubfach.writeDouble(buf, pos, value, dtd);
    }

    /**
     * Writes an epoch-seconds timestamp directly from an Instant using integer arithmetic.
     * Writes "seconds" for whole seconds or "seconds.nanos"
     * for fractional, with full nanosecond precision and trailing zeros stripped.
     *
     * <p>For negative epoch seconds with non-zero nanos, the Instant contract is:
     * {@code Instant.ofEpochSecond(-1, 500_000_000)} = -0.5 seconds (not -1.5).
     * The nano field is always non-negative and added to the epoch second.
     */
    static int writeEpochSeconds(byte[] buf, int pos, long epochSecond, int nano) {
        if (nano == 0) {
            return writeLong(buf, pos, epochSecond);
        }

        int fraction = nano;
        if (epochSecond < 0) {
            // Instant(-1, 500_000_000) means -1 + 0.5 = -0.5 seconds.
            // Adjust: seconds part becomes epochSecond+1, fraction becomes 1e9-nano.
            epochSecond += 1;
            fraction = 1_000_000_000 - nano;
            if (epochSecond == 0) {
                // Special case: -0.xxx (epochSecond was -1, adjusted to 0, but value is negative)
                buf[pos++] = '-';
                buf[pos++] = '0';
            } else {
                pos = writeLong(buf, pos, epochSecond);
            }
        } else {
            pos = writeLong(buf, pos, epochSecond);
        }

        buf[pos++] = '.';

        // Write all 9 fractional digits (zero-padded), then strip trailing zeros.
        int hi = fraction / 1_000_000;
        int mid = (fraction / 1_000) % 1_000;
        int lo = fraction % 1_000;
        buf[pos++] = (byte) ('0' + hi / 100);
        buf[pos++] = (byte) ('0' + (hi / 10) % 10);
        buf[pos++] = (byte) ('0' + hi % 10);
        buf[pos++] = (byte) ('0' + mid / 100);
        buf[pos++] = (byte) ('0' + (mid / 10) % 10);
        buf[pos++] = (byte) ('0' + mid % 10);
        buf[pos++] = (byte) ('0' + lo / 100);
        buf[pos++] = (byte) ('0' + (lo / 10) % 10);
        buf[pos++] = (byte) ('0' + lo % 10);
        // Strip trailing zeros
        while (buf[pos - 1] == '0') {
            pos--;
        }
        return pos;
    }

    /**
     * Writes a float value as JSON. Handles integer-valued floats optimization.
     * Returns new position.
     */
    static int writeFloat(byte[] buf, int pos, float value) {
        int intValue = (int) value;
        if (value == (float) intValue) {
            return writeInt(buf, pos, intValue);
        }
        return Schubfach.writeFloat(buf, pos, value);
    }

    /**
     * Writes a float using a reusable Schubfach instance to avoid per-call allocation.
     */
    static int writeFloat(byte[] buf, int pos, float value, Schubfach.FloatToDecimal ftd) {
        int intValue = (int) value;
        if (value == (float) intValue) {
            return writeInt(buf, pos, intValue);
        }
        return Schubfach.writeFloat(buf, pos, value, ftd);
    }

    /**
     * Writes an ISO-8601 timestamp directly to the byte buffer as a quoted JSON string.
     * Produces output like {@code "2025-01-15T10:30:00Z"} or {@code "2025-01-15T10:30:00.123Z"}
     * for timestamps with sub-second precision.
     *
     * <p>Uses pure integer arithmetic from epoch seconds to compute date/time components,
     * avoiding the 4 object allocations from {@code Instant.atOffset(ZoneOffset.UTC)}.
     */
    static int writeIso8601Timestamp(byte[] buf, int pos, Instant value) {
        long epochSecond = value.getEpochSecond();
        int nano = value.getNano();

        // Compute time-of-day from epoch second
        int secondOfDay = (int) Math.floorMod(epochSecond, 86400);
        int hour = secondOfDay / 3600;
        int minute = (secondOfDay % 3600) / 60;
        int second = secondOfDay % 60;

        // Compute date from epoch day using civil calendar algorithm (inlined to avoid allocation)
        long epochDay = Math.floorDiv(epochSecond, 86400);
        long z = epochDay + 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        int day = (int) (doy - (153 * mp + 2) / 5 + 1);
        int month = (int) (mp < 10 ? mp + 3 : mp - 9);
        int year = (int) (yoe + era * 400 + (month <= 2 ? 1 : 0));

        buf[pos++] = '"';

        // Year (4 digits, with sign for years outside 0000-9999)
        if (year >= 0 && year <= 9999) {
            int hi = year / 100;
            int lo = year - hi * 100;
            buf[pos++] = DIGIT_PAIRS[hi * 2];
            buf[pos++] = DIGIT_PAIRS[hi * 2 + 1];
            buf[pos++] = DIGIT_PAIRS[lo * 2];
            buf[pos++] = DIGIT_PAIRS[lo * 2 + 1];
        } else {
            // Fall back for years outside 0000-9999
            String yearStr = String.format("%04d", year);
            for (int i = 0; i < yearStr.length(); i++) {
                buf[pos++] = (byte) yearStr.charAt(i);
            }
        }

        buf[pos++] = '-';
        buf[pos++] = DIGIT_PAIRS[month * 2];
        buf[pos++] = DIGIT_PAIRS[month * 2 + 1];
        buf[pos++] = '-';
        buf[pos++] = DIGIT_PAIRS[day * 2];
        buf[pos++] = DIGIT_PAIRS[day * 2 + 1];
        buf[pos++] = 'T';
        buf[pos++] = DIGIT_PAIRS[hour * 2];
        buf[pos++] = DIGIT_PAIRS[hour * 2 + 1];
        buf[pos++] = ':';
        buf[pos++] = DIGIT_PAIRS[minute * 2];
        buf[pos++] = DIGIT_PAIRS[minute * 2 + 1];
        buf[pos++] = ':';
        buf[pos++] = DIGIT_PAIRS[second * 2];
        buf[pos++] = DIGIT_PAIRS[second * 2 + 1];

        if (nano != 0) {
            buf[pos++] = '.';
            // Write up to 9 fractional digits, stripping trailing zeros
            int frac = nano;
            int digits = 9;
            while (frac % 10 == 0) {
                frac /= 10;
                digits--;
            }
            // Write digits left-to-right
            int scale = 1;
            for (int i = 1; i < digits; i++) {
                scale *= 10;
            }
            while (scale > 0) {
                buf[pos++] = (byte) ('0' + frac / scale);
                frac %= scale;
                scale /= 10;
            }
        }

        buf[pos++] = 'Z';
        buf[pos++] = '"';
        return pos;
    }

    // Day-of-week names as pre-computed byte arrays (Mon=1..Sun=7 per java.time DayOfWeek)
    private static final byte[][] DAY_NAMES = {
            null, // 0 unused
            {'M', 'o', 'n'}, // 1 = Monday
            {'T', 'u', 'e'}, // 2 = Tuesday
            {'W', 'e', 'd'}, // 3 = Wednesday
            {'T', 'h', 'u'}, // 4 = Thursday
            {'F', 'r', 'i'}, // 5 = Friday
            {'S', 'a', 't'}, // 6 = Saturday
            {'S', 'u', 'n'}, // 7 = Sunday
    };

    // Month abbreviations as pre-computed byte arrays (Jan=1..Dec=12)
    private static final byte[][] MONTH_NAMES = {
            null, // 0 unused
            {'J', 'a', 'n'},
            {'F', 'e', 'b'},
            {'M', 'a', 'r'},
            {'A', 'p', 'r'},
            {'M', 'a', 'y'},
            {'J', 'u', 'n'},
            {'J', 'u', 'l'},
            {'A', 'u', 'g'},
            {'S', 'e', 'p'},
            {'O', 'c', 't'},
            {'N', 'o', 'v'},
            {'D', 'e', 'c'},
    };

    /**
     * Writes an HTTP-date timestamp directly to the byte buffer as a quoted JSON string.
     * Produces output like {@code "Sat, 01 Jan 2026 00:00:00 GMT"}.
     *
     * <p>Uses pure integer arithmetic from epoch seconds, avoiding the 4 object allocations
     * from {@code Instant.atOffset(ZoneOffset.UTC)} and the heavy DateTimeFormatter machinery.
     */
    static int writeHttpDate(byte[] buf, int pos, Instant value) {
        long epochSecond = value.getEpochSecond();

        // Compute time-of-day
        int secondOfDay = (int) Math.floorMod(epochSecond, 86400);
        int hour = secondOfDay / 3600;
        int minute = (secondOfDay % 3600) / 60;
        int second = secondOfDay % 60;

        // Compute date from epoch day using civil calendar algorithm (inlined to avoid allocation)
        long epochDay = Math.floorDiv(epochSecond, 86400);
        long z = epochDay + 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        int day = (int) (doy - (153 * mp + 2) / 5 + 1);
        int month = (int) (mp < 10 ? mp + 3 : mp - 9);
        int year = (int) (yoe + era * 400 + (month <= 2 ? 1 : 0));

        // Day of week: epoch day 0 (1970-01-01) was Thursday (4).
        // 1=Monday..7=Sunday per java.time convention.
        int dow = (int) Math.floorMod(epochDay + 3, 7) + 1;

        buf[pos++] = '"';

        // Day name
        byte[] dayName = DAY_NAMES[dow];
        buf[pos++] = dayName[0];
        buf[pos++] = dayName[1];
        buf[pos++] = dayName[2];
        buf[pos++] = ',';
        buf[pos++] = ' ';

        // Day of month (2 digits)
        buf[pos++] = DIGIT_PAIRS[day * 2];
        buf[pos++] = DIGIT_PAIRS[day * 2 + 1];
        buf[pos++] = ' ';

        // Month name
        byte[] monthName = MONTH_NAMES[month];
        buf[pos++] = monthName[0];
        buf[pos++] = monthName[1];
        buf[pos++] = monthName[2];
        buf[pos++] = ' ';

        // Year (4 digits)
        if (year >= 0 && year <= 9999) {
            int hi = year / 100;
            int lo = year - hi * 100;
            buf[pos++] = DIGIT_PAIRS[hi * 2];
            buf[pos++] = DIGIT_PAIRS[hi * 2 + 1];
            buf[pos++] = DIGIT_PAIRS[lo * 2];
            buf[pos++] = DIGIT_PAIRS[lo * 2 + 1];
        } else {
            String yearStr = String.format("%04d", year);
            for (int i = 0; i < yearStr.length(); i++) {
                buf[pos++] = (byte) yearStr.charAt(i);
            }
        }

        buf[pos++] = ' ';
        buf[pos++] = DIGIT_PAIRS[hour * 2];
        buf[pos++] = DIGIT_PAIRS[hour * 2 + 1];
        buf[pos++] = ':';
        buf[pos++] = DIGIT_PAIRS[minute * 2];
        buf[pos++] = DIGIT_PAIRS[minute * 2 + 1];
        buf[pos++] = ':';
        buf[pos++] = DIGIT_PAIRS[second * 2];
        buf[pos++] = DIGIT_PAIRS[second * 2 + 1];
        buf[pos++] = ' ';
        buf[pos++] = 'G';
        buf[pos++] = 'M';
        buf[pos++] = 'T';
        buf[pos++] = '"';
        return pos;
    }

    // Pre-computed powers of 10 for BigDecimal fast path
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
     * Writes a BigDecimal with known unscaled long value and positive scale directly.
     * E.g., unscaled=9999999999, scale=5 writes "99999.99999".
     */
    static int writeBigDecimalFromLong(byte[] buf, int pos, long unscaled, int scale) {
        if (unscaled < 0) {
            buf[pos++] = '-';
            if (unscaled == Long.MIN_VALUE) {
                // Extremely unlikely edge case — fall through won't work, but caller
                // checks bitLength < 64 so this can't happen
                throw new ArithmeticException("Cannot negate Long.MIN_VALUE");
            }
            unscaled = -unscaled;
        }

        if (scale < POWERS_OF_10.length) {
            long divisor = POWERS_OF_10[scale];
            long intPart = unscaled / divisor;
            long fracPart = unscaled - intPart * divisor;

            // Write integer part (or 0 if unscaled < divisor)
            pos = writePositiveLong(buf, pos, intPart);
            buf[pos++] = '.';

            // Write fractional part with leading zeros
            // e.g., scale=5 and fracPart=99 -> "00099"
            for (int i = scale - 1; i >= 0; i--) {
                long p10 = POWERS_OF_10[i];
                int d = (int) (fracPart / p10);
                buf[pos++] = (byte) ('0' + d);
                fracPart -= d * p10;
            }
        } else {
            // Scale too large for our table — shouldn't happen for practical values
            pos = writePositiveLong(buf, pos, unscaled);
        }

        return pos;
    }

    /**
     * Writes an ASCII string directly to the buffer without quoting.
     * Used for number-to-string conversions (Double.toString, BigDecimal.toString, etc).
     */
    @SuppressWarnings("deprecation")
    static int writeAsciiString(byte[] buf, int pos, String s) {
        int len = s.length();
        s.getBytes(0, len, buf, pos);
        return pos + len;
    }

    /**
     * Base64-encodes the given data and writes it as a JSON quoted string.
     * Returns the new write position.
     */
    static int writeBase64String(byte[] buf, int pos, byte[] data, int off, int len) {
        buf[pos++] = '"';
        // Use JDK Base64 encoder — produces standard base64 with +/ alphabet, no line breaks.
        // This matches Jackson's MIME_NO_LINEFEEDS variant for JSON.
        byte[] encoded = BASE64_ENCODER.encode(
                off == 0 && len == data.length ? data : Arrays.copyOfRange(data, off, off + len));
        System.arraycopy(encoded, 0, buf, pos, encoded.length);
        pos += encoded.length;
        buf[pos++] = '"';
        return pos;
    }

    /**
     * Returns the maximum number of bytes needed to write a JSON-quoted string.
     * Used for buffer capacity estimation.
     */
    static int maxQuotedStringBytes(String value) {
        // Worst case: every char is a control char needing unicode escape (6 bytes) + 2 quotes
        return value.length() * 6 + 2;
    }

    /**
     * Returns the maximum number of bytes needed for a base64-encoded string.
     */
    static int maxBase64Bytes(int dataLen) {
        // Base64: 4 bytes per 3 input bytes, rounded up, plus 2 quotes
        return ((dataLen + 2) / 3) * 4 + 2;
    }

    /**
     * Pre-computes the UTF-8 byte representation of a JSON field name prefix.
     * The result includes the opening quote, the field name, the closing quote, and the colon.
     * Example: for field name "foo", returns bytes for {@code "foo":}
     */
    static byte[] precomputeFieldNameBytes(String fieldName) {
        byte[] nameUtf8 = fieldName.getBytes(StandardCharsets.UTF_8);
        // "fieldName":
        byte[] result = new byte[nameUtf8.length + 3]; // quote + name + quote + colon
        result[0] = '"';
        System.arraycopy(nameUtf8, 0, result, 1, nameUtf8.length);
        result[nameUtf8.length + 1] = '"';
        result[nameUtf8.length + 2] = ':';
        return result;
    }
}
