/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * HTTP/2 flow control window backed by a Semaphore.
 *
 * <p>Wraps acquire/release semantics for flow control with Virtual Thread-friendly
 * blocking. Uses an unfair semaphore for throughput over ordering.
 */
final class FlowControlWindow {

    private final Semaphore permits;

    /**
     * Create a flow control window.
     *
     * @param initialWindow the initial window size (e.g., 65535 for HTTP/2 default)
     */
    FlowControlWindow(int initialWindow) {
        // Use unfair semaphore for better throughput (no FIFO ordering overhead)
        this.permits = new Semaphore(initialWindow, false);
    }

    /**
     * Acquire permits with a timeout.
     *
     * @param bytes number of bytes to acquire
     * @param timeout maximum time to wait
     * @param unit time unit for timeout
     * @return true if permits acquired, false if timeout expired
     * @throws InterruptedException if interrupted while waiting
     */
    boolean tryAcquire(int bytes, long timeout, TimeUnit unit) throws InterruptedException {
        return permits.tryAcquire(bytes, timeout, unit);
    }

    /**
     * Release permits back to the window.
     *
     * @param bytes number of bytes to release
     */
    void release(int bytes) {
        if (bytes > 0) {
            permits.release(bytes);
        }
    }

    /**
     * Get the current available window size.
     *
     * @return available bytes in the window
     */
    int available() {
        return permits.availablePermits();
    }

    /**
     * Adjust the window size (e.g., when SETTINGS changes initial window).
     *
     * <p>This can increase or decrease the window. If decreasing and the new window would be negative, this may
     * cause subsequent acquires to block until the window recovers.
     *
     * @param delta change in window size (positive or negative)
     */
    void adjust(int delta) {
        if (delta > 0) {
            permits.release(delta);
        } else if (delta < 0) {
            // Reducing window: try to acquire the permits. If not enough available, the window goes "into debt"
            // and future acquires will block longer.
            var _ignored = permits.tryAcquire(-delta);
        }
    }
}
