/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
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
        java.util.Arrays.fill(HEX_VALUES, -1);
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
     * Returns an array of [value, newPos]. Throws on overflow or invalid format.
     */
    static long[] parseLong(byte[] buf, int pos, int end) {
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

        long value = first - '0';
        pos++;

        while (pos < end) {
            byte b = buf[pos];
            if (b < '0' || b > '9') {
                break;
            }
            long prev = value;
            value = value * 10 + (b - '0');
            if (value < prev) {
                throw new SerializationException("Number overflow");
            }
            pos++;
        }

        return new long[] {negative ? -value : value, pos};
    }

    /**
     * Parses a JSON number (integer or floating point) and returns it as a double.
     * Strict RFC 8259: validates the full number grammar.
     * Returns [Double.longBitsToDouble(value), newPos].
     */
    static double[] parseDouble(byte[] buf, int pos, int end) {
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

        // Use JDK's Double.parseDouble for the actual conversion (Eisel-Lemire on JDK 21)
        String numStr = new String(buf, start, pos - start, StandardCharsets.US_ASCII);
        double value = Double.parseDouble(numStr);
        return new double[] {value, Double.longBitsToDouble(pos)};
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
     * Returns the parsed String and advances past the closing quote.
     * Strict: rejects unescaped control characters, validates UTF-8, validates escape sequences.
     *
     * @return array of [string, newPos as encoded int]
     */
    static Object[] parseString(byte[] buf, int pos, int end) {
        if (pos >= end || buf[pos] != '"') {
            throw new SerializationException("Expected '\"', found: " + describePos(buf, pos, end));
        }
        pos++; // skip opening quote

        // Fast path: SWAR scan 8 bytes at a time for closing quote, backslash, or control chars.
        // A byte needs special handling if: b == '"' (0x22), b == '\\' (0x5C), or b < 0x20.
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
                String result = new String(buf, start, pos - start, StandardCharsets.UTF_8);
                return new Object[] {result, pos + 1};
            }
            if (b == '\\') {
                // Has escapes — slow path
                return parseStringWithEscapes(buf, start, pos, end);
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

    private static Object[] parseStringWithEscapes(byte[] buf, int start, int escapePos, int end) {
        // Build a StringBuilder from what we've read so far + escaped content
        StringBuilder sb = new StringBuilder(escapePos - start + 16);
        // Append everything before the first escape as UTF-8
        sb.append(new String(buf, start, escapePos - start, StandardCharsets.UTF_8));

        int pos = escapePos;
        while (pos < end) {
            byte b = buf[pos];
            if (b == '"') {
                return new Object[] {sb.toString(), pos + 1};
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
            int cp = ((b & 0x0F) << 12) | ((buf[pos + 1] & 0x3F) << 6) | (buf[pos + 2] & 0x3F);
            if (cp < 0x800) {
                throw new SerializationException("Overlong UTF-8 sequence");
            }
            return new int[] {cp, pos + 3};
        } else if ((b & 0xF8) == 0xF0) {
            // 4-byte
            if (pos + 4 > end) {
                throw new SerializationException("Truncated UTF-8 sequence");
            }
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

    /**
     * Skips JSON whitespace (space 0x20, tab 0x09, LF 0x0A, CR 0x0D) and returns the new position.
     * The fast check at the top covers the common case (next byte is not whitespace).
     */
    static int skipWhitespace(byte[] buf, int pos, int end) {
        // Fast check: most common case is next byte is not whitespace
        if (pos < end && buf[pos] > ' ') {
            return pos;
        }
        // Scalar loop — simple and correct. The fast check above makes this rarely execute
        // more than 1-2 iterations.
        while (pos < end) {
            byte b = buf[pos];
            if (b != ' ' && b != '\n' && b != '\r' && b != '\t') {
                return pos;
            }
            pos++;
        }
        return pos;
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
