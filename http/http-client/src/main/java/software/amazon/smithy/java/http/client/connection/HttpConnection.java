/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import javax.net.ssl.SSLSession;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpExchange;

/**
 * Protocol-agnostic HTTP connection.
 */
public interface HttpConnection extends AutoCloseable {
    /**
     * Create a new HTTP exchange on this connection.
     *
     * <p>For HTTP/1.1: only one exchange at a time. For HTTP/2: multiple concurrent exchanges (multiplexing).
     *
     * @param request the HTTP request to execute
     * @return a new exchange for this request
     * @throws IOException           if the connection is not in a valid state or network error occurs
     * @throws IllegalStateException if connection is closed
     */
    HttpExchange newExchange(HttpRequest request) throws IOException;

    /**
     * Protocol version of this connection.
     *
     * @return HTTP version (HTTP/1.1, HTTP/2, etc.)
     */
    HttpVersion httpVersion();

    /**
     * Get the destination of the HTTP connection.
     *
     * @return the request route.
     */
    Route route();

    /**
     * Get SSL session if this is a secure connection.
     *
     * <p>Provides access to negotiated cipher suite, TLS version, peer certificates, etc.
     *
     * @return SSLSession, or null if not using TLS
     */
    SSLSession sslSession();

    /**
     * Get ALPN negotiated protocol if applicable.
     *
     * @return "h2", "http/1.1", or null if ALPN not used
     */
    String negotiatedProtocol();

    /**
     * Check if connection is still usable for new requests.
     *
     * <p>This is meant to be a fast check suitable for frequent calls. For connections retrieved from a pool after
     * being idle, use {@link #validateForReuse()} which performs more thorough checks.
     *
     * @return true if connection can be used for new exchanges
     */
    boolean isActive();

    /**
     * Thorough validation to check if a pooled connection can be reused.
     *
     * <p>This is more expensive than {@link #isActive()} but catches connections that were closed by the server while
     * idle in the pool. Should be called when retrieving a connection that has been idle.
     *
     * <p>The default implementation just calls {@link #isActive()}.
     *
     * @return true if connection is healthy and usable
     */
    default boolean validateForReuse() {
        return isActive();
    }

    /**
     * Close the underlying transport.
     * Any active exchanges will be terminated.
     */
    @Override
    void close() throws IOException;
}
