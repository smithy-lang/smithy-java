/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import software.amazon.smithy.java.codecs.commons.NumberCodec;
import software.amazon.smithy.java.codecs.commons.TimestampCodec;
import software.amazon.smithy.java.core.serde.SerializationException;

/**
 * Low-level utilities for parsing JSON primitives directly from byte arrays.
 *
 * <p>All methods implement strict RFC 8259 compliance: no leading zeros on numbers,
 * no unescaped control characters in strings, full UTF-8 validation.
 */
final class JsonReadUtils {

    private JsonReadUtils() {}

    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    // VarHandle for reading 8 bytes at a time from byte arrays (SWAR technique)
    private static final VarHandle LONG_HANDLE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT_HANDLE =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final long ASCII_ZEROES = 0x3030303030303030L;
    private static final long ASCII_NINES = 0x3939393939393939L;
    private static final long ASCII_HIGH_BITS = 0x8080808080808080L;

    static long readLongLittleEndian(byte[] buf, int pos) {
        return (long) LONG_HANDLE.get(buf, pos);
    }

    static void writeLongLittleEndian(byte[] buf, int pos, long value) {
        LONG_HANDLE.set(buf, pos, value);
    }

    static int readIntLittleEndian(byte[] buf, int pos) {
        return (int) INT_HANDLE.get(buf, pos);
    }

    static void writeIntLittleEndian(byte[] buf, int pos, int value) {
        INT_HANDLE.set(buf, pos, value);
    }

    static long tryParseTenDigitEpochSecond(byte[] buf, int pos, int end) {
        int stop = pos + 10;
        if (stop > end || (buf[pos] < '1' || buf[pos] > '9')) {
            return -1;
        }
        if (stop < end) {
            byte next = buf[stop];
            if ((next >= '0' && next <= '9') || next == '.' || next == 'e' || next == 'E') {
                return -1;
            }
        }

        long chunk = readLongLittleEndian(buf, pos);
        long digits = chunk - ASCII_ZEROES;
        if (((digits | (ASCII_NINES - chunk)) & ASCII_HIGH_BITS) != 0) {
            return -1;
        }
        int ninth = buf[pos + 8] - '0';
        int tenth = buf[pos + 9] - '0';
        if ((ninth | tenth) < 0 || ninth > 9 || tenth > 9) {
            return -1;
        }

        long pairs = (digits * 10 + (digits >>> 8)) & 0x00FF00FF00FF00FFL;
        long quads = (pairs * 100 + (pairs >>> 16)) & 0x0000FFFF0000FFFFL;
        long firstEight = (quads & 0xFFFF) * 10_000 + (quads >>> 32);
        return firstEight * 100 + ninth * 10L + tenth;
    }

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

        if (pos < end && buf[pos] == '-') {
            pos++;
        }

        if (pos >= end) {
            throw new SerializationException("Unexpected end of input while parsing number");
        }

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

        if (pos < end && buf[pos] == '.') {
            pos++;
            if (pos >= end || buf[pos] < '0' || buf[pos] > '9') {
                throw new SerializationException("Expected digit after decimal point");
            }
            while (pos < end && buf[pos] >= '0' && buf[pos] <= '9') {
                pos++;
            }
        }

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

