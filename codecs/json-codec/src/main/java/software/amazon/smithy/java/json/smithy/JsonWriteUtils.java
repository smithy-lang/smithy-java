/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import software.amazon.smithy.java.codecs.commons.NumberCodec;
import software.amazon.smithy.java.codecs.commons.TimestampCodec;
import software.amazon.smithy.java.io.ByteBufferUtils;

/**
 * Low-level utilities for writing JSON primitives directly to byte arrays.
 *
 * <p>All methods write UTF-8 encoded JSON bytes and return the new write position.
 */
final class JsonWriteUtils {

    private JsonWriteUtils() {}

    static final byte[] NULL_BYTES = {'n', 'u', 'l', 'l'};

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

    static int writeInt(byte[] buf, int pos, int value) {
        return NumberCodec.writeInt(buf, pos, value);
    }

    static int writeLong(byte[] buf, int pos, long value) {
        return NumberCodec.writeLong(buf, pos, value);
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
                if (c >= 0x20 && !NEEDS_ESCAPE[c]) {
                    buf[pos++] = (byte) c;
                } else if (ESCAPE_TABLE[c] != 0) {
                    buf[pos++] = '\\';
                    buf[pos++] = ESCAPE_TABLE[c];
                } else {
                    pos = writeUnicodeEscape(buf, pos, c);
                }
            } else if (c < 0x800) {
                buf[pos++] = (byte) (0xC0 | (c >> 6));
                buf[pos++] = (byte) (0x80 | (c & 0x3F));
            } else if (!Character.isSurrogate(c)) {
                buf[pos++] = (byte) (0xE0 | (c >> 12));
                buf[pos++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                buf[pos++] = (byte) (0x80 | (c & 0x3F));
            } else {
                if (Character.isHighSurrogate(c) && i + 1 < len) {
                    char low = value.charAt(++i);
                    if (Character.isLowSurrogate(low)) {
                        int cp = Character.toCodePoint(c, low);
                        buf[pos++] = (byte) (0xF0 | (cp >> 18));
                        buf[pos++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
                        buf[pos++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
                        buf[pos++] = (byte) (0x80 | (cp & 0x3F));
                    } else {
                        // Lone high surrogate followed by non-low -- escape both
                        pos = writeUnicodeEscape(buf, pos, c);
                        i--; // re-process the non-low char
                    }
                } else {
                    // Lone surrogate -- escape as unicode
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
        return NumberCodec.writeDouble(buf, pos, value);
    }

    static int writeEpochSeconds(byte[] buf, int pos, long epochSecond, int nano) {
        return TimestampCodec.writeEpochSeconds(buf, pos, epochSecond, nano);
    }

    static int writeFloat(byte[] buf, int pos, float value) {
        return NumberCodec.writeFloat(buf, pos, value);
    }

    static int writeIso8601Timestamp(byte[] buf, int pos, Instant value) {
        buf[pos++] = '"';
        pos = TimestampCodec.writeIso8601(buf, pos, value);
        buf[pos++] = '"';
        return pos;
    }

    static int writeHttpDate(byte[] buf, int pos, Instant value) {
        buf[pos++] = '"';
        pos = TimestampCodec.writeHttpDate(buf, pos, value);
        buf[pos++] = '"';
        return pos;
    }

    /**
     * Writes an ASCII string directly to the buffer without quoting.
     * Used for number-to-string conversions (Double.toString, BigDecimal.toString, etc).
     */
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
        return writeBase64String(buf, pos, ByteBuffer.wrap(data, off, len));
    }

    static int writeBase64String(byte[] buf, int pos, ByteBuffer data) {
        buf[pos++] = '"';
        byte[] encoded = ByteBufferUtils.base64EncodeToBytes(data);
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
        return value.length() * 6 + 2;
    }

    /**
     * Returns the maximum number of bytes needed for a base64-encoded string.
     */
    static int maxBase64Bytes(int dataLen) {
        return ((dataLen + 2) / 3) * 4 + 2;
    }

    /**
     * Pre-computes the UTF-8 byte representation of a JSON field name prefix.
     * The result includes the opening quote, the field name, the closing quote, and the colon.
     * Example: for field name "foo", returns bytes for {@code "foo":}
     */
    static byte[] precomputeFieldNameBytes(String fieldName) {
        byte[] nameUtf8 = fieldName.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[nameUtf8.length + 3];
        result[0] = '"';
        System.arraycopy(nameUtf8, 0, result, 1, nameUtf8.length);
        result[nameUtf8.length + 1] = '"';
        result[nameUtf8.length + 2] = ':';
        return result;
    }
}
