/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Internal diagnostic counters for an H2 connection.
 *
 * <p>All counters use {@link LongAdder} for contention-free increments on hot paths.
 * Max gauges use {@link AtomicLong} with CAS updates.
 *
 * <p>Package-private. Not a public API — shape may change without notice.
 */
final class H2ConnectionStats {

    // --- Frame counters (reader thread) ---
    final LongAdder dataFramesRead = new LongAdder();
    final LongAdder dataBytesRead = new LongAdder();

    // --- Signal batching (reader thread) ---
    final LongAdder signalsSent = new LongAdder();
    final LongAdder signalsDeferred = new LongAdder();

    // --- Flow control ---
    final LongAdder streamWindowUpdates = new LongAdder();
    final LongAdder connectionWindowUpdates = new LongAdder();
    final LongAdder connWindowUpdatesReceived = new LongAdder();
    final LongAdder connWindowBytesReceived = new LongAdder();
    final LongAdder dataBytesQueued = new LongAdder();
    final LongAdder dataBytesReleased = new LongAdder();

    // --- Send window contention (muxer-writer / VT sender) ---
    final LongAdder connWindowAcquires = new LongAdder(); // total acquireConnectionWindowUpTo calls
    final LongAdder connWindowWaits = new LongAdder(); // calls that had to queue (slow path)
    final LongAdder connWindowWaitNs = new LongAdder(); // total nanos spent parked waiting
    final AtomicLong maxConnWindowWaiters = new AtomicLong(); // peak waiter queue depth

    // --- Buffer pool ---
    final LongAdder buffersBorrowed = new LongAdder();
    final LongAdder buffersReused = new LongAdder();
    final LongAdder buffersAllocated = new LongAdder();
    final LongAdder buffersDropped = new LongAdder();

    // --- Queue depth gauges ---
    final AtomicLong maxQueuedBytesPerStream = new AtomicLong();
    final AtomicLong maxQueuedBytesPerConnection = new AtomicLong();

    void updateMaxQueued(AtomicLong gauge, long value) {
        long prev;
        while (value > (prev = gauge.get())) {
            if (gauge.compareAndSet(prev, value)) {
                return;
            }
        }
    }

    @Override
    public String toString() {
        return "H2ConnectionStats{"
                + "dataFrames=" + dataFramesRead.sum()
                + ", dataBytes=" + dataBytesRead.sum()
                + ", signalsSent=" + signalsSent.sum()
                + ", signalsDeferred=" + signalsDeferred.sum()
                + ", streamWU=" + streamWindowUpdates.sum()
                + ", connWU=" + connectionWindowUpdates.sum()
                + ", connWURx=" + connWindowUpdatesReceived.sum()
                + ", connWUBytesRx=" + connWindowBytesReceived.sum()
                + ", connAcq=" + connWindowAcquires.sum()
                + ", connWaits=" + connWindowWaits.sum()
                + ", connWaitMs=" + (connWindowWaitNs.sum() / 1_000_000)
                + ", maxConnWaiters=" + maxConnWindowWaiters.get()
                + ", queued=" + dataBytesQueued.sum()
                + ", released=" + dataBytesReleased.sum()
                + ", borrowed=" + buffersBorrowed.sum()
                + ", reused=" + buffersReused.sum()
                + ", allocated=" + buffersAllocated.sum()
                + ", dropped=" + buffersDropped.sum()
                + ", maxQueueStream=" + maxQueuedBytesPerStream.get()
                + ", maxQueueConn=" + maxQueuedBytesPerConnection.get()
                + '}';
    }
}
