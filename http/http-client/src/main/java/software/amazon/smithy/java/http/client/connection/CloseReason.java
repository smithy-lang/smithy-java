/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

/**
 * Reason why a connection was closed by the connection pool.
 */
public enum CloseReason {
    /**
     * Connection was idle too long.
     *
     * <p>Used when the pool's background cleanup removes idle connections.
     */
    IDLE_TIMEOUT,

    /**
     * Connection was closed unexpectedly.
     *
     * <p>The socket was closed by the peer, reset, or encountered an I/O error.
     */
    UNEXPECTED_CLOSE,

    /**
     * Connection couldn't be pooled because the pool was full.
     *
     * <p>The user returned the connection but the per-route pool was at capacity.
     */
    POOL_FULL,

    /**
     * Connection closed due to pool shutdown.
     *
     * <p>The pool is closing and all connections are being terminated.
     */
    POOL_SHUTDOWN,

    /**
     * User explicitly evicted the connection.
     *
     * <p>The user called {@link ConnectionPool#evict} with {@code isError=false}.
     */
    EVICTED,

    /**
     * User evicted the connection due to an error.
     *
     * <p>The user called {@link ConnectionPool#evict} with {@code isError=true},
     * indicating an error occurred during use.
     */
    ERRORED
}
