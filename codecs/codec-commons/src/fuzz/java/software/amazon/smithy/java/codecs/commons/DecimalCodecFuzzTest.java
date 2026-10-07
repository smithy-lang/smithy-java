/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

class DecimalCodecFuzzTest {
    @FuzzTest
    void fuzzValidatedSignificands(FuzzedDataProvider input) {
        long unscaled = input.consumeLong(0, 999999999999999999L);
        int scale = input.consumeInt(-23, 23);
        double fast = NumberCodec.decimalToDouble(unscaled, scale);
        if (!Double.isNaN(fast)) {
            assertEquals(Double.doubleToRawLongBits(Double.parseDouble(unscaled + "e" + -scale)),
                    Double.doubleToRawLongBits(fast));
        }
    }

}
