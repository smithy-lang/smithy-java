/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.interceptors;

import java.util.Objects;
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
     * Casts the given output value to the output type of this hook.
     *
     * <p>This is useful when modifying the output using {@code instanceof} pattern matching:
     * {@snippet :
     * if (hook.output() instanceof MyOutput myOutput) {
     *     return hook.asOutputType(myOutput.toBuilder().foo("bar").build());
     * }
     * return hook.forward(e);
     * }
     *
     * @param output Output value to cast.
     * @return the output value cast to the output type.
     */
    @SuppressWarnings("unchecked")
    public O asOutputType(SerializableStruct output) {
        return (O) output;
    }
}
