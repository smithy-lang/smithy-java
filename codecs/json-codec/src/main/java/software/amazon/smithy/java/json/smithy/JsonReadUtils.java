/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import ch.randelshofer.fastdoubleparser.JavaDoubleParser;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import software.amazon.smithy.java.core.serde.SerializationException;

/**
 * Low-level utilities for parsing JSON primitives directly from byte arrays.
 *
 * <p>All methods implement strict RFC 8259 compliance: no leading zeros on numbers,
 * no unescaped control characters in strings, full UTF-8 validation.
 */
final class JsonReadUtils {

    private JsonReadUtils() {}

    // VarHandle for reading 8 bytes at a time from byte arrays (SWAR technique)
    private static final VarHandle LONG_HANDLE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    // Hex digit lookup table: -1 means invalid hex digit
    private static final int[] HEX_VALUES = new int[128];

    static {
        Arrays.fill(HEX_VALUES, -1);
        for (int i = '0'; i <= '9'; i++) {
            HEX_VALUES[i] = i - '0';
        }
        for (int i = 'a'; i <= 'f'; i++) {
            HEX_VALUES[i] = 10 + (i - 'a');
        }
        for (int i = 'A'; i <= 'F'; i++) {
            HEX_VALUES[i] = 10 + (i - 'A');
        }
    }

    /**
     * Parses a JSON integer value starting at pos. Strict RFC 8259: no leading zeros, no + prefix.
     * Stores result in deser.parsedLong and deser.parsedEndPos (avoids array allocation).
     * Accumulates as negative to correctly handle Long.MIN_VALUE.
     */
    static void parseLong(byte[] buf, int pos, int end, SmithyJsonDeserializer deser) {
        if (pos >= end) {
            throw new SerializationException("Unexpected end of input while parsing number");
        }

        boolean negative = false;
        if (buf[pos] == '-') {
            negative = true;
            pos++;
            if (pos >= end) {
                throw new SerializationException("Unexpected end of input after '-'");
            }
        }

        byte first = buf[pos];
        if (first < '0' || first > '9') {
            throw new SerializationException("Expected digit, found: " + describeChar(first));
        }

        // RFC 8259: no leading zeros (except 0 itself)
        if (first == '0' && pos + 1 < end && buf[pos + 1] >= '0' && buf[pos + 1] <= '9') {
            throw new SerializationException("Leading zeros not allowed in JSON numbers");
        }

        // Accumulate as negative to handle Long.MIN_VALUE correctly
        // (Long.MIN_VALUE has no positive counterpart)
        long value = -(first - '0');
        pos++;

        while (pos < end) {
            byte b = buf[pos];
            if (b < '0' || b > '9') {
                break;
            }
            long prev = value;
            value = value * 10 - (b - '0');
            if (value > prev) {
                // Overflowed past Long.MIN_VALUE
                throw new SerializationException("Number overflow");
            }
            pos++;
        }

        deser.parsedLong = negative ? value : -value;
        deser.parsedEndPos = pos;

        // Check for positive overflow (e.g., 9223372036854775808 without minus)
        if (!negative && deser.parsedLong < 0) {
            throw new SerializationException("Number overflow");
        }
    }

