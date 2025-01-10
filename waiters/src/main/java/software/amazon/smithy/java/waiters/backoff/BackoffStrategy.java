/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters.backoff;

import java.time.Duration;

/**
 * Algorithm that computes the amount of time for a Waiter to wait before attempting a retry.
 *
 * <p><strong>NOTE</strong>The default strategy is exponential backoff with jitter.
 * @see <a href="https://smithy.io/2.0/additional-specs/waiters.html#waiter-retries">Waiter specification</a>
 */
public interface BackoffStrategy {
    /**
     * Computes time to wait for the next retry attempt in milliseconds.
     *
     * <p><strong>Note:</strong>package private for testing.
     *
     * @see <a href="https://smithy.io/2.0/additional-specs/waiters.html#waiter-retries">Waiter Specification</a>
     */
    long computeNextDelayInMills(int attempt, long remainingTime);

    /**
     * Get a new backoff strategy implementing the default backoff strategy.
     *
     * @param minDelayMillis minimum delay between attempts, in milliseconds
     * @param maxDelayMillis maximum delay between attempts, in milliseconds
     * @return new, default backoff strategy
     */
    static BackoffStrategy getDefault(Long minDelayMillis, Long maxDelayMillis) {
        return new DefaultRetryStrategy(minDelayMillis, maxDelayMillis);
    }

    /**
     * Get a new backoff strategy implementing the default backoff strategy.
     *
     * @param minDelay minimum delay between attempts
     * @param maxDelay maximum delay between attempts
     * @return new, default backoff strategy
     */
    static BackoffStrategy getDefault(Duration minDelay, Duration maxDelay) {
        return new DefaultRetryStrategy(
                minDelay != null ? minDelay.toMillis() : null,
                maxDelay != null ? maxDelay.toMillis() : null);
    }

    /**
     * Get a new backoff strategy implementing the default backoff strategy.
     *
     * <p><strong>Note:</strong>Uses min delay of 2 seconds and max delay of 120 seconds.
     *
     * @return new, default backoff strategy
     */
    static BackoffStrategy getDefault() {
        return new DefaultRetryStrategy(null, null);
    }
}
