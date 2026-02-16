/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzz tests for {@link BytecodeReader} — constant deserialization and register definition parsing.
 */
class BytecodeReaderFuzzTest {

    private static final int MAX_FUZZ_INPUT = 1024;

    @FuzzTest
    void fuzzReadConstant(byte[] data) {
        if (data.length > MAX_FUZZ_INPUT) {
            return;
        }
        try {
            new BytecodeReader(data, 0).readConstant();
        } catch (IllegalArgumentException ignored) {}
    }

    @FuzzTest
    void fuzzReadRegisterDefinitions(byte[] data) {
        if (data.length < 1 || data.length > MAX_FUZZ_INPUT) {
            return;
        }
        // Use first byte to determine count (1-16)
        int count = (data[0] & 0x0F) + 1;
        try {
            new BytecodeReader(data, 1).readRegisterDefinitions(count);
        } catch (IllegalArgumentException ignored) {}
    }
}
