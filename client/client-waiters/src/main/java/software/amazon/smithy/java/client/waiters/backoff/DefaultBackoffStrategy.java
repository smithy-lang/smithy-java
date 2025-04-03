/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.waiters.backoff;

import java.util.Objects;
import java.util.Random;

// TODO: Add additional testing of exponential backoff impl
final class DefaultBackoffStrategy implements BackoffStrategy {
    private static final long defaultMinDelayMillis = 2;
    private static final long defaultMaxDelayMillis = 120000;

    private final long minDelayMillis;
    private final long maxDelayMillis;
    private final Random rng;

    DefaultBackoffStrategy(Long minDelayMillis, Long maxDelayMillis) {
        this.minDelayMillis = Objects.requireNonNullElse(minDelayMillis, defaultMinDelayMillis);
        this.maxDelayMillis = Objects.requireNonNullElse(maxDelayMillis, defaultMaxDelayMillis);
        this.rng = new Random();
    }

    @Override
    public long computeNextDelayInMills(int attempt, long remainingTime) {
        var attemptCeiling = (Math.log((double) maxDelayMillis / minDelayMillis) / Math.log(2)) + 1;
        long delay = maxDelayMillis;
        if (((double) attempt) < attemptCeiling) {
            delay = Math.min(maxDelayMillis, (long) Math.pow(minDelayMillis * 2, attempt));
        }
        delay = rng.nextLong(minDelayMillis, delay);
        if (remainingTime - delay <= minDelayMillis) {
            delay = remainingTime;
        }
        return delay;
    }
}
