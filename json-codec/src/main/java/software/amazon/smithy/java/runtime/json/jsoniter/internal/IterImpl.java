/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jsoniter.internal;

final class IterImpl {

    // read the bytes between " "
    static Slice readSlice(JsonIterator iter) {
        if (IterImpl.nextToken(iter) != '"') {
            throw iter.reportError("Expected \" for string");
        }
        int end = IterImplString.findSliceEnd(iter);
        // reuse current buffer
        iter.reusableSlice.reset(iter.buf, iter.head, end - 1);
        iter.head = end;
        return iter.reusableSlice;
    }

    static byte nextToken(final JsonIterator iter) {
        int i = iter.head;
        for (;;) {
            byte c = iter.buf[i++];
            switch (c) {
                case ' ':
                case '\n':
                case '\r':
                case '\t':
                    continue;
                default:
                    iter.head = i;
                    return c;
            }
        }
    }

    static byte readByte(JsonIterator iter) {
        return iter.buf[iter.head++];
    }

    static void readTrueRemainder(JsonIterator iter) {
        if (nextToken(iter) != 'r') {
            throw iter.reportError("Expected boolean true value");
        }
        if (nextToken(iter) != 'u') {
            throw iter.reportError("Expected boolean true value");
        }
        if (nextToken(iter) != 'e') {
            throw iter.reportError("Expected boolean true value");
        }
    }

    static void readFalseRemainder(JsonIterator iter) {
        if (nextToken(iter) != 'a') {
            throw iter.reportError("Expected boolean false value");
        }
        if (nextToken(iter) != 'l') {
            throw iter.reportError("Expected boolean false value");
        }
        if (nextToken(iter) != 's') {
            throw iter.reportError("Expected boolean false value");
        }
        if (nextToken(iter) != 'e') {
            throw iter.reportError("Expected boolean false value");
        }
    }

    static int readStringSlowPath(JsonIterator iter, int j) {
        try {
            boolean isExpectingLowSurrogate = false;
            for (int i = iter.head; i < iter.tail;) {
                int bc = iter.buf[i++];
                switch (bc) {
                    case '\r', '\n', '\t', '\b', '\f', 0:
                        throw iter.reportError("Unescaped special character");
                    case '"':
                        iter.head = i;
                        return j;
                    case '\\':
                        bc = iter.buf[i++];
                        switch (bc) {
                            case 'b':
                                bc = '\b';
                                break;
                            case 't':
                                bc = '\t';
                                break;
                            case 'n':
                                bc = '\n';
                                break;
                            case 'f':
                                bc = '\f';
                                break;
                            case 'r':
                                bc = '\r';
                                break;
                            case '"':
                            case '/':
                            case '\\':
                                break;
                            case 'u':
                                bc = (IterImplString.translateHex(iter.buf[i++]) << 12) +
                                    (IterImplString.translateHex(iter.buf[i++]) << 8) +
                                    (IterImplString.translateHex(iter.buf[i++]) << 4) +
                                    IterImplString.translateHex(iter.buf[i++]);
                                if (Character.isHighSurrogate((char) bc)) {
                                    if (isExpectingLowSurrogate) {
                                        throw new JsonException("Invalid surrogate");
                                    } else {
                                        isExpectingLowSurrogate = true;
                                    }
                                } else if (Character.isLowSurrogate((char) bc)) {
                                    if (isExpectingLowSurrogate) {
                                        isExpectingLowSurrogate = false;
                                    } else {
                                        throw new JsonException("Invalid surrogate");
                                    }
                                } else {
                                    if (isExpectingLowSurrogate) {
                                        throw new JsonException("Invalid surrogate");
                                    }
                                }
                                break;

                            default:
                                throw iter.reportError("Invalid escape character: " + bc);
                        }
                        break;
                    default:
                        if ((bc & 0x80) != 0) {
                            final int u2 = iter.buf[i++];
                            if ((bc & 0xE0) == 0xC0) {
                                bc = ((bc & 0x1F) << 6) + (u2 & 0x3F);
                            } else {
                                final int u3 = iter.buf[i++];
                                if ((bc & 0xF0) == 0xE0) {
                                    bc = ((bc & 0x0F) << 12) + ((u2 & 0x3F) << 6) + (u3 & 0x3F);
                                } else {
                                    final int u4 = iter.buf[i++];
                                    if ((bc & 0xF8) == 0xF0) {
                                        bc = ((bc & 0x07) << 18)
                                            + ((u2 & 0x3F) << 12)
                                            + ((u3 & 0x3F) << 6)
                                            + (u4 & 0x3F);
                                    } else {
                                        throw iter.reportError("Invalid unicode character");
                                    }

                                    if (bc >= 0x10000) {
                                        // check if valid unicode
                                        if (bc >= 0x110000)
                                            throw iter.reportError("Invalid unicode character");

                                        // split surrogates
                                        final int sup = bc - 0x10000;
                                        if (iter.reusableChars.length == j) {
                                            char[] newBuf = new char[iter.reusableChars.length * 2];
                                            System.arraycopy(
                                                iter.reusableChars,
                                                0,
                                                newBuf,
                                                0,
                                                iter.reusableChars.length
                                            );
                                            iter.reusableChars = newBuf;
                                        }
                                        iter.reusableChars[j++] = (char) ((sup >>> 10) + 0xd800);
                                        if (iter.reusableChars.length == j) {
                                            char[] newBuf = new char[iter.reusableChars.length * 2];
                                            System.arraycopy(
                                                iter.reusableChars,
                                                0,
                                                newBuf,
                                                0,
                                                iter.reusableChars.length
                                            );
                                            iter.reusableChars = newBuf;
                                        }
                                        iter.reusableChars[j++] = (char) ((sup & 0x3ff) + 0xdc00);
                                        continue;
                                    }
                                }
                            }
                        }
                }
                if (iter.reusableChars.length == j) {
                    char[] newBuf = new char[iter.reusableChars.length * 2];
                    System.arraycopy(iter.reusableChars, 0, newBuf, 0, iter.reusableChars.length);
                    iter.reusableChars = newBuf;
                }
                iter.reusableChars[j++] = (char) bc;
            }
            throw iter.reportError("Incomplete string");
        } catch (IndexOutOfBoundsException e) {
            throw iter.reportError("Incomplete string");
        }
    }