    /**
     * Parses a JSON number (integer or floating point) with strict RFC 8259 validation.
     * Stores result in deser.parsedDouble and deser.parsedEndPos.
     */
    static void parseDouble(byte[] buf, int pos, int end, SmithyJsonDeserializer deser) {
        int start = pos;

        // Optional minus sign
        if (pos < end && buf[pos] == '-') {
            pos++;
        }

        if (pos >= end) {
            throw new SerializationException("Unexpected end of input while parsing number");
        }

        // Integer part — no leading zeros
        byte first = buf[pos];
        if (first < '0' || first > '9') {
            throw new SerializationException("Expected digit, found: " + describeChar(first));
        }
        if (first == '0') {
            pos++;
            // After leading 0, only allowed: '.', 'e', 'E', or end of number
            if (pos < end && buf[pos] >= '0' && buf[pos] <= '9') {
                throw new SerializationException("Leading zeros not allowed in JSON numbers");
            }
        } else {
            while (pos < end && buf[pos] >= '0' && buf[pos] <= '9') {
                pos++;
            }
        }

        // Optional fractional part
        if (pos < end && buf[pos] == '.') {
            pos++;
            if (pos >= end || buf[pos] < '0' || buf[pos] > '9') {
                throw new SerializationException("Expected digit after decimal point");
            }
            while (pos < end && buf[pos] >= '0' && buf[pos] <= '9') {
                pos++;
            }
        }

        // Optional exponent
        if (pos < end && (buf[pos] == 'e' || buf[pos] == 'E')) {
            pos++;
            if (pos < end && (buf[pos] == '+' || buf[pos] == '-')) {
                pos++;
            }
            if (pos >= end || buf[pos] < '0' || buf[pos] > '9') {
                throw new SerializationException("Expected digit in exponent");
            }
            while (pos < end && buf[pos] >= '0' && buf[pos] <= '9') {
                pos++;
            }
        }

        // Parse directly from byte array — no String allocation.
        deser.parsedDouble = JavaDoubleParser.parseDouble(buf, start, pos - start);
        deser.parsedEndPos = pos;
    }

    /**
     * Finds the end position of a JSON number starting at pos.
     * Returns the position of the first non-number character.
     */
    static int findNumberEnd(byte[] buf, int pos, int end) {
        while (pos < end) {
            byte b = buf[pos];
            if ((b >= '0' && b <= '9') || b == '-' || b == '+' || b == '.' || b == 'e' || b == 'E') {
                pos++;
            } else {
                break;
            }
        }
        return pos;
    }

    /**
     * Parses a JSON string starting at the current position (which should be at the opening quote).
     * Stores result in deser.parsedString and deser.parsedEndPos (avoids Object[] allocation).
     * Strict: rejects unescaped control characters, validates UTF-8, validates escape sequences.
     */
    static void parseString(byte[] buf, int pos, int end, SmithyJsonDeserializer deser) {
        if (pos >= end || buf[pos] != '"') {
            throw new SerializationException("Expected '\"', found: " + describePos(buf, pos, end));
        }
        pos++; // skip opening quote

        // Fast path: SWAR scan 8 bytes at a time for closing quote, backslash, or control chars.
        int start = pos;

        while (pos + 8 <= end) {
            long word = (long) LONG_HANDLE.get(buf, pos);
            if (hasSpecialStringByte(word)) {
                break; // found something, fall through to scalar loop
            }
            pos += 8;
        }

        // Scalar loop for remaining bytes and to find the exact special byte
        while (pos < end) {
            byte b = buf[pos];
            if (b == '"') {
                // No escapes found — fast path
                deser.parsedString = new String(buf, start, pos - start, StandardCharsets.UTF_8);
                deser.parsedEndPos = pos + 1;
                return;
            }
            if (b == '\\') {
                // Has escapes — slow path
                parseStringWithEscapes(buf, start, pos, end, deser);
                return;
            }
            if ((b & 0xFF) < 0x20) {
                throw new SerializationException(
                        "Unescaped control character 0x" + Integer.toHexString(b & 0xFF) + " in string");
            }
            pos++;
        }

        throw new SerializationException("Unterminated string");
    }

