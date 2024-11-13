/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.interceptors;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

/**
 * A hook that can contain a protocol-specific response.
 *
 * @param <I> Input shape.
 * @param <RequestT> Protocol specific request.
 * @param <ResponseT> Protocol specific response.
 */
public sealed class ResponseHook<I extends SerializableStruct, O extends SerializableStruct, RequestT, ResponseT>
    extends RequestHook<I, O, RequestT>
    permits OutputHook {

    private final ResponseT response;

    public ResponseHook(ApiOperation<I, O> operation, Context context, I input, RequestT request, ResponseT response) {
        super(operation, context, input, request);
        this.response = response;
    }

    /**
     * Get the potentially set response.
     *
     * @return the response, or null if not set.
     */
    public ResponseT response() {
        return response;
    }

    /**
     * Create a new response hook using the given response, or return the same hook if response is unchanged.
     *
     * @param response Response to use.
     * @return the hook.
     */
    public ResponseHook<I, O, RequestT, ResponseT> withResponse(ResponseT response) {
        return Objects.equals(response, this.response)
            ? this
            : new ResponseHook<>(operation(), context(), input(), request(), response);
    }

    /**
     * Provides a type-safe convenience method to modify the response if it is of a specific class.
     *
     * @param predicateType Response type to map over.
     * @param mapper Mapper that accepts the response.
     * @return the updated response.
     * @param <R> Expected response class.
     */
    @SuppressWarnings("unchecked")
    public <R> ResponseT mapResponse(Class<R> predicateType, Function<R, R> mapper) {
        if (response.getClass() == predicateType) {
            return (ResponseT) mapper.apply((R) response);
        } else {
            return response;
        }
    }

    /**
     * Provides a type-safe convenience method to modify the response if it is of a specific class.
     *
     * @param predicateType Response type to map over.
     * @param state State to pass to the mapper.
     * @param mapper Mapper that accepts the response.
     * @return the updated response.
     * @param <R> Expected response class.
     * @param <T> State value class.
     */
    @SuppressWarnings("unchecked")
    public <R, T> ResponseT mapResponse(Class<R> predicateType, T state, BiFunction<R, T, R> mapper) {
        if (response.getClass() == predicateType) {
            return (ResponseT) mapper.apply((R) response, state);
        } else {
            return response;
        }
    }
}