        deser.parsedDouble = NumberCodec.parseDouble(buf, start, pos - start);
        deser.parsedEndPos = pos;
    }

    /**
     * Parses the common subset of JSON doubles that are encoded as short integer tokens.
     *
     * <p>Returns false for fractions, exponents, and integers longer than 18 digits so the
     * caller can use the full floating-point parser. Eighteen digits always fit in a signed
     * long, which keeps this path free of overflow branches.
     */
    static boolean tryParseIntegerDouble(byte[] buf, int pos, int end, SmithyJsonDeserializer deser) {
        int start = pos;
        boolean negative = pos < end && buf[pos] == '-';
        if (negative) {
            pos++;
        }
        if (pos >= end) {
            return false;
        }

        byte first = buf[pos];
        if (first < '0' || first > '9') {
            return false;
        }
        if (first == '0') {
            pos++;
            if (pos < end) {
                byte next = buf[pos];
                if ((next >= '0' && next <= '9') || next == '.' || next == 'e' || next == 'E') {
                    return false;
                }
            }
            deser.parsedDouble = negative ? -0.0d : 0.0d;
            deser.parsedEndPos = pos;
            return true;
        }

        long value = 0;
        int digits = 0;
        do {
            if (++digits > 18) {
                return false;
            }
            value = value * 10 + (buf[pos] - '0');
            pos++;
        } while (pos < end && buf[pos] >= '0' && buf[pos] <= '9');

        if (pos < end && (buf[pos] == '.' || buf[pos] == 'e' || buf[pos] == 'E')) {
            return false;
        }
        deser.parsedDouble = negative ? -(double) value : (double) value;
        deser.parsedEndPos = pos;
        return pos > start;
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
        boolean ascii = true;
        while (pos + Long.BYTES <= end) {
            long stopMask = stringStopMask(readLongLittleEndian(buf, pos));
            if (stopMask == 0) {
                pos += Long.BYTES;
                continue;
            }
            int stop = pos + (Long.numberOfTrailingZeros(stopMask) >>> 3);
            byte b = buf[stop];
            if (b == '"') {
                deser.parsedString = deser.decodeAsciiCached(buf, start, stop - start);
                deser.parsedEndPos = stop + 1;
                return;
            }
            if (b == '\\') {
                parseStringWithEscapes(buf, start, stop, end, deser);
                return;
            }
            if ((b & 0xFF) < 0x20) {
                throw new SerializationException(
                        "Unescaped control character 0x" + Integer.toHexString(b & 0xFF) + " in string");
            }
            ascii = false;
            pos = stop + 1;
            break;
        }

        // Scalar loop for remaining bytes and to find the exact special byte
        while (pos < end) {
            byte b = buf[pos];
            if (b == '"') {
                // No escapes found -- fast path. Dedup short strings through the
                // deserializer's per-document cache (repeated keys/values are common).
                deser.parsedString = ascii
                        ? deser.decodeAsciiCached(buf, start, pos - start)
                        : deser.decodeUtf8Cached(buf, start, pos - start);
                deser.parsedEndPos = pos + 1;
                return;
            }
            if (b == '\\') {
                // Has escapes -- slow path
                parseStringWithEscapes(buf, start, pos, end, deser);
                return;
            }
            if ((b & 0xFF) < 0x20) {
                throw new SerializationException(
                        "Unescaped control character 0x" + Integer.toHexString(b & 0xFF) + " in string");
            }
            ascii &= b >= 0;
            pos++;
        }

        throw new SerializationException("Unterminated string");
    }

    /**
     * Returns a byte-lane mask for the first quote, backslash, control, or UTF-8 byte.
     *
     * <p>Subtraction can set later lanes through borrow, but never a lane before the first
     * actual stop. Callers only inspect the least-significant set bit.
     */
    static long stringStopMask(long word) {
        long quoteOrControl = (word ^ 0x0202020202020202L) - 0x2121212121212121L;
        long backslash = (word ^ 0x5C5C5C5C5C5C5C5CL) - 0x0101010101010101L;
        return (quoteOrControl | backslash | word) & 0x8080808080808080L;
    }

    /** Four-lane form of {@link #stringStopMask}, for strings too short for a full word. */
    static int stringStopMask(int word) {
        int quoteOrControl = (word ^ 0x02020202) - 0x21212121;
        int backslash = (word ^ 0x5C5C5C5C) - 0x01010101;
        return (quoteOrControl | backslash | word) & 0x80808080;
    }

    private static void parseStringWithEscapes(
            byte[] buf,
            int start,
            int escapePos,
            int end,
            SmithyJsonDeserializer deser
    ) {
        StringBuilder sb = new StringBuilder(escapePos - start + 16);
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
            if (b < 0 || HEX_VALUES[b] == -1) {
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
        if (pos < end && buf[pos] > ' ') {
            return pos;
        }
        while (pos < end) {
            byte b = buf[pos];
            if (b != ' ' && b != '\n' && b != '\r' && b != '\t') {
                return pos;
            }
            pos++;
        }
        return pos;
    }

    /**
     * Parses an ISO-8601 timestamp directly from a JSON quoted string in the byte buffer.
     * Expects pos to be at the opening quote.
     */
    static Instant parseIso8601(byte[] buf, int pos, int end, SmithyJsonDeserializer deser) {
        if (pos >= end || buf[pos] != '"' || pos + 22 > end) {
            return null;
        }
        int contentStart = pos + 1;
        int closeQuote = contentStart;
        while (closeQuote < end && buf[closeQuote] != '"') {
            closeQuote++;
        }
        if (closeQuote >= end) {
            return null;
        }
        Instant result = TimestampCodec.parseIso8601(buf, contentStart, closeQuote);
        if (result == null) {
            return null;
        }
        deser.parsedEndPos = closeQuote + 1;
        return result;
    }

    /**
     * Parses an HTTP-date directly from a JSON quoted string in the byte buffer.
     * Expects pos to be at the opening quote.
     */
    static Instant parseHttpDate(byte[] buf, int pos, int end, SmithyJsonDeserializer deser) {
        if (pos >= end || buf[pos] != '"' || pos + 31 > end) {
            return null;
        }
        int contentStart = pos + 1;
        int closeQuote = contentStart;
        while (closeQuote < end && buf[closeQuote] != '"') {
            closeQuote++;
        }
        if (closeQuote >= end) {
            return null;
        }
        Instant result = TimestampCodec.parseHttpDate(buf, contentStart, closeQuote);
        if (result == null) {
            return null;
        }
        deser.parsedEndPos = closeQuote + 1;
        return result;
    }

    /**
     * Decodes a base64-encoded JSON string from the byte buffer, bypassing String allocation.
     * Scans for the closing quote to find the base64 content boundaries, then decodes
     * directly from the span via ByteBuffer.wrap (zero-copy input, JDK SIMD intrinsic).
     *
     * <p>Expects {@code pos} at the opening quote. Stores the position after the closing
     * quote in {@code deser.parsedEndPos}.
     *
     * @return the decoded ByteBuffer
     * @throws SerializationException on unterminated string or invalid base64
     */
    static ByteBuffer decodeBase64String(byte[] buf, int pos, int end, SmithyJsonDeserializer deser) {
        if (pos >= end || buf[pos] != '"') {
            throw new SerializationException("Expected '\"', found: " + describePos(buf, pos, end));
        }
        pos++; // skip opening quote

        // Find closing quote using SWAR (8 bytes at a time).
        // Base64 chars never include '"', so a simple quote scan suffices.
        int contentStart = pos;
        while (pos + 8 <= end) {
            long word = readLongLittleEndian(buf, pos);
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
            return ByteBuffer.allocate(0);
        }

        try {
            return BASE64_DECODER.decode(ByteBuffer.wrap(buf, contentStart, contentEnd - contentStart));
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
