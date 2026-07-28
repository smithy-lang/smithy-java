/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.retries;

import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * A backoff strategy that uses exponential delay with full jitter.
 *
 * <p>The first attempt has no delay. Each subsequent attempt waits a random duration between 0ms and
 * {@code min(maxDelay, baseDelay * (1 << (attempt - 2)))}.
 */
final class ExponentialDelayWithJitter {
    private final Supplier<Random> randomSupplier;
    private final Duration baseDelay;
    private final Duration maxDelay;

    ExponentialDelayWithJitter(Supplier<Random> randomSupplier, Duration baseDelay, Duration maxDelay) {
        this.randomSupplier = Objects.requireNonNull(randomSupplier, "random");
        this.baseDelay = min(requireNonNegativeDuration(baseDelay, "baseDelay"), Constants.BASE_DELAY_CEILING);
        this.maxDelay = min(requireNonNegativeDuration(maxDelay, "maxDelay"), Constants.MAX_BACKOFF_CEILING);
    }

    ExponentialDelayWithJitter(Duration baseDelay, Duration maxDelay) {
        this(ThreadLocalRandom::current, baseDelay, maxDelay);
    }

    Duration baseDelay() {
        return baseDelay;
    }

    Duration maxDelay() {
        return maxDelay;
    }

    Duration computeDelay(int attempt) {
        if (attempt == 1) {
            return Duration.ZERO;
        }
        var delay = calculateExponentialDelay(attempt);
        if (delay <= 0) {
            return Duration.ZERO;
        }
        var jitter = 1.0 - randomSupplier.get().nextDouble();
        return Duration.ofMillis(Math.max(1L, (long) (jitter * delay)));
    }

    int calculateExponentialDelay(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("Attempt " + attempt + " is less than 1");
        }
        var cappedRetries = Math.min(attempt, Constants.RETRIES_ATTEMPTED_CEILING);
        return (int) Math.min(baseDelay.multipliedBy(1L << (cappedRetries - 2)).toMillis(), maxDelay.toMillis());
    }

    static Duration requireNonNegativeDuration(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration " + name + " is negative");
        }
        return duration;
    }

    static Duration min(Duration left, Duration right) {
        if (left.compareTo(right) <= 0) {
            return left;
        }
        return right;
    }
}
