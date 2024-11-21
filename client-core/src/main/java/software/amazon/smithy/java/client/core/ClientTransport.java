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
 *
 * <p>ClientTransport also functions as a {@link ClientPlugin}, but is only applied to a client configuration when
 * used with a request. The protocol and transport of a client can change per/request using
 * {@link RequestOverrideConfig}, so a ClientTransport plugin only configures the client when actually in use.
 * This kind of transport-dependent configuration can be useful for applying transport-specific functionality
 * to a call (e.g., adding a user-agent header to a request). ClientTransport is applied as a plugin after other
 * user-defined plugins are applied.
 */
public interface ClientTransport<RequestT, ResponseT> extends ClientPlugin {
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

    @Override
    default void configureClient(ClientConfig.Builder config) {
        // By default, does nothing.
    }
}
