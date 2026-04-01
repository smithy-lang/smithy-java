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
     * Casts the given request value to the request type of this hook.
     *
     * <p>This is useful when modifying the request using {@code instanceof} pattern matching:
     * {@snippet :
     * if (hook.request() instanceof HttpRequest req) {
     *     return hook.asRequestType(req.toModifiableCopy().addHeader("X-Foo", "Bar"));
     * }
     * return hook.request();
     * }
     *
     * @param request Request value to cast.
     * @return the request value cast to the request type.
     */
    @SuppressWarnings("unchecked")
    public RequestT asRequestType(Object request) {
        return (RequestT) request;
    }
}
