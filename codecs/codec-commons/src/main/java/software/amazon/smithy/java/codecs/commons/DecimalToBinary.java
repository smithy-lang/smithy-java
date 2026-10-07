/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

/** Decimal fast paths with NaN indicating that the caller must use a general parser. */
final class DecimalToBinary {
    private DecimalToBinary() {}

    static final int MAX_DIGITS = 18;
    private static final long TWO_POW_53 = 1L << 53;
    private static final int MAX_EXACT_DOUBLE_POW10 = 22;
    private static final int DOUBLE_FRACTION_BITS = 52;
    private static final long DOUBLE_FRACTION_MASK = (1L << DOUBLE_FRACTION_BITS) - 1;

    private static final double[] EXACT_DOUBLE_POW10 = {
            1e0,
            1e1,
            1e2,
            1e3,
            1e4,
            1e5,
            1e6,
            1e7,
            1e8,
            1e9,
            1e10,
            1e11,
            1e12,
            1e13,
            1e14,
            1e15,
            1e16,
            1e17,
            1e18,
            1e19,
            1e20,
            1e21,
            1e22
    };

    // Normalized truncated reciprocals: each is a lower bound of the exact power of ten.
    static final long[] RECIPROCAL_POW10 = {
            0x0000000000000000L,
            0xCCCCCCCCCCCCCCCCL,
            0xA3D70A3D70A3D70AL,
            0x83126E978D4FDF3BL,
            0xD1B71758E219652BL,
            0xA7C5AC471B478423L,
            0x8637BD05AF6C69B5L,
            0xD6BF94D5E57A42BCL,
            0xABCC77118461CEFCL,
            0x89705F4136B4A597L,
            0xDBE6FECEBDEDD5BEL,
            0xAFEBFF0BCB24AAFEL,
            0x8CBCCC096F5088CBL,
            0xE12E13424BB40E13L,
            0xB424DC35095CD80FL,
            0x901D7CF73AB0ACD9L,
            0xE69594BEC44DE15BL,
            0xB877AA3236A4B449L,
            0x9392EE8E921D5D07L
    };

    static double toDouble(long unscaled, int scale) {
        if (unscaled <= TWO_POW_53) {
            if (scale >= 0) {
                if (scale <= MAX_EXACT_DOUBLE_POW10) {
                    return (double) unscaled / EXACT_DOUBLE_POW10[scale];
                }
            } else if (scale >= -MAX_EXACT_DOUBLE_POW10) {
                return (double) unscaled * EXACT_DOUBLE_POW10[-scale];
            }
            return Double.NaN;
        }
        if (scale == 0) {
            return (double) unscaled;
        }
        if (scale > 0 && scale <= MAX_DIGITS) {
            long bits = eiselLemireBits(unscaled, scale);
            if (bits != 0) {
                return Double.longBitsToDouble(bits);
            }
        }
        return Double.NaN;
    }

    private static long eiselLemireBits(long unscaled, int scale) {
        int leadingZeros = Long.numberOfLeadingZeros(unscaled);
        long upper = Math.unsignedMultiplyHigh(unscaled << leadingZeros, RECIPROCAL_POW10[scale]);
        int upperBit = (int) (upper >>> 63);
        long mantissa = upper >>> (upperBit + 9);
        leadingZeros += 1 ^ upperBit;
        long roundBits = upper & 0x1ff;
        if (roundBits == 0x1ff || (roundBits == 0 && (mantissa & 3) == 1)) {
            return 0;
        }
        mantissa = (mantissa + 1) >>> 1;
        if (mantissa >= (1L << (DOUBLE_FRACTION_BITS + 1))) {
            mantissa = 1L << DOUBLE_FRACTION_BITS;
            leadingZeros--;
        }
        mantissa &= DOUBLE_FRACTION_MASK;
        // floor(-scale * log2(10)) via the 217706 / 2^16 approximation, exact for scale <= 18.
        long exponent = ((217706L * -scale) >> 16) + 1023 + 64 - leadingZeros;
        if (exponent < 1 || exponent > 2046) {
            return 0;
        }
        return mantissa | (exponent << DOUBLE_FRACTION_BITS);
    }
}
