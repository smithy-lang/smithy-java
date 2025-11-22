/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Manages a pool of HTTP connections with automatic lifecycle management.
 * Thread-safe.
 */
public interface ConnectionPool extends AutoCloseable {
    /**
     * Acquire a connection for the given URI.
     * May return an existing pooled connection or create a new one.
     * Blocks if pool is exhausted and maxConnectionsPerHost is reached.
     *
     * @param uri target URI (scheme, host, port determine pool key)
     * @return active connection ready for use
     */
    HttpConnection acquire(URI uri) throws IOException;

    /**
     * Release a connection back to the pool for reuse.
     * If connection is not active, it will be closed instead of pooled.
     *
     * @param connection connection to release
     * @param uri the URI this connection was used for
     */
    void release(HttpConnection connection, URI uri);

    /**
     * Remove and close a connection (called on errors).
     *
     * @param connection connection to evict
     * @param uri the URI this connection was used for
     */
    void evict(HttpConnection connection, URI uri);

    /**
     * Close all pooled connections and shut down the pool.
     */
    @Override
    void close() throws IOException;

    /**
     * Get pool statistics (optional, for monitoring).
     */
    Stats stats();

    record Stats(
            int totalConnections,
            int activeConnections,
            int idleConnections,
            Map<String, Integer> connectionsPerHost) {}
}
