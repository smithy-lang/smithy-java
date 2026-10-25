/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class ZmijTest {

    private static final byte SENTINEL = (byte) 0x7f;

    private static void check(double value) {
        if (!Double.isFinite(value)) {
            return;
        }
        byte[] expected = new byte[64];
        byte[] actual = new byte[64];
        Arrays.fill(expected, SENTINEL);
        Arrays.fill(actual, SENTINEL);
        int expectedEnd = Schubfach.writeDouble(expected, 8, value);
        int actualEnd = Zmij.writeDouble(actual, 8, value);
        String expectedText = new String(expected, 8, expectedEnd - 8, StandardCharsets.US_ASCII);
        String actualText = new String(actual, 8, actualEnd - 8, StandardCharsets.US_ASCII);
        assertEquals(expectedText, actualText, () -> "bits " + Long.toHexString(Double.doubleToRawLongBits(value)));
        assertEquals(Double.toString(value), actualText);
        for (int i = 0; i < 8; i++) {
            assertEquals(SENTINEL, actual[i], "wrote before pos");
        }
        for (int i = 8 + NumberCodec.DOUBLE_MAX_BYTES; i < actual.length; i++) {
            assertEquals(SENTINEL, actual[i], () -> "wrote past DOUBLE_MAX_BYTES for " + value);
        }
    }

    private static void check(float value) {
        if (!Float.isFinite(value)) {
            return;
        }
        byte[] expected = new byte[64];
        byte[] actual = new byte[64];
        Arrays.fill(expected, SENTINEL);
        Arrays.fill(actual, SENTINEL);
        int expectedEnd = Schubfach.writeFloat(expected, 8, value);
        int actualEnd = Zmij.writeFloat(actual, 8, value);
        String expectedText = new String(expected, 8, expectedEnd - 8, StandardCharsets.US_ASCII);
        String actualText = new String(actual, 8, actualEnd - 8, StandardCharsets.US_ASCII);
        assertEquals(expectedText, actualText, () -> "bits " + Integer.toHexString(Float.floatToRawIntBits(value)));
        assertEquals(Float.toString(value), actualText);
        for (int i = 0; i < 8; i++) {
            assertEquals(SENTINEL, actual[i], "wrote before pos");
        }
        for (int i = 8 + NumberCodec.FLOAT_MAX_BYTES; i < actual.length; i++) {
            assertEquals(SENTINEL, actual[i], () -> "wrote past FLOAT_MAX_BYTES for " + value);
        }
    }

    @Test
    @Tag("stress")
    void binaryBoundaries() {
        for (int bits = 0; bits < 20000; bits++) {
            check(Float.intBitsToFloat(bits));
            check(Double.longBitsToDouble(bits));
            check(-Float.intBitsToFloat(bits));
            check(-Double.longBitsToDouble(bits));
        }
        for (int exponent = 0; exponent < 255; exponent++) {
            for (int mantissa : new int[] {0, 1, 2, 3, 0x3fffff, 0x400000, 0x7ffffd, 0x7ffffe, 0x7fffff}) {
                check(Float.intBitsToFloat((exponent << 23) | mantissa));
            }
        }
        for (int exponent = 0; exponent < 2047; exponent++) {
            for (long mantissa : new long[] {
                    0,
                    1,
                    2,
                    3,
                    0x7ffffffffffffL,
                    0x8000000000000L,
                    0xffffffffffffdL,
                    0xffffffffffffeL,
                    0xfffffffffffffL}) {
                check(Double.longBitsToDouble(((long) exponent << 52) | mantissa));
            }
        }
    }

    @Test
    @Tag("stress")
    void decimalBoundaries() {
        for (int e = -324; e <= 308; e++) {
            double p = Double.parseDouble("1e" + e);
            check(p);
            check(Math.nextUp(p));
            check(Math.nextDown(p));
            check(p * 5);
            check(p * 9.99);
        }
        for (int e = -45; e <= 38; e++) {
            float p = Float.parseFloat("1e" + e);
            check(p);
            check(Math.nextUp(p));
            check(Math.nextDown(p));
        }
        for (long i = 0; i < 100_000; i++) {
            check((double) i);
            check((double) i / 8);
            check((double) i / 1000);
            check((float) i);
            check((float) i / 8);
            check((float) i / 1000);
        }
    }

    @Test
    @Tag("stress")
    void randomValues() {
        Random random = new Random(20260918);
        for (int i = 0; i < 2_000_000; i++) {
            double d = Double.longBitsToDouble(random.nextLong());
            if (Double.isFinite(d)) {
                check(d);
            }
            float f = Float.intBitsToFloat(random.nextInt());
            if (Float.isFinite(f)) {
                check(f);
            }
        }
        for (int i = 0; i < 500_000; i++) {
            check(random.nextDouble() * Math.pow(10, random.nextInt(40) - 20));
            check((float) (random.nextFloat() * Math.pow(10, random.nextInt(20) - 10)));
        }
    }
}
