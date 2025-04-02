/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.waiters.matching;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * A matcher defines how a waiter acceptor determines if it matches the current state of a resource.
 *
 * @param <I> Input shape type
 * @param <O> Output shape type
 */
public sealed interface Matcher<I extends SerializableStruct, O extends SerializableStruct>
        permits ErrorTypeMatcher, InputOutputMatcher, OutputMatcher, SuccessMatcher {
    /**
     * Checks if the input, output, and/or exception returned by a client call meet a condition.
     *
     * @param input Input shape type
     * @param output Output type if response is successful, or null.
     * @param exception Modeled exception if response failed, or null.
     * @return true if the matcher matches the client response.
     */
    boolean matches(I input, O output, ModeledException exception);

    /**
     * Matches on the successful output of an operation.
     *
     * <p>This matcher is checked only if an operation completes successfully and has
     * non-empty output.
     *
     * @param predicate Predicate used to match against output values.
     */
    static <I extends SerializableStruct, O extends SerializableStruct> Matcher<I, O> output(Predicate<O> predicate) {
        return new OutputMatcher<>(predicate);
    }

    /**
     * Matches on both the input and output of a successful operation.
     *
     * <p>This matcher is checked only if an operation completes successfully.
     *
     * @param predicate BiPredicate used to test for a match
     * @param <I> Input shape type
     * @param <O> Output shape type
     */
    static <I extends SerializableStruct,
            O extends SerializableStruct> Matcher<I, O> inputOutput(BiPredicate<I, O> predicate) {
        return new InputOutputMatcher<>(predicate);
    }

    /**
     * When set to true, matches when an operation returns a successful response.
     *
     * <p>When set to false, matches when an operation fails with any error.
     * This matcher is checked regardless of if an operation succeeds or fails with an error.
     *
     * @param onSuccess true if this matches on successful response
     */
    static <I extends SerializableStruct, O extends SerializableStruct> Matcher<I, O> success(boolean onSuccess) {
        return new SuccessMatcher<>(onSuccess);
    }

    /**
     * Matches if an operation returns an error of an expected type.
     *
     * <p>The errorType matcher typically refer to errors that are associated with an operation through its errors property,
     * though some operations might need to refer to framework errors that are not defined in the model.
     *
     * @param errorName Name of error to match
     */
    static <I extends SerializableStruct, O extends SerializableStruct> Matcher<I, O> errorType(String errorName) {
        return new ErrorTypeMatcher<>(errorName);
    }
}
