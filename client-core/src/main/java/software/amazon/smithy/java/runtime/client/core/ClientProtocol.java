/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.client.endpoint.api.Endpoint;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.schema.ApiException;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

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
     * The request type and context key used by the client protocol.
     *
     * @return the context key.
     */
    Context.Key<RequestT> requestKey();

    /**
     * The response type and context key used by the client protocol.
     *
     * @return the context key.
     */
    Context.Key<ResponseT> responseKey();

    /**
     * Creates the underlying transport request.
     *
     * @param call     Call being sent.
     * @param endpoint Where to send the request.
     * @return Returns the request to send.
     */
    RequestT createRequest(ClientCall<?, ?> call, URI endpoint);

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
     * @param call     Call being sent.
     * @param request  Request that was sent for this response.
     * @param response Response to deserialize.
     * @return the deserialized output shape.
     * @throws ApiException if an error occurs, including deserialized modeled errors and protocol errors.
     */
    <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> deserializeResponse(
        ClientCall<I, O> call,
        RequestT request,
        ResponseT response
    );
}
