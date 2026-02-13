/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.List;
import java.util.Map;

/**
 * Fuzz tests for {@link BytecodeEvaluator} — evaluating bytecode with random instruction streams.
 *
 * <p>Constructs minimal valid Bytecode objects with fuzz-derived instruction bytes,
 * then runs the evaluator to check for crash safety.
 */
class BytecodeEvaluatorFuzzTest {

    private static final int MAX_FUZZ_INPUT = 512;

    @FuzzTest
    void fuzzEvaluateCondition(byte[] data) {
        if (data.length < 1 || data.length > MAX_FUZZ_INPUT) {
            return;
        }

        // Build a Bytecode with the fuzz data as the instruction stream and a single condition at offset 0.
        Bytecode bytecode = new Bytecode(
                data,
                new int[] {0}, // one condition at offset 0
                new int[0],
                new RegisterDefinition[] {
                        new RegisterDefinition("p0", false, "default", null, false),
                        new RegisterDefinition("t0", false, null, null, true)
                },
                new Object[] {"a", "b", 1, true, false, null, List.of(), Map.of()},
                new RulesFunction[0],
                new int[] {0, 1, -1}, // BDD: condition 0 -> TRUE/FALSE
                2 // root = node 0
        );

        BytecodeEvaluator evaluator = new BytecodeEvaluator(bytecode,
                new RulesExtension[0],
                RegisterFiller.of(bytecode, Map.of()));
        evaluator.reset(null, Map.of());

        try {
            evaluator.test(0);
        } catch (RulesEvaluationError | IllegalArgumentException ignored) {}
    }

    @FuzzTest
    void fuzzEvaluateResult(byte[] data) {
        if (data.length < 1 || data.length > MAX_FUZZ_INPUT) {
            return;
        }

        Bytecode bytecode = new Bytecode(
                data,
                new int[0],
                new int[] {0}, // one result at offset 0
                new RegisterDefinition[] {
                        new RegisterDefinition("p0", false, "https://example.com", null, false),
                        new RegisterDefinition("t0", false, null, null, true)
                },
                new Object[] {"https://example.com", "key", "value", 42, true, false},
                new RulesFunction[0],
                new int[] {-1, 100_000_000, -1}, // Terminal -> result 0
                100_000_000);

        BytecodeEvaluator evaluator = new BytecodeEvaluator(bytecode,
                new RulesExtension[0],
                RegisterFiller.of(bytecode, Map.of()));
        evaluator.reset(null, Map.of());

        try {
            evaluator.resolveResult(0);
        } catch (RulesEvaluationError | IllegalArgumentException ignored) {}
    }
}
