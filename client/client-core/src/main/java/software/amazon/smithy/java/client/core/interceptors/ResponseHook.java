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
     * Casts the given response value to the response type of this hook.
     *
     * <p>This is useful when modifying the response using {@code instanceof} pattern matching:
     * {@snippet :
     * if (hook.response() instanceof HttpResponse resp) {
     *     return hook.asResponseType(resp.toBuilder().withAddedHeader("X-Foo", "Bar").build());
     * }
     * return hook.response();
     * }
     *
     * @param response Response value to cast.
     * @return the response value cast to the response type.
     */
    @SuppressWarnings("unchecked")
    public ResponseT asResponseType(Object response) {
        return (ResponseT) response;
    }
}
