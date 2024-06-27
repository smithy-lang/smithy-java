/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.schema.ApiException;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

/**
 * Serializes inputs, sends requests on the wire, and deserializes responses.
 */
public interface ClientTransport {
    /**
     * Send a call using the transport and protocol.
     *
     * <p>The request to send can be retrieved by grabbing it from the call context using
     * {@link #requestKey()}.
     *
     * <p>The transport is required to set the appropriate response in the context using
     * {@link #responseKey()} before completing the returned future.
     *
     * @param call Call associated with the request.
     * @return a CompletableFuture that is completed when the response is set on the context.
     * @throws ModeledApiException if a modeled error occurs.
     * @throws ApiException if an error occurs.
     */
    <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<Void> send(ClientCall<I, O> call);

    /**
     * The request type and context key used by transport.
     *
     * <p>The transport expects that this key is present in the context of a call to send.
     *
     * @return the context key.
     */
    Context.Key<?> requestKey();

    /**
     * The response type and context key used by the transport.
     *
     * <p>The transport is required to set this context key in the context of the call before returning.
     *
     * @return the context key.
     */
    Context.Key<?> responseKey();
}
