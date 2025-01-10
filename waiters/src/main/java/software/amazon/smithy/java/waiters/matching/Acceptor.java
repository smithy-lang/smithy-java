/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters.matching;

import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.waiters.WaiterState;

/**
 * Causes a waiter to transition states if the polling function input/output match a condition.
 *
 * @param <I> Input type of polling function.
 * @param <O> Output type of polling function.
 */
public sealed interface Acceptor<I extends SerializableStruct, O extends SerializableStruct> permits AcceptorImpl {

    WaiterState state();

    Matcher<I, O> matcher();

    /**
     * The waiter will finish successfully if it matches the provided matcher.
     *
     * @param matcher condition to evaluate
     */
    static <I extends SerializableStruct, O extends SerializableStruct> Acceptor<I, O> success(Matcher<I, O> matcher) {
        return new AcceptorImpl<>(WaiterState.SUCCESS, matcher);
    }

    /**
     * The waiter has failed to complete successfully if it matches the provided matcher.
     *
     * @param matcher condition to evaluate
     */
    static <I extends SerializableStruct, O extends SerializableStruct> Acceptor<I, O> failure(Matcher<I, O> matcher) {
        return new AcceptorImpl<>(WaiterState.FAILURE, matcher);
    }

    /**
     * The waiter will retry the operation if it matches the provided matcher.
     *
     * @param matcher condition to evaluate
     */
    static <I extends SerializableStruct, O extends SerializableStruct> Acceptor<I, O> retry(Matcher<I, O> matcher) {
        return new AcceptorImpl<>(WaiterState.RETRY, matcher);
    }
}
