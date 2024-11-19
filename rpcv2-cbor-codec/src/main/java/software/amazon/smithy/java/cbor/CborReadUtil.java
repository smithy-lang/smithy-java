/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static software.amazon.smithy.java.cbor.CborParser.EIGHT_BYTES;
import static software.amazon.smithy.java.cbor.CborParser.MAJOR_TYPE_MASK;
import static software.amazon.smithy.java.cbor.CborParser.MAJOR_TYPE_SHIFT;
import static software.amazon.smithy.java.cbor.CborParser.MINOR_TYPE_MASK;
import static software.amazon.smithy.java.cbor.CborParser.ONE_BYTE;
import static software.amazon.smithy.java.cbor.CborParser.TAG_NEG_BIGNUM;
import static software.amazon.smithy.java.cbor.CborParser.TAG_POS_BIGNUM;
import static software.amazon.smithy.java.cbor.CborParser.TYPE_NEGINT;
import static software.amazon.smithy.java.cbor.CborParser.TYPE_POSINT;
import static software.amazon.smithy.java.cbor.CborParser.TYPE_TAG;
import static software.amazon.smithy.java.cbor.CborParser.ZERO_BYTES;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class CborReadUtil {
    public static int argLength(int minorType) {
        if (minorType <= ZERO_BYTES) return 0;
        if (minorType > EIGHT_BYTES) throw new BadCborException("illegal arg length type: " + minorType);
        int shift = minorType - ONE_BYTE;
        return 1 << shift;
    }

    public static int readPosInt(byte[] buffer, int off, int len) {
        if (len == 0) {
            return buffer[off] & MINOR_TYPE_MASK;
        }
        long val = readLong0(buffer, off, len);
        if (val > Integer.MAX_VALUE) throw new BadCborException("value cannot fit into an int");
        if (val < 0) throw new BadCborException("expected positive int");
        return (int) val;
    }

    public static int readStrLen(byte[] buffer, int off, int minor, int argLen) {
        if (argLen == 0) {
            return minor;
        }
        return readPosInt(buffer, off + 1, argLen);
    }

    /**
     * Flips all the bits of the given byte[] using the bitwise negation operator (~)
     *
     */
    private static void flipBytes(final byte[] src) {
        for (int i = 0; i < src.length; i++) {
            src[i] = (byte) ~src[i];
        }
    }

    public static long readLong(byte[] buffer, byte type, int off, int len) {
        long val = len == 0 ? buffer[off] & MINOR_TYPE_MASK : readLong0(buffer, off, len);
        if (type == TYPE_NEGINT) return -val - 1;
        else return val;
    }

    @SuppressFBWarnings("SF_SWITCH_FALLTHROUGH")
    private static long readLong0(byte[] buffer, int off, int len) {
        long acc = 0;
        // case order is important here, do not reorder
        switch (len) {
            case 8:
                acc = ((long) buffer[off++] & 0xff) << 56
                    | ((long) buffer[off++] & 0xff) << 48
                    | ((long) buffer[off++] & 0xff) << 40
                    | ((long) buffer[off++] & 0xff) << 32;
            case 4:
                acc |= ((long) buffer[off++] & 0xff) << 24
                    | ((long) buffer[off++] & 0xff) << 16;
            case 2:
                acc |= ((long) buffer[off++] & 0xff) << 8;
            case 1:
                return acc | ((long) buffer[off] & 0xff);
            default:
                return invalidLength(len);
        }
    }

    private static long invalidLength(int len) {
        throw new BadCborException("invalid length: " + len, true);
    }

    public static BigInteger readBigInteger(byte[] buffer, byte context, int off, int len) {
        //If the value fits inside a long
        boolean isPosInt = context == CborParser.Token.POS_INT;
        if (isPosInt || context == CborParser.Token.NEG_INT) {
            return readSmallBigInteger(buffer, context, off, len, isPosInt);
        }
        final byte[] buff;
        if (len >= 0) {
            final int desOff;
            if (buffer[off] >= 0) {
                buff = new byte[len];
                desOff = 0;
            } else {
                buff = new byte[len + 1];
                desOff = 1;
            }
            CborReadUtil.readByteString(buffer, off, buff, desOff, len);
        } else {
            //Handle indefinite length string
            byte[] indefBuf = CborReadUtil.readByteString(buffer, off, len);
            if (indefBuf.length == 0 || indefBuf[0] < 0) {
                buff = new byte[indefBuf.length + 1];
                System.arraycopy(indefBuf, 0, buff, 1, indefBuf.length);
            } else {
                buff = indefBuf;
            }
        }
        if (context == CborParser.Token.NEG_BIGINT) {
            CborReadUtil.flipBytes(buff);
        }
        return new BigInteger(buff);
    }

    private static BigInteger readSmallBigInteger(byte[] buffer, byte context, int off, int len, boolean isPosInt) {
        //If its exactly 64 bits, we write a negative number.
        if (len == 8 && buffer[off] >> 8 == -1) {
            byte[] val = new byte[9];
            CborReadUtil.readByteString(buffer, off, val, 1, len);
            if (!isPosInt) {
                CborReadUtil.flipBytes(val);
            }
            return new BigInteger(val);
        }
        return BigInteger.valueOf(readLong(buffer, context, off, len));
    }

    public static BigDecimal readBigDecimal(byte[] buffer, int originalOff) {
        int cur = originalOff;
        int exponentLength = argLength(getMinor(buffer[cur]));
        long exponent = readLong(buffer, getMajor(buffer[cur]), exponentLength > 0 ? cur + 1 : cur, exponentLength);
        int intExponent = (int) exponent;
        if (intExponent != exponent) {
            throw new BadCborException("exponent cannot fit into an int");
        }
        cur += exponentLength + 1;
        byte major = getMajor(buffer[cur]);
        byte minor = getMinor(buffer[cur]);
        byte context;
        int mantissaLength;
        switch (major) {
            case TYPE_TAG:
                if (minor == TAG_POS_BIGNUM) {
                    context = CborParser.Token.POS_BIGINT;
                } else if (minor == TAG_NEG_BIGNUM) {
                    context = CborParser.Token.NEG_BIGINT;
                } else {
                    throw new BadCborException("Unexpected minor " + minor, true);
                }
                cur++;
                byte mantissaLengthMinor = getMinor(buffer[cur]);
                int argLen = argLength(mantissaLengthMinor);
                if (argLen == 0) {
                    mantissaLength = mantissaLengthMinor;
                    cur++;
                } else {
                    mantissaLength = readPosInt(buffer, ++cur, argLen);
                    cur += argLen;
                }
                break;
            case TYPE_POSINT:
            case TYPE_NEGINT:
                mantissaLength = argLength(minor);
                if (mantissaLength > 0) {
                    cur++;
                }
                context = major;
                break;
            default:
                throw new BadCborException("Unexpected major " + major, true);
        }
        BigInteger unscaled = readBigInteger(buffer, context, cur, mantissaLength);
        return new BigDecimal(unscaled, -intExponent);
    }

    public static String readTextString(byte[] buffer, int off, int len) {
        if (CborParser.isIndefinite(len)) {
            return new String(readBytesIndefinite(buffer, off, CborParser.itemLength(len)), StandardCharsets.UTF_8);
        } else {
            return new String(buffer, off, len, StandardCharsets.UTF_8);
        }
    }

    public static byte[] readByteString(byte[] buffer, int off, int len) {
        if (CborParser.isIndefinite(len)) {
            return readBytesIndefinite(buffer, off, CborParser.itemLength(len));
        } else {
            return readBytesFinite(buffer, off, len);
        }
    }

    public static void readByteString(byte[] buffer, int off, byte[] dest, int destOff, int len) {
        if (CborParser.isIndefinite(len)) {
            readBytesIndefinite(buffer, off, dest, destOff, CborParser.itemLength(len));
        } else {
            readBytesFinite(buffer, off, dest, destOff, len);
        }
    }

    private static byte[] readBytesFinite(byte[] b, int off, int len) {
        byte[] dest = new byte[len];
        readBytesFinite(b, off, dest, 0, len);
        return dest;
    }

    private static void readBytesFinite(byte[] b, int off, byte[] dest, int destOff, int len) {
        if (off + len > b.length) throw new BadCborException("out-of-bounds finite string read operands", true);
        System.arraycopy(b, off, dest, destOff, len);
    }

    private static byte[] readBytesIndefinite(byte[] buffer, int off, int len) {
        byte[] dest = new byte[len];
        readBytesIndefinite(buffer, off, dest, 0, len);
        return dest;
    }

    private static void readBytesIndefinite(byte[] buffer, int off, byte[] dest, int destOff, int len) {
        int strPos = 0;
        while (strPos < len) {
            byte b = buffer[off];
            int minor = b & MINOR_TYPE_MASK;
            int argLen = argLength(minor);
            int strLen = readStrLen(buffer, off, minor, argLen);
            off += argLen + 1;
            System.arraycopy(buffer, off, dest, destOff + strPos, strLen);
            off += strLen;
            strPos += strLen;
        }
        if (strPos != len) throw new BadCborException("cannot read unclosed indefinite length string");
    }

    /**
     * Compares a byte sequence within the CBOR payload to an arbitrary byte sequence. It is assumed that the second
     * sequence (argument {@code str}) is a freestanding byte sequence and does not contain CBOR indefinite length
     * coding.
     *
     * @param buf  the CBOR payload
     * @param bOff offset in {@code buf} where the byte sequence to compare begins
     * @param bLen length of the byte sequence in {@code buf}
     * @param str  the byte sequence to compare against
     * @param sOff the offset in {@code str} where the sequence begins
     * @param sLen the length of the sequence in {@code str} to compare against
     * @return true if they match
     */
    public static boolean compareStringExternal(byte[] buf, int bOff, int bLen, byte[] str, int sOff, int sLen) {
        if (CborParser.isIndefinite(bLen)) {
            return compareIndefinite(buf, bOff, CborParser.itemLength(bLen), str, sOff, sLen);
        } else {
            return compareFinite(buf, bOff, bLen, str, sOff, sLen);
        }
    }

    public static boolean compareStringsInPayload(byte[] buf, int off1, int len1, int off2, int len2) {
        boolean indefinite1 = CborParser.isIndefinite(len1);
        boolean indefinite2 = CborParser.isIndefinite(len2);
        if (!indefinite1 && !indefinite2) {
            return compareFinite(buf, off1, len1, buf, off2, len2);
        } else {
            // TODO: don't read one up front, or at least try to make sure we always use `compareFinite` when possible
            byte[] one = CborReadUtil.readByteString(buf, off1, len1);
            return compareStringExternal(buf, off2, len2, one, 0, one.length);
        }
    }

    private static boolean compareIndefinite(byte[] buf, int bOff, int bLen, byte[] s, int sOff, int sLen) {
        if (bLen != sLen) return false;
        int lim = sOff + sLen;
        while (sOff < lim) {
            byte b = buf[bOff];
            int minor = b & MINOR_TYPE_MASK;
            int argLen = argLength(minor);
            int chunkLen = readStrLen(buf, bOff, minor, argLen);
            bOff += argLen + 1;
            if (!compareFinite(buf, bOff, chunkLen, s, sOff, chunkLen)) return false;
            bOff += chunkLen;
            sOff += chunkLen;
        }
        if (sOff != lim) throw new BadCborException("cannot compare unclosed indefinite length string");
        return true;
    }

    private static boolean compareFinite(byte[] buf, int bOff, int bLen, byte[] s, int sOff, int sLen) {
        return Arrays.compare(buf, bOff, bLen, s, sOff, sLen) == 0;
    }

    private static byte getMajor(byte b) {
        return (byte) ((b & MAJOR_TYPE_MASK) >> MAJOR_TYPE_SHIFT);
    }

    private static byte getMinor(byte b) {
        return (byte) (b & MINOR_TYPE_MASK);
    }

    private CborReadUtil() {}
}
