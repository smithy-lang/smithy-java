/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jsoniter.internal;

import java.io.IOException;

final class IterImplSkip {

    static void skip(JsonIterator iter) throws IOException {
        try {
            byte c = IterImpl.nextToken(iter);
            switch (c) {
                case '"':
                    skipString(iter);
                    return;
                case '-':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    skipNumber(iter);
                    return;
                case 't':
                    IterImpl.readTrueRemainder(iter);
                    return;
                case 'n':
                    iter.unreadByte();
                    iter.readNull();
                    return;
                case 'f':
                    IterImpl.readFalseRemainder(iter);
                    return;
                case '[':
                    iter.unreadByte();
                    skipArray(iter);
                    return;
                case '{':
                    skipObject(iter);
                    return;
                default:
                    throw iter.reportError("Syntax error: " + c);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw iter.reportError("Unexpected end of JSON");
        }
    }

    private static void skipString(JsonIterator iter) {
        int end = IterImplSkip.findStringEnd(iter);
        if (end == -1) {
            throw iter.reportError("Incomplete string");
        } else {
            iter.head = end;
        }
    }

    private static void skipNumber(JsonIterator iter) {
        // Rather than reimplement number parsing and checking edge cases, just parse and skip the number.
        IterImplForStreaming.parseJsonNumber(iter);
    }

    private static void skipArray(JsonIterator iter) throws IOException {
        if (iter.startReadArray()) {
            do {
                iter.skip();
            } while (iter.readNextArrayValue());
        }
    }

    private static void skipObject(JsonIterator iter) throws IOException {
        if (iter.startReadObject()) {
            do {
                iter.readObjectKeySlice();
                iter.skip();
            } while (iter.keepReadingObject());
        }
    }

    // adapted from: https://github.com/buger/jsonparser/blob/master/parser.go
    // Tries to find the end of string
    // Support if string contains escaped quote symbols.
    private static int findStringEnd(JsonIterator iter) {
        boolean escaped = false;
        for (int i = iter.head; i < iter.tail; i++) {
            byte c = iter.buf[i];
            if (c == '"') {
                if (!escaped) {
                    return i + 1;
                } else {
                    int j = i - 1;
                    for (;;) {
                        if (j < iter.head || iter.buf[j] != '\\') {
                            // even number of backslashes
                            // either end of buffer, or " found
                            return i + 1;
                        }
                        j--;
                        if (j < iter.head || iter.buf[j] != '\\') {
                            // odd number of backslashes
                            // it is \" or \\\"
                            break;
                        }
                        j--;
                    }
                }
            } else if (c == '\\') {
                escaped = true;
            }
        }
        return -1;
    }
}
