/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DecimalToBinaryBoundaryTest {

    @Test
    void conversionBoundariesMatchJdk() {
        for (long unscaled : new long[] {0, 1, (1L << 53) - 1, 1L << 53, (1L << 53) + 1, 999999999999999999L}) {
            for (int scale = -23; scale <= 23; scale++) {
                double fast = NumberCodec.decimalToDouble(unscaled, scale);
                if (!Double.isNaN(fast)) {
                    assertEquals(Double.doubleToRawLongBits(Double.parseDouble(unscaled + "e" + -scale)),
                            Double.doubleToRawLongBits(fast),
                            unscaled + " scale=" + scale);
                }
            }
        }
    }

    @Test
    void ambiguousRoundingRequestsFallback() {
        assertTrue(Double.isNaN(DecimalToBinary.toDouble(90071992547409930L, 1)));
        assertTrue(Double.isNaN(DecimalToBinary.toDouble(566222069060736792L, 5)));
    }

    @Test
    void commonValidatedDecimalsUseTheConverter() {
        for (long[] decimal : new long[][] {
                {1, 22},
                {1, -22},
                {1L << 53, 0},
                {30000000000000004L, 17},
                {123456789012345678L, 8},
                {999999999999999999L, 18}
        }) {
            double actual = NumberCodec.decimalToDouble(decimal[0], (int) decimal[1]);
            assertTrue(Double.isFinite(actual));
            assertEquals(Double.doubleToRawLongBits(Double.parseDouble(decimal[0] + "e" + -decimal[1])),
                    Double.doubleToRawLongBits(actual));
        }
    }

    @Test
    void digitLanesRejectEveryNonDigitWithoutBorrowing() {
        byte[] digits = "12345678".getBytes(StandardCharsets.US_ASCII);
        long lanes = NumberCodec.digitLanes(digits, 0);
        assertEquals(8, NumberCodec.leadingDigitLaneCount(lanes));
        assertEquals(12345678, NumberCodec.combineDigitLanes(lanes));
        for (int position = 0; position < 8; position++) {
            for (int candidate = 0; candidate <= 255; candidate++) {
                if (candidate >= '0' && candidate <= '9') {
                    continue;
                }
                byte[] bytes = digits.clone();
                bytes[position] = (byte) candidate;
                long word = NumberCodec.digitLanes(bytes, 0);
                assertEquals(position, NumberCodec.leadingDigitLaneCount(word));
                if (position > 0) {
                    long expected = Long.parseLong("12345678".substring(0, position));
                    assertEquals(expected, NumberCodec.combineDigitLanes(word >>> ((8 - position) * 8)));
                }
            }
        }
    }

}
