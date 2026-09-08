/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

/**
 * Form-urlencoded value encoding, shared by the interpreted serializer and by generated codecs.
 *
 * <p>Every method writes into a caller-owned buffer at a caller-supplied position and returns the
 * new position, so capacity stays the caller's responsibility. The reservation each one requires is
 * documented on the method, because getting it wrong is a buffer overrun rather than a wrong answer:
 * the writers do no bounds checking of their own. {@link #writeAsciiUrlEncoded} exists so that the
 * common all-ASCII case can be reserved at three bytes per character; it declines by returning
 * {@code -1} instead of writing past what that reservation covers.
 */
final class QueryUrlEncoding {
    /**
     * Worst-case encoded bytes per {@code char}.
     *
     * <p>A BMP character outside the basic multilingual plane's ASCII range encodes to three UTF-8
     * bytes, each of which percent-encodes to three bytes. A surrogate pair is four UTF-8 bytes, so
     * twelve encoded bytes across two chars, which this bound also covers.
     */
    static final int MAX_BYTES_PER_CHAR = 9;

    /** Worst-case encoded bytes per compact Latin-1 byte. */
    static final int MAX_BYTES_PER_LATIN1_BYTE = 6;

    static final boolean[] UNRESERVED = new boolean[128];
    static final byte[] PERCENT_ENCODED = new byte[256 * 3];

    static {
        for (int c = 'A'; c <= 'Z'; c++)
            UNRESERVED[c] = true;
        for (int c = 'a'; c <= 'z'; c++)
            UNRESERVED[c] = true;
        for (int c = '0'; c <= '9'; c++)
            UNRESERVED[c] = true;
        UNRESERVED['-'] = true;
        UNRESERVED['.'] = true;
        UNRESERVED['_'] = true;
        UNRESERVED['~'] = true;

        byte[] hex = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        for (int b = 0; b < 256; b++) {
            int off = b * 3;
            PERCENT_ENCODED[off] = '%';
            PERCENT_ENCODED[off + 1] = hex[(b >> 4) & 0xF];
            PERCENT_ENCODED[off + 2] = hex[b & 0xF];
        }
    }

    private QueryUrlEncoding() {}

