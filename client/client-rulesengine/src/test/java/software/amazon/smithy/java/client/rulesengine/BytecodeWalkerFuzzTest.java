/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzz tests for {@link BytecodeWalker} — walking arbitrary instruction streams.
 */
class BytecodeWalkerFuzzTest {

    private static final int MAX_FUZZ_INPUT = 1024;

    @FuzzTest
    void fuzzWalker(byte[] data) {
        if (data.length > MAX_FUZZ_INPUT) {
            return;
        }
        try {
            BytecodeWalker walker = new BytecodeWalker(data);
            int steps = 0;
            while (walker.hasNext() && steps++ < 500) {
                walker.currentOpcode();
                int count = walker.getOperandCount();
                for (int i = 0; i < count; i++) {
                    walker.getOperand(i);
                }
                if (walker.isReturnOpcode()) {
                    break;
                }
                if (!walker.advance()) {
                    break;
                }
            }
        } catch (IllegalArgumentException | IllegalStateException ignored) {}
    }
}
