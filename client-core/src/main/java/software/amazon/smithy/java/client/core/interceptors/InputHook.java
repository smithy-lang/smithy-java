/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.interceptors;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Hook data that always contains an input shape.
 *
 * @param <I> Input shape.
 */
public sealed class InputHook<I extends SerializableStruct, O extends SerializableStruct> permits RequestHook {

    private final Context context;
    private final I input;
    private final ApiOperation<I, O> operation;

    public InputHook(ApiOperation<I, O> operation, Context context, I input) {
        this.operation = Objects.requireNonNull(operation);
        this.context = Objects.requireNonNull(context);
        this.input = Objects.requireNonNull(input);
    }

    /**
     * Get the API operation being called.
     *
     * @return the operation being called.
     */
    public ApiOperation<I, O> operation() {
        return operation;
    }

    /**
     * Get the context of the hook.
     *
     * @return the context.
     */
    public Context context() {
        return context;
    }

    /**
     * Get the always present input shape value.
     *
     * @return the input value.
     */
    public I input() {
        return input;
    }

    /**
     * Create a new input hook using the given input, or return the same hook if input is unchanged.
     *
     * @param input Input to use.
     * @return the hook.
     */
    public InputHook<I, O> withInput(I input) {
        return this.input.equals(input) ? this : new InputHook<>(operation, context, input);
    }

    /**
     * Provides a type-safe convenience method to modify the input if it is of a specific class.
     *
     * @param predicateType Type to map over.
     * @param mapper Mapper that accepts the value if it matches the expected class.
     * @return the updated value.
     * @param <R> Class to map over.
     */
    @SuppressWarnings("unchecked")
    public <R extends SerializableStruct> I mapInput(Class<R> predicateType, Function<InputHook<R, ?>, R> mapper) {
        if (predicateType.isInstance(input)) {
            return (I) mapper.apply((InputHook<R, ?>) this);
        } else {
            return input;
        }
    }

    /**
     * Provides a type-safe convenience method to modify the input if it is of a specific class.
     *
     * @param predicateType Type to map over.
     * @param state State to provide to the mapper.
     * @param mapper Mapper that accepts the value if it matches the expected class.
     * @return the updated value.
     * @param <R> Class to map over.
     */
    @SuppressWarnings("unchecked")
    public <R extends SerializableStruct, T> I mapInput(
            Class<R> predicateType,
            T state,
            BiFunction<InputHook<R, ?>, T, R> mapper
    ) {
        if (predicateType.isInstance(input)) {
            return (I) mapper.apply((InputHook<R, ?>) this, state);
        } else {
            return input;
        }
    }
}
