/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.Map;

/**
 * Receives a step-by-step trace of a single BDD endpoint resolution, for tooling and debugging.
 *
 * <p>Only driven when set on the call context ({@link RulesEngineSettings#BDD_TRACE_SINK}); resolution
 * is otherwise unaffected. Call order: {@link #init} once, {@link #condition} per evaluated condition in
 * visitation order, then one {@link #result}. Ids index the {@link #init} bytecode (and, in the same
 * order, {@link software.amazon.smithy.rulesengine.traits.EndpointBddTrait#getConditions()} /
 * {@code getResults()}).
 *
 * <p>A sink receives the callbacks for exactly one resolution and is not invoked concurrently, so an
 * implementation needs no synchronization or correlation id. Supply a fresh sink per call (it is the
 * correlation handle); reusing one instance across concurrent resolutions would interleave callbacks.
 *
 * <p>The maps passed to {@link #init}/{@link #result} are live, zero-allocation views over the
 * evaluator's registers, not copies: read during the callback, and copy (e.g.
 * {@code new LinkedHashMap<>(map)}) to retain.
 */
public interface BddTraceSink {
    /**
     * Starts a trace with the compiled program and resolved input parameters (name to value).
     */
    void init(Bytecode bytecode, Map<String, Object> parameters);

    /**
     * A condition was evaluated.
     *
     * @param conditionId index of the condition.
     * @param satisfied the condition's own truth value.
     * @param branch the edge taken after applying complement state ({@code true} = high/true edge);
     *               differs from {@code satisfied} only on complemented nodes.
     */
    void condition(int conditionId, boolean satisfied, boolean branch);

    /**
     * Resolution terminated.
     *
     * @param resultId index of the matched result rule, or {@code -1} for no match.
     * @param variables named registers at result time (a superset of {@link #init}'s parameters, adding
     *                  variables assigned during traversal).
     */
    void result(int resultId, Map<String, Object> variables);
}
