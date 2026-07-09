/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * HTTP/2 flow control window.
 *
 * <p>Fast path uses an {@link AtomicLong} CAS loop with no lock. Under typical load
 * (window available, no waiters) acquires and releases are lock-free. The lock is only
 * acquired on the slow path when a caller has to wait for window.
 *
 * <p>Uses ReentrantLock instead of synchronized to avoid virtual thread pinning on the slow path.
 */
final class FlowControlWindow {

    // Poll interval for timeout checking (avoids ScheduledThreadPoolExecutor contention)
    private static final long POLL_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(10);

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();
    private final AtomicLong window;
    // Poisoned state: non-null when the connection is closed or stream errored, causing
    // waiting writers to fail fast instead of blocking until writeTimeoutMs expires.
    private volatile IOException failCause;

    FlowControlWindow(int initialWindow) {
        this.window = new AtomicLong(initialWindow);
    }

    /**
     * Try to acquire up to the requested bytes from the window without blocking.
     *
     * @return number of bytes acquired (0 if window is empty)
     */
    int tryAcquireNonBlocking(int maxBytes) {
        while (true) {
            long current = window.get();
            if (current <= 0) {
                return 0;
            }
            int acquired = (int) Math.min(current, maxBytes);
            if (window.compareAndSet(current, current - acquired)) {
                return acquired;
            }
            // CAS lost to another acquirer or a release; retry.
        }
    }

    /**
     * Try to acquire up to the requested bytes, waiting if the window is empty.
     *
     * @return number of bytes acquired (0 if timeout expired)
     * @throws IOException if the window has been poisoned (connection closed / stream error)
     */
    int tryAcquireUpTo(int maxBytes, long timeoutMs) throws InterruptedException, IOException {
        // Check poisoned state before attempting
        IOException cause = failCause;
        if (cause != null) {
            throw cause;
        }

        // Fast path: lock-free CAS
        int acquired = tryAcquireNonBlocking(maxBytes);
        if (acquired > 0) {
            return acquired;
        }

        // Slow path: window empty, take the lock and wait on condition
        lock.lock();
        try {
            long deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (true) {
                cause = failCause;
                if (cause != null) {
                    throw cause;
                }
                acquired = tryAcquireNonBlocking(maxBytes);
                if (acquired > 0) {
                    return acquired;
                }
                long remainingNs = deadlineNs - System.nanoTime();
                if (remainingNs <= 0) {
                    return 0;
                }
                // Return value intentionally ignored: the loop re-checks the window via
                // tryAcquireNonBlocking and recomputes the remaining time from deadlineNs, so the
                // nanos-left hint from awaitNanos adds nothing. A short POLL_INTERVAL_NS cap bounds
                // the wait so a missed release signal is still picked up on the next tick.
                long ignored = available.awaitNanos(Math.min(remainingNs, POLL_INTERVAL_NS));
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Release bytes back to the window.
     */
    void release(int bytes) {
        if (bytes <= 0) {
            return;
        }
        window.addAndGet(bytes);
        // Signal condition so any slow-path waiters wake up and re-try the fast path.
        // Uses tryLock to avoid pinning the writer if a VT is currently holding the lock;
        // the VT will re-check after awaitNanos returns so a missed signal is caught at the
        // next POLL_INTERVAL_NS tick.
        if (lock.tryLock()) {
            try {
                available.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Get the current available window size.
     */
    int available() {
        long cur = window.get();
        return (int) Math.max(Integer.MIN_VALUE, Math.min(cur, Integer.MAX_VALUE));
    }

    /**
     * Poison the window so that waiting and future acquirers fail fast with the given cause.
     *
     * <p>Called when the connection is closed or a stream-level error occurs, ensuring
     * that a VT blocked in {@link #tryAcquireUpTo} does not hang until writeTimeoutMs expires.
     *
     * @param cause the error to propagate to waiters
     */
    void fail(IOException cause) {
        this.failCause = cause;
        lock.lock();
        try {
            available.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adjust the window size (e.g., when SETTINGS changes initial window).
     *
     * <p>This can increase or decrease the window. If decreasing, the window
     * may become negative (valid in HTTP/2), and writers will block until
     * WINDOW_UPDATE frames restore capacity.
     */
    void adjust(int delta) {
        window.addAndGet(delta);
        if (delta > 0) {
            lock.lock();
            try {
                available.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
}
