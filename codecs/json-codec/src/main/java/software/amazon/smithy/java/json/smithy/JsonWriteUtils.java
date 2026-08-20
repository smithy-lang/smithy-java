/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import software.amazon.smithy.java.codecs.commons.CompactStringAccess;
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
     * Writes a quoted JSON string. Requires {@link #maxQuotedStringBytes} of capacity.
     */
    static int writeQuotedString(byte[] buf, int pos, String value) {
        int next = tryWriteQuotedAscii(buf, pos, value);
        return next >= 0 ? next : writeQuotedStringGeneral(buf, pos, value);
    }

    /**
     * Writes a quoted string if every char is unescaped ASCII. Returns {@code -1} otherwise.
     * Requires {@code value.length() + 2} bytes; on rejection, retry from the original position.
     */
    static int tryWriteQuotedAscii(byte[] buf, int pos, String value) {
        byte[] latin1 = CompactStringAccess.latin1Bytes(value);
        return latin1 != null
                ? writeQuotedAscii(buf, pos, latin1)
                : tryWriteQuotedAsciiChars(buf, pos, value);
    }

    private static int tryWriteQuotedAsciiChars(byte[] buf, int pos, String value) {
        int len = value.length();
        int p = pos;
        buf[p++] = '"';

        // The JIT auto-vectorizes this loop on JDK 21.
        //
        // String.getBytes(int,int,byte[],int) truncates chars above 0xff, so it cannot
        // safely replace this fallback.
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c >= 0x80 || c < 0x20 || c == '"' || c == '\\') {
                return -1;
            }
            buf[p++] = (byte) c;
        }

        buf[p++] = '"';
        return p;
    }

    private static int writeQuotedAscii(byte[] buf, int pos, byte[] latin1) {
        int copied = copyJsonAscii(latin1, buf, pos + 1);
        if (copied < 0) {
            return -1;
        }
        buf[pos] = '"';
        int endQuote = pos + 1 + copied;
        buf[endQuote] = '"';
        return endQuote + 1;
    }

    /**
     * Writes a JSON quoted string that may need escaping or multi-byte encoding, from scratch.
     * Requires {@link #maxQuotedStringBytes} of capacity.
     */
    static int writeQuotedStringGeneral(byte[] buf, int pos, String value) {
        int len = value.length();
        buf[pos++] = '"';

        if (len == 0) {
            buf[pos++] = '"';
            return pos;
        }

        // Copy the safe ASCII prefix before using the per-char encoder.
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

    private static int copyJsonAscii(byte[] value, byte[] buf, int pos) {
        int length = value.length;
        if (length < Long.BYTES) {
            if (length >= Integer.BYTES) {
                int tail = length - Integer.BYTES;
                int head = JsonReadUtils.readHalfWord(value, 0);
                int last = JsonReadUtils.readHalfWord(value, tail);
                if ((JsonReadUtils.stringStopMask(head) | JsonReadUtils.stringStopMask(last)) != 0) {
                    return -1;
                }
                JsonReadUtils.writeHalfWord(buf, pos, head);
                JsonReadUtils.writeHalfWord(buf, pos + tail, last);
                return length;
            }
            for (int index = 0; index < length; index++) {
                int current = value[index] & 0xff;
                if (current < 0x20 || current >= 0x80 || current == '"' || current == '\\') {
                    return -1;
                }
                buf[pos + index] = (byte) current;
            }
            return length;
        }
        int tail = length - Long.BYTES;
        long head = JsonReadUtils.readWord(value, 0);
        long last = JsonReadUtils.readWord(value, tail);
        if (length <= Long.BYTES * 2) {
            if ((JsonReadUtils.stringStopMask(head) | JsonReadUtils.stringStopMask(last)) != 0) {
                return -1;
            }
            JsonReadUtils.writeWord(buf, pos, head);
            JsonReadUtils.writeWord(buf, pos + tail, last);
            return length;
        }
        long middle = JsonReadUtils.readWord(value, Long.BYTES);
        if (length <= Long.BYTES * 3) {
            if ((JsonReadUtils.stringStopMask(head)
                    | JsonReadUtils.stringStopMask(middle)
                    | JsonReadUtils.stringStopMask(last)) != 0) {
                return -1;
            }
            JsonReadUtils.writeWord(buf, pos, head);
            JsonReadUtils.writeWord(buf, pos + Long.BYTES, middle);
            JsonReadUtils.writeWord(buf, pos + tail, last);
            return length;
        }
        return copyJsonAsciiLoop(value, buf, pos, length, tail, head, last);
    }

    private static int copyJsonAsciiLoop(
            byte[] value,
            byte[] buf,
            int pos,
            int length,
            int tail,
            long head,
            long last
    ) {
        long middle = JsonReadUtils.readWord(value, Long.BYTES);
        if ((JsonReadUtils.stringStopMask(head) | JsonReadUtils.stringStopMask(middle)) != 0) {
            return -1;
        }
        JsonReadUtils.writeWord(buf, pos, head);
        JsonReadUtils.writeWord(buf, pos + Long.BYTES, middle);
        int index = Long.BYTES * 2;
        while (index < tail) {
            long word = JsonReadUtils.readWord(value, index);
            if (JsonReadUtils.stringStopMask(word) != 0) {
                return -1;
            }
            JsonReadUtils.writeWord(buf, pos + index, word);
            index += Long.BYTES;
        }
        if (JsonReadUtils.stringStopMask(last) != 0) {
            return -1;
        }
        JsonReadUtils.writeWord(buf, pos + tail, last);
        return length;
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

    static int writeEpochSeconds(byte[] buf, int pos, long epochSecond, int nano) {
        return TimestampCodec.writeEpochSeconds(buf, pos, epochSecond, nano);
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

    /// Computes the UTF-8 byte representation of a JSON field name prefix. The result includes the opening quote, the
    /// field name, the closing quote, and the colon. Example: for field name "foo", returns bytes for `"foo":`
    ///
    /// The name is escaped like any other JSON string. Smithy member names are alphanumeric identifiers, but a
    /// `@jsonName` trait can carry any character, including `"` and `\\`, which would otherwise emit invalid JSON.
    static byte[] encodeFieldNameToken(String fieldName) {
        byte[] scratch = new byte[maxQuotedStringBytes(fieldName) + 1];
        int pos = writeQuotedString(scratch, 0, fieldName);
        scratch[pos++] = ':';
        return Arrays.copyOf(scratch, pos);
    }
}
