/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jsoniter.internal.output;

import java.io.IOException;
import software.amazon.smithy.java.runtime.json.jsoniter.internal.JsonException;

final class StreamImplString {

    private static final byte[] ITOA = new byte[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    private static final boolean[] CAN_DIRECT_WRITE = new boolean[128];
    private final static int SURR1_FIRST = 0xD800;
    private final static int SURR1_LAST = 0xDBFF;
    private final static int SURR2_FIRST = 0xDC00;
    private final static int SURR2_LAST = 0xDFFF;

    static {
        for (int i = 0; i < CAN_DIRECT_WRITE.length; i++) {
            if (i > 31 && i <= 126 && i != '"' && i != '\\') {
                CAN_DIRECT_WRITE[i] = true;
            }
        }
    }

    static void writeString(final JsonStream stream, final String val) throws IOException {
        int i = 0;
        int valLen = val.length();
        int toWriteLen = valLen;
        int bufLengthMinusTwo = stream.buf.length - 2; // make room for the quotes
        if (stream.count + toWriteLen > bufLengthMinusTwo) {
            toWriteLen = bufLengthMinusTwo - stream.count;
        }
        if (toWriteLen < 0) {
            stream.ensure(32);
            if (stream.count + toWriteLen > bufLengthMinusTwo) {
                toWriteLen = bufLengthMinusTwo - stream.count;
            }
        }
        int n = stream.count;
        stream.buf[n++] = '"';
        // write string, the fast path, without utf8 and escape support
        for (; i < toWriteLen; i++) {
            char c = val.charAt(i);
            try {
                if (CAN_DIRECT_WRITE[c]) {
                    stream.buf[n++] = (byte) c;
                } else {
                    break;
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                break;
            }
        }
        if (i == valLen) {
            stream.buf[n++] = '"';
            stream.count = n;
            return;
        }
        stream.count = n;
        // for the remaining parts, we process them char by char
        writeStringSlowPath(stream, val, i, valLen);
        stream.write('"');
    }

    private static void writeStringSlowPath(JsonStream stream, String val, int i, int valLen) throws IOException {
        boolean escapeUnicode = stream.escapeUnicode;
        if (escapeUnicode) {
            for (; i < valLen; i++) {
                int c = val.charAt(i);
                if (c > 127) {
                    writeAsSlashU(stream, c);
                } else {
                    writeAsciiChar(stream, c);
                }
            }
        } else {
            writeStringSlowPathWithoutEscapeUnicode(stream, val, i, valLen);
        }
    }

    private static void writeStringSlowPathWithoutEscapeUnicode(
        JsonStream stream,
        String val,
        int i,
        int valLen
    ) throws IOException {
        int _surrogate;
        for (; i < valLen; i++) {
            int c = val.charAt(i);
            if (c > 127) {
                if (c < 0x800) { // 2-byte
                    stream.write(
                        (byte) (0xc0 | (c >> 6)),
                        (byte) (0x80 | (c & 0x3f))
                    );
                } else { // 3 or 4 bytes
                    // Surrogates?
                    if (c < SURR1_FIRST || c > SURR2_LAST) {
                        stream.write(
                            (byte) (0xe0 | (c >> 12)),
                            (byte) (0x80 | ((c >> 6) & 0x3f)),
                            (byte) (0x80 | (c & 0x3f))
                        );
                        continue;
                    }
                    // Yup, a surrogate:
                    if (c > SURR1_LAST) { // must be from first range
                        throw new JsonException("illegalSurrogate");
                    }
                    _surrogate = c;
                    // and if so, followed by another from next range
                    if (i >= valLen) { // unless we hit the end?
                        break;
                    }
                    int firstPart = _surrogate;
                    _surrogate = 0;

                    // Ok, then, is the second part valid?
                    // TODO: this seems broken.
                    // if (c < SURR2_FIRST || c > SURR2_LAST) {
                    throw new JsonException(
                        "Broken surrogate pair: first char 0x" + Integer.toHexString(firstPart) + ", second 0x"
                            + Integer.toHexString(c) + "; illegal combination"
                    );
//                  }
                    //                    c = 0x10000 + ((firstPart - SURR1_FIRST) << 10) + (c - SURR2_FIRST);
//                    if (c > 0x10FFFF) { // illegal in JSON as well as in XML
//                        throw new JsonException("illegal surrogate");
//                    }
//                    stream.write(
//                        (byte) (0xf0 | (c >> 18)),
//                        (byte) (0x80 | ((c >> 12) & 0x3f)),
//                        (byte) (0x80 | ((c >> 6) & 0x3f)),
//                        (byte) (0x80 | (c & 0x3f))
//                    );
                }
            } else {
                writeAsciiChar(stream, c);
            }
        }
    }

    private static void writeAsciiChar(JsonStream stream, int c) throws IOException {
        switch (c) {
            case '"':
                stream.write((byte) '\\', (byte) '"');
                break;
            case '\\':
                stream.write((byte) '\\', (byte) '\\');
                break;
            case '\b':
                stream.write((byte) '\\', (byte) 'b');
                break;
            case '\f':
                stream.write((byte) '\\', (byte) 'f');
                break;
            case '\n':
                stream.write((byte) '\\', (byte) 'n');
                break;
            case '\r':
                stream.write((byte) '\\', (byte) 'r');
                break;
            case '\t':
                stream.write((byte) '\\', (byte) 't');
                break;
            default:
                if (c < 32) {
                    writeAsSlashU(stream, c);
                } else {
                    stream.write(c);
                }
        }
    }

    private static void writeAsSlashU(JsonStream stream, int c) throws IOException {
        byte b4 = (byte) (c & 0xf);
        byte b3 = (byte) (c >> 4 & 0xf);
        byte b2 = (byte) (c >> 8 & 0xf);
        byte b1 = (byte) (c >> 12 & 0xf);
        stream.write((byte) '\\', (byte) 'u', ITOA[b1], ITOA[b2], ITOA[b3], ITOA[b4]);
    }
}
