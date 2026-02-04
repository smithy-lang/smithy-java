/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import java.nio.ByteBuffer;
import java.util.Arrays;

final class FormUrlEncodedSink {
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
            'A',
            'B',
            'C',
            'D',
            'E',
            'F'
    };

    private byte[] bytes;
    private int pos;

    FormUrlEncodedSink() {
        this.bytes = new byte[256];
        this.pos = 0;
    }

    FormUrlEncodedSink(int initialCapacity) {
        this.bytes = new byte[initialCapacity];
        this.pos = 0;
    }

    void writeByte(int b) {
        ensureCapacity(1);
        bytes[pos++] = (byte) b;
    }

    void writeBytes(byte[] b, int off, int len) {
        ensureCapacity(len);
        System.arraycopy(b, off, bytes, pos, len);
        pos += len;
    }

    @SuppressWarnings("deprecation")
    void writeAscii(String s) {
        int len = s.length();
        ensureCapacity(len);
        s.getBytes(0, len, bytes, pos);
        pos += len;
    }

    void writeUrlEncoded(String s) {
        int len = s.length();
        ensureCapacity(len * 3);
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (isUnreserved(c)) {
                bytes[pos++] = (byte) c;
            } else if (c < 0x80) {
                writePercentEncoded(c);
            } else if (c < 0x800) {
                writePercentEncoded(0xC0 | (c >> 6));
                writePercentEncoded(0x80 | (c & 0x3F));
            } else if (Character.isHighSurrogate(c) && i + 1 < len) {
                char low = s.charAt(++i);
                if (Character.isLowSurrogate(low)) {
                    int cp = Character.toCodePoint(c, low);
                    writePercentEncoded(0xF0 | (cp >> 18));
                    writePercentEncoded(0x80 | ((cp >> 12) & 0x3F));
                    writePercentEncoded(0x80 | ((cp >> 6) & 0x3F));
                    writePercentEncoded(0x80 | (cp & 0x3F));
                }
            } else {
                writePercentEncoded(0xE0 | (c >> 12));
                writePercentEncoded(0x80 | ((c >> 6) & 0x3F));
                writePercentEncoded(0x80 | (c & 0x3F));
            }
        }
    }

    @SuppressWarnings("deprecation")
    void writeInt(int value) {
        String s = Integer.toString(value);
        int len = s.length();
        ensureCapacity(len);
        s.getBytes(0, len, bytes, pos);
        pos += len;
    }

    ByteBuffer finish() {
        return ByteBuffer.wrap(bytes, 0, pos);
    }

    private static boolean isUnreserved(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '-'
                || c == '.'
                || c == '_'
                || c == '~';
    }

    private void writePercentEncoded(int b) {
        bytes[pos++] = '%';
        bytes[pos++] = HEX[(b >> 4) & 0xF];
        bytes[pos++] = HEX[b & 0xF];
    }

    private void ensureCapacity(int len) {
        int required = pos + len;
        if (required > bytes.length) {
            bytes = Arrays.copyOf(bytes, Math.max(required, bytes.length + (bytes.length >> 1)));
        }
    }
}
