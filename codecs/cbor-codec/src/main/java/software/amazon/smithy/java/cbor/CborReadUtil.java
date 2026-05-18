/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static software.amazon.smithy.java.cbor.CborDeserializer.EIGHT_BYTES;
import static software.amazon.smithy.java.cbor.CborDeserializer.MAJOR_TYPE_MASK;
import static software.amazon.smithy.java.cbor.CborDeserializer.MAJOR_TYPE_SHIFT;
import static software.amazon.smithy.java.cbor.CborDeserializer.MINOR_TYPE_MASK;
import static software.amazon.smithy.java.cbor.CborDeserializer.ONE_BYTE;
import static software.amazon.smithy.java.cbor.CborDeserializer.TAG_NEG_BIGNUM;
import static software.amazon.smithy.java.cbor.CborDeserializer.TAG_POS_BIGNUM;
import static software.amazon.smithy.java.cbor.CborDeserializer.TYPE_NEGINT;
import static software.amazon.smithy.java.cbor.CborDeserializer.TYPE_POSINT;
import static software.amazon.smithy.java.cbor.CborDeserializer.TYPE_TAG;
import static software.amazon.smithy.java.cbor.CborDeserializer.ZERO_BYTES;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class CborReadUtil {

    static final VarHandle BE_SHORT =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);
    static final VarHandle BE_INT =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    static final VarHandle BE_LONG =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    public static int argLength(int minorType) {
        if (minorType <= ZERO_BYTES)
            return 0;
        if (minorType > EIGHT_BYTES)
            throw new BadCborException("illegal arg length type: " + minorType);
        int shift = minorType - ONE_BYTE;
        return 1 << shift;
    }

    public static int readPosInt(byte[] buffer, int off, int len) {
        if (len == 0) {
            return buffer[off] & MINOR_TYPE_MASK;
        }
        long val = readLong0(buffer, off, len);
        if (val > Integer.MAX_VALUE)
            throw new BadCborException("value cannot fit into an int");
        if (val < 0)
            throw new BadCborException("expected positive int");
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
    static void flipBytes(final byte[] src) {
        for (int i = 0; i < src.length; i++) {
            src[i] = (byte) ~src[i];
        }
    }

    public static long readLong(byte[] buffer, byte type, int off, int len) {
        long val = len == 0 ? buffer[off] & MINOR_TYPE_MASK : readLong0(buffer, off, len);
        if (type == TYPE_NEGINT)
            return -val - 1;
        else
            return val;
    }

    private static long readLong0(byte[] buffer, int off, int len) {
        switch (len) {
            case 1:
                return buffer[off] & 0xffL;
            case 2:
                return ((short) BE_SHORT.get(buffer, off)) & 0xffffL;
            case 4:
                return ((int) BE_INT.get(buffer, off)) & 0xffffffffL;
            case 8:
                return (long) BE_LONG.get(buffer, off);
            default:
                return invalidLength(len);
        }
    }

    private static long invalidLength(int len) {
        throw new BadCborException("invalid length: " + len, true);
    }

    public static BigInteger readBigInteger(byte[] buffer, byte context, int off, int len) {
        //If the value fits inside a long
        boolean isPosInt = context == CborDeserializer.Token.POS_INT;
        if (isPosInt || context == CborDeserializer.Token.NEG_INT) {
            return readSmallBigInteger(buffer, context, off, len, isPosInt);
        }
        final byte[] buff;
        if (len >= 0) {
            checkInvalidLength(buffer, off, len);
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
        if (context == CborDeserializer.Token.NEG_BIGINT) {
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
        if (intExponent != exponent || intExponent == Integer.MIN_VALUE) {
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
                    context = CborDeserializer.Token.POS_BIGINT;
                } else if (minor == TAG_NEG_BIGNUM) {
                    context = CborDeserializer.Token.NEG_BIGINT;
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
        if (CborDeserializer.isIndefinite(len)) {
            return new String(readBytesIndefinite(buffer, off, CborDeserializer.itemLength(len)),
                    StandardCharsets.UTF_8);
        } else {
            return new String(buffer, off, len, StandardCharsets.UTF_8);
        }
    }

    public static byte[] readByteString(byte[] buffer, int off, int len) {
        if (CborDeserializer.isIndefinite(len)) {
            return readBytesIndefinite(buffer, off, CborDeserializer.itemLength(len));
        } else {
            return readBytesFinite(buffer, off, len);
        }
    }

    public static void readByteString(byte[] buffer, int off, byte[] dest, int destOff, int len) {
        if (CborDeserializer.isIndefinite(len)) {
            readBytesIndefinite(buffer, off, dest, destOff, CborDeserializer.itemLength(len));
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
        if (off + len > b.length)
            throw new BadCborException("out-of-bounds finite string read operands", true);
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
        if (strPos != len)
            throw new BadCborException("cannot read unclosed indefinite length string");
    }

    private static byte getMajor(byte b) {
        return (byte) ((b & MAJOR_TYPE_MASK) >> MAJOR_TYPE_SHIFT);
    }

    private static byte getMinor(byte b) {
        return (byte) (b & MINOR_TYPE_MASK);
    }

    private static void checkInvalidLength(byte[] buf, int off, int len) {
        if (len > buf.length - off) {
            throw new BadCborException("Invalid length");
        }
    }

    private CborReadUtil() {}
}
