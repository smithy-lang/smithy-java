/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import software.amazon.smithy.java.http.api.HttpRequest;

/**
 * Protocol-agnostic HTTP connection.
 * Implementations handle HTTP/1.1, HTTP/2, etc.
 */
public interface HttpConnection extends AutoCloseable {
    /**
     * Create a new HTTP exchange on this connection.
     * For HTTP/1.1: only one exchange at a time.
     * For HTTP/2: multiple concurrent exchanges (multiplexing).
     */
    HttpExchange newExchange(HttpRequest request) throws IOException;

    /**
     * Protocol version of this connection (HTTP/1.1, HTTP/2, etc.)
     */
    String protocol();

    /**
     * Check if connection is still usable.
     */
    boolean isActive();

    /**
     * Close the underlying transport.
     */
    @Override
    void close() throws IOException;
}
