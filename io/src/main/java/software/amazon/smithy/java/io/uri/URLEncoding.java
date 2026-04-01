/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.uri;

import java.nio.charset.StandardCharsets;

/**
 * Handles percent-encoding and decoding per RFC 3986.
 */
public final class URLEncoding {

    private URLEncoding() {}

    private static final boolean[] UNRESERVED = new boolean[128];
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    static {
        for (char c = 'A'; c <= 'Z'; c++) {
            UNRESERVED[c] = true;
        }
        for (char c = 'a'; c <= 'z'; c++) {
            UNRESERVED[c] = true;
        }
        for (char c = '0'; c <= '9'; c++) {
            UNRESERVED[c] = true;
        }
        UNRESERVED['-'] = true;
        UNRESERVED['.'] = true;
        UNRESERVED['_'] = true;
        UNRESERVED['~'] = true;
    }

    /**
     * Percent-encodes all characters except RFC 3986 unreserved characters.
     * If {@code preserveSlashes} is true, '/' is also left unencoded.
     *
     * @param source The raw string to encode. Any existing percent-encoding will be encoded again.
     * @param sink   Where to write the percent-encoded string.
     * @param preserveSlashes true if '/' should be left unencoded.
     */
    public static void encodeUnreserved(String source, StringBuilder sink, boolean preserveSlashes) {
        sink.ensureCapacity(sink.length() + source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c < 128 && UNRESERVED[c]) {
                sink.append(c);
            } else if (preserveSlashes && c == '/') {
                sink.append('/');
            } else if (c < 0x80) {
                percentEncode(sink, (byte) c);
            } else {
                int codePoint;
                if (Character.isHighSurrogate(c) && i + 1 < source.length()) {
                    char d = source.charAt(i + 1);
                    if (Character.isLowSurrogate(d)) {
                        codePoint = Character.toCodePoint(c, d);
                        i++;
                    } else {
                        // Lone high surrogate → replace with U+FFFD
                        codePoint = 0xFFFD;
                    }
                } else if (Character.isLowSurrogate(c)) {
                    // Lone low surrogate → replace with U+FFFD
                    codePoint = 0xFFFD;
                } else {
                    codePoint = c;
                }
                encodeUtf8(sink, codePoint);
            }
        }
    }

    // Manually encodes a Unicode code point as percent-encoded UTF-8 bytes.
    // UTF-8 encoding scheme:
    //   U+0080..U+07FF    → 2 bytes: 110xxxxx 10xxxxxx
    //   U+0800..U+FFFF    → 3 bytes: 1110xxxx 10xxxxxx 10xxxxxx
    //   U+10000..U+10FFFF → 4 bytes: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
    // Each byte is then percent-encoded as %XX.
    // Code points below U+0080 are single-byte ASCII and handled before this method is called.
    private static void encodeUtf8(StringBuilder sink, int cp) {
        if (cp < 0x800) {
            percentEncode(sink, (byte) (0xC0 | (cp >> 6)));
            percentEncode(sink, (byte) (0x80 | (cp & 0x3F)));
        } else if (cp < 0x10000) {
            percentEncode(sink, (byte) (0xE0 | (cp >> 12)));
            percentEncode(sink, (byte) (0x80 | ((cp >> 6) & 0x3F)));
            percentEncode(sink, (byte) (0x80 | (cp & 0x3F)));
        } else {
            percentEncode(sink, (byte) (0xF0 | (cp >> 18)));
            percentEncode(sink, (byte) (0x80 | ((cp >> 12) & 0x3F)));
            percentEncode(sink, (byte) (0x80 | ((cp >> 6) & 0x3F)));
            percentEncode(sink, (byte) (0x80 | (cp & 0x3F)));
        }
    }

    private static void percentEncode(StringBuilder sink, byte b) {
        sink.append('%');
        sink.append(HEX[(b >> 4) & 0x0F]);
        sink.append(HEX[b & 0x0F]);
    }

    /**
     * Percent-encodes all characters except RFC 3986 unreserved characters.
     *
     * @param source Value to encode.
     * @param preserveSlashes true if '/' should be left unencoded.
     * @return Returns the encoded string.
     */
    public static String encodeUnreserved(String source, boolean preserveSlashes) {
        StringBuilder result = new StringBuilder(source.length());
        encodeUnreserved(source, result, preserveSlashes);
        return result.toString();
    }

    /**
     * Decode a percent-encoded string per RFC 3986.
     *
     * <p>Unlike {@link java.net.URLDecoder}, this does NOT treat '+' as a space.
     * The '+' character is left as-is, which is correct for URI percent-encoding
     * (as opposed to HTML form encoding).
     *
     * @param value The string to decode.
     * @return The decoded string, or null if the input is null.
     */
    public static String urlDecode(String value) {
        if (value == null) {
            return null;
        }

        // Fast path: no percent-encoding present.
        if (value.indexOf('%') < 0) {
            return value;
        }

        var sb = new StringBuilder(value.length());
        // Small buffer for accumulating decoded %XX bytes before flushing as UTF-8.
        byte[] buf = new byte[value.length()];
        int pos = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    buf[pos++] = (byte) ((hi << 4) | lo);
                    i += 2;
                    continue;
                }
            }
            // Flush any accumulated bytes before appending a literal char.
            if (pos > 0) {
                sb.append(new String(buf, 0, pos, StandardCharsets.UTF_8));
                pos = 0;
            }
            sb.append(c);
        }

        if (pos > 0) {
            sb.append(new String(buf, 0, pos, StandardCharsets.UTF_8));
        }

        return sb.toString();
    }
}
