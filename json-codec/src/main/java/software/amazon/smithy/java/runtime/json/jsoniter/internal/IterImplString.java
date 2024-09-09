/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jsoniter.internal;

import java.io.IOException;
import java.util.Arrays;

final class IterImplString {

    private final static int[] hexDigits = new int['f' + 1];

    static {
        Arrays.fill(hexDigits, -1);
        for (int i = '0'; i <= '9'; ++i) {
            hexDigits[i] = (i - '0');
        }
        for (int i = 'a'; i <= 'f'; ++i) {
            hexDigits[i] = ((i - 'a') + 10);
        }
        for (int i = 'A'; i <= 'F'; ++i) {
            hexDigits[i] = ((i - 'A') + 10);
        }
    }

    static String readString(JsonIterator iter) throws IOException {
        byte c = IterImpl.nextToken(iter);
        if (c == '"') {
            int j = parse(iter);
            return new String(iter.reusableChars, 0, j);
        } else if (c == 'n') {
            iter.unreadByte();
            iter.readNull();
            return null;
        } else {
            throw iter.reportError("Expected string or null, but found " + (char) c);
        }
    }

    private static int parse(JsonIterator iter) {
        try {
            byte c;// try fast path first
            int i = iter.head;
            // this code will trigger jvm hotspot pattern matching to highly optimized assembly
            int bound = iter.reusableChars.length;

            for (int j = 0; j < bound; j++) {
                c = iter.buf[i++];
                if (c == '"') {
                    iter.head = i;
                    return j;
                }
                // Control characters (U+0000 through U+001F)
                if (c >= 0 && c <= 31) {
                    throw iter.reportError("Unescaped special character: " + c);
                }
                // If we encounter a backslash, which is a beginning of an escape sequence
                // or a high bit was set - indicating an UTF-8 encoded multibyte character,
                // there is no chance that we can decode the string without instantiating
                // a temporary buffer, so quit this loop
                if (c == '\\') {
                    break;
                }
                iter.reusableChars[j] = (char) c;
            }

            int alreadyCopied = 0;
            if (i > iter.head) {
                alreadyCopied = i - iter.head - 1;
                iter.head = i - 1;
            }
            return IterImpl.readStringSlowPath(iter, alreadyCopied);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw iter.reportError("Error parsing string");
        }
    }

    static int translateHex(final byte b) {
        int val = hexDigits[b];
        if (val == -1) {
            throw new IndexOutOfBoundsException(b + " is not valid hex digit");
        }
        return val;
    }

    // slice does not allow escape
    static int findSliceEnd(JsonIterator iter) {
        for (int i = iter.head; i < iter.tail; i++) {
            byte c = iter.buf[i];
            if (c == '"') {
                return i + 1;
            } else if (c == '\\') {
                throw iter.reportError("Slice does not support escape char");
            } else if (c >= 0 && c <= 31) {
                throw iter.reportError("Unescaped special character: " + c);
            }
        }
        throw iter.reportError("Incomplete string");
    }
}
