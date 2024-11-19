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
 * Hook data that may contain a request shape.
 *
 * @param <I> Input shape.
 * @param <RequestT> Protocol specific request.
 */
public sealed class RequestHook<I extends SerializableStruct, O extends SerializableStruct, RequestT> extends
    InputHook<I, O> permits ResponseHook {

    private final RequestT request;

    public RequestHook(ApiOperation<I, O> operation, Context context, I input, RequestT request) {
        super(operation, context, input);
        this.request = request;
    }

    /**
     * Returns a potentially null request value.
     *
     * @return the request value, or null if not set.
     */
    public RequestT request() {
        return request;
    }

    /**
     * Create a new request hook using the given request, or return the same hook if request is unchanged.
     *
     * @param request Request to use.
     * @return the hook.
     */
    public RequestHook<I, O, RequestT> withRequest(RequestT request) {
        return Objects.equals(request, this.request)
            ? this
            : new RequestHook<>(operation(), context(), input(), request);
    }

    /**
     * Provides a type-safe convenience method to modify the request if it is of a specific class.
     *
     * @param predicateType Request type to map over.
     * @param mapper Mapper that accepts the request and a state value.
     * @return the updated request.
     * @param <R> Expected request class.
     */
    @SuppressWarnings("unchecked")
    public <R> RequestT mapRequest(Class<R> predicateType, Function<RequestHook<?, ?, R>, R> mapper) {
        if (predicateType.isAssignableFrom(request.getClass())) {
            return (RequestT) mapper.apply((RequestHook<?, ?, R>) this);
        } else {
            return request;
        }
    }

    /**
     * Provides a type-safe convenience method to modify the request if it is of a specific class.
     *
     * @param predicateType Request type to map over.
     * @param state State to pass to the mapper.
     * @param mapper Mapper that accepts the request and a state value.
     * @return the updated request.
     * @param <R> Expected request class.
     * @param <T> State value class.
     */
    @SuppressWarnings("unchecked")
    public <R, T> RequestT mapRequest(Class<R> predicateType, T state, BiFunction<RequestHook<?, ?, R>, T, R> mapper) {
        if (request.getClass() == predicateType) {
            return (RequestT) mapper.apply((RequestHook<?, ?, R>) this, state);
        } else {
            return request;
        }
    }
}
