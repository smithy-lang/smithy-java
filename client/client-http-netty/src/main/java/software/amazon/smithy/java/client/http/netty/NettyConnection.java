/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.channel.Channel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pooled Netty connection. Either H1 (single in-flight request at a time) or H2 (multiplexed).
 */
final class NettyConnection {
    enum Mode {
        H1, H2
    }

    final Channel channel;
    final Mode mode;
    final Route route;
    final AtomicInteger inFlightStreams = new AtomicInteger(0);
    volatile long lastUsedNanos;
    private volatile boolean closed;

    NettyConnection(Channel channel, Mode mode, Route route) {
        this.channel = channel;
        this.mode = mode;
        this.route = route;
        this.lastUsedNanos = System.nanoTime();
    }

    boolean isActive() {
        return !closed && channel.isActive();
    }

    boolean isClosed() {
        return closed;
    }

    void markClosed() {
        closed = true;
    }

    boolean canAcceptMoreStreams(int h2MaxStreams) {
        return mode == Mode.H2 && inFlightStreams.get() < h2MaxStreams;
    }

    int acquireStream() {
        return inFlightStreams.incrementAndGet();
    }

    void releaseStream() {
        inFlightStreams.decrementAndGet();
        lastUsedNanos = System.nanoTime();
    }

    @Override
    public String toString() {
        return "NettyConnection{" + mode + " " + route + " inFlight=" + inFlightStreams.get() + "}";
    }
}
