/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzz tests for loading bytecode from arbitrary byte arrays via {@link RulesEngineBuilder#load(byte[])}.
 *
 * <p>This exercises header parsing, offset validation, constant pool deserialization,
 * register definition loading, BDD node loading, and function resolution.
 */
class BytecodeLoadFuzzTest {

    private static final int MAX_FUZZ_INPUT = 2048;

    @FuzzTest
    void fuzzLoad(byte[] data) {
        if (data.length > MAX_FUZZ_INPUT) {
            return;
        }
        try {
            new RulesEngineBuilder().load(data);
        } catch (IllegalArgumentException | RulesEvaluationError ignored) {}
    }
}
