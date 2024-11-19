/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.context.Context;

/**
 * Sends a serialized request and returns a response.
 *
 * @implNote To be discoverable by dynamic clients and client code generators,
 * ClientTransport's should implement a {@link ClientTransportFactory} service provider.
 */
public interface ClientTransport<RequestT, ResponseT> {
    /**
     * Send a prepared request.
     *
     * @param context Call context.
     * @param request Request to send.
     * @return a CompletableFuture that is completed with the response.
     */
    CompletableFuture<ResponseT> send(Context context, RequestT request);

    /**
     * The request class used by transport.
     *
     * @return the request class.
     */
    Class<RequestT> requestClass();

    /**
     * The response class used by the transport.
     *
     * @return the response class.
     */
    Class<ResponseT> responseClass();
}
