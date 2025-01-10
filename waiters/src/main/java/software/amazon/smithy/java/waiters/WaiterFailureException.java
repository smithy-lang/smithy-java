/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters;

import java.time.Duration;
import java.util.Objects;

/**
 * Indicates that a {@link Waiter} reached a terminal, FAILURE state.
 *
 * <p>{@code Waiter}'s can reach a terminal FAILURE state if:
 * <ul>
 *     <li>An {@link software.amazon.smithy.java.waiters.matching.Acceptor} transitions the {@code Waiter} to a FAILURE state.</li>
 *     <li>The {@code Waiter} times out.</li>
 *     <li>The {@code Waiter} encounters an unknown exception.</li>
 * </ul>
 */
public final class WaiterFailureException extends RuntimeException {
    private final int attemptNumber;
    private final long totalTimeMillis;

    private WaiterFailureException(Builder builder) {
        super(Objects.requireNonNull(builder.message, "message cannot be null."), builder.cause);
        this.attemptNumber = builder.attemptNumber;
        this.totalTimeMillis = builder.totalTimeMillis;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Duration getTotalTime() {
        return Duration.ofMillis(totalTimeMillis);
    }

    /**
     * @return new static builder for {@link WaiterFailureException}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Static builder for {@link WaiterFailureException}.
     */
    public static final class Builder {
        private Throwable cause;
        private String message;
        private int attemptNumber;
        private long totalTimeMillis;

        private Builder() {}

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public Builder attemptNumber(int attemptNumber) {
            this.attemptNumber = attemptNumber;
            return this;
        }

        public Builder totalTimeMillis(long totalTimeMillis) {
            this.totalTimeMillis = totalTimeMillis;
            return this;
        }

        public WaiterFailureException build() {
            return new WaiterFailureException(this);
        }
    }
}
