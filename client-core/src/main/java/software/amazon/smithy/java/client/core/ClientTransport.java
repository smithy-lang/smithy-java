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
 * @implNote ClientTransports can modify a ClientBuilder to configure default functionality like adding a
 * user-agent header for HTTP requests. By default, the {@link ClientTransport#configureClient} calls the
 * ClientTransport calls the {@link MessageExchange#configureClient(ClientConfig.Builder)} method.
 * When overriding this method of a ClientTransport, you need to also call the {@code configureClient}
 * method of the {@link MessageExchange} manually, if you want it to take effect. This allows for
 * transports to override or even completely remove MessageExchange-wide functionality.
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
     * Get the message exchange.
     *
     * @return the message exchange.
     */
    MessageExchange<RequestT, ResponseT> messageExchange();

    @Override
    default void configureClient(ClientConfig.Builder config) {
        config.applyPlugin(messageExchange());
    }
}
