/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.endpoint.api.Endpoint;
import software.amazon.smithy.java.runtime.core.schema.ApiException;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;

/**
 * Handles request and response serialization.
 *
 * @param <RequestT> Request type to create.
 * @param <ResponseT> Response type to create.
 */
public interface ClientProtocol<RequestT, ResponseT> {
    /**
     * Get the ID of the protocol (e.g., aws.protocols#restJson1).
     *
     * @return the protocol ID.
     */
    String id();

    /**
     * The request class used by protocol.
     *
     * @return the request class.
     */
    Class<RequestT> requestClass();

    /**
     * The response class used by the protocol.
     *
     * @return the response class.
     */
    Class<ResponseT> responseClass();

    /**
     * Creates the underlying transport request.
     *
     * @param operation Operation to create request for.
     * @param input     Input shape for the request.
     * @param context   Context for the request.
     * @param endpoint  Where to send the request.
     * @return Returns the request to send.
     */
    <I extends SerializableStruct, O extends SerializableStruct> RequestT createRequest(
        ApiOperation<I, O> operation,
        I input,
        Context context,
        URI endpoint
    );

    /**
     * Updates the underlying transport request to use the service endpoint.
     *
     * <p>The service endpoint should be considered the root of the endpoint, and any existing endpoint information
     * on the request should be combined with the service endpoint. The actual behavior of how the endpoint is
     * combined is protocol-specific. For example, with HTTP protocols, any path found on the request
     * should be concatenated to the end of the service endpoint's path, if any.
     *
     * @param request  Request to update.
     * @param endpoint Where to send the request.
     * @return Returns the request to send.
     */
    RequestT setServiceEndpoint(RequestT request, Endpoint endpoint);

    /**
     * Deserializes the output from the transport response or throws a modeled or unmodeled exception.
     *
     * <p>For modeled exceptions, the {@code errorCreator} is used to build the error. If the errorCreator returns null
     * or none is provided, the protocol can create an error based on protocol hints (e.g., HTTP status codes).
     *
     * @param request  Request that was sent for this response.
     * @param response Response to deserialize.
     * @param errorCreator A function used to create an error based on the context and extracted shape ID.
     * @return the deserialized output shape.
     * @throws ApiException if an error occurs, including deserialized modeled errors and protocol errors.
     */
    <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> deserializeResponse(
        ApiOperation<I, O> operation,
        Context context,
        BiFunction<Context, String, ShapeBuilder<ModeledApiException>> errorCreator,
        RequestT request,
        ResponseT response
    );
}
