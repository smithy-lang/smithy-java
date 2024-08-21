/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.interceptors;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

/**
 * Hook data that always contains an input shape.
 *
 * @param <I> Input shape.
 */
public sealed class InputHook<I extends SerializableStruct> permits RequestHook {

    private final Context context;
    private final I input;

    public InputHook(Context context, I input) {
        this.context = Objects.requireNonNull(context);
        this.input = Objects.requireNonNull(input);
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
    public InputHook<I> withInput(I input) {
        return this.input.equals(input) ? this : new InputHook<>(context, input);
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
    public <R extends SerializableStruct> I mapInput(Class<R> predicateType, Function<R, R> mapper) {
        if (input.getClass().isAssignableFrom(predicateType)) {
            return (I) mapper.apply((R) input);
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
    public <R extends SerializableStruct, T> I mapInput(Class<R> predicateType, T state, BiFunction<R, T, R> mapper) {
        if (input.getClass() == predicateType) {
            return (I) mapper.apply((R) input, state);
        } else {
            return input;
        }
    }
}
