/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.interceptors;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

/**
 * A hook that can contain a deserialized output shape.
 *
 * @param <I> Input shape.
 * @param <RequestT> Protocol specific request.
 * @param <ResponseT> Protocol specific response.
 * @param <O> Output shape.
 */
public final class OutputHook<I extends SerializableStruct, RequestT, ResponseT, O extends SerializableStruct> extends
    ResponseHook<I, RequestT, ResponseT> {

    private final O output;

    public OutputHook(Context context, I input, RequestT request, ResponseT response, O output) {
        super(context, input, request, response);
        this.output = output;
    }

    /**
     * The potentially present output shape.
     *
     * @return the output shape, or null if not set.
     */
    public O output() {
        return output;
    }

    /**
     * Create a new output hook using the given output, or return the same hook if output is unchanged.
     *
     * @param output Output to use.
     * @return the hook.
     */
    public OutputHook<I, RequestT, ResponseT, O> withOutput(O output) {
        return Objects.equals(output, this.output)
            ? this
            : new OutputHook<>(context(), input(), request(), response(), output);
    }

    /**
     * If an exception {@code e} is provided, throw it, otherwise return the output value.
     *
     * @param e Error to potentially rethrow.
     * @return the output value.
     */
    public O forward(RuntimeException e) {
        if (e != null) {
            throw e;
        }
        return output;
    }

    /**
     * Provides a type-safe convenience method to modify the output if it is of a specific class.
     *
     * @param predicateType Type to map over.
     * @param mapper Mapper that accepts the value if it matches the expected class.
     * @return the updated value.
     * @param <R> Class to map over.
     */
    @SuppressWarnings("unchecked")
    public <R> O mapOutput(Class<R> predicateType, Function<R, R> mapper) {
        if (output.getClass() == predicateType) {
            return (O) mapper.apply((R) output);
        } else {
            return output;
        }
    }

    /**
     * Provides a type-safe convenience method to modify the output if it is of a specific class.
     *
     * @param predicateType Type to map over.
     * @param state State to provide to the mapper.
     * @param mapper Mapper that accepts the value if it matches the expected class.
     * @return the updated value.
     * @param <R> Class to map over.
     */
    @SuppressWarnings("unchecked")
    public <R, T> O mapOutput(Class<R> predicateType, T state, BiFunction<R, T, R> mapper) {
        if (output.getClass() == predicateType) {
            return (O) mapper.apply((R) output, state);
        } else {
            return output;
        }
    }
}
