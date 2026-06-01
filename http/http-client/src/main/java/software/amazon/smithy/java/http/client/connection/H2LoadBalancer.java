/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Strategy for selecting the HTTP/2 connection to use or whether to create a new one.
 *
 * <p>The strategy receives an array of active stream counts (one per connection) and returns the index of the
 * connection to use, or -1 to signal that a new connection should be created.
 */
@FunctionalInterface
interface H2LoadBalancer {

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
     * Create the default watermark-based HTTP/2 load balancer.
     *
     * <p>Green zone (under soft limit): round-robin.
     * Expansion: all above soft limit and under max connections → {@link #CREATE_NEW}.
     * Red zone (at max connections): least-loaded under hard limit.
     * Saturated: returns {@link #SATURATED}.
     *
     * @param softLimit stream count where the balancer starts preferring expansion
     * @param hardLimit maximum stream count accepted on an existing connection
     * @return the load balancer
     */
    static H2LoadBalancer watermark(int softLimit, int hardLimit) {
        if (softLimit > hardLimit) {
            throw new IllegalArgumentException("Soft limit must not exceed hard limit");
        }

        return new H2LoadBalancer() {
            private final AtomicInteger nextIndex = new AtomicInteger();

            @Override
            public int select(int[] activeStreams, int connectionCount, int maxConnections) {
                // Green zone: round-robin among connections under soft limit.
                if (connectionCount > 0) {
                    int start = (nextIndex.getAndIncrement() & Integer.MAX_VALUE) % connectionCount;
                    for (int i = 0; i < connectionCount; i++) {
                        int idx = start + i;
                        if (idx >= connectionCount) {
                            idx -= connectionCount;
                        }
                        int active = activeStreams[idx];
                        if (active >= 0 && active < softLimit) {
                            return idx;
                        }
                    }
                }

                // Expansion: all above soft limit, create new if allowed.
                if (connectionCount < maxConnections) {
                    return CREATE_NEW;
                }

                // Red zone: least-loaded under hard limit.
                int bestIdx = SATURATED;
                int bestActive = Integer.MAX_VALUE;
                for (int i = 0; i < connectionCount; i++) {
                    int active = activeStreams[i];
                    if (active >= 0 && active < hardLimit && active < bestActive) {
                        bestIdx = i;
                        bestActive = active;
                        if (active == 0) {
                            break;
                        }
                    }
                }

                return bestIdx;
            }
        };
    }
}
