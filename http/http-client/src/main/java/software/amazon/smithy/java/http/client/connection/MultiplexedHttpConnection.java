/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

/**
 * Internal connection-pool surface for multiplexed HTTP connections.
 *
 * <p>This is currently used by the HTTP/2 pool path so it can manage both the
 * legacy {@code H2Connection} and alternate H2 implementations behind the same
 * load-balancing and lifecycle code.
 */
public interface MultiplexedHttpConnection extends HttpConnection {
    /**
     * Set a callback to invoke when stream capacity becomes available again.
     */
    void setStreamReleaseCallback(Runnable callback);

    /**
     * Fast check for whether this connection can accept new streams.
     */
    boolean canAcceptMoreStreams();

    /**
     * Get the active stream count if this connection can accept more streams, or -1 if not.
     */
    int getActiveStreamCountIfAccepting();

    /**
     * Get idle time in nanoseconds, or 0 if the connection is not idle.
     */
    long getIdleTimeNanos();
}
