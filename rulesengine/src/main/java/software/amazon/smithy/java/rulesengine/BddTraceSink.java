/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.Map;

/**
 * Factory for {@link BddTrace} recorders, set on the call context ({@link RulesEngineSettings#BDD_TRACE_SINK})
 * to trace BDD endpoint resolution for tooling and debugging.
 *
 * <p>A single sink instance is shared across all resolutions and threads, so it must be thread-safe and
 * holds no per-resolution state. {@link #begin} is called once per resolution to decide whether to
 * trace: return a fresh {@link BddTrace} to record it, or {@code null} to skip (e.g. for sampling), in
 * which case resolution runs its normal untraced path.
 */
public interface BddTraceSink {
    /**
     * Begins tracing one resolution, or returns {@code null} to skip it.
     *
     * @param bytecode the compiled BDD program being evaluated.
     * @param parameters live view of the named registers (name to value): the resolved input parameters
     *                   at first, plus any variables assigned during evaluation (e.g. {@code
     *                   partitionResult}) as they are set. This is a live, zero-allocation view over the
     *                   evaluator's registers; the returned {@link BddTrace} may hold it and re-read it
     *                   during later callbacks, since its contents evolve as resolution proceeds. Copy it
     *                   (e.g. {@code new LinkedHashMap<>(parameters)}) to retain.
     * @return a recorder for this resolution, or {@code null} to not trace it.
     */
    BddTrace begin(Bytecode bytecode, Map<String, Object> parameters);
}
