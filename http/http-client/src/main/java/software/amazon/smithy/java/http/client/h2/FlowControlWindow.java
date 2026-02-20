/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * HTTP/2 flow control window.
 *
 * <p>Uses ReentrantLock instead of synchronized to avoid virtual thread pinning.
 */
final class FlowControlWindow {

    // Poll interval for timeout checking (avoids ScheduledThreadPoolExecutor contention)
    private static final long POLL_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(10);

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();
    private long window;

    /**
     * Create a flow control window.
     *
     * @param initialWindow the initial window size (e.g., 65535 for HTTP/2 default)
     */
    FlowControlWindow(int initialWindow) {
        this.window = initialWindow;
    }

    /**
     * Try to acquire up to the requested bytes from the window without blocking.
     *
     * @param maxBytes maximum number of bytes to acquire
     * @return number of bytes acquired (0 if window is empty)
     */
    int tryAcquireNonBlocking(int maxBytes) {
        lock.lock();
        try {
            if (window > 0) {
                int acquired = (int) Math.min(window, maxBytes);
                window -= acquired;
                return acquired;
            }
            return 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Try to acquire up to the requested bytes from the window.
     *
     * <p>This method acquires as many bytes as available (up to the requested amount),
     * waiting only if the window is completely empty. Uses short polling intervals
     * to avoid contention on the ScheduledThreadPoolExecutor used for long timed waits.
     *
     * @param maxBytes maximum number of bytes to acquire
     * @param timeoutMs maximum time to wait in milliseconds
     * @return number of bytes acquired (0 if timeout expired)
     * @throws InterruptedException if interrupted while waiting
     */
    int tryAcquireUpTo(int maxBytes, long timeoutMs) throws InterruptedException {
        lock.lock();
        try {
            // Fast path: window has capacity
            if (window > 0) {
                int acquired = (int) Math.min(window, maxBytes);
                window -= acquired;
                return acquired;
            }

            // Slow path: poll with short intervals to avoid timed-wait contention
            long remainingNs = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (window <= 0) {
                if (remainingNs <= 0) {
                    return 0; // Timeout
                }
                // Use short poll interval instead of full timeout
                long waitNs = Math.min(remainingNs, POLL_INTERVAL_NS);
                remainingNs = available.awaitNanos(waitNs);
            }

            int acquired = (int) Math.min(window, maxBytes);
            window -= acquired;
            return acquired;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Release bytes back to the window.
     *
     * @param bytes number of bytes to release
     */
    void release(int bytes) {
        if (bytes > 0) {
            lock.lock();
            try {
                window += bytes;
                available.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Get the current available window size.
     *
     * @return available bytes in the window (may be negative if window was shrunk)
     */
    int available() {
        lock.lock();
        try {
            return (int) Math.min(window, Integer.MAX_VALUE);
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
     *
     * @param delta change in window size (positive or negative)
     */
    void adjust(int delta) {
        lock.lock();
        try {
            window += delta;
            if (delta > 0) {
                available.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }
}