    static int readInt(final JsonIterator iter, final byte c) {
        int ind = IterImplNumber.intDigits[c];
        if (ind == 0) {
            IterImplForStreaming.assertNotLeadingZero(iter);
            return 0;
        }
        if (ind == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
            throw iter.reportError("Expected 0~9");
        }
        if (iter.tail - iter.head > 9) {
            int i = iter.head;
            int ind2 = IterImplNumber.intDigits[iter.buf[i]];
            if (ind2 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                return -ind;
            }
            int ind3 = IterImplNumber.intDigits[iter.buf[++i]];
            if (ind3 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                iter.head = i;
                ind = ind * 10 + ind2;
                return -ind;
            }
            int ind4 = IterImplNumber.intDigits[iter.buf[++i]];
            if (ind4 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                iter.head = i;
                ind = ind * 100 + ind2 * 10 + ind3;
                return -ind;
            }
            int ind5 = IterImplNumber.intDigits[iter.buf[++i]];
            if (ind5 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                iter.head = i;
                ind = ind * 1000 + ind2 * 100 + ind3 * 10 + ind4;
                return -ind;
            }
            int ind6 = IterImplNumber.intDigits[iter.buf[++i]];
            if (ind6 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                iter.head = i;
                ind = ind * 10000 + ind2 * 1000 + ind3 * 100 + ind4 * 10 + ind5;
                return -ind;
            }
            int ind7 = IterImplNumber.intDigits[iter.buf[++i]];
            if (ind7 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                iter.head = i;
                ind = ind * 100000 + ind2 * 10000 + ind3 * 1000 + ind4 * 100 + ind5 * 10 + ind6;
                return -ind;
            }
            int ind8 = IterImplNumber.intDigits[iter.buf[++i]];
            if (ind8 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                iter.head = i;
                ind = ind * 1000000 + ind2 * 100000 + ind3 * 10000 + ind4 * 1000 + ind5 * 100 + ind6 * 10 + ind7;
                return -ind;
            }
            int ind9 = IterImplNumber.intDigits[iter.buf[++i]];
            ind = ind * 10000000 + ind2 * 1000000 + ind3 * 100000 + ind4 * 10000 + ind5 * 1000 + ind6 * 100 + ind7 * 10
                + ind8;
            iter.head = i;
            if (ind9 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                return -ind;
            }
        }
        return IterImplForStreaming.readIntSlowPath(iter, ind);
    }

