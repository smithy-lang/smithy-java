/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLException;
import software.amazon.smithy.java.client.core.error.ConnectTimeoutException;
import software.amazon.smithy.java.client.core.error.TlsException;
import software.amazon.smithy.java.client.core.error.TransportException;
import software.amazon.smithy.java.client.core.error.TransportProtocolException;
import software.amazon.smithy.java.client.core.error.TransportSocketException;
import software.amazon.smithy.java.client.core.error.TransportSocketTimeout;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.CallException;

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
     * <p>Transports must only throw exceptions that extend from {@link TransportException} or {@link CallException},
     * mapping the exceptions thrown by the underlying implementation to the most specific subtype of
     * {@code TransportException}.
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

    /**
     * Remaps a thrown exception to an appropriate {@link TransportException} or {@link CallException}.
     *
     * <p>This method attempts to map built-in JDK exceptions to the appropriate subclass of
     * {@code TransportException}.
     *
     * @param e Exception to map to a {@link TransportException} or subclass.
     * @return the remapped exception. A given {@code CallException} or {@code TransportException} is returned as-is.
     */
    static CallException remapExceptions(Throwable e) {
        if (e instanceof CallException ce) {
            return ce; // rethrow CallException and TransportException as-is.
        } else if (e instanceof ConnectException) {
            return new ConnectTimeoutException(e);
        } else if (e instanceof SocketTimeoutException) {
            return new TransportSocketTimeout(e);
        } else if (e instanceof SocketException) {
            return new TransportSocketException(e);
        } else if (e instanceof SSLException) {
            return new TlsException(e);
        } else if (e instanceof ProtocolException) {
            return new TransportProtocolException(e);
        } else {
            // Wrap all other exceptions as a TransportException.
            return new TransportException(e);
        }
    }
}
