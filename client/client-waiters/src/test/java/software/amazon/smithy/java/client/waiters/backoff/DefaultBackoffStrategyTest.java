/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.waiters.backoff;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DefaultBackoffStrategyTest {

    private static final long REMAINING = Long.MAX_VALUE / 2;

    @Test
    void earlyAttemptDoesNotThrowWhenComputedDelayIsBelowMinDelay() {
        // Regression: on early attempts Math.pow(minDelay*2, attempt) can be <= minDelay
        // (e.g. attempt=0 with minDelay=2 gives 1). The upper bound then fell below the
        // origin and Random.nextLong(origin, bound) threw IllegalArgumentException, breaking
        // the very first retry of any waiter using the default (2ms) min delay.
        var strategy = BackoffStrategy.getDefault(2L, 20L);
        assertDoesNotThrow(() -> strategy.computeNextDelayInMills(0, REMAINING));
    }

    @Test
    void defaultStrategyDoesNotThrowOnFirstAttempt() {
        // The default min delay (2ms) is exactly the value that triggered the bug.
        var strategy = BackoffStrategy.getDefault();
        assertDoesNotThrow(() -> strategy.computeNextDelayInMills(0, REMAINING));
    }

    @Test
    void delayStaysWithinBoundsAcrossAttempts() {
        long minDelay = 2;
        long maxDelay = 20;
        var strategy = BackoffStrategy.getDefault(minDelay, maxDelay);
        // Sweep early attempts (including the buggy attempt=0) and beyond the attempt ceiling.
        for (int attempt = 0; attempt < 64; attempt++) {
            long delay = strategy.computeNextDelayInMills(attempt, REMAINING);
            assertTrue(
                    delay >= minDelay && delay <= maxDelay,
                    "attempt " + attempt + " produced out-of-range delay: " + delay);
        }
    }
}