    /**
     * SWAR check: returns true if any byte in the 8-byte word is '"' (0x22), '\\' (0x5C),
     * or a control character (< 0x20).
     */
    private static boolean hasSpecialStringByte(long word) {
        // Check for control chars (< 0x20): a byte b < 0x20 means (b - 0x20) sets the high bit
        // when the original high bit was 0. We use the standard "has byte less than" SWAR trick.
        long controlCheck = (word - 0x2020202020202020L) & ~word & 0x8080808080808080L;

        // Check for '"' (0x22) using XOR + has-zero-byte trick
        long xorQuote = word ^ 0x2222222222222222L;
        long hasQuote = (xorQuote - 0x0101010101010101L) & ~xorQuote & 0x8080808080808080L;

        // Check for '\\' (0x5C)
        long xorBackslash = word ^ 0x5C5C5C5C5C5C5C5CL;
        long hasBackslash = (xorBackslash - 0x0101010101010101L) & ~xorBackslash & 0x8080808080808080L;

        return (controlCheck | hasQuote | hasBackslash) != 0;
    }

    private static void parseStringWithEscapes(
            byte[] buf,
            int start,
            int escapePos,
            int end,
            SmithyJsonDeserializer deser
    ) {
        // Build a StringBuilder from what we've read so far + escaped content
        StringBuilder sb = new StringBuilder(escapePos - start + 16);
        // Append everything before the first escape as UTF-8
        sb.append(new String(buf, start, escapePos - start, StandardCharsets.UTF_8));

        int pos = escapePos;
        while (pos < end) {
            byte b = buf[pos];
            if (b == '"') {
                deser.parsedString = sb.toString();
                deser.parsedEndPos = pos + 1;
                return;
            }

            if ((b & 0xFF) < 0x20) {
                throw new SerializationException(
                        "Unescaped control character 0x" + Integer.toHexString(b & 0xFF) + " in string");
            }

            if (b == '\\') {
                pos++;
                if (pos >= end) {
                    throw new SerializationException("Unterminated escape sequence");
                }
                byte escaped = buf[pos++];
                switch (escaped) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > end) {
                            throw new SerializationException("Incomplete \\uXXXX escape");
                        }
                        char c = parseHex4(buf, pos);
                        pos += 4;
                        if (Character.isHighSurrogate(c)) {
                            // Expect low surrogate escape
                            if (pos + 6 > end || buf[pos] != '\\' || buf[pos + 1] != 'u') {
                                throw new SerializationException("Expected low surrogate after high surrogate");
                            }
                            pos += 2; // skip backslash-u
                            char low = parseHex4(buf, pos);
                            pos += 4;
                            if (!Character.isLowSurrogate(low)) {
                                throw new SerializationException("Expected low surrogate, got \\u"
                                        + Integer.toHexString(low));
                            }
                            sb.append(c);
                            sb.append(low);
                        } else if (Character.isLowSurrogate(c)) {
                            throw new SerializationException(
                                    "Unexpected low surrogate without preceding high surrogate");
                        } else {
                            sb.append(c);
                        }
                    }
                    default -> throw new SerializationException(
                            "Invalid escape character: \\" + (char) escaped);
                }
            } else {
                // Regular UTF-8 byte — decode
                if ((b & 0x80) == 0) {
                    sb.append((char) b);
                    pos++;
                } else {
                    int[] result = decodeUtf8Char(buf, pos, end);
                    sb.appendCodePoint(result[0]);
                    pos = result[1];
                }
            }
        }
        throw new SerializationException("Unterminated string");
    }

    private static char parseHex4(byte[] buf, int pos) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            byte b = buf[pos + i];
            if (b < 0 || b >= HEX_VALUES.length || HEX_VALUES[b] == -1) {
                throw new SerializationException(
                        "Invalid hex digit in \\u escape: " + (char) (b & 0xFF));
            }
            value = (value << 4) | HEX_VALUES[b];
        }
        return (char) value;
    }

    /**
     * Decodes a single UTF-8 character starting at pos.
     * Returns [codePoint, newPos].
     * Validates continuation bytes (must be 10xxxxxx per RFC 3629) and rejects
     * surrogate code points (U+D800..U+DFFF) which are forbidden in UTF-8.
     */
    private static int[] decodeUtf8Char(byte[] buf, int pos, int end) {
        byte b = buf[pos];
        if ((b & 0x80) == 0) {
            return new int[] {b, pos + 1};
        } else if ((b & 0xE0) == 0xC0) {
            // 2-byte
            if (pos + 2 > end) {
                throw new SerializationException("Truncated UTF-8 sequence");
            }
            validateContinuationByte(buf[pos + 1]);
            int cp = ((b & 0x1F) << 6) | (buf[pos + 1] & 0x3F);
            if (cp < 0x80) {
                throw new SerializationException("Overlong UTF-8 sequence");
            }
            return new int[] {cp, pos + 2};
        } else if ((b & 0xF0) == 0xE0) {
            // 3-byte
            if (pos + 3 > end) {
                throw new SerializationException("Truncated UTF-8 sequence");
            }
            validateContinuationByte(buf[pos + 1]);
            validateContinuationByte(buf[pos + 2]);
            int cp = ((b & 0x0F) << 12) | ((buf[pos + 1] & 0x3F) << 6) | (buf[pos + 2] & 0x3F);
            if (cp < 0x800) {
                throw new SerializationException("Overlong UTF-8 sequence");
            }
            if (cp >= 0xD800 && cp <= 0xDFFF) {
                throw new SerializationException(
                        "UTF-8 encoded surrogate code point: U+" + Integer.toHexString(cp));
            }
            return new int[] {cp, pos + 3};
        } else if ((b & 0xF8) == 0xF0) {
            // 4-byte
            if (pos + 4 > end) {
                throw new SerializationException("Truncated UTF-8 sequence");
            }
            validateContinuationByte(buf[pos + 1]);
            validateContinuationByte(buf[pos + 2]);
            validateContinuationByte(buf[pos + 3]);
            int cp = ((b & 0x07) << 18) | ((buf[pos + 1] & 0x3F) << 12)
                    | ((buf[pos + 2] & 0x3F) << 6)
                    | (buf[pos + 3] & 0x3F);
            if (cp < 0x10000 || cp > 0x10FFFF) {
                throw new SerializationException("Invalid UTF-8 code point: " + cp);
            }
            return new int[] {cp, pos + 4};
        } else {
            throw new SerializationException("Invalid UTF-8 start byte: 0x" + Integer.toHexString(b & 0xFF));
        }
    }

    private static void validateContinuationByte(byte b) {
        if ((b & 0xC0) != 0x80) {
            throw new SerializationException(
                    "Invalid UTF-8 continuation byte: 0x" + Integer.toHexString(b & 0xFF));
        }
    }

    /**
     * Skips JSON whitespace (space 0x20, tab 0x09, LF 0x0A, CR 0x0D) and returns the new position.
     * The fast check at the top covers the common case (next byte is not whitespace).
     */
    static int skipWhitespace(byte[] buf, int pos, int end) {
        // Fast check: most common case is next byte is not whitespace
        if (pos < end && buf[pos] > ' ') {
            return pos;
        }
        // Scalar loop for remaining bytes
        while (pos < end) {
            byte b = buf[pos];
            if (b != ' ' && b != '\n' && b != '\r' && b != '\t') {
                return pos;
            }
            pos++;
        }
        return pos;
    }

    // Month lookup: index by first two bytes of 3-letter month abbreviation
    // Jan=1, Feb=2, ..., Dec=12. Used by parseHttpDate.
    private static final int[] MONTH_LOOKUP = new int[128 * 128];

    static {
        // Populate month lookup: key = first_char * 128 + second_char
        MONTH_LOOKUP['J' * 128 + 'a'] = 1; // Jan
        MONTH_LOOKUP['F' * 128 + 'e'] = 2; // Feb
        MONTH_LOOKUP['M' * 128 + 'a'] = 3; // Mar (also May — disambiguate with 3rd char)
        MONTH_LOOKUP['A' * 128 + 'p'] = 4; // Apr
        MONTH_LOOKUP['J' * 128 + 'u'] = 6; // Jun (also Jul — disambiguate with 3rd char)
        MONTH_LOOKUP['A' * 128 + 'u'] = 8; // Aug
        MONTH_LOOKUP['S' * 128 + 'e'] = 9; // Sep
        MONTH_LOOKUP['O' * 128 + 'c'] = 10; // Oct
        MONTH_LOOKUP['N' * 128 + 'o'] = 11; // Nov
        MONTH_LOOKUP['D' * 128 + 'e'] = 12; // Dec
    }

    // Full 3-letter month name validation table: third character for each month
    private static final byte[] MONTH_THIRD_CHAR = {
            0,
            'n',
            'b',
            'r',
            'r',
            'y',
            'n',
            'l',
            'g',
            'p',
            't',
            'v',
            'c'
    };
    //  Jan  Feb  Mar  Apr  May  Jun  Jul  Aug  Sep  Oct  Nov  Dec

    /**
     * Looks up month number (1-12) from a 3-letter abbreviation at buf[pos..pos+3).
     * Validates all three characters: uses a two-char lookup table for the first two,
     * then disambiguates Mar/May and Jun/Jul with the third character, and validates
     * the third character for all other months.
     */
    private static int lookupMonth(byte[] buf, int pos) {
        int key = (buf[pos] & 0x7F) * 128 + (buf[pos + 1] & 0x7F);
        int month = key < MONTH_LOOKUP.length ? MONTH_LOOKUP[key] : 0;
        if (month == 3) {
            // Mar or May — disambiguate on third char
            if (buf[pos + 2] == 'y') {
                return 5; // May
            }
            if (buf[pos + 2] != 'r') {
                throw new SerializationException("Invalid month: "
                        + (char) buf[pos] + (char) buf[pos + 1] + (char) buf[pos + 2]);
            }
            return 3; // Mar
        }
        if (month == 6) {
            // Jun or Jul — disambiguate on third char
            if (buf[pos + 2] == 'l') {
                return 7; // Jul
            }
            if (buf[pos + 2] != 'n') {
                throw new SerializationException("Invalid month: "
                        + (char) buf[pos] + (char) buf[pos + 1] + (char) buf[pos + 2]);
            }
            return 6; // Jun
        }
        if (month == 0 || buf[pos + 2] != MONTH_THIRD_CHAR[month]) {
            throw new SerializationException("Invalid month: "
                    + (char) buf[pos] + (char) buf[pos + 1] + (char) buf[pos + 2]);
        }
        return month;
    }

    /**
     * Computes epoch day (days since 1970-01-01) from year/month/day using the
     * proleptic Gregorian calendar algorithm. Pure integer arithmetic — no java.time overhead.
     */
    private static long computeEpochDay(int year, int month, int day) {
        long y = year;
        long m = month;
        // Shift March=0..Feb=11 so Feb (the leap-day month) is at the end
        if (m <= 2) {
            y--;
            m += 9;
        } else {
            m -= 3;
        }
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * m + 2) / 5 + day - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    private static int digit(byte b) {
        int d = b - '0';
        if (d < 0 || d > 9) {
            throw new SerializationException("Expected digit, found: " + describeChar(b));
        }
        return d;
    }

    /**
     * Parses an ISO-8601 timestamp directly from a JSON quoted string in the byte buffer.
     * Expects pos to be at the opening quote. Avoids String allocation and DateTimeFormatter.
     *
     * <p>On success, stores result in deser.parsedEndPos (position after closing quote)
     * and returns the Instant. On failure (non-standard format), returns null and
     * deser state is unchanged caller should fall back to DateTimeFormatter.
     */
    static Instant parseIso8601(byte[] buf, int pos, int end, SmithyJsonDeserializer deser) {
        // Minimum: "YYYY-MM-DDThh:mm:ssZ" = 22 bytes (including quotes)
        if (pos >= end || buf[pos] != '"' || pos + 22 > end) {
            return null;
        }
        pos++; // skip opening quote

        // Parse YYYY-MM-DD
        int year = digit(buf[pos]) * 1000 + digit(buf[pos + 1]) * 100
                + digit(buf[pos + 2]) * 10
                + digit(buf[pos + 3]);
        pos += 4;
        if (buf[pos++] != '-') {
            return null;
        }
        int month = digit(buf[pos]) * 10 + digit(buf[pos + 1]);
        pos += 2;
        if (month < 1 || month > 12) {
            throw new SerializationException("Invalid ISO-8601 month: " + month);
        }
        if (buf[pos++] != '-') {
            return null;
        }
        int day = digit(buf[pos]) * 10 + digit(buf[pos + 1]);
        pos += 2;
        if (day < 1 || day > 31) {
            throw new SerializationException("Invalid ISO-8601 day: " + day);
        }

        if (buf[pos] != 'T' && buf[pos] != 't') {
            return null;
        }
        pos++;

        // Parse hh:mm:ss
        int hour = digit(buf[pos]) * 10 + digit(buf[pos + 1]);
        pos += 2;
        if (buf[pos++] != ':') {
            return null;
        }
        int minute = digit(buf[pos]) * 10 + digit(buf[pos + 1]);
        pos += 2;
        if (buf[pos++] != ':') {
            return null;
        }
        int second = digit(buf[pos]) * 10 + digit(buf[pos + 1]);
        pos += 2;
        if (hour > 23 || minute > 59 || second > 59) {
            throw new SerializationException(
                    "Invalid ISO-8601 time: " + hour + ":" + minute + ":" + second);
        }

        // Optional fractional seconds
        int nano = 0;
        if (pos < end && buf[pos] == '.') {
            pos++;
            int fracStart = pos;
            while (pos < end && buf[pos] >= '0' && buf[pos] <= '9') {
                pos++;
            }
            int fracLen = pos - fracStart;
            if (fracLen == 0) {
                return null;
            }
            // Parse up to 9 fractional digits, zero-padding on the right
            for (int i = 0; i < 9; i++) {
                nano *= 10;
                if (i < fracLen) {
                    nano += buf[fracStart + i] - '0';
                }
            }
        }

        // Must end with 'Z' for UTC fast path
        if (pos >= end || buf[pos] != 'Z') {
            return null; // timezone offset — fall back to DateTimeFormatter
        }
        pos++; // skip Z

        // Expect closing quote
        if (pos >= end || buf[pos] != '"') {
            return null;
        }
        pos++; // skip closing quote

        deser.parsedEndPos = pos;
        long epochDay = computeEpochDay(year, month, day);
        long epochSecond = epochDay * 86400 + hour * 3600 + minute * 60 + second;
        return Instant.ofEpochSecond(epochSecond, nano);
    }

    /**
     * Parses an HTTP-date ("EEE, dd MMM yyyy HH:mm:ss GMT") directly from a JSON
     * quoted string. Expects pos to be at the opening quote.
     *
     * <p>On success, stores deser.parsedEndPos (after closing quote) and returns Instant.
     * On failure, returns null — caller should fall back to DateTimeFormatter.
     */
    static Instant parseHttpDate(byte[] buf, int pos, int end, SmithyJsonDeserializer deser) {
        // Minimum: "Thu, 01 Jan 2026 00:00:00 GMT" = 31 bytes (including quotes)
        if (pos >= end || buf[pos] != '"' || pos + 31 > end) {
            return null;
        }
        pos++; // skip opening quote

        // Skip day name — find comma
        while (pos < end && buf[pos] != ',') {
            pos++;
        }
        if (pos >= end) {
            return null;
        }
        pos++; // skip comma
        if (pos >= end || buf[pos] != ' ') {
            return null;
        }
        pos++; // skip space

        // Parse dd
        int day = digit(buf[pos]) * 10 + digit(buf[pos + 1]);
        pos += 2;
        if (buf[pos++] != ' ') {
            return null;
        }

        // Parse MMM
        int month = lookupMonth(buf, pos);
        pos += 3;
        if (buf[pos++] != ' ') {
            return null;
        }

        // Parse yyyy
        int year = digit(buf[pos]) * 1000 + digit(buf[pos + 1]) * 100
                + digit(buf[pos + 2]) * 10
                + digit(buf[pos + 3]);
        pos += 4;
        if (buf[pos++] != ' ') {
            return null;
        }

        // Parse HH:mm:ss
        int hour = digit(buf[pos]) * 10 + digit(buf[pos + 1]);
        pos += 2;
        if (buf[pos++] != ':') {
            return null;
        }
        int minute = digit(buf[pos]) * 10 + digit(buf[pos + 1]);
        pos += 2;
        if (buf[pos++] != ':') {
            return null;
        }
        int second = digit(buf[pos]) * 10 + digit(buf[pos + 1]);
        pos += 2;

        // Expect " GMT"
        if (pos + 4 > end || buf[pos] != ' '
                || buf[pos + 1] != 'G'
                || buf[pos + 2] != 'M'
                || buf[pos + 3] != 'T') {
            return null;
        }
        pos += 4; // skip " GMT"

        // Expect closing quote
        if (pos >= end || buf[pos] != '"') {
            return null;
        }
        pos++; // skip closing quote

        deser.parsedEndPos = pos;
        long epochDay = computeEpochDay(year, month, day);
        long epochSecond = epochDay * 86400 + hour * 3600 + minute * 60 + second;
        return Instant.ofEpochSecond(epochSecond);
    }

    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    /**
     * Decodes a base64-encoded JSON string from the byte buffer, bypassing String allocation.
     * Scans for the closing quote to find the base64 content boundaries, then uses the JDK
     * Base64 decoder which is backed by @IntrinsicCandidate SIMD on HotSpot.
     *
     * <p>Expects {@code pos} at the opening quote. Stores the position after the closing
     * quote in {@code deser.parsedEndPos}.
     *
     * @return the decoded bytes
     * @throws SerializationException on unterminated string or invalid base64
     */
    static byte[] decodeBase64String(byte[] buf, int pos, int end, SmithyJsonDeserializer deser) {
        if (pos >= end || buf[pos] != '"') {
            throw new SerializationException("Expected '\"', found: " + describePos(buf, pos, end));
        }
        pos++; // skip opening quote

        // Find closing quote using SWAR (8 bytes at a time).
        // Base64 chars never include '"', so a simple quote scan suffices.
        int contentStart = pos;
        while (pos + 8 <= end) {
            long word = (long) LONG_HANDLE.get(buf, pos);
            long xorQuote = word ^ 0x2222222222222222L;
            if (((xorQuote - 0x0101010101010101L) & ~xorQuote & 0x8080808080808080L) != 0) {
                break;
            }
            pos += 8;
        }
        while (pos < end && buf[pos] != '"') {
            pos++;
        }
        if (pos >= end) {
            throw new SerializationException("Unterminated base64 string");
        }
        int contentEnd = pos;
        deser.parsedEndPos = pos + 1; // after closing quote

        if (contentStart == contentEnd) {
            return new byte[0];
        }

        byte[] base64Bytes = Arrays.copyOfRange(buf, contentStart, contentEnd);
        try {
            return BASE64_DECODER.decode(base64Bytes);
        } catch (IllegalArgumentException e) {
            throw new SerializationException("Invalid base64 in blob value", e);
        }
    }

    static String describeChar(byte b) {
        if (b >= 0x20 && b < 0x7F) {
            return "'" + (char) b + "'";
        }
        return "0x" + Integer.toHexString(b & 0xFF);
    }

    static String describePos(byte[] buf, int pos, int end) {
        if (pos >= end) {
            return "end of input";
        }
        return describeChar(buf[pos]);
    }
}
