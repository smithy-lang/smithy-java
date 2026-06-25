/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import software.amazon.smithy.java.endpoints.Endpoint;

/**
 * Records the step-by-step trace of a single BDD endpoint resolution. Created by
 * {@link BddTraceSink#begin} and used by exactly one resolution on one thread, so it needs no
 * synchronization and can freely accumulate per-resolution state.
 *
 * <p>Call order: {@link #node} per visited BDD node in traversal order, then one {@link #result}.
 * Condition and result ids index the bytecode passed to {@link BddTraceSink#begin} (and, in the same
 * order, {@link software.amazon.smithy.rulesengine.traits.EndpointBddTrait#getConditions()} /
 * {@code getResults()}). Node references follow the BDD's own convention (see {@link #node}), so the
 * sequence of visited nodes reconstructs the path taken through the graph.
 *
 * <p>To inspect parameter/variable values, read the live view handed to {@link BddTraceSink#begin}
 * during a callback: it reflects current register state, so at {@link #node} time it shows values as of
 * that node, and at {@link #result} time it includes variables assigned during traversal.
 */
public interface BddTrace {
    /**
     * A BDD node was visited and its condition evaluated.
     *
     * @param nodeRef the visited node's reference, in the BDD's signed convention (a negative value is a
     *                complemented edge to the node, a value over 100M is a result ID).
     * @param conditionId index of the condition tested at this node.
     * @param satisfied the condition's own truth value.
     * @param branch the edge taken after applying complement state ({@code true} = high/true edge);
     *               differs from {@code satisfied} only on complemented nodes.
     */
    void node(int nodeRef, int conditionId, boolean satisfied, boolean branch);

    /**
     * Resolution terminated. Reported after the endpoint is built, so the trace concludes with the
     * concrete outcome, not just the result rule's id.
     *
     * @param resultId index of the matched result rule, or {@code -1} for no match.
     * @param endpoint the resolved endpoint (uri, auth schemes, properties), or {@code null} when there
     *                 was no match.
     */
    void result(int resultId, Endpoint endpoint);
}
