/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import static java.lang.Math.unsignedMultiplyHigh;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Formats finite floats and doubles with JDK decimal spelling, adapted from Victor Zverovich's zmij.
 * Callers must reserve {@link NumberCodec#FLOAT_MAX_BYTES} or {@link NumberCodec#DOUBLE_MAX_BYTES};
 * packed stores may extend past the returned end.
 */
/*
 * Adapted from https://github.com/vitaut/zmij.
 *
 * MIT License
 *
 * Copyright (c) 2025 Victor Zverovich
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
final class Zmij {

    private Zmij() {}

    private static final VarHandle LE_LONG =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final long ASCII_ZEROES = 0x3030303030303030L;
    private static final long[] POWERS = {
            1L,
            10L,
            100L,
            1000L,
            10000L,
            100000L,
            1000000L,
            10000000L,
            100000000L,
            1000000000L,
            10000000000L,
            100000000000L,
            1000000000000L,
            10000000000000L,
            100000000000000L,
            1000000000000000L,
            10000000000000000L,
            100000000000000000L,
            1000000000000000000L
    };

    static int writeFloat(byte[] bytes, int pos, float value) {
        assert Float.isFinite(value) : "Infinity/NaN should be handled by caller";
        int bits = Float.floatToRawIntBits(value);
        if (bits < 0) {
            bytes[pos++] = '-';
        }
        int rawExponent = (bits >>> 23) & 255;
        int significand = bits & 0x7fffff;
        if (rawExponent == 0 && significand == 0) {
            bytes[pos] = '0';
            bytes[pos + 1] = '.';
            bytes[pos + 2] = '0';
            return pos + 3;
        }
        boolean regular = significand != 0 || rawExponent == 0;
        int exponent = rawExponent == 0 ? -149 : rawExponent - 150;
        int adjustment = 0;
        if (rawExponent != 0) {
            significand |= 1 << 23;
        } else if (significand < 8) {
            // Java chooses the closest representation with at least two significant digits.
            significand *= 10;
            adjustment = -1;
        }
        if (!regular) {
            return writeIrregular(bytes, pos, significand, exponent, adjustment);
        }
        int decimalExponent = (exponent * 315653) >> 20;
        int shift = exponent + ((-(decimalExponent + 1) * 217707) >> 16) + 35;
        long power = Powers.SIGNIFICANDS[(-decimalExponent - 1 + 307) * 2];
        long product = unsignedMultiplyHigh(power + 1, (long) significand << shift);
        long integral = product >>> 34;
        long fractional = product & ((1L << 34) - 1);
        long halfUlp = (power >>> (65 - shift)) + (1 - (significand & 1));
        boolean up = ((fractional + halfUlp) >>> 34) != 0;
        boolean down = halfUlp > fractional;
        // Java selects the closest value on a grid with at least two significant digits.
        if (integral < 10) {
            up = false;
            down = false;
        }
        integral += up ? 1 : 0;
        if (!up && !down) {
            int digit = (int) ((fractional * 10 + (1L << 33)) >>> 34);
            if (fractional == (1L << 32)) {
                digit = 2;
            }
            return writeDecimal(bytes, pos, integral * 10 + digit, decimalExponent + adjustment);
        }
        return writeDecimal(bytes, pos, integral, decimalExponent + 1 + adjustment);
    }

    static int writeDouble(byte[] bytes, int pos, double value) {
        assert Double.isFinite(value) : "Infinity/NaN should be handled by caller";
        long bits = Double.doubleToRawLongBits(value);
        if (bits < 0) {
            bytes[pos++] = '-';
        }
        int rawExponent = (int) (bits >>> 52) & 2047;
        long significand = bits & 0xfffffffffffffL;
        if (rawExponent == 0 && significand == 0) {
            bytes[pos] = '0';
            bytes[pos + 1] = '.';
            bytes[pos + 2] = '0';
            return pos + 3;
        }
        boolean regular = significand != 0 || rawExponent == 0;
        int exponent = rawExponent == 0 ? -1074 : rawExponent - 1075;
        int adjustment = 0;
        if (rawExponent != 0) {
            significand |= 1L << 52;
        } else if (significand < 3) {
            significand *= 10;
            adjustment = -1;
        }
        if (!regular) {
            return writeIrregular(bytes, pos, significand, exponent, adjustment);
        }
        int decimalExponent = (exponent * 315653) >> 20;
        int shift = exponent + ((-(decimalExponent + 1) * 217707) >> 16) + 10;
        int index = (-decimalExponent - 1 + 307) * 2;
        long high = Powers.SIGNIFICANDS[index];
        long low = Powers.SIGNIFICANDS[index + 1];
        long scaled = significand << shift;
        long productLow = high * scaled;
        long productHigh = unsignedMultiplyHigh(high, scaled);
        long middle = productLow + unsignedMultiplyHigh(low, scaled);
        productHigh += Long.compareUnsigned(middle, productLow) < 0 ? 1 : 0;
        long integral = productHigh >>> 9;
        long fractional = (productHigh << 55) | (middle >>> 9);
        long halfUlp = (high >>> (10 - shift)) + (1 - (significand & 1));
        boolean up = Long.compareUnsigned(fractional + halfUlp, fractional) < 0;
        boolean down = Long.compareUnsigned(halfUlp, fractional) > 0;
        // Java selects the closest value on a grid with at least two significant digits.
        if (integral < 10) {
            up = false;
            down = false;
        }
        integral += up ? 1 : 0;
        if (!up && !down) {
            int digit = roundedDigit(fractional, Long.MIN_VALUE + 6);
            if (fractional == (1L << 62)) {
                digit = 2;
            }
            return writeDecimal(bytes, pos, integral * 10 + digit, decimalExponent + adjustment);
        }
        return writeDecimal(bytes, pos, integral, decimalExponent + 1 + adjustment);
    }

    // Normal powers of two have a closer lower boundary; the smallest normal is also safe here.
    private static int writeIrregular(byte[] bytes, int pos, long significand, int exponent, int adjustment) {
        int decimalExponent = (exponent * 315653 - 131072) >> 20;
        int shift = exponent + ((-(decimalExponent + 1) * 217707) >> 16) + 10;
        int index = (-decimalExponent - 1 + 307) * 2;
        long high = Powers.SIGNIFICANDS[index];
        long low = Powers.SIGNIFICANDS[index + 1];
        long scaled = significand << shift;
        long productLow = high * scaled;
        long productHigh = unsignedMultiplyHigh(high, scaled);
        long middle = productLow + unsignedMultiplyHigh(low, scaled);
        productHigh += Long.compareUnsigned(middle, productLow) < 0 ? 1 : 0;
        long integral = productHigh >>> 9;
        long fractional = (productHigh << 55) | (middle >>> 9);
        long halfUlp = high >>> (10 - shift);
        boolean up = Long.compareUnsigned(halfUlp, ~fractional) > 0;
        boolean down = Long.compareUnsigned(halfUlp >>> 1, fractional) > 0;
        integral += up ? 1 : 0;
        if (!up && !down) {
            int digit = roundedDigit(fractional, Long.MAX_VALUE);
            int lower = roundedDigit(fractional - (halfUlp >>> 1), -1L);
            if (digit < lower) {
                digit = lower;
            }
            return writeDecimal(bytes, pos, integral * 10 + digit, decimalExponent + adjustment);
        }
        return writeDecimal(bytes, pos, integral, decimalExponent + 1 + adjustment);
    }

    private static int roundedDigit(long fraction, long bias) {
        long low = fraction * 10;
        return (int) (unsignedMultiplyHigh(fraction, 10) + (Long.compareUnsigned(low + bias, low) < 0 ? 1 : 0));
    }

    // Convert eight decimal digits in parallel within one machine word, first digit in the lowest byte.
    private static long digits8(long value) {
        long groups4 = value + 4294957296L * ((value * 109951163L) >>> 40);
        long groups2 = groups4 + 65436L * (((groups4 * 5243L) >>> 19) & 0x7f0000007fL);
        long digits = groups2 + 246L * (((groups2 * 103L) >>> 10) & 0xf000f000f000fL);
        return Long.reverseBytes(digits);
    }

    private static int writeDecimal(byte[] bytes, int pos, long significand, int exponent) {
        int length = ((64 - Long.numberOfLeadingZeros(significand)) * 1233) >>> 12;
        length += significand >= POWERS[length] ? 1 : 0;
        long low;
        long high = 0;
        int highValue = 0;
        int trailing;
        if (length <= 8) {
            low = digits8(significand);
            trailing = Long.numberOfLeadingZeros(low) >>> 3;
        } else {
            highValue = (int) (significand / 100000000L);
            low = digits8(significand - (long) highValue * 100000000L);
            high = digits8(highValue % 100000000);
            trailing = low != 0
                    ? Long.numberOfLeadingZeros(low) >>> 3
                    : 8 + (Long.numberOfLeadingZeros(high) >>> 3);
        }
        int significant = length - trailing;
        int point = length + exponent;
        boolean scientific = point < -2 || point > 7;
        int digitsStart;
        if (scientific) {
            digitsStart = pos + 1;
        } else if (point <= 0) {
            bytes[pos++] = '0';
            bytes[pos++] = '.';
            for (int i = 0; i < -point; i++) {
                bytes[pos++] = '0';
            }
            digitsStart = pos;
        } else {
            digitsStart = pos;
        }
        if (length <= 8) {
            LE_LONG.set(bytes, digitsStart, (low | ASCII_ZEROES) >>> ((8 - length) * 8));
        } else {
            int count = length - 8;
            if (count == 9) {
                bytes[digitsStart] = (byte) ('0' + highValue / 100000000);
                LE_LONG.set(bytes, digitsStart + 1, high | ASCII_ZEROES);
            } else {
                LE_LONG.set(bytes, digitsStart, (high | ASCII_ZEROES) >>> ((8 - count) * 8));
            }
            LE_LONG.set(bytes, digitsStart + count, low | ASCII_ZEROES);
        }
        if (scientific) {
            bytes[pos] = bytes[pos + 1];
            bytes[pos + 1] = '.';
            int end = pos + significant + 1;
            if (significant == 1) {
                bytes[end++] = '0';
            }
            bytes[end++] = 'E';
            int scientificExponent = point - 1;
            if (scientificExponent < 0) {
                bytes[end++] = '-';
                scientificExponent = -scientificExponent;
            }
            if (scientificExponent >= 100) {
                bytes[end++] = (byte) ('0' + scientificExponent / 100);
                scientificExponent %= 100;
                bytes[end++] = (byte) ('0' + scientificExponent / 10);
            } else if (scientificExponent >= 10) {
                bytes[end++] = (byte) ('0' + scientificExponent / 10);
            }
            bytes[end++] = (byte) ('0' + scientificExponent % 10);
            return end;
        }
        if (point <= 0) {
            return digitsStart + significant;
        }
        if (point >= significant) {
            for (int i = significant; i < point; i++) {
                bytes[pos + i] = '0';
            }
            bytes[pos + point] = '.';
            bytes[pos + point + 1] = '0';
            return pos + point + 2;
        }
        System.arraycopy(bytes, pos + point, bytes, pos + point + 1, significant - point);
        bytes[pos + point] = '.';
        return pos + significant + 1;
    }

    private static final class Powers {
        // Each pair is the high/low halves of floor(10^e * 2^(127 - floor(log2(10^e)))), e in [-307, 341].
        // Generated with exact integer arithmetic; exponents are listed beside each pair.
        private static final long[] SIGNIFICANDS = {
                0x8fd0c16206306babL,
                0xa5d3b6d479f8e056L, // -307
                0xb3c4f1ba87bc8696L,
                0x8f48a4899877186cL, // -306
                0xe0b62e2929aba83cL,
                0x331acdabfe94de87L, // -305
                0x8c71dcd9ba0b4925L,
                0x9ff0c08b7f1d0b14L, // -304
                0xaf8e5410288e1b6fL,
                0x07ecf0ae5ee44dd9L, // -303
                0xdb71e91432b1a24aL,
                0xc9e82cd9f69d6150L, // -302
                0x892731ac9faf056eL,
                0xbe311c083a225cd2L, // -301
                0xab70fe17c79ac6caL,
                0x6dbd630a48aaf406L, // -300
                0xd64d3d9db981787dL,
                0x092cbbccdad5b108L, // -299
                0x85f0468293f0eb4eL,
                0x25bbf56008c58ea5L, // -298
                0xa76c582338ed2621L,
                0xaf2af2b80af6f24eL, // -297
                0xd1476e2c07286faaL,
                0x1af5af660db4aee1L, // -296
                0x82cca4db847945caL,
                0x50d98d9fc890ed4dL, // -295
                0xa37fce126597973cL,
                0xe50ff107bab528a0L, // -294
                0xcc5fc196fefd7d0cL,
                0x1e53ed49a96272c8L, // -293
                0xff77b1fcbebcdc4fL,
                0x25e8e89c13bb0f7aL, // -292
                0x9faacf3df73609b1L,
                0x77b191618c54e9acL, // -291
                0xc795830d75038c1dL,
                0xd59df5b9ef6a2417L, // -290
                0xf97ae3d0d2446f25L,
                0x4b0573286b44ad1dL, // -289
                0x9becce62836ac577L,
                0x4ee367f9430aec32L, // -288
                0xc2e801fb244576d5L,
                0x229c41f793cda73fL, // -287
                0xf3a20279ed56d48aL,
                0x6b43527578c1110fL, // -286
                0x9845418c345644d6L,
                0x830a13896b78aaa9L, // -285
                0xbe5691ef416bd60cL,
                0x23cc986bc656d553L, // -284
                0xedec366b11c6cb8fL,
                0x2cbfbe86b7ec8aa8L, // -283
                0x94b3a202eb1c3f39L,
                0x7bf7d71432f3d6a9L, // -282
                0xb9e08a83a5e34f07L,
                0xdaf5ccd93fb0cc53L, // -281
                0xe858ad248f5c22c9L,
                0xd1b3400f8f9cff68L, // -280
                0x91376c36d99995beL,
                0x23100809b9c21fa1L, // -279
                0xb58547448ffffb2dL,
                0xabd40a0c2832a78aL, // -278
                0xe2e69915b3fff9f9L,
                0x16c90c8f323f516cL, // -277
                0x8dd01fad907ffc3bL,
                0xae3da7d97f6792e3L, // -276
                0xb1442798f49ffb4aL,
                0x99cd11cfdf41779cL, // -275
                0xdd95317f31c7fa1dL,
                0x40405643d711d583L, // -274
                0x8a7d3eef7f1cfc52L,
                0x482835ea666b2572L, // -273
                0xad1c8eab5ee43b66L,
                0xda3243650005eecfL, // -272
                0xd863b256369d4a40L,
                0x90bed43e40076a82L, // -271
                0x873e4f75e2224e68L,
                0x5a7744a6e804a291L, // -270
                0xa90de3535aaae202L,
                0x711515d0a205cb36L, // -269
                0xd3515c2831559a83L,
                0x0d5a5b44ca873e03L, // -268
                0x8412d9991ed58091L,
                0xe858790afe9486c2L, // -267
                0xa5178fff668ae0b6L,
                0x626e974dbe39a872L, // -266
                0xce5d73ff402d98e3L,
                0xfb0a3d212dc8128fL, // -265
                0x80fa687f881c7f8eL,
                0x7ce66634bc9d0b99L, // -264
                0xa139029f6a239f72L,
                0x1c1fffc1ebc44e80L, // -263
                0xc987434744ac874eL,
                0xa327ffb266b56220L, // -262
                0xfbe9141915d7a922L,
                0x4bf1ff9f0062baa8L, // -261
                0x9d71ac8fada6c9b5L,
                0x6f773fc3603db4a9L, // -260
                0xc4ce17b399107c22L,
                0xcb550fb4384d21d3L, // -259
                0xf6019da07f549b2bL,
                0x7e2a53a146606a48L, // -258
                0x99c102844f94e0fbL,
                0x2eda7444cbfc426dL, // -257
                0xc0314325637a1939L,
                0xfa911155fefb5308L, // -256
                0xf03d93eebc589f88L,
                0x793555ab7eba27caL, // -255
                0x96267c7535b763b5L,
                0x4bc1558b2f3458deL, // -254
                0xbbb01b9283253ca2L,
                0x9eb1aaedfb016f16L, // -253
                0xea9c227723ee8bcbL,
                0x465e15a979c1cadcL, // -252
                0x92a1958a7675175fL,
                0x0bfacd89ec191ec9L, // -251
                0xb749faed14125d36L,
                0xcef980ec671f667bL, // -250
                0xe51c79a85916f484L,
                0x82b7e12780e7401aL, // -249
                0x8f31cc0937ae58d2L,
                0xd1b2ecb8b0908810L, // -248
                0xb2fe3f0b8599ef07L,
                0x861fa7e6dcb4aa15L, // -247
                0xdfbdcece67006ac9L,
                0x67a791e093e1d49aL, // -246
                0x8bd6a141006042bdL,
                0xe0c8bb2c5c6d24e0L, // -245
                0xaecc49914078536dL,
                0x58fae9f773886e18L, // -244
                0xda7f5bf590966848L,
                0xaf39a475506a899eL, // -243
                0x888f99797a5e012dL,
                0x6d8406c952429603L, // -242
                0xaab37fd7d8f58178L,
                0xc8e5087ba6d33b83L, // -241
                0xd5605fcdcf32e1d6L,
                0xfb1e4a9a90880a64L, // -240
                0x855c3be0a17fcd26L,
                0x5cf2eea09a55067fL, // -239
                0xa6b34ad8c9dfc06fL,
                0xf42faa48c0ea481eL, // -238
                0xd0601d8efc57b08bL,
                0xf13b94daf124da26L, // -237
                0x823c12795db6ce57L,
                0x76c53d08d6b70858L, // -236
                0xa2cb1717b52481edL,
                0x54768c4b0c64ca6eL, // -235
                0xcb7ddcdda26da268L,
                0xa9942f5dcf7dfd09L, // -234
                0xfe5d54150b090b02L,
                0xd3f93b35435d7c4cL, // -233
                0x9efa548d26e5a6e1L,
                0xc47bc5014a1a6dafL, // -232
                0xc6b8e9b0709f109aL,
                0x359ab6419ca1091bL, // -231
                0xf867241c8cc6d4c0L,
                0xc30163d203c94b62L, // -230
                0x9b407691d7fc44f8L,
                0x79e0de63425dcf1dL, // -229
                0xc21094364dfb5636L,
                0x985915fc12f542e4L, // -228
                0xf294b943e17a2bc4L,
                0x3e6f5b7b17b2939dL, // -227
                0x979cf3ca6cec5b5aL,
                0xa705992ceecf9c42L, // -226
                0xbd8430bd08277231L,
                0x50c6ff782a838353L, // -225
                0xece53cec4a314ebdL,
                0xa4f8bf5635246428L, // -224
                0x940f4613ae5ed136L,
                0x871b7795e136be99L, // -223
                0xb913179899f68584L,
                0x28e2557b59846e3fL, // -222
                0xe757dd7ec07426e5L,
                0x331aeada2fe589cfL, // -221
                0x9096ea6f3848984fL,
                0x3ff0d2c85def7621L, // -220
                0xb4bca50b065abe63L,
                0x0fed077a756b53a9L, // -219
                0xe1ebce4dc7f16dfbL,
                0xd3e8495912c62894L, // -218
                0x8d3360f09cf6e4bdL,
                0x64712dd7abbbd95cL, // -217
                0xb080392cc4349decL,
                0xbd8d794d96aacfb3L, // -216
                0xdca04777f541c567L,
                0xecf0d7a0fc5583a0L, // -215
                0x89e42caaf9491b60L,
                0xf41686c49db57244L, // -214
                0xac5d37d5b79b6239L,
                0x311c2875c522ced5L, // -213
                0xd77485cb25823ac7L,
                0x7d633293366b828bL, // -212
                0x86a8d39ef77164bcL,
                0xae5dff9c02033197L, // -211
                0xa8530886b54dbdebL,
                0xd9f57f830283fdfcL, // -210
                0xd267caa862a12d66L,
                0xd072df63c324fd7bL, // -209
                0x8380dea93da4bc60L,
                0x4247cb9e59f71e6dL, // -208
                0xa46116538d0deb78L,
                0x52d9be85f074e608L, // -207
                0xcd795be870516656L,
                0x67902e276c921f8bL, // -206
                0x806bd9714632dff6L,
                0x00ba1cd8a3db53b6L, // -205
                0xa086cfcd97bf97f3L,
                0x80e8a40eccd228a4L, // -204
                0xc8a883c0fdaf7df0L,
                0x6122cd128006b2cdL, // -203
                0xfad2a4b13d1b5d6cL,
                0x796b805720085f81L, // -202
                0x9cc3a6eec6311a63L,
                0xcbe3303674053bb0L, // -201
                0xc3f490aa77bd60fcL,
                0xbedbfc4411068a9cL, // -200
                0xf4f1b4d515acb93bL,
                0xee92fb5515482d44L, // -199
                0x991711052d8bf3c5L,
                0x751bdd152d4d1c4aL, // -198
                0xbf5cd54678eef0b6L,
                0xd262d45a78a0635dL, // -197
                0xef340a98172aace4L,
                0x86fb897116c87c34L, // -196
                0x9580869f0e7aac0eL,
                0xd45d35e6ae3d4da0L, // -195
                0xbae0a846d2195712L,
                0x8974836059cca109L, // -194
                0xe998d258869facd7L,
                0x2bd1a438703fc94bL, // -193
                0x91ff83775423cc06L,
                0x7b6306a34627ddcfL, // -192
                0xb67f6455292cbf08L,
                0x1a3bc84c17b1d542L, // -191
                0xe41f3d6a7377eecaL,
                0x20caba5f1d9e4a93L, // -190
                0x8e938662882af53eL,
                0x547eb47b7282ee9cL, // -189
                0xb23867fb2a35b28dL,
                0xe99e619a4f23aa43L, // -188
                0xdec681f9f4c31f31L,
                0x6405fa00e2ec94d4L, // -187
                0x8b3c113c38f9f37eL,
                0xde83bc408dd3dd04L, // -186
                0xae0b158b4738705eL,
                0x9624ab50b148d445L, // -185
                0xd98ddaee19068c76L,
                0x3badd624dd9b0957L, // -184
                0x87f8a8d4cfa417c9L,
                0xe54ca5d70a80e5d6L, // -183
                0xa9f6d30a038d1dbcL,
                0x5e9fcf4ccd211f4cL, // -182
                0xd47487cc8470652bL,
                0x7647c3200069671fL, // -181
                0x84c8d4dfd2c63f3bL,
                0x29ecd9f40041e073L, // -180
                0xa5fb0a17c777cf09L,
                0xf468107100525890L, // -179
                0xcf79cc9db955c2ccL,
                0x7182148d4066eeb4L, // -178
                0x81ac1fe293d599bfL,
                0xc6f14cd848405530L, // -177
                0xa21727db38cb002fL,
                0xb8ada00e5a506a7cL, // -176
                0xca9cf1d206fdc03bL,
                0xa6d90811f0e4851cL, // -175
                0xfd442e4688bd304aL,
                0x908f4a166d1da663L, // -174
                0x9e4a9cec15763e2eL,
                0x9a598e4e043287feL, // -173
                0xc5dd44271ad3cdbaL,
                0x40eff1e1853f29fdL, // -172
                0xf7549530e188c128L,
                0xd12bee59e68ef47cL, // -171
                0x9a94dd3e8cf578b9L,
                0x82bb74f8301958ceL, // -170
                0xc13a148e3032d6e7L,
                0xe36a52363c1faf01L, // -169
                0xf18899b1bc3f8ca1L,
                0xdc44e6c3cb279ac1L, // -168
                0x96f5600f15a7b7e5L,
                0x29ab103a5ef8c0b9L, // -167
                0xbcb2b812db11a5deL,
                0x7415d448f6b6f0e7L, // -166
                0xebdf661791d60f56L,
                0x111b495b3464ad21L, // -165
                0x936b9fcebb25c995L,
                0xcab10dd900beec34L, // -164
                0xb84687c269ef3bfbL,
                0x3d5d514f40eea742L, // -163
                0xe65829b3046b0afaL,
                0x0cb4a5a3112a5112L, // -162
                0x8ff71a0fe2c2e6dcL,
                0x47f0e785eaba72abL, // -161
                0xb3f4e093db73a093L,
                0x59ed216765690f56L, // -160
                0xe0f218b8d25088b8L,
                0x306869c13ec3532cL, // -159
                0x8c974f7383725573L,
                0x1e414218c73a13fbL, // -158
                0xafbd2350644eeacfL,
                0xe5d1929ef90898faL, // -157
                0xdbac6c247d62a583L,
                0xdf45f746b74abf39L, // -156
                0x894bc396ce5da772L,
                0x6b8bba8c328eb783L, // -155
                0xab9eb47c81f5114fL,
                0x066ea92f3f326564L, // -154
                0xd686619ba27255a2L,
                0xc80a537b0efefebdL, // -153
                0x8613fd0145877585L,
                0xbd06742ce95f5f36L, // -152
                0xa798fc4196e952e7L,
                0x2c48113823b73704L, // -151
                0xd17f3b51fca3a7a0L,
                0xf75a15862ca504c5L, // -150
                0x82ef85133de648c4L,
                0x9a984d73dbe722fbL, // -149
                0xa3ab66580d5fdaf5L,
                0xc13e60d0d2e0ebbaL, // -148
                0xcc963fee10b7d1b3L,
                0x318df905079926a8L, // -147
                0xffbbcfe994e5c61fL,
                0xfdf17746497f7052L, // -146
                0x9fd561f1fd0f9bd3L,
                0xfeb6ea8bedefa633L, // -145
                0xc7caba6e7c5382c8L,
                0xfe64a52ee96b8fc0L, // -144
                0xf9bd690a1b68637bL,
                0x3dfdce7aa3c673b0L, // -143
                0x9c1661a651213e2dL,
                0x06bea10ca65c084eL, // -142
                0xc31bfa0fe5698db8L,
                0x486e494fcff30a62L, // -141
                0xf3e2f893dec3f126L,
                0x5a89dba3c3efccfaL, // -140
                0x986ddb5c6b3a76b7L,
                0xf89629465a75e01cL, // -139
                0xbe89523386091465L,
                0xf6bbb397f1135823L, // -138
                0xee2ba6c0678b597fL,
                0x746aa07ded582e2cL, // -137
                0x94db483840b717efL,
                0xa8c2a44eb4571cdcL, // -136
                0xba121a4650e4ddebL,
                0x92f34d62616ce413L, // -135
                0xe896a0d7e51e1566L,
                0x77b020baf9c81d17L, // -134
                0x915e2486ef32cd60L,
                0x0ace1474dc1d122eL, // -133
                0xb5b5ada8aaff80b8L,
                0x0d819992132456baL, // -132
                0xe3231912d5bf60e6L,
                0x10e1fff697ed6c69L, // -131
                0x8df5efabc5979c8fL,
                0xca8d3ffa1ef463c1L, // -130
                0xb1736b96b6fd83b3L,
                0xbd308ff8a6b17cb2L, // -129
                0xddd0467c64bce4a0L,
                0xac7cb3f6d05ddbdeL, // -128
                0x8aa22c0dbef60ee4L,
                0x6bcdf07a423aa96bL, // -127
                0xad4ab7112eb3929dL,
                0x86c16c98d2c953c6L, // -126
                0xd89d64d57a607744L,
                0xe871c7bf077ba8b7L, // -125
                0x87625f056c7c4a8bL,
                0x11471cd764ad4972L, // -124
                0xa93af6c6c79b5d2dL,
                0xd598e40d3dd89bcfL, // -123
                0xd389b47879823479L,
                0x4aff1d108d4ec2c3L, // -122
                0x843610cb4bf160cbL,
                0xcedf722a585139baL, // -121
                0xa54394fe1eedb8feL,
                0xc2974eb4ee658828L, // -120
                0xce947a3da6a9273eL,
                0x733d226229feea32L, // -119
                0x811ccc668829b887L,
                0x0806357d5a3f525fL, // -118
                0xa163ff802a3426a8L,
                0xca07c2dcb0cf26f7L, // -117
                0xc9bcff6034c13052L,
                0xfc89b393dd02f0b5L, // -116
                0xfc2c3f3841f17c67L,
                0xbbac2078d443ace2L, // -115
                0x9d9ba7832936edc0L,
                0xd54b944b84aa4c0dL, // -114
                0xc5029163f384a931L,
                0x0a9e795e65d4df11L, // -113
                0xf64335bcf065d37dL,
                0x4d4617b5ff4a16d5L, // -112
                0x99ea0196163fa42eL,
                0x504bced1bf8e4e45L, // -111
                0xc06481fb9bcf8d39L,
                0xe45ec2862f71e1d6L, // -110
                0xf07da27a82c37088L,
                0x5d767327bb4e5a4cL, // -109
                0x964e858c91ba2655L,
                0x3a6a07f8d510f86fL, // -108
                0xbbe226efb628afeaL,
                0x890489f70a55368bL, // -107
                0xeadab0aba3b2dbe5L,
                0x2b45ac74ccea842eL, // -106
                0x92c8ae6b464fc96fL,
                0x3b0b8bc90012929dL, // -105
                0xb77ada0617e3bbcbL,
                0x09ce6ebb40173744L, // -104
                0xe55990879ddcaabdL,
                0xcc420a6a101d0515L, // -103
                0x8f57fa54c2a9eab6L,
                0x9fa946824a12232dL, // -102
                0xb32df8e9f3546564L,
                0x47939822dc96abf9L, // -101
                0xdff9772470297ebdL,
                0x59787e2b93bc56f7L, // -100
                0x8bfbea76c619ef36L,
                0x57eb4edb3c55b65aL, // -99
                0xaefae51477a06b03L,
                0xede622920b6b23f1L, // -98
                0xdab99e59958885c4L,
                0xe95fab368e45ecedL, // -97
                0x88b402f7fd75539bL,
                0x11dbcb0218ebb414L, // -96
                0xaae103b5fcd2a881L,
                0xd652bdc29f26a119L, // -95
                0xd59944a37c0752a2L,
                0x4be76d3346f0495fL, // -94
                0x857fcae62d8493a5L,
                0x6f70a4400c562ddbL, // -93
                0xa6dfbd9fb8e5b88eL,
                0xcb4ccd500f6bb952L, // -92
                0xd097ad07a71f26b2L,
                0x7e2000a41346a7a7L, // -91
                0x825ecc24c873782fL,
                0x8ed400668c0c28c8L, // -90
                0xa2f67f2dfa90563bL,
                0x728900802f0f32faL, // -89
                0xcbb41ef979346bcaL,
                0x4f2b40a03ad2ffb9L, // -88
                0xfea126b7d78186bcL,
                0xe2f610c84987bfa8L, // -87
                0x9f24b832e6b0f436L,
                0x0dd9ca7d2df4d7c9L, // -86
                0xc6ede63fa05d3143L,
                0x91503d1c79720dbbL, // -85
                0xf8a95fcf88747d94L,
                0x75a44c6397ce912aL, // -84
                0x9b69dbe1b548ce7cL,
                0xc986afbe3ee11abaL, // -83
                0xc24452da229b021bL,
                0xfbe85badce996168L, // -82
                0xf2d56790ab41c2a2L,
                0xfae27299423fb9c3L, // -81
                0x97c560ba6b0919a5L,
                0xdccd879fc967d41aL, // -80
                0xbdb6b8e905cb600fL,
                0x5400e987bbc1c920L, // -79
                0xed246723473e3813L,
                0x290123e9aab23b68L, // -78
                0x9436c0760c86e30bL,
                0xf9a0b6720aaf6521L, // -77
                0xb94470938fa89bceL,
                0xf808e40e8d5b3e69L, // -76
                0xe7958cb87392c2c2L,
                0xb60b1d1230b20e04L, // -75
                0x90bd77f3483bb9b9L,
                0xb1c6f22b5e6f48c2L, // -74
                0xb4ecd5f01a4aa828L,
                0x1e38aeb6360b1af3L, // -73
                0xe2280b6c20dd5232L,
                0x25c6da63c38de1b0L, // -72
                0x8d590723948a535fL,
                0x579c487e5a38ad0eL, // -71
                0xb0af48ec79ace837L,
                0x2d835a9df0c6d851L, // -70
                0xdcdb1b2798182244L,
                0xf8e431456cf88e65L, // -69
                0x8a08f0f8bf0f156bL,
                0x1b8e9ecb641b58ffL, // -68
                0xac8b2d36eed2dac5L,
                0xe272467e3d222f3fL, // -67
                0xd7adf884aa879177L,
                0x5b0ed81dcc6abb0fL, // -66
                0x86ccbb52ea94baeaL,
                0x98e947129fc2b4e9L, // -65
                0xa87fea27a539e9a5L,
                0x3f2398d747b36224L, // -64
                0xd29fe4b18e88640eL,
                0x8eec7f0d19a03aadL, // -63
                0x83a3eeeef9153e89L,
                0x1953cf68300424acL, // -62
                0xa48ceaaab75a8e2bL,
                0x5fa8c3423c052dd7L, // -61
                0xcdb02555653131b6L,
                0x3792f412cb06794dL, // -60
                0x808e17555f3ebf11L,
                0xe2bbd88bbee40bd0L, // -59
                0xa0b19d2ab70e6ed6L,
                0x5b6aceaeae9d0ec4L, // -58
                0xc8de047564d20a8bL,
                0xf245825a5a445275L, // -57
                0xfb158592be068d2eL,
                0xeed6e2f0f0d56712L, // -56
                0x9ced737bb6c4183dL,
                0x55464dd69685606bL, // -55
                0xc428d05aa4751e4cL,
                0xaa97e14c3c26b886L, // -54
                0xf53304714d9265dfL,
                0xd53dd99f4b3066a8L, // -53
                0x993fe2c6d07b7fabL,
                0xe546a8038efe4029L, // -52
                0xbf8fdb78849a5f96L,
                0xde98520472bdd033L, // -51
                0xef73d256a5c0f77cL,
                0x963e66858f6d4440L, // -50
                0x95a8637627989aadL,
                0xdde7001379a44aa8L, // -49
                0xbb127c53b17ec159L,
                0x5560c018580d5d52L, // -48
                0xe9d71b689dde71afL,
                0xaab8f01e6e10b4a6L, // -47
                0x9226712162ab070dL,
                0xcab3961304ca70e8L, // -46
                0xb6b00d69bb55c8d1L,
                0x3d607b97c5fd0d22L, // -45
                0xe45c10c42a2b3b05L,
                0x8cb89a7db77c506aL, // -44
                0x8eb98a7a9a5b04e3L,
                0x77f3608e92adb242L, // -43
                0xb267ed1940f1c61cL,
                0x55f038b237591ed3L, // -42
                0xdf01e85f912e37a3L,
                0x6b6c46dec52f6688L, // -41
                0x8b61313bbabce2c6L,
                0x2323ac4b3b3da015L, // -40
                0xae397d8aa96c1b77L,
                0xabec975e0a0d081aL, // -39
                0xd9c7dced53c72255L,
                0x96e7bd358c904a21L, // -38
                0x881cea14545c7575L,
                0x7e50d64177da2e54L, // -37
                0xaa242499697392d2L,
                0xdde50bd1d5d0b9e9L, // -36
                0xd4ad2dbfc3d07787L,
                0x955e4ec64b44e864L, // -35
                0x84ec3c97da624ab4L,
                0xbd5af13bef0b113eL, // -34
                0xa6274bbdd0fadd61L,
                0xecb1ad8aeacdd58eL, // -33
                0xcfb11ead453994baL,
                0x67de18eda5814af2L, // -32
                0x81ceb32c4b43fcf4L,
                0x80eacf948770ced7L, // -31
                0xa2425ff75e14fc31L,
                0xa1258379a94d028dL, // -30
                0xcad2f7f5359a3b3eL,
                0x096ee45813a04330L, // -29
                0xfd87b5f28300ca0dL,
                0x8bca9d6e188853fcL, // -28
                0x9e74d1b791e07e48L,
                0x775ea264cf55347dL, // -27
                0xc612062576589ddaL,
                0x95364afe032a819dL, // -26
                0xf79687aed3eec551L,
                0x3a83ddbd83f52204L, // -25
                0x9abe14cd44753b52L,
                0xc4926a9672793542L, // -24
                0xc16d9a0095928a27L,
                0x75b7053c0f178293L, // -23
                0xf1c90080baf72cb1L,
                0x5324c68b12dd6338L, // -22
                0x971da05074da7beeL,
                0xd3f6fc16ebca5e03L, // -21
                0xbce5086492111aeaL,
                0x88f4bb1ca6bcf584L, // -20
                0xec1e4a7db69561a5L,
                0x2b31e9e3d06c32e5L, // -19
                0x9392ee8e921d5d07L,
                0x3aff322e62439fcfL, // -18
                0xb877aa3236a4b449L,
                0x09befeb9fad487c2L, // -17
                0xe69594bec44de15bL,
                0x4c2ebe687989a9b3L, // -16
                0x901d7cf73ab0acd9L,
                0x0f9d37014bf60a10L, // -15
                0xb424dc35095cd80fL,
                0x538484c19ef38c94L, // -14
                0xe12e13424bb40e13L,
                0x2865a5f206b06fb9L, // -13
                0x8cbccc096f5088cbL,
                0xf93f87b7442e45d3L, // -12
                0xafebff0bcb24aafeL,
                0xf78f69a51539d748L, // -11
                0xdbe6fecebdedd5beL,
                0xb573440e5a884d1bL, // -10
                0x89705f4136b4a597L,
                0x31680a88f8953030L, // -9
                0xabcc77118461cefcL,
                0xfdc20d2b36ba7c3dL, // -8
                0xd6bf94d5e57a42bcL,
                0x3d32907604691b4cL, // -7
                0x8637bd05af6c69b5L,
                0xa63f9a49c2c1b10fL, // -6
                0xa7c5ac471b478423L,
                0x0fcf80dc33721d53L, // -5
                0xd1b71758e219652bL,
                0xd3c36113404ea4a8L, // -4
                0x83126e978d4fdf3bL,
                0x645a1cac083126e9L, // -3
                0xa3d70a3d70a3d70aL,
                0x3d70a3d70a3d70a3L, // -2
                0xccccccccccccccccL,
                0xccccccccccccccccL, // -1
                0x8000000000000000L,
                0x0000000000000000L, // 0
                0xa000000000000000L,
                0x0000000000000000L, // 1
                0xc800000000000000L,
                0x0000000000000000L, // 2
                0xfa00000000000000L,
                0x0000000000000000L, // 3
                0x9c40000000000000L,
                0x0000000000000000L, // 4
                0xc350000000000000L,
                0x0000000000000000L, // 5
                0xf424000000000000L,
                0x0000000000000000L, // 6
                0x9896800000000000L,
                0x0000000000000000L, // 7
                0xbebc200000000000L,
                0x0000000000000000L, // 8
                0xee6b280000000000L,
                0x0000000000000000L, // 9
                0x9502f90000000000L,
                0x0000000000000000L, // 10
                0xba43b74000000000L,
                0x0000000000000000L, // 11
                0xe8d4a51000000000L,
                0x0000000000000000L, // 12
                0x9184e72a00000000L,
                0x0000000000000000L, // 13
                0xb5e620f480000000L,
                0x0000000000000000L, // 14
                0xe35fa931a0000000L,
                0x0000000000000000L, // 15
                0x8e1bc9bf04000000L,
                0x0000000000000000L, // 16
                0xb1a2bc2ec5000000L,
                0x0000000000000000L, // 17
                0xde0b6b3a76400000L,
                0x0000000000000000L, // 18
                0x8ac7230489e80000L,
                0x0000000000000000L, // 19
                0xad78ebc5ac620000L,
                0x0000000000000000L, // 20
                0xd8d726b7177a8000L,
                0x0000000000000000L, // 21
                0x878678326eac9000L,
                0x0000000000000000L, // 22
                0xa968163f0a57b400L,
                0x0000000000000000L, // 23
                0xd3c21bcecceda100L,
                0x0000000000000000L, // 24
                0x84595161401484a0L,
                0x0000000000000000L, // 25
                0xa56fa5b99019a5c8L,
                0x0000000000000000L, // 26
                0xcecb8f27f4200f3aL,
                0x0000000000000000L, // 27
                0x813f3978f8940984L,
                0x4000000000000000L, // 28
                0xa18f07d736b90be5L,
                0x5000000000000000L, // 29
                0xc9f2c9cd04674edeL,
                0xa400000000000000L, // 30
                0xfc6f7c4045812296L,
                0x4d00000000000000L, // 31
                0x9dc5ada82b70b59dL,
                0xf020000000000000L, // 32
                0xc5371912364ce305L,
                0x6c28000000000000L, // 33
                0xf684df56c3e01bc6L,
                0xc732000000000000L, // 34
                0x9a130b963a6c115cL,
                0x3c7f400000000000L, // 35
                0xc097ce7bc90715b3L,
                0x4b9f100000000000L, // 36
                0xf0bdc21abb48db20L,
                0x1e86d40000000000L, // 37
                0x96769950b50d88f4L,
                0x1314448000000000L, // 38
                0xbc143fa4e250eb31L,
                0x17d955a000000000L, // 39
                0xeb194f8e1ae525fdL,
                0x5dcfab0800000000L, // 40
                0x92efd1b8d0cf37beL,
                0x5aa1cae500000000L, // 41
                0xb7abc627050305adL,
                0xf14a3d9e40000000L, // 42
                0xe596b7b0c643c719L,
                0x6d9ccd05d0000000L, // 43
                0x8f7e32ce7bea5c6fL,
                0xe4820023a2000000L, // 44
                0xb35dbf821ae4f38bL,
                0xdda2802c8a800000L, // 45
                0xe0352f62a19e306eL,
                0xd50b2037ad200000L, // 46
                0x8c213d9da502de45L,
                0x4526f422cc340000L, // 47
                0xaf298d050e4395d6L,
                0x9670b12b7f410000L, // 48
                0xdaf3f04651d47b4cL,
                0x3c0cdd765f114000L, // 49
                0x88d8762bf324cd0fL,
                0xa5880a69fb6ac800L, // 50
                0xab0e93b6efee0053L,
                0x8eea0d047a457a00L, // 51
                0xd5d238a4abe98068L,
                0x72a4904598d6d880L, // 52
                0x85a36366eb71f041L,
                0x47a6da2b7f864750L, // 53
                0xa70c3c40a64e6c51L,
                0x999090b65f67d924L, // 54
                0xd0cf4b50cfe20765L,
                0xfff4b4e3f741cf6dL, // 55
                0x82818f1281ed449fL,
                0xbff8f10e7a8921a4L, // 56
                0xa321f2d7226895c7L,
                0xaff72d52192b6a0dL, // 57
                0xcbea6f8ceb02bb39L,
                0x9bf4f8a69f764490L, // 58
                0xfee50b7025c36a08L,
                0x02f236d04753d5b4L, // 59
                0x9f4f2726179a2245L,
                0x01d762422c946590L, // 60
                0xc722f0ef9d80aad6L,
                0x424d3ad2b7b97ef5L, // 61
                0xf8ebad2b84e0d58bL,
                0xd2e0898765a7deb2L, // 62
                0x9b934c3b330c8577L,
                0x63cc55f49f88eb2fL, // 63
                0xc2781f49ffcfa6d5L,
                0x3cbf6b71c76b25fbL, // 64
                0xf316271c7fc3908aL,
                0x8bef464e3945ef7aL, // 65
                0x97edd871cfda3a56L,
                0x97758bf0e3cbb5acL, // 66
                0xbde94e8e43d0c8ecL,
                0x3d52eeed1cbea317L, // 67
                0xed63a231d4c4fb27L,
                0x4ca7aaa863ee4bddL, // 68
                0x945e455f24fb1cf8L,
                0x8fe8caa93e74ef6aL, // 69
                0xb975d6b6ee39e436L,
                0xb3e2fd538e122b44L, // 70
                0xe7d34c64a9c85d44L,
                0x60dbbca87196b616L, // 71
                0x90e40fbeea1d3a4aL,
                0xbc8955e946fe31cdL, // 72
                0xb51d13aea4a488ddL,
                0x6babab6398bdbe41L, // 73
                0xe264589a4dcdab14L,
                0xc696963c7eed2dd1L, // 74
                0x8d7eb76070a08aecL,
                0xfc1e1de5cf543ca2L, // 75
                0xb0de65388cc8ada8L,
                0x3b25a55f43294bcbL, // 76
                0xdd15fe86affad912L,
                0x49ef0eb713f39ebeL, // 77
                0x8a2dbf142dfcc7abL,
                0x6e3569326c784337L, // 78
                0xacb92ed9397bf996L,
                0x49c2c37f07965404L, // 79
                0xd7e77a8f87daf7fbL,
                0xdc33745ec97be906L, // 80
                0x86f0ac99b4e8dafdL,
                0x69a028bb3ded71a3L, // 81
                0xa8acd7c0222311bcL,
                0xc40832ea0d68ce0cL, // 82
                0xd2d80db02aabd62bL,
                0xf50a3fa490c30190L, // 83
                0x83c7088e1aab65dbL,
                0x792667c6da79e0faL, // 84
                0xa4b8cab1a1563f52L,
                0x577001b891185938L, // 85
                0xcde6fd5e09abcf26L,
                0xed4c0226b55e6f86L, // 86
                0x80b05e5ac60b6178L,
                0x544f8158315b05b4L, // 87
                0xa0dc75f1778e39d6L,
                0x696361ae3db1c721L, // 88
                0xc913936dd571c84cL,
                0x03bc3a19cd1e38e9L, // 89
                0xfb5878494ace3a5fL,
                0x04ab48a04065c723L, // 90
                0x9d174b2dcec0e47bL,
                0x62eb0d64283f9c76L, // 91
                0xc45d1df942711d9aL,
                0x3ba5d0bd324f8394L, // 92
                0xf5746577930d6500L,
                0xca8f44ec7ee36479L, // 93
                0x9968bf6abbe85f20L,
                0x7e998b13cf4e1ecbL, // 94
                0xbfc2ef456ae276e8L,
                0x9e3fedd8c321a67eL, // 95
                0xefb3ab16c59b14a2L,
                0xc5cfe94ef3ea101eL, // 96
                0x95d04aee3b80ece5L,
                0xbba1f1d158724a12L, // 97
                0xbb445da9ca61281fL,
                0x2a8a6e45ae8edc97L, // 98
                0xea1575143cf97226L,
                0xf52d09d71a3293bdL, // 99
                0x924d692ca61be758L,
                0x593c2626705f9c56L, // 100
                0xb6e0c377cfa2e12eL,
                0x6f8b2fb00c77836cL, // 101
                0xe498f455c38b997aL,
                0x0b6dfb9c0f956447L, // 102
                0x8edf98b59a373fecL,
                0x4724bd4189bd5eacL, // 103
                0xb2977ee300c50fe7L,
                0x58edec91ec2cb657L, // 104
                0xdf3d5e9bc0f653e1L,
                0x2f2967b66737e3edL, // 105
                0x8b865b215899f46cL,
                0xbd79e0d20082ee74L, // 106
                0xae67f1e9aec07187L,
                0xecd8590680a3aa11L, // 107
                0xda01ee641a708de9L,
                0xe80e6f4820cc9495L, // 108
                0x884134fe908658b2L,
                0x3109058d147fdcddL, // 109
                0xaa51823e34a7eedeL,
                0xbd4b46f0599fd415L, // 110
                0xd4e5e2cdc1d1ea96L,
                0x6c9e18ac7007c91aL, // 111
                0x850fadc09923329eL,
                0x03e2cf6bc604ddb0L, // 112
                0xa6539930bf6bff45L,
                0x84db8346b786151cL, // 113
                0xcfe87f7cef46ff16L,
                0xe612641865679a63L, // 114
                0x81f14fae158c5f6eL,
                0x4fcb7e8f3f60c07eL, // 115
                0xa26da3999aef7749L,
                0xe3be5e330f38f09dL, // 116
                0xcb090c8001ab551cL,
                0x5cadf5bfd3072cc5L, // 117
                0xfdcb4fa002162a63L,
                0x73d9732fc7c8f7f6L, // 118
                0x9e9f11c4014dda7eL,
                0x2867e7fddcdd9afaL, // 119
                0xc646d63501a1511dL,
                0xb281e1fd541501b8L, // 120
                0xf7d88bc24209a565L,
                0x1f225a7ca91a4226L, // 121
                0x9ae757596946075fL,
                0x3375788de9b06958L, // 122
                0xc1a12d2fc3978937L,
                0x0052d6b1641c83aeL, // 123
                0xf209787bb47d6b84L,
                0xc0678c5dbd23a49aL, // 124
                0x9745eb4d50ce6332L,
                0xf840b7ba963646e0L, // 125
                0xbd176620a501fbffL,
                0xb650e5a93bc3d898L, // 126
                0xec5d3fa8ce427affL,
                0xa3e51f138ab4cebeL, // 127
                0x93ba47c980e98cdfL,
                0xc66f336c36b10137L, // 128
                0xb8a8d9bbe123f017L,
                0xb80b0047445d4184L, // 129
                0xe6d3102ad96cec1dL,
                0xa60dc059157491e5L, // 130
                0x9043ea1ac7e41392L,
                0x87c89837ad68db2fL, // 131
                0xb454e4a179dd1877L,
                0x29babe4598c311fbL, // 132
                0xe16a1dc9d8545e94L,
                0xf4296dd6fef3d67aL, // 133
                0x8ce2529e2734bb1dL,
                0x1899e4a65f58660cL, // 134
                0xb01ae745b101e9e4L,
                0x5ec05dcff72e7f8fL, // 135
                0xdc21a1171d42645dL,
                0x76707543f4fa1f73L, // 136
                0x899504ae72497ebaL,
                0x6a06494a791c53a8L, // 137
                0xabfa45da0edbde69L,
                0x0487db9d17636892L, // 138
                0xd6f8d7509292d603L,
                0x45a9d2845d3c42b6L, // 139
                0x865b86925b9bc5c2L,
                0x0b8a2392ba45a9b2L, // 140
                0xa7f26836f282b732L,
                0x8e6cac7768d7141eL, // 141
                0xd1ef0244af2364ffL,
                0x3207d795430cd926L, // 142
                0x8335616aed761f1fL,
                0x7f44e6bd49e807b8L, // 143
                0xa402b9c5a8d3a6e7L,
                0x5f16206c9c6209a6L, // 144
                0xcd036837130890a1L,
                0x36dba887c37a8c0fL, // 145
                0x802221226be55a64L,
                0xc2494954da2c9789L, // 146
                0xa02aa96b06deb0fdL,
                0xf2db9baa10b7bd6cL, // 147
                0xc83553c5c8965d3dL,
                0x6f92829494e5acc7L, // 148
                0xfa42a8b73abbf48cL,
                0xcb772339ba1f17f9L, // 149
                0x9c69a97284b578d7L,
                0xff2a760414536efbL, // 150
                0xc38413cf25e2d70dL,
                0xfef5138519684abaL, // 151
                0xf46518c2ef5b8cd1L,
                0x7eb258665fc25d69L, // 152
                0x98bf2f79d5993802L,
                0xef2f773ffbd97a61L, // 153
                0xbeeefb584aff8603L,
                0xaafb550ffacfd8faL, // 154
                0xeeaaba2e5dbf6784L,
                0x95ba2a53f983cf38L, // 155
                0x952ab45cfa97a0b2L,
                0xdd945a747bf26183L, // 156
                0xba756174393d88dfL,
                0x94f971119aeef9e4L, // 157
                0xe912b9d1478ceb17L,
                0x7a37cd5601aab85dL, // 158
                0x91abb422ccb812eeL,
                0xac62e055c10ab33aL, // 159
                0xb616a12b7fe617aaL,
                0x577b986b314d6009L, // 160
                0xe39c49765fdf9d94L,
                0xed5a7e85fda0b80bL, // 161
                0x8e41ade9fbebc27dL,
                0x14588f13be847307L, // 162
                0xb1d219647ae6b31cL,
                0x596eb2d8ae258fc8L, // 163
                0xde469fbd99a05fe3L,
                0x6fca5f8ed9aef3bbL, // 164
                0x8aec23d680043beeL,
                0x25de7bb9480d5854L, // 165
                0xada72ccc20054ae9L,
                0xaf561aa79a10ae6aL, // 166
                0xd910f7ff28069da4L,
                0x1b2ba1518094da04L, // 167
                0x87aa9aff79042286L,
                0x90fb44d2f05d0842L, // 168
                0xa99541bf57452b28L,
                0x353a1607ac744a53L, // 169
                0xd3fa922f2d1675f2L,
                0x42889b8997915ce8L, // 170
                0x847c9b5d7c2e09b7L,
                0x69956135febada11L, // 171
                0xa59bc234db398c25L,
                0x43fab9837e699095L, // 172
                0xcf02b2c21207ef2eL,
                0x94f967e45e03f4bbL, // 173
                0x8161afb94b44f57dL,
                0x1d1be0eebac278f5L, // 174
                0xa1ba1ba79e1632dcL,
                0x6462d92a69731732L, // 175
                0xca28a291859bbf93L,
                0x7d7b8f7503cfdcfeL, // 176
                0xfcb2cb35e702af78L,
                0x5cda735244c3d43eL, // 177
                0x9defbf01b061adabL,
                0x3a0888136afa64a7L, // 178
                0xc56baec21c7a1916L,
                0x088aaa1845b8fdd0L, // 179
                0xf6c69a72a3989f5bL,
                0x8aad549e57273d45L, // 180
                0x9a3c2087a63f6399L,
                0x36ac54e2f678864bL, // 181
                0xc0cb28a98fcf3c7fL,
                0x84576a1bb416a7ddL, // 182
                0xf0fdf2d3f3c30b9fL,
                0x656d44a2a11c51d5L, // 183
                0x969eb7c47859e743L,
                0x9f644ae5a4b1b325L, // 184
                0xbc4665b596706114L,
                0x873d5d9f0dde1feeL, // 185
                0xeb57ff22fc0c7959L,
                0xa90cb506d155a7eaL, // 186
                0x9316ff75dd87cbd8L,
                0x09a7f12442d588f2L, // 187
                0xb7dcbf5354e9beceL,
                0x0c11ed6d538aeb2fL, // 188
                0xe5d3ef282a242e81L,
                0x8f1668c8a86da5faL, // 189
                0x8fa475791a569d10L,
                0xf96e017d694487bcL, // 190
                0xb38d92d760ec4455L,
                0x37c981dcc395a9acL, // 191
                0xe070f78d3927556aL,
                0x85bbe253f47b1417L, // 192
                0x8c469ab843b89562L,
                0x93956d7478ccec8eL, // 193
                0xaf58416654a6babbL,
                0x387ac8d1970027b2L, // 194
                0xdb2e51bfe9d0696aL,
                0x06997b05fcc0319eL, // 195
                0x88fcf317f22241e2L,
                0x441fece3bdf81f03L, // 196
                0xab3c2fddeeaad25aL,
                0xd527e81cad7626c3L, // 197
                0xd60b3bd56a5586f1L,
                0x8a71e223d8d3b074L, // 198
                0x85c7056562757456L,
                0xf6872d5667844e49L, // 199
                0xa738c6bebb12d16cL,
                0xb428f8ac016561dbL, // 200
                0xd106f86e69d785c7L,
                0xe13336d701beba52L, // 201
                0x82a45b450226b39cL,
                0xecc0024661173473L, // 202
                0xa34d721642b06084L,
                0x27f002d7f95d0190L, // 203
                0xcc20ce9bd35c78a5L,
                0x31ec038df7b441f4L, // 204
                0xff290242c83396ceL,
                0x7e67047175a15271L, // 205
                0x9f79a169bd203e41L,
                0x0f0062c6e984d386L, // 206
                0xc75809c42c684dd1L,
                0x52c07b78a3e60868L, // 207
                0xf92e0c3537826145L,
                0xa7709a56ccdf8a82L, // 208
                0x9bbcc7a142b17ccbL,
                0x88a66076400bb691L, // 209
                0xc2abf989935ddbfeL,
                0x6acff893d00ea435L, // 210
                0xf356f7ebf83552feL,
                0x0583f6b8c4124d43L, // 211
                0x98165af37b2153deL,
                0xc3727a337a8b704aL, // 212
                0xbe1bf1b059e9a8d6L,
                0x744f18c0592e4c5cL, // 213
                0xeda2ee1c7064130cL,
                0x1162def06f79df73L, // 214
                0x9485d4d1c63e8be7L,
                0x8addcb5645ac2ba8L, // 215
                0xb9a74a0637ce2ee1L,
                0x6d953e2bd7173692L, // 216
                0xe8111c87c5c1ba99L,
                0xc8fa8db6ccdd0437L, // 217
                0x910ab1d4db9914a0L,
                0x1d9c9892400a22a2L, // 218
                0xb54d5e4a127f59c8L,
                0x2503beb6d00cab4bL, // 219
                0xe2a0b5dc971f303aL,
                0x2e44ae64840fd61dL, // 220
                0x8da471a9de737e24L,
                0x5ceaecfed289e5d2L, // 221
                0xb10d8e1456105dadL,
                0x7425a83e872c5f47L, // 222
                0xdd50f1996b947518L,
                0xd12f124e28f77719L, // 223
                0x8a5296ffe33cc92fL,
                0x82bd6b70d99aaa6fL, // 224
                0xace73cbfdc0bfb7bL,
                0x636cc64d1001550bL, // 225
                0xd8210befd30efa5aL,
                0x3c47f7e05401aa4eL, // 226
                0x8714a775e3e95c78L,
                0x65acfaec34810a71L, // 227
                0xa8d9d1535ce3b396L,
                0x7f1839a741a14d0dL, // 228
                0xd31045a8341ca07cL,
                0x1ede48111209a050L, // 229
                0x83ea2b892091e44dL,
                0x934aed0aab460432L, // 230
                0xa4e4b66b68b65d60L,
                0xf81da84d5617853fL, // 231
                0xce1de40642e3f4b9L,
                0x36251260ab9d668eL, // 232
                0x80d2ae83e9ce78f3L,
                0xc1d72b7c6b426019L, // 233
                0xa1075a24e4421730L,
                0xb24cf65b8612f81fL, // 234
                0xc94930ae1d529cfcL,
                0xdee033f26797b627L, // 235
                0xfb9b7cd9a4a7443cL,
                0x169840ef017da3b1L, // 236
                0x9d412e0806e88aa5L,
                0x8e1f289560ee864eL, // 237
                0xc491798a08a2ad4eL,
                0xf1a6f2bab92a27e2L, // 238
                0xf5b5d7ec8acb58a2L,
                0xae10af696774b1dbL, // 239
                0x9991a6f3d6bf1765L,
                0xacca6da1e0a8ef29L, // 240
                0xbff610b0cc6edd3fL,
                0x17fd090a58d32af3L, // 241
                0xeff394dcff8a948eL,
                0xddfc4b4cef07f5b0L, // 242
                0x95f83d0a1fb69cd9L,
                0x4abdaf101564f98eL, // 243
                0xbb764c4ca7a4440fL,
                0x9d6d1ad41abe37f1L, // 244
                0xea53df5fd18d5513L,
                0x84c86189216dc5edL, // 245
                0x92746b9be2f8552cL,
                0x32fd3cf5b4e49bb4L, // 246
                0xb7118682dbb66a77L,
                0x3fbc8c33221dc2a1L, // 247
                0xe4d5e82392a40515L,
                0x0fabaf3feaa5334aL, // 248
                0x8f05b1163ba6832dL,
                0x29cb4d87f2a7400eL, // 249
                0xb2c71d5bca9023f8L,
                0x743e20e9ef511012L, // 250
                0xdf78e4b2bd342cf6L,
                0x914da9246b255416L, // 251
                0x8bab8eefb6409c1aL,
                0x1ad089b6c2f7548eL, // 252
                0xae9672aba3d0c320L,
                0xa184ac2473b529b1L, // 253
                0xda3c0f568cc4f3e8L,
                0xc9e5d72d90a2741eL, // 254
                0x8865899617fb1871L,
                0x7e2fa67c7a658892L, // 255
                0xaa7eebfb9df9de8dL,
                0xddbb901b98feeab7L, // 256
                0xd51ea6fa85785631L,
                0x552a74227f3ea565L, // 257
                0x8533285c936b35deL,
                0xd53a88958f87275fL, // 258
                0xa67ff273b8460356L,
                0x8a892abaf368f137L, // 259
                0xd01fef10a657842cL,
                0x2d2b7569b0432d85L, // 260
                0x8213f56a67f6b29bL,
                0x9c3b29620e29fc73L, // 261
                0xa298f2c501f45f42L,
                0x8349f3ba91b47b8fL, // 262
                0xcb3f2f7642717713L,
                0x241c70a936219a73L, // 263
                0xfe0efb53d30dd4d7L,
                0xed238cd383aa0110L, // 264
                0x9ec95d1463e8a506L,
                0xf4363804324a40aaL, // 265
                0xc67bb4597ce2ce48L,
                0xb143c6053edcd0d5L, // 266
                0xf81aa16fdc1b81daL,
                0xdd94b7868e94050aL, // 267
                0x9b10a4e5e9913128L,
                0xca7cf2b4191c8326L, // 268
                0xc1d4ce1f63f57d72L,
                0xfd1c2f611f63a3f0L, // 269
                0xf24a01a73cf2dccfL,
                0xbc633b39673c8cecL, // 270
                0x976e41088617ca01L,
                0xd5be0503e085d813L, // 271
                0xbd49d14aa79dbc82L,
                0x4b2d8644d8a74e18L, // 272
                0xec9c459d51852ba2L,
                0xddf8e7d60ed1219eL, // 273
                0x93e1ab8252f33b45L,
                0xcabb90e5c942b503L, // 274
                0xb8da1662e7b00a17L,
                0x3d6a751f3b936243L, // 275
                0xe7109bfba19c0c9dL,
                0x0cc512670a783ad4L, // 276
                0x906a617d450187e2L,
                0x27fb2b80668b24c5L, // 277
                0xb484f9dc9641e9daL,
                0xb1f9f660802dedf6L, // 278
                0xe1a63853bbd26451L,
                0x5e7873f8a0396973L, // 279
                0x8d07e33455637eb2L,
                0xdb0b487b6423e1e8L, // 280
                0xb049dc016abc5e5fL,
                0x91ce1a9a3d2cda62L, // 281
                0xdc5c5301c56b75f7L,
                0x7641a140cc7810fbL, // 282
                0x89b9b3e11b6329baL,
                0xa9e904c87fcb0a9dL, // 283
                0xac2820d9623bf429L,
                0x546345fa9fbdcd44L, // 284
                0xd732290fbacaf133L,
                0xa97c177947ad4095L, // 285
                0x867f59a9d4bed6c0L,
                0x49ed8eabcccc485dL, // 286
                0xa81f301449ee8c70L,
                0x5c68f256bfff5a74L, // 287
                0xd226fc195c6a2f8cL,
                0x73832eec6fff3111L, // 288
                0x83585d8fd9c25db7L,
                0xc831fd53c5ff7eabL, // 289
                0xa42e74f3d032f525L,
                0xba3e7ca8b77f5e55L, // 290
                0xcd3a1230c43fb26fL,
                0x28ce1bd2e55f35ebL, // 291
                0x80444b5e7aa7cf85L,
                0x7980d163cf5b81b3L, // 292
                0xa0555e361951c366L,
                0xd7e105bcc332621fL, // 293
                0xc86ab5c39fa63440L,
                0x8dd9472bf3fefaa7L, // 294
                0xfa856334878fc150L,
                0xb14f98f6f0feb951L, // 295
                0x9c935e00d4b9d8d2L,
                0x6ed1bf9a569f33d3L, // 296
                0xc3b8358109e84f07L,
                0x0a862f80ec4700c8L, // 297
                0xf4a642e14c6262c8L,
                0xcd27bb612758c0faL, // 298
                0x98e7e9cccfbd7dbdL,
                0x8038d51cb897789cL, // 299
                0xbf21e44003acdd2cL,
                0xe0470a63e6bd56c3L, // 300
                0xeeea5d5004981478L,
                0x1858ccfce06cac74L, // 301
                0x95527a5202df0ccbL,
                0x0f37801e0c43ebc8L, // 302
                0xbaa718e68396cffdL,
                0xd30560258f54e6baL, // 303
                0xe950df20247c83fdL,
                0x47c6b82ef32a2069L, // 304
                0x91d28b7416cdd27eL,
                0x4cdc331d57fa5441L, // 305
                0xb6472e511c81471dL,
                0xe0133fe4adf8e952L, // 306
                0xe3d8f9e563a198e5L,
                0x58180fddd97723a6L, // 307
                0x8e679c2f5e44ff8fL,
                0x570f09eaa7ea7648L, // 308
                0xb201833b35d63f73L,
                0x2cd2cc6551e513daL, // 309
                0xde81e40a034bcf4fL,
                0xf8077f7ea65e58d1L, // 310
                0x8b112e86420f6191L,
                0xfb04afaf27faf782L, // 311
                0xadd57a27d29339f6L,
                0x79c5db9af1f9b563L, // 312
                0xd94ad8b1c7380874L,
                0x18375281ae7822bcL, // 313
                0x87cec76f1c830548L,
                0x8f2293910d0b15b5L, // 314
                0xa9c2794ae3a3c69aL,
                0xb2eb3875504ddb22L, // 315
                0xd433179d9c8cb841L,
                0x5fa60692a46151ebL, // 316
                0x849feec281d7f328L,
                0xdbc7c41ba6bcd333L, // 317
                0xa5c7ea73224deff3L,
                0x12b9b522906c0800L, // 318
                0xcf39e50feae16befL,
                0xd768226b34870a00L, // 319
                0x81842f29f2cce375L,
                0xe6a1158300d46640L, // 320
                0xa1e53af46f801c53L,
                0x60495ae3c1097fd0L, // 321
                0xca5e89b18b602368L,
                0x385bb19cb14bdfc4L, // 322
                0xfcf62c1dee382c42L,
                0x46729e03dd9ed7b5L, // 323
                0x9e19db92b4e31ba9L,
                0x6c07a2c26a8346d1L, // 324
                0xc5a05277621be293L,
                0xc7098b7305241885L, // 325
                0xf70867153aa2db38L,
                0xb8cbee4fc66d1ea7L, // 326
                0x9a65406d44a5c903L,
                0x737f74f1dc043328L, // 327
                0xc0fe908895cf3b44L,
                0x505f522e53053ff2L, // 328
                0xf13e34aabb430a15L,
                0x647726b9e7c68fefL, // 329
                0x96c6e0eab509e64dL,
                0x5eca783430dc19f5L, // 330
                0xbc789925624c5fe0L,
                0xb67d16413d132072L, // 331
                0xeb96bf6ebadf77d8L,
                0xe41c5bd18c57e88fL, // 332
                0x933e37a534cbaae7L,
                0x8e91b962f7b6f159L, // 333
                0xb80dc58e81fe95a1L,
                0x723627bbb5a4adb0L, // 334
                0xe61136f2227e3b09L,
                0xcec3b1aaa30dd91cL, // 335
                0x8fcac257558ee4e6L,
                0x213a4f0aa5e8a7b1L, // 336
                0xb3bd72ed2af29e1fL,
                0xa988e2cd4f62d19dL, // 337
                0xe0accfa875af45a7L,
                0x93eb1b80a33b8605L, // 338
                0x8c6c01c9498d8b88L,
                0xbc72f130660533c3L, // 339
                0xaf87023b9bf0ee6aL,
                0xeb8fad7c7f8680b4L, // 340
                0xdb68c2ca82ed2a05L,
                0xa67398db9f6820e1L, // 341
        };
    }
}
