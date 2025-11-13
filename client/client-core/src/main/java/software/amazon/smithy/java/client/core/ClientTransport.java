/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
 */
public interface ClientTransport<RequestT, ResponseT> {
    /**
     * Send a prepared request.
     *
     * <p>Transports must only throw exceptions that extend from {@link TransportException} or {@link CallException},
     * mapping the exceptions thrown by the underlying implementation to the most specific subtype of
     * {@code TransportException}.
     *
     * @param context Call context.
     * @param request Request to send.
     * @return the response.
     */
    ResponseT send(Context context, RequestT request);

    /**
     * Get the message exchange.
     *
     * @return the message exchange.
     */
    MessageExchange<RequestT, ResponseT> messageExchange();

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
        return switch (e) {
            case CallException ce -> ce; // rethrow CallException and TransportException as-is.
            case ConnectException connectException -> new ConnectTimeoutException(e);
            case SocketTimeoutException socketTimeoutException -> new TransportSocketTimeout(e);
            case SocketException socketException -> new TransportSocketException(e);
            case SSLException sslException -> new TlsException(e);
            case ProtocolException protocolException -> new TransportProtocolException(e);
            // Wrap all other exceptions as a TransportException.
            case null, default -> new TransportException(e);
        };
    }
}
