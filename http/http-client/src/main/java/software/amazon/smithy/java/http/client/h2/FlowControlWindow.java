/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * HTTP/2 flow control window.
 *
 * <p>Fast path uses an {@link AtomicLong} CAS loop with no lock — under typical load
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
     */
    int tryAcquireUpTo(int maxBytes, long timeoutMs) throws InterruptedException {
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
                acquired = tryAcquireNonBlocking(maxBytes);
                if (acquired > 0) {
                    return acquired;
                }
                long remainingNs = deadlineNs - System.nanoTime();
                if (remainingNs <= 0) {
                    return 0;
                }
                available.awaitNanos(Math.min(remainingNs, POLL_INTERVAL_NS));
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
