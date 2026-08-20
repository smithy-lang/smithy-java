/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import software.amazon.smithy.java.codecs.commons.CompactStringAccess;

/**
 * Low-level utilities for writing XML content directly to byte arrays with proper escaping.
 */
final class XmlWriteUtils {

    private XmlWriteUtils() {}

    private static final byte[] AMP_ESC = {'&', 'a', 'm', 'p', ';'};
    private static final byte[] LT_ESC = {'&', 'l', 't', ';'};
    private static final byte[] GT_ESC = {'&', 'g', 't', ';'};
    private static final byte[] QUOT_ESC = {'&', 'q', 'u', 'o', 't', ';'};
    private static final byte[] APOS_ESC = {'&', 'a', 'p', 'o', 's', ';'};

    static int writeEscapedText(byte[] buf, int pos, String value) {
        byte[] latin1 = CompactStringAccess.latin1Bytes(value);
        return latin1 != null
                ? writeEscapedLatin1(buf, pos, latin1, false)
                : writeEscapedTextChars(buf, pos, value);
    }

    private static int writeEscapedTextChars(byte[] buf, int pos, String value) {
        int len = value.length();
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c < 0x80) {
                if (c == '&') {
                    System.arraycopy(AMP_ESC, 0, buf, pos, 5);
                    pos += 5;
                } else if (c == '<') {
                    System.arraycopy(LT_ESC, 0, buf, pos, 4);
                    pos += 4;
                } else if (c == '>') {
                    System.arraycopy(GT_ESC, 0, buf, pos, 4);
                    pos += 4;
                } else {
                    buf[pos++] = (byte) c;
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
                        buf[pos++] = '?';
                        i--;
                    }
                } else {
                    buf[pos++] = '?';
                }
            }
        }
        return pos;
    }

    static int writeEscapedAttribute(byte[] buf, int pos, String value) {
        byte[] latin1 = CompactStringAccess.latin1Bytes(value);
        return latin1 != null
                ? writeEscapedLatin1(buf, pos, latin1, true)
                : writeEscapedAttributeChars(buf, pos, value);
    }

    private static int writeEscapedAttributeChars(byte[] buf, int pos, String value) {
        int len = value.length();
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c < 0x80) {
                if (c == '&') {
                    System.arraycopy(AMP_ESC, 0, buf, pos, 5);
                    pos += 5;
                } else if (c == '<') {
                    System.arraycopy(LT_ESC, 0, buf, pos, 4);
                    pos += 4;
                } else if (c == '>') {
                    System.arraycopy(GT_ESC, 0, buf, pos, 4);
                    pos += 4;
                } else if (c == '"') {
                    System.arraycopy(QUOT_ESC, 0, buf, pos, 6);
                    pos += 6;
                } else if (c == '\'') {
                    System.arraycopy(APOS_ESC, 0, buf, pos, 6);
                    pos += 6;
                } else {
                    buf[pos++] = (byte) c;
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
                        buf[pos++] = '?';
                        i--;
                    }
                } else {
                    buf[pos++] = '?';
                }
            }
        }
        return pos;
    }

    private static int writeEscapedLatin1(byte[] buf, int pos, byte[] value, boolean attribute) {
        int copyStart = 0;
        for (int i = 0; i < value.length; i++) {
            byte current = value[i];
            byte[] escape = null;
            if (current == '&') {
                escape = AMP_ESC;
            } else if (current == '<') {
                escape = LT_ESC;
            } else if (current == '>') {
                escape = GT_ESC;
            } else if (attribute && current == '"') {
                escape = QUOT_ESC;
            } else if (attribute && current == '\'') {
                escape = APOS_ESC;
            }

            if (escape != null) {
                pos = copyAscii(value, copyStart, i, buf, pos);
                System.arraycopy(escape, 0, buf, pos, escape.length);
                pos += escape.length;
                copyStart = i + 1;
            } else if (current < 0) {
                pos = copyAscii(value, copyStart, i, buf, pos);
                int c = current & 0xff;
                buf[pos++] = (byte) (0xC0 | (c >> 6));
                buf[pos++] = (byte) (0x80 | (c & 0x3F));
                copyStart = i + 1;
            }
        }
        return copyAscii(value, copyStart, value.length, buf, pos);
    }

    private static int copyAscii(byte[] value, int start, int end, byte[] buf, int pos) {
        int length = end - start;
        System.arraycopy(value, start, buf, pos, length);
        return pos + length;
    }

    static int maxEscapedTextBytes(String value) {
        return value.length() * 5;
    }

    static int maxEscapedAttributeBytes(String value) {
        return value.length() * 6;
    }
}
