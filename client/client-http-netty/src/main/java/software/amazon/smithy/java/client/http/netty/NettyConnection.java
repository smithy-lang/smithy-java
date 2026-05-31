/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.channel.Channel;
import java.util.concurrent.atomic.AtomicBoolean;
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
    // True when this connection was handed out by reuse of a previously-pooled connection rather
    // than freshly opened. Set by the pool at hand-out time and read once by the caller immediately
    // after acquire; only reused connections can be stale keep-alives closed server-side.
    boolean fromReuse;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    NettyConnection(Channel channel, Mode mode, Route route) {
        this.channel = channel;
        this.mode = mode;
        this.route = route;
        this.lastUsedNanos = System.nanoTime();
    }

    boolean isActive() {
        return !closed.get() && channel.isActive();
    }

    boolean isClosed() {
        return closed.get();
    }

    void markClosed() {
        closed.set(true);
    }

    /**
     * Atomically marks this connection closed, returning {@code true} only for the caller that won
     * the race. Used to make disposal idempotent so connection-count accounting decrements exactly
     * once even though {@code dispose} can be triggered both explicitly and by the channel's
     * close-future listener.
     */
    boolean markClosedOnce() {
        return closed.compareAndSet(false, true);
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
