/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters;

import java.util.Objects;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.waiters.matching.Matcher;

/**
 * Causes a waiter to transition states if the polling function input/output match a condition.
 *
 * @param <I> Input type of polling function.
 * @param <O> Output type of polling function.
 */
record Acceptor<I extends SerializableStruct, O extends SerializableStruct>(
        WaiterState state,
        Matcher<I, O> matcher) {
    Acceptor {
        Objects.requireNonNull(matcher, "matcher cannot be null");
    }
}
