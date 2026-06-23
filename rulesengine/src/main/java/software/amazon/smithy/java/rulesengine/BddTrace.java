/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

/**
 * Records the step-by-step trace of a single BDD endpoint resolution. Created by
 * {@link BddTraceSink#begin} and used by exactly one resolution on one thread, so it needs no
 * synchronization and can freely accumulate per-resolution state.
 *
 * <p>Call order: {@link #condition} per evaluated condition in visitation order, then one
 * {@link #result}. Condition and result ids index the bytecode passed to {@link BddTraceSink#begin}
 * (and, in the same order, {@link software.amazon.smithy.rulesengine.traits.EndpointBddTrait#getConditions()}
 * / {@code getResults()}).
 *
 * <p>To inspect parameter/variable values, read the live view handed to {@link BddTraceSink#begin}
 * during a callback: it reflects current register state, so at {@link #condition} time it shows values
 * as of that condition, and at {@link #result} time it includes variables assigned during traversal.
 */
public interface BddTrace {
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
     */
    void result(int resultId);
}
