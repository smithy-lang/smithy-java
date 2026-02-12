/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

/**
 * Strategy for selecting hat HTTP/2 connection to use or whether to create a new one.
 *
 * <p>The strategy receives an array of active stream counts (one per connection) and returns the index of the
 * connection to use, or -1 to signal that a new connection should be created.
 */
@FunctionalInterface
public interface H2LoadBalancer {

    /** Return value indicating a new connection should be created. */
    int CREATE_NEW = -1;

    /** Return value indicating all connections are saturated. */
    int SATURATED = -2;

    /**
     * Select a connection index or signal new connection creation.
     *
     * <p>When {@code maxConnections == connectionCount}, the balancer must not return {@link #CREATE_NEW}
     * (no room to expand). Return a valid index or {@link #SATURATED} instead.
     *
     * @param activeStreams active stream count per connection; a value of -1 means
     *                      the connection is not accepting new streams
     * @param connectionCount number of valid entries in activeStreams
     * @param maxConnections maximum connections allowed; equals connectionCount when expansion is not possible
     * @return index into activeStreams to use, {@link #CREATE_NEW}, or {@link #SATURATED}
     */
    int select(int[] activeStreams, int connectionCount, int maxConnections);

    /**
     * Create a watermark-based load balancer.
     *
     * <p>Uses a two-tier strategy: prefers connections under the soft limit via round-robin,
     * expands when all exceed it, and falls back to least-loaded up to the hard limit.
     *
     * @param softLimit expand to a new connection when all connections have at least this many streams
     * @param hardLimit maximum streams per connection (never exceed this)
     * @return a watermark load balancer
     */
    static H2LoadBalancer watermark(int softLimit, int hardLimit) {
        return new WatermarkLoadBalancer(softLimit, hardLimit);
    }
}
