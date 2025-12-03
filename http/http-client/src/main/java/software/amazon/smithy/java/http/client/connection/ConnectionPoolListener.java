/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

/**
 * Listener for connection pool lifecycle events.
 *
 * <p>Implement this interface to monitor connection pool activity for metrics collection, leak detection, logging,
 * etc. All methods have empty default implementations, so you only need to override the events you care about.
 * Listeners are called synchronously on the thread performing the pool operation. Implementations should be fast
 * and non-blocking to avoid impacting pool performance.
 *
 * <h2>Important: Do not modify connections</h2>
 * <p>Listeners receive connection references for identification and metadata access only. Do NOT call
 * {@link HttpConnection#close()}, {@link HttpConnection#newExchange}, or hold strong references to connections.
 *
 * <h2>Connection Lifecycle</h2>
 * <pre>{@code
 * New connection with successful use: onConnected → onAcquire(reused=false) → use → onReturn → (pooled, no close)
 * New connection, pool full on return: onConnected → onAcquire(reused=false) → use → onReturn → onClosed
 * Reused connection: onAcquire(reused=true) → use → onReturn → (pooled, no close)
 * Connection with error: onAcquire → use → onClosed  (no onReturn, user evicted)
 * Idle connection expires: (in pool) → onClosed
 * Pool shutdown: (in pool) → onClosed
 * }</pre>
 *
 * @see HttpConnectionPoolBuilder#addListener(ConnectionPoolListener)
 */
public interface ConnectionPoolListener {
    /**
     * Called when a new connection is fully established.
     *
     * <p>This is called after TCP connection (and TLS handshake for HTTPS) completes successfully,
     * before the connection is handed to the caller. Called once per connection lifetime.
     *
     * @param connection the newly established connection
     */
    default void onConnected(HttpConnection connection) {}

    /**
     * Called when a connection is acquired from the pool.
     *
     * <p>This is called when a connection is handed to a caller, whether it's a newly created
     * or reused pooled connection. Called each time a connection is acquired.
     *
     * @param connection the acquired connection
     * @param reused true if this is a reused pooled connection, false if newly created
     */
    default void onAcquire(HttpConnection connection, boolean reused) {}

    /**
     * Called when a user returns a connection to the pool.
     *
     * <p>This indicates the user is done with the connection. The connection may be pooled for
     * reuse or closed (if unhealthy or pool is full). If closed, {@link #onClosed} will also be called.
     *
     * <p>This is NOT called when a user evicts a connection - only {@link #onClosed} is called in that case.
     *
     * @param connection the returned connection
     */
    default void onReturn(HttpConnection connection) {}

    /**
     * Called when a connection is closed.
     *
     * <p>This is called whenever a connection is terminated, regardless of why.
     *
     * @param connection the closed connection
     * @param reason why the connection was closed
     */
    default void onClosed(HttpConnection connection, CloseReason reason) {}
}
