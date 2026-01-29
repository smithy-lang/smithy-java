/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlowControlWindowTest {

    @Test
    void initialWindowIsAvailable() {
        var window = new FlowControlWindow(65535);

        assertEquals(65535, window.available());
    }

    @Test
    void tryAcquireReducesWindow() throws Exception {
        var window = new FlowControlWindow(1000);
        boolean acquired = window.tryAcquire(400, 100);

        assertTrue(acquired);
        assertEquals(600, window.available());
    }

    @Test
    void tryAcquireBlocksWhenInsufficient() throws Exception {
        var window = new FlowControlWindow(100);
        // Try to acquire more than available with short timeout
        boolean acquired = window.tryAcquire(200, 50);

        assertFalse(acquired, "Should timeout when insufficient");
    }

    @Test
    void releaseIncreasesWindow() {
        var window = new FlowControlWindow(1000);
        window.release(500);

        assertEquals(1500, window.available());
    }

    @Test
    void releaseWakesWaitingThread() throws Exception {
        var window = new FlowControlWindow(100);

        Thread acquirer = Thread.startVirtualThread(() -> {
            try {
                window.tryAcquire(200, 5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(50); // Let acquirer start waiting
        window.release(200); // Release enough
        acquirer.join(1000);

        assertEquals(100, window.available()); // 100 + 200 - 200 = 100
    }

    @Test
    void adjustIncreasesWindow() {
        var window = new FlowControlWindow(1000);
        window.adjust(500);

        assertEquals(1500, window.available());
    }

    @Test
    void adjustDecreasesWindow() {
        var window = new FlowControlWindow(1000);
        window.adjust(-300);

        assertEquals(700, window.available());
    }

    @Test
    void adjustCanMakeWindowNegative() {
        var window = new FlowControlWindow(100);
        window.adjust(-200);

        assertEquals(-100, window.available());
    }

    @Test
    void tryAcquireWithZeroTimeoutFailsImmediately() throws Exception {
        var window = new FlowControlWindow(100);
        boolean acquired = window.tryAcquire(200, 0);

        assertFalse(acquired);
    }

    @Test
    void concurrentAcquireAndRelease() throws Exception {
        var window = new FlowControlWindow(1000);
        int threads = 10;
        int iterations = 100;

        Thread[] acquirers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            acquirers[i] = Thread.startVirtualThread(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        window.tryAcquire(10, 1000);
                        window.release(10);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        for (Thread t : acquirers) {
            t.join(5000);
        }

        assertEquals(1000, window.available(), "Window should be back to initial");
    }
}
