/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.net.URI;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.schema.SdkException;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;

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
     * Updates the underlying transport request with the given URI.
     *
     * @param request  Request to update.
     * @param endpoint Where to send the request.
     * @return Returns the request to send.
     */
    RequestT updateRequest(RequestT request, URI endpoint);

    /**
     * Sends the underlying transport request and returns the response.
     *
     * @param call    Call being sent.
     * @param request Request to send.
     * @return Returns the response.
     */
    ResponseT sendRequest(ClientCall<?, ?> call, RequestT request);

    /**
     * Deserializes the output from the transport response or throws a modeled or unmodeled exception.
     *
     * @param call     Call being sent.
     * @param request  Request that was sent for this response.
     * @param response Response to deserialize.
     * @return the deserialized output shape.
     * @throws SdkException if an error occurs, including deserialized modeled errors and protocol errors.
     */
    <I extends SerializableShape, O extends SerializableShape> O deserializeResponse(
            ClientCall<I, O> call, RequestT request, ResponseT response);
}