    static long readLong(final JsonIterator iter, final byte c) {
        long ind = IterImplNumber.intDigits[c];
        if (ind == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
            throw iter.reportError("Expected 0~9");
        }
        if (iter.tail - iter.head > 9) {
            int i = iter.head;
            int ind2 = IterImplNumber.intDigits[iter.buf[i]];
            if (ind2 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                return -ind;
            }
            int ind3 = IterImplNumber.intDigits[iter.buf[++i]];
            if (ind3 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                iter.head = i;
                ind = ind * 10 + ind2;
                return -ind;
            }
            int ind4 = IterImplNumber.intDigits[iter.buf[++i]];
            if (ind4 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                iter.head = i;
                ind = ind * 100 + ind2 * 10 + ind3;
                return -ind;
            }
            int ind5 = IterImplNumber.intDigits[iter.buf[++i]];
            if (ind5 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                iter.head = i;
                ind = ind * 1000 + ind2 * 100 + ind3 * 10 + ind4;
                return -ind;
            }
            int ind6 = IterImplNumber.intDigits[iter.buf[++i]];
            if (ind6 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                iter.head = i;
                ind = ind * 10000 + ind2 * 1000 + ind3 * 100 + ind4 * 10 + ind5;
                return -ind;
            }
            int ind7 = IterImplNumber.intDigits[iter.buf[++i]];
            if (ind7 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                iter.head = i;
                ind = ind * 100000 + ind2 * 10000 + ind3 * 1000 + ind4 * 100 + ind5 * 10 + ind6;
                return -ind;
            }
            int ind8 = IterImplNumber.intDigits[iter.buf[++i]];
            if (ind8 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                iter.head = i;
                ind = ind * 1000000 + ind2 * 100000 + ind3 * 10000 + ind4 * 1000 + ind5 * 100 + ind6 * 10 + ind7;
                return -ind;
            }
            int ind9 = IterImplNumber.intDigits[iter.buf[++i]];
            ind = ind * 10000000 + ind2 * 1000000 + ind3 * 100000 + ind4 * 10000 + ind5 * 1000 + ind6 * 100 + ind7 * 10
                + ind8;
            iter.head = i;
            if (ind9 == IterImplNumber.INVALID_CHAR_FOR_NUMBER) {
                return -ind;
            }
        }
        return IterImplForStreaming.readLongSlowPath(iter, ind);
    }

    static double readDouble(final JsonIterator iter) {
        int oldHead = iter.head;
        try {
            try {
                long value = IterImplNumber.readLong(iter); // without the dot & sign
                if (iter.head == iter.tail) {
                    return value;
                }
                byte c = iter.buf[iter.head];
                if (c == '.') {
                    iter.head++;
                    int start = iter.head;
                    c = iter.buf[iter.head++];
                    long decimalPart = readLong(iter, c);
                    if (decimalPart == Long.MIN_VALUE) {
                        return IterImplForStreaming.readDoubleSlowPath(iter);
                    }
                    decimalPart = -decimalPart;
                    int decimalPlaces = iter.head - start;
                    if (decimalPlaces > 0 && decimalPlaces < IterImplNumber.POW10.length && (iter.head
                        - oldHead) < 10) {
                        return value + (decimalPart / (double) IterImplNumber.POW10[decimalPlaces]);
                    } else {
                        iter.head = oldHead;
                        return IterImplForStreaming.readDoubleSlowPath(iter);
                    }
                } else {
                    return value;
                }
            } finally {
                if (iter.head < iter.tail && (iter.buf[iter.head] == 'e' || iter.buf[iter.head] == 'E')) {
                    iter.head = oldHead;
                    return IterImplForStreaming.readDoubleSlowPath(iter);
                }
            }
        } catch (JsonException e) {
            iter.head = oldHead;
            return IterImplForStreaming.readDoubleSlowPath(iter);
        }
    }
}
