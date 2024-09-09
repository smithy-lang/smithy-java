/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jsoniter.internal;

import java.io.IOException;

final class IterImplNumber {

    final static int[] intDigits = new int[127];
    final static int[] floatDigits = new int[127];
    final static int END_OF_NUMBER = -2;
    final static int DOT_IN_NUMBER = -3;
    final static int INVALID_CHAR_FOR_NUMBER = -1;
    static final long POW10[] = {1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000, 1000000000, 10000000000L, 100000000000L, 1000000000000L, 10000000000000L, 100000000000000L, 1000000000000000L};

    static {
        for (int i = 0; i < floatDigits.length; i++) {
            floatDigits[i] = INVALID_CHAR_FOR_NUMBER;
            intDigits[i] = INVALID_CHAR_FOR_NUMBER;
        }
        for (int i = '0'; i <= '9'; ++i) {
            floatDigits[i] = (i - '0');
            intDigits[i] = (i - '0');
        }
        floatDigits[','] = END_OF_NUMBER;
        floatDigits[']'] = END_OF_NUMBER;
        floatDigits['}'] = END_OF_NUMBER;
        floatDigits[' '] = END_OF_NUMBER;
        floatDigits['.'] = DOT_IN_NUMBER;
    }

    static double readDouble(final JsonIterator iter) throws IOException {
        final byte c = IterImpl.nextToken(iter);
        if (c == '-') {
            return -IterImpl.readDouble(iter);
        } else {
            iter.unreadByte();
            return IterImpl.readDouble(iter);
        }
    }

    static float readFloat(final JsonIterator iter) throws IOException {
        return (float) readDouble(iter);
    }

    static int readInt(final JsonIterator iter) {
        byte c = IterImpl.nextToken(iter);
        if (c == '-') {
            return IterImpl.readInt(iter, IterImpl.readByte(iter));
        } else {
            int val = IterImpl.readInt(iter, c);
            if (val == Integer.MIN_VALUE) {
                throw iter.reportError("Number is too large for int");
            }
            return -val;
        }
    }

    static long readLong(JsonIterator iter) {
        byte c = IterImpl.nextToken(iter);
        if (c == '-') {
            c = IterImpl.readByte(iter);
            if (intDigits[c] == 0) {
                IterImplForStreaming.assertNotLeadingZero(iter);
                return 0;
            }
            return IterImpl.readLong(iter, c);
        } else {
            if (intDigits[c] == 0) {
                IterImplForStreaming.assertNotLeadingZero(iter);
                return 0;
            }
            long val = IterImpl.readLong(iter, c);
            if (val == Long.MIN_VALUE) {
                throw iter.reportError("Number is too large for long");
            }
            return -val;
        }
    }
}
