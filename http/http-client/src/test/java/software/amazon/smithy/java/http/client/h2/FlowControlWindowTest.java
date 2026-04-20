/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FlowControlWindowTest {

    @Test
    void initialWindowIsAvailable() {
        var window = new FlowControlWindow(65535);
        assertEquals(65535, window.available());
    }

    @Test
    void tryAcquireNonBlockingReducesWindow() {
        var window = new FlowControlWindow(1000);
        int acquired = window.tryAcquireNonBlocking(400);

        assertEquals(400, acquired);
        assertEquals(600, window.available());
    }

    @Test
    void tryAcquireNonBlockingReturnsZeroWhenEmpty() {
        var window = new FlowControlWindow(0);
        int acquired = window.tryAcquireNonBlocking(200);

        assertEquals(0, acquired);
    }

    @Test
    void tryAcquireNonBlockingAcquiresPartial() {
        var window = new FlowControlWindow(100);
        int acquired = window.tryAcquireNonBlocking(200);

        assertEquals(100, acquired);
        assertEquals(0, window.available());
    }

    @Test
    void releaseIncreasesWindow() {
        var window = new FlowControlWindow(1000);
        window.release(500);

        assertEquals(1500, window.available());
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
    void concurrentAcquireAndRelease() throws Exception {
        var window = new FlowControlWindow(1000);
        int threads = 10;
        int iterations = 100;

        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = Thread.startVirtualThread(() -> {
                for (int j = 0; j < iterations; j++) {
                    window.tryAcquireNonBlocking(10);
                    window.release(10);
                }
            });
        }

        for (Thread t : workers) {
            t.join(5000);
        }

        assertEquals(1000, window.available(), "Window should be back to initial");
    }

    @Nested
    class BlockingAcquireTest {

        @Test
        void tryAcquireUpToReturnsImmediatelyWhenWindowAvailable() throws InterruptedException {
            var window = new FlowControlWindow(1000);
            int acquired = window.tryAcquireUpTo(500, 1000);

            assertEquals(500, acquired);
            assertEquals(500, window.available());
        }

        @Test
        void tryAcquireUpToReturnsPartialWhenWindowSmaller() throws InterruptedException {
            var window = new FlowControlWindow(100);
            int acquired = window.tryAcquireUpTo(500, 1000);

            assertEquals(100, acquired);
            assertEquals(0, window.available());
        }

        @Test
        void tryAcquireUpToTimesOutWhenWindowEmpty() throws InterruptedException {
            var window = new FlowControlWindow(0);
            long start = System.nanoTime();
            int acquired = window.tryAcquireUpTo(100, 50);
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            assertEquals(0, acquired);
            assertTrue(elapsed >= 40, "Should have waited ~50ms, waited " + elapsed + "ms");
        }

        @Test
        void tryAcquireUpToWakesOnRelease() throws InterruptedException {
            var window = new FlowControlWindow(0);

            Thread releaser = Thread.startVirtualThread(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                window.release(200);
            });

            long start = System.nanoTime();
            int acquired = window.tryAcquireUpTo(100, 5000);
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            assertEquals(100, acquired);
            assertTrue(elapsed < 2000, "Should have woken up quickly after release, took " + elapsed + "ms");
            releaser.join(1000);
        }
    }
}
