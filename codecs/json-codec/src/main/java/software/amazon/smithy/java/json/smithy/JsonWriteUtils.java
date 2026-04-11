/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
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

    // Hex digits for unicode escapes
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
        // All control characters need escaping
        for (int i = 0; i < 0x20; i++) {
            NEEDS_ESCAPE[i] = true;
        }
        NEEDS_ESCAPE['"'] = true;
        NEEDS_ESCAPE['\\'] = true;

        // Two-character escape sequences
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
        // Determine digit count to write left-to-right
        int digits = digitCount(value);
        int end = pos + digits;
        int p = end;

        // Process two digits at a time from least significant
        while (value >= 100) {
            int q = value / 100;
            int r = (value - q * 100) * 2;
            value = q;
            buf[--p] = DIGIT_PAIRS[r + 1];
            buf[--p] = DIGIT_PAIRS[r];
        }

        // Handle remaining 1-2 digits
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
        // For values that fit in int, use the int path
        if (value <= Integer.MAX_VALUE) {
            return writePositiveInt(buf, pos, (int) value);
        }

        int digits = digitCountLong(value);
        int end = pos + digits;
        int p = end;

        // Process two digits at a time
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
        // Fast digit count for positive integers
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

    /**
     * Writes a JSON quoted string. Single-pass for safe ASCII strings.
     *
     * <p>Strategy: copy bytes first via the fast {@code String.getBytes(int,int,byte[],int)}
     * (single arraycopy for LATIN1 compact strings), then SWAR-scan the copied bytes for
     * characters needing escaping or multi-byte UTF-8 encoding. If none found, done in one pass.
     * If special chars found, rewrite from that position using the slow path.
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
                // Surrogate pair → 4-byte UTF-8
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
        // Avoid writing 1.0 when 1 suffices — match Jackson behavior
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
     * Avoids the Instant → double → Double.toString → bytes round-trip that accounts for
     * ~21% of simple-serialize time. Writes "seconds" for whole seconds or "seconds.millis"
     * for fractional, stripping trailing zeros.
     */
    static int writeEpochSeconds(byte[] buf, int pos, long epochSecond, int nano) {
        if (nano == 0) {
            return writeLong(buf, pos, epochSecond);
        }
        // Write seconds part
        pos = writeLong(buf, pos, epochSecond);
        buf[pos++] = '.';
        // Convert nano to millis (epoch-seconds uses up to 3 decimal places)
        int millis = nano / 1_000_000;
        // Strip trailing zeros
        if (millis % 100 == 0) {
            buf[pos++] = (byte) ('0' + millis / 100);
        } else if (millis % 10 == 0) {
            buf[pos++] = (byte) ('0' + millis / 100);
            buf[pos++] = (byte) ('0' + (millis / 10) % 10);
        } else {
            buf[pos++] = (byte) ('0' + millis / 100);
            buf[pos++] = (byte) ('0' + (millis / 10) % 10);
            buf[pos++] = (byte) ('0' + millis % 10);
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
     * <p>Bypasses {@link Instant#toString()} and {@link java.time.format.DateTimeFormatter}
     * to avoid String allocation on the hot path.
     */
    static int writeIso8601Timestamp(byte[] buf, int pos, Instant value) {
        var dt = value.atOffset(ZoneOffset.UTC);
        int year = dt.getYear();
        int month = dt.getMonthValue();
        int day = dt.getDayOfMonth();
        int hour = dt.getHour();
        int minute = dt.getMinute();
        int second = dt.getSecond();
        int nano = dt.getNano();

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
                off == 0 && len == data.length ? data : java.util.Arrays.copyOfRange(data, off, off + len));
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
