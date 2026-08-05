/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.retries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.retries.api.AcquireInitialTokenRequest;
import software.amazon.smithy.java.retries.api.RecordSuccessRequest;
import software.amazon.smithy.java.retries.api.RefreshRetryTokenRequest;
import software.amazon.smithy.java.retries.api.RetryInfo;
import software.amazon.smithy.java.retries.api.RetrySafety;
import software.amazon.smithy.java.retries.api.TokenAcquisitionFailedException;

class AdaptiveRetryStrategyIntegrationTest {
    private static final int THREAD_COUNT = 5;
    private static final int CALLS_PER_THREAD = 10;
    private static final long RATE_LIMIT_MS = 5;

    @Test
    void rateLimiterThrottlesClientsToServerRate() throws InterruptedException {
        var strategy = AdaptiveRetryStrategy.builder()
                .maxAttempts(10)
                .build();
        var server = new RateLimitedServer(RATE_LIMIT_MS);
        var successCount = new AtomicInteger(0);
        var throttleCount = new AtomicInteger(0);
        var latch = new CountDownLatch(THREAD_COUNT);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        for (var i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    for (var c = 0; c < CALLS_PER_THREAD; c++) {
                        executeWithRetry(strategy, server, successCount, throttleCount);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        var totalCalls = THREAD_COUNT * CALLS_PER_THREAD;
        // All calls should eventually succeed
        assertEquals(totalCalls, successCount.get());
        // Some calls should have been throttled initially
        assertTrue(throttleCount.get() > 0,
                "Expected some throttling but got none");
    }

    private void executeWithRetry(
            AdaptiveRetryStrategy strategy,
            RateLimitedServer server,
            AtomicInteger successCount,
            AtomicInteger throttleCount
    ) {
        var acquireResponse = strategy.acquireInitialToken(new AcquireInitialTokenRequest("test-scope"));
        var token = acquireResponse.token();
        sleep(acquireResponse.delay());

        while (true) {
            try {
                server.call();
                strategy.recordSuccess(new RecordSuccessRequest(token));
                successCount.incrementAndGet();
                return;
            } catch (ServerThrottleException e) {
                throttleCount.incrementAndGet();
                try {
                    var refreshResponse = strategy.refreshRetryToken(
                            new RefreshRetryTokenRequest(token, e, null));
                    token = refreshResponse.token();
                    sleep(refreshResponse.delay());
                } catch (TokenAcquisitionFailedException ex) {
                    // This shouldn't happen with maxAttempts=10 and adaptive backoff.
                    throw new AssertionError("Retries exhausted unexpectedly", ex);
                }
            }
        }
    }

    private static void sleep(Duration delay) {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * A simulated server that only allows one operation every {@code rateLimitMs} milliseconds.
     * Calls that arrive too fast are rejected with a {@link ServerThrottleException}.
     */
    static class RateLimitedServer {
        private final long rateLimitMs;
        private final AtomicLong lastAllowedTime = new AtomicLong(0);

        RateLimitedServer(long rateLimitMs) {
            this.rateLimitMs = rateLimitMs;
        }

        void call() {
            var now = System.nanoTime() / 1_000_000;
            var last = lastAllowedTime.get();
            if (now - last >= rateLimitMs && lastAllowedTime.compareAndSet(last, now)) {
                return; // success
            }
            throw new ServerThrottleException();
        }
    }

    static class ServerThrottleException extends RuntimeException implements RetryInfo {
        @Override
        public RetrySafety isRetrySafe() {
            return RetrySafety.YES;
        }

        @Override
        public boolean isThrottle() {
            return true;
        }

        @Override
        public Duration retryAfter() {
            return null;
        }
    }
}
