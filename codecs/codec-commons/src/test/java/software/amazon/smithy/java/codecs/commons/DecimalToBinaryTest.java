/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class DecimalToBinaryTest {

    @Test
    void reciprocalTableMatchesBigIntegerDerivation() {
        BigInteger numerator = BigInteger.ONE.shiftLeft(256);
        assertEquals(0, DecimalToBinary.RECIPROCAL_POW10[0]);
        for (int scale = 1; scale <= DecimalToBinary.MAX_DIGITS; scale++) {
            BigInteger quotient = numerator.divide(BigInteger.TEN.pow(scale));
            long expected = quotient.shiftRight(quotient.bitLength() - Long.SIZE).longValue();
            assertEquals(expected, DecimalToBinary.RECIPROCAL_POW10[scale], "scale=" + scale);
        }
    }

    @Test
    @Tag("stress")
    void randomValidatedSignificandsMatchJdk() {
        Random random = new Random(7);
        for (int i = 0; i < 200_000; i++) {
            long unscaled = (random.nextLong() & Long.MAX_VALUE) % 1_000_000_000_000_000_000L;
            int scale = random.nextInt(47) - 23;
            double fast = DecimalToBinary.toDouble(unscaled, scale);
            if (!Double.isNaN(fast)) {
                assertEquals(Double.doubleToRawLongBits(Double.parseDouble(unscaled + "e" + -scale)),
                        Double.doubleToRawLongBits(fast));
            }
        }
    }
}
