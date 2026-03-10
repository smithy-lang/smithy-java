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
 * A hook that can contain a deserialized output shape.
 *
 * @param <I> Input shape.
 * @param <RequestT> Protocol specific request.
 * @param <ResponseT> Protocol specific response.
 * @param <O> Output shape.
 */
public final class OutputHook<I extends SerializableStruct, O extends SerializableStruct, RequestT, ResponseT> extends
        ResponseHook<I, O, RequestT, ResponseT> {

    private final O output;

    public OutputHook(
            ApiOperation<I, O> operation,
            Context context,
            I input,
            RequestT request,
            ResponseT response,
            O output
    ) {
        super(operation, context, input, request, response);
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
    public OutputHook<I, O, RequestT, ResponseT> withOutput(O output) {
        return Objects.equals(output, this.output)
                ? this
                : new OutputHook<>(operation(), context(), input(), request(), response(), output);
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
     * @param e Error to throw if not null.
     * @param predicateType Type to map over.
     * @param mapper Mapper that accepts the value if it matches the expected class.
     * @return the updated value.
     * @param <R> Class to map over.
     */
    @SuppressWarnings("unchecked")
    public <R extends SerializableStruct> O mapOutput(
            RuntimeException e,
            Class<R> predicateType,
            Function<OutputHook<?, R, ?, ?>, R> mapper
    ) {
        if (e != null) {
            throw e;
        } else if (predicateType.isInstance(output)) {
            return (O) mapper.apply((OutputHook<?, R, ?, ?>) this);
        } else {
            return output;
        }
    }

    /**
     * Provides a type-safe convenience method to modify the output if it is of a specific class.
     *
     * @param e Error to throw if not null.
     * @param predicateType Type to map over.
     * @param state State to provide to the mapper.
     * @param mapper Mapper that accepts the value if it matches the expected class.
     * @return the updated value.
     * @param <R> Class to map over.
     */
    @SuppressWarnings("unchecked")
    public <R extends SerializableStruct, T> O mapOutput(
            RuntimeException e,
            Class<R> predicateType,
            T state,
            BiFunction<OutputHook<?, R, ?, ?>, T, R> mapper
    ) {
        if (e != null) {
            throw e;
        } else if (predicateType.isInstance(output)) {
            return (O) mapper.apply((OutputHook<?, R, ?, ?>) this, state);
        } else {
            return output;
        }
    }
}
