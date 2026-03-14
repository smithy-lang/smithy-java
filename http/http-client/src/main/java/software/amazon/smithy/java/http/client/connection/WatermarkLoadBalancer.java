/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Watermark-based load balancer for HTTP/2 connections.
 *
 * <p>Green zone (under soft limit): round-robin.
 * Expansion: all above soft limit and under max connections → {@link H2LoadBalancer#CREATE_NEW}.
 * Red zone (at max connections): least-loaded under hard limit.
 * Saturated: returns {@link H2LoadBalancer#SATURATED}.
 */
final class WatermarkLoadBalancer implements H2LoadBalancer {

    private final int softLimit;
    private final int hardLimit;
    private final AtomicInteger nextIndex = new AtomicInteger(0);

    WatermarkLoadBalancer(int softLimit, int hardLimit) {
        if (softLimit > hardLimit) {
            throw new IllegalArgumentException("Soft limit must not exceed hard limit");
        }

        this.softLimit = softLimit;
        this.hardLimit = hardLimit;
    }

    @Override
    public int select(int[] activeStreams, int connectionCount, int maxConnections) {
        // Green zone: round-robin among connections under soft limit
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

        // Expansion: all above soft limit, create new if allowed
        if (connectionCount < maxConnections) {
            return H2LoadBalancer.CREATE_NEW;
        }

        // Red zone: least-loaded under hard limit
        int bestIdx = H2LoadBalancer.SATURATED;
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
}
