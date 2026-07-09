/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tick-based read-timeout watchdog state for a single stream.
 *
 * <p>Instead of calling {@code System.nanoTime()} (expensive) on every read, activity is
 * tracked against the muxer's coarse tick counter (a cheap volatile read, 1 tick =
 * {@link H2Muxer#TIMEOUT_POLL_INTERVAL_MS}). The muxer's timeout sweep reads {@link #deadlineTick()}
 * and {@link #seq()} to detect a stalled stream and claims the timeout at-most-once via
 * {@link #markTimedOut()}.
 */
final class H2ReadTimeout {

    private final H2Muxer muxer;
    private final int timeoutTicks; // Number of ticks before timeout (0 = no timeout)
    private final AtomicLong seq = new AtomicLong(); // Activity counter, incremented on read activity
    private volatile int deadlineTick; // 0 = no deadline, >0 = deadline tick
    private final AtomicBoolean timedOut = new AtomicBoolean(); // At-most-once timeout flag

    H2ReadTimeout(H2Muxer muxer, long timeoutMs) {
        this.muxer = muxer;
        // Convert timeout to ticks: ceil(timeoutMs / pollIntervalMs)
        this.timeoutTicks = timeoutMs <= 0
                ? 0
                : Math.max(1, (int) Math.ceil((double) timeoutMs / H2Muxer.TIMEOUT_POLL_INTERVAL_MS));
    }

    /**
     * Record read activity: bump the sequence and reset the deadline. Called when headers or data arrive.
     */
    void onActivity() {
        if (timeoutTicks > 0) {
            seq.incrementAndGet();
            deadlineTick = muxer.currentTimeoutTick() + timeoutTicks;
        }
    }

    /**
     * Clear the read deadline (no timeout).
     */
    void clearDeadline() {
        deadlineTick = 0;
    }

    /**
     * Deadline tick (0 = no deadline).
     */
    int deadlineTick() {
        return deadlineTick;
    }

    /**
     * Read activity sequence number.
     */
    long seq() {
        return seq.get();
    }

    /**
     * Attempt to mark this stream as timed out. Returns true if successful (first caller wins).
     */
    boolean markTimedOut() {
        return timedOut.compareAndSet(false, true);
    }
}
