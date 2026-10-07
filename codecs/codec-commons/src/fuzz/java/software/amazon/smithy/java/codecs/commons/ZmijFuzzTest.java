/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

class ZmijFuzzTest {
    @FuzzTest
    void fuzzDouble(long bits) {
        double value = Double.longBitsToDouble(bits);
        if (!Double.isFinite(value)) {
            return;
        }
        byte[] bytes = new byte[3 + NumberCodec.DOUBLE_MAX_BYTES + 1];
        Arrays.fill(bytes, (byte) 0x7f);
        int end = Zmij.writeDouble(bytes, 3, value);
        assertEquals(Double.toString(value), new String(bytes, 3, end - 3, StandardCharsets.US_ASCII));
        assertEquals(0x7f, bytes[2]);
        assertEquals(0x7f, bytes[bytes.length - 1]);
    }

    @FuzzTest
    void fuzzFloat(int bits) {
        float value = Float.intBitsToFloat(bits);
        if (!Float.isFinite(value)) {
            return;
        }
        byte[] bytes = new byte[3 + NumberCodec.FLOAT_MAX_BYTES + 1];
        Arrays.fill(bytes, (byte) 0x7f);
        int end = Zmij.writeFloat(bytes, 3, value);
        assertEquals(Float.toString(value), new String(bytes, 3, end - 3, StandardCharsets.US_ASCII));
        assertEquals(0x7f, bytes[2]);
        assertEquals(0x7f, bytes[bytes.length - 1]);
    }
}