    /**
     * Encodes an all-ASCII string, or declines.
     *
     * <p>Returns {@code -1} on the first character at or above {@code U+0080}, having written at
     * most three bytes per character consumed so far. The caller keeps its original position in that
     * case, so the partial write is simply overwritten by {@link #writeUrlEncoded}.
     *
     * <p>The caller must have reserved {@code s.length() * 3} bytes.
     */
    @SuppressWarnings("deprecation")
    static int writeAsciiUrlEncoded(byte[] buf, int pos, String s) {
        int len = s.length();

        // Keep the all-unreserved path as a scan followed by one intrinsic array copy. Query values
        // are commonly identifiers, metric names, and enum values, so most strings take this path.
        boolean allUnreserved = true;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c >= 0x80) {
                return -1;
            }
            allUnreserved &= UNRESERVED[c];
        }
        if (allUnreserved) {
            s.getBytes(0, len, buf, pos);
            return pos + len;
        }

        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (UNRESERVED[c]) {
                buf[pos++] = (byte) c;
            } else {
                int off = c * 3;
                buf[pos] = PERCENT_ENCODED[off];
                buf[pos + 1] = PERCENT_ENCODED[off + 1];
                buf[pos + 2] = PERCENT_ENCODED[off + 2];
                pos += 3;
            }
        }
        return pos;
    }

    /**
     * Encodes compact-string bytes when they are all ASCII, or declines.
     *
     * <p>The caller must have reserved {@code value.length * 3} bytes.
     */
    static int writeAsciiUrlEncoded(byte[] buf, int pos, byte[] value) {
        boolean allUnreserved = true;
        for (byte current : value) {
            int c = current & 0xff;
            if (c >= 0x80) {
                return -1;
            }
            allUnreserved &= UNRESERVED[c];
        }
        if (allUnreserved) {
            System.arraycopy(value, 0, buf, pos, value.length);
            return pos + value.length;
        }

        for (byte current : value) {
            int c = current & 0xff;
            if (UNRESERVED[c]) {
                buf[pos++] = current;
            } else {
                int off = c * 3;
                buf[pos] = PERCENT_ENCODED[off];
                buf[pos + 1] = PERCENT_ENCODED[off + 1];
                buf[pos + 2] = PERCENT_ENCODED[off + 2];
                pos += 3;
            }
        }
        return pos;
    }

    /**
     * Encodes compact Latin-1 bytes as UTF-8 followed by percent encoding.
     *
     * <p>The caller must have reserved {@code value.length * MAX_BYTES_PER_LATIN1_BYTE} bytes.
     */
    static int writeLatin1UrlEncoded(byte[] buf, int pos, byte[] value) {
        for (byte current : value) {
            int c = current & 0xff;
            if (c < 0x80) {
                if (UNRESERVED[c]) {
                    buf[pos++] = current;
                } else {
                    int off = c * 3;
                    buf[pos] = PERCENT_ENCODED[off];
                    buf[pos + 1] = PERCENT_ENCODED[off + 1];
                    buf[pos + 2] = PERCENT_ENCODED[off + 2];
                    pos += 3;
                }
            } else {
                int b0 = 0xC0 | (c >> 6);
                int b1 = 0x80 | (c & 0x3F);
                System.arraycopy(PERCENT_ENCODED, b0 * 3, buf, pos, 3);
                pos += 3;
                System.arraycopy(PERCENT_ENCODED, b1 * 3, buf, pos, 3);
                pos += 3;
            }
        }
        return pos;
    }

    /**
     * Encodes an arbitrary string.
     *
     * <p>The caller must have reserved {@code s.length() * MAX_BYTES_PER_CHAR} bytes.
     */
    static int writeUrlEncoded(byte[] buf, int pos, String s) {
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                if (UNRESERVED[c]) {
                    buf[pos++] = (byte) c;
                } else {
                    int off = c * 3;
                    buf[pos] = PERCENT_ENCODED[off];
                    buf[pos + 1] = PERCENT_ENCODED[off + 1];
                    buf[pos + 2] = PERCENT_ENCODED[off + 2];
                    pos += 3;
                }
            } else if (c < 0x800) {
                int b0 = 0xC0 | (c >> 6);
                int b1 = 0x80 | (c & 0x3F);
                System.arraycopy(PERCENT_ENCODED, b0 * 3, buf, pos, 3);
                pos += 3;
                System.arraycopy(PERCENT_ENCODED, b1 * 3, buf, pos, 3);
                pos += 3;
            } else if (Character.isHighSurrogate(c) && i + 1 < len && Character.isLowSurrogate(s.charAt(i + 1))) {
                char low = s.charAt(++i);
                int cp = Character.toCodePoint(c, low);
                int b0 = 0xF0 | (cp >> 18);
                int b1 = 0x80 | ((cp >> 12) & 0x3F);
                int b2 = 0x80 | ((cp >> 6) & 0x3F);
                int b3 = 0x80 | (cp & 0x3F);
                System.arraycopy(PERCENT_ENCODED, b0 * 3, buf, pos, 3);
                pos += 3;
                System.arraycopy(PERCENT_ENCODED, b1 * 3, buf, pos, 3);
                pos += 3;
                System.arraycopy(PERCENT_ENCODED, b2 * 3, buf, pos, 3);
                pos += 3;
                System.arraycopy(PERCENT_ENCODED, b3 * 3, buf, pos, 3);
                pos += 3;
            } else {
                int b0 = 0xE0 | (c >> 12);
                int b1 = 0x80 | ((c >> 6) & 0x3F);
                int b2 = 0x80 | (c & 0x3F);
                System.arraycopy(PERCENT_ENCODED, b0 * 3, buf, pos, 3);
                pos += 3;
                System.arraycopy(PERCENT_ENCODED, b1 * 3, buf, pos, 3);
                pos += 3;
                System.arraycopy(PERCENT_ENCODED, b2 * 3, buf, pos, 3);
                pos += 3;
            }
        }
        return pos;
    }

    /**
     * Encodes {@code len} bytes that are already known to be ASCII, such as base64 or an HTTP date.
     *
     * <p>The caller must have reserved {@code len * 3} bytes.
     */
    static int writeUrlEncodedBytes(byte[] buf, int pos, byte[] data, int len) {
        for (int i = 0; i < len; i++) {
            int b = data[i] & 0xFF;
            if (b < 128 && UNRESERVED[b]) {
                buf[pos++] = data[i];
            } else {
                int off = b * 3;
                buf[pos] = PERCENT_ENCODED[off];
                buf[pos + 1] = PERCENT_ENCODED[off + 1];
                buf[pos + 2] = PERCENT_ENCODED[off + 2];
                pos += 3;
            }
        }
        return pos;
    }
}
