/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.time.Duration;

/**
 * Connection pool for managing HTTP connections.
 *
 * <p>Pools connections by {@link Route}, validates health before reuse, and enforces connection limits.
 * All methods must be thread-safe.
 *
 * @see HttpConnectionPool
 */
public interface ConnectionPool extends AutoCloseable {
    /**
     * Acquire a connection for the given route.
     *
     * <p>Returns a pooled connection if available and healthy, otherwise creates new.
     * Blocks until a connection is available or limits are exceeded.
     *
     * @param route the route to connect to
     * @return a usable connection
     * @throws IOException if connection cannot be established
     * @throws IllegalStateException if pool is closed
     */
    HttpConnection acquire(Route route) throws IOException;

    /**
     * Release a connection back to the pool for reuse.
     *
     * <p>Use for normal completion. Use {@link #evict} if connection is broken.
     *
     * @param connection the connection to release
     */
    void release(HttpConnection connection);

    /**
     * Evict a connection without returning it to the pool.
     *
     * <p>Use when the connection should not be reused. The connection is closed immediately.
     *
     * @param connection the connection to evict
     * @param isError true if eviction is due to an error (IOException, protocol error, etc.),
     *                false for intentional eviction
     */
    void evict(HttpConnection connection, boolean isError);

    /**
     * Gracefully shut down, waiting for active connections to complete.
     *
     * @param gracePeriod maximum time to wait before force-closing
     * @throws IOException if connections fail to close
     */
    void shutdown(Duration gracePeriod) throws IOException;

    /**
     * Close the pool and all connections. Idempotent.
     *
     * @throws IOException if connections fail to close
     */
    @Override
    void close() throws IOException;
}
