/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.waiters.backoff.BackoffStrategy;
import software.amazon.smithy.java.waiters.matching.Matcher;

/**
 * Waiters are used to poll a resource until a desired state is reached, or until it is determined that the resource
 * has reached an undesirable terminal state.
 *
 * <p>Waiters will repeatedly poll for the state of a resource using a provided polling function. The state of the
 * resource is then evaluated using a number of {@code Acceptor}s. These acceptors are evaluated in a fixed order and
 * can transition the state of the waiter if they determine the resource state matches some condition.
 *
 * <p>{@code SUCCESS} and {@code FAILURE} states are terminal states for Waiters and will cause the waiter to complete, returning the
 * terminal status. The default waiter state {@code RETRY} causes the waiter to retry polling the resource state. Retries are
 * performed using an exponential backoff approach with jitter.
 *
 * <p>Example usage<pre>{@code
 * var waiter = Waiter.builder(client::getFoo)
 *                 .success(Matcher.output(o -> o.status().equals("DONE")))
 *                 .failure(Matcher.output(o -> o.status().equals("STOPPED")))
 *                 .build();
 * waiter.wait(GetFooInput.builder().id("my-id").build(), 1000);
 * }</pre>
 *
 * @param <I> Input type of resource polling function.
 * @param <O> Output type of resource polling function.
 * @see <a href="https://smithy.io/2.0/additional-specs/waiters.html">Waiter Specification</a>
 */
public final class Waiter<I extends SerializableStruct, O extends SerializableStruct> implements WaiterSettings {
    private final Waitable<I, O> pollingFunction;
    private final List<Acceptor<I, O>> acceptors;
    private BackoffStrategy backoffStrategy;
    private RequestOverrideConfig overrideConfig;

    private Waiter(Builder<I, O> builder) {
        this.pollingFunction = builder.pollingFunction;
        this.acceptors = Collections.unmodifiableList(builder.acceptors);
        this.backoffStrategy = Objects.requireNonNullElse(builder.backoffStrategy, BackoffStrategy.getDefault());
    }

    /**
     * Wait for the resource to reach a terminal state.
     *
     * @param input Input to use for polling function.
     * @param maxWaitTime maximum amount of time for waiter to wait.
     * @throws WaiterFailureException if the waiter reaches a FAILURE state
     */
    public void wait(I input, Duration maxWaitTime) {
        wait(input, maxWaitTime.toMillis());
    }

    /**
     * Wait for the resource to reach a terminal state.
     *
     * @param input Input to use for polling function.
     * @param maxWaitTimeMillis maximum wait time
     * @throws WaiterFailureException if the waiter reaches a FAILURE state
     */
    public void wait(I input, long maxWaitTimeMillis) {
        int attemptNumber = 0;
        long startTime = System.currentTimeMillis();

        while (true) {
            attemptNumber++;

            ModeledException exception = null;
            O output = null;
            // Execute call to get input and output types
            try {
                output = pollingFunction.poll(input, overrideConfig);
            } catch (ModeledException modeledException) {
                exception = modeledException;
            } catch (Exception exc) {
                throw WaiterFailureException.builder()
                        .message("Waiter encountered unexpected, unmodeled exception while polling.")
                        .attemptNumber(attemptNumber)
                        .cause(exc)
                        .totalTimeMillis(System.currentTimeMillis() - startTime)
                        .build();
            }

            WaiterState state;
            try {
                state = resolveState(input, output, exception);
            } catch (Exception exc) {
                throw WaiterFailureException.builder()
                        .message("Waiter encountered unexpected exception.")
                        .cause(exc)
                        .attemptNumber(attemptNumber)
                        .totalTimeMillis(System.currentTimeMillis() - startTime)
                        .build();
            }

            switch (state) {
                case SUCCESS:
                    return;
                case RETRY:
                    waitToRetry(attemptNumber, maxWaitTimeMillis, startTime);
                    break;
                case FAILURE:
                    throw WaiterFailureException.builder()
                            .message("Waiter reached terminal, FAILURE state")
                            .attemptNumber(attemptNumber)
                            .totalTimeMillis(System.currentTimeMillis() - startTime)
                            .build();
            }
        }
    }

    private WaiterState resolveState(I input, O output, ModeledException exception) {
        // Update state based on first matcher that matches
        for (Acceptor<I, O> acceptor : acceptors) {
            if (acceptor.matcher().matches(input, output, exception)) {
                return acceptor.state();
            }
        }

        // If there was an unmatched exception return failure
        if (exception != null) {
            throw exception;
        }

        // Otherwise retry
        return WaiterState.RETRY;
    }

    private void waitToRetry(int attemptNumber, long maxWaitTimeMillis, long startTimeMillis) {
        long elapsedTimeMillis = System.currentTimeMillis() - startTimeMillis;
        long remainingTime = maxWaitTimeMillis - elapsedTimeMillis;

        if (remainingTime < 0) {
            throw WaiterFailureException.builder()
                    .message("Waiter timed out after " + attemptNumber + " retry attempts.")
                    .attemptNumber(attemptNumber)
                    .totalTimeMillis(elapsedTimeMillis)
                    .build();
        }
        var delay = backoffStrategy.computeNextDelayInMills(attemptNumber, remainingTime);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw WaiterFailureException.builder()
                    .message("Waiter interrupted while waiting to retry.")
                    .attemptNumber(attemptNumber)
                    .totalTimeMillis(System.currentTimeMillis() - startTimeMillis)
                    .build();
        }
    }

    @Override
    public void backoffStrategy(BackoffStrategy backoffStrategy) {
        this.backoffStrategy = Objects.requireNonNull(backoffStrategy, "backoffStrategy cannot be null.");
    }

    @Override
    public void overrideConfig(RequestOverrideConfig overrideConfig) {
        this.overrideConfig = Objects.requireNonNull(overrideConfig, "overrideConfig cannot be null.");
    }

    /**
     * Create a new {@link Builder}.
     *
     * @param pollingFunction Client call that will be used to poll for the resource state.
     * @return new {@link Builder} instance.
     * @param <I> Input shape type
     * @param <O> Output shape type
     */
    public static <I extends SerializableStruct,
            O extends SerializableStruct> Builder<I, O> builder(Waitable<I, O> pollingFunction) {
        return new Builder<>(pollingFunction);
    }

    /**
     * Static builder for {@link Waiter}.
     *
     * @param <I> Polling function input shape type
     * @param <O> Polling function output shape type
     */
    public static final class Builder<I extends SerializableStruct, O extends SerializableStruct> {
        private final List<Acceptor<I, O>> acceptors = new ArrayList<>();
        private final Waitable<I, O> pollingFunction;
        private BackoffStrategy backoffStrategy;

        private Builder(Waitable<I, O> pollingFunction) {
            this.pollingFunction = pollingFunction;
        }

        /**
         * Add a matcher to the Waiter that will transition the waiter to a SUCCESS state if matched.
         *
         * @param matcher matcher to add
         * @return this builder
         */
        public Builder<I, O> success(Matcher<I, O> matcher) {
            this.acceptors.add(new Acceptor<>(WaiterState.SUCCESS, matcher));
            return this;
        }

        /**
         * Add a matcher to the Waiter that will transition the waiter to a FAILURE state if matched.
         *
         * @param matcher matcher to add
         * @return this builder
         */
        public Builder<I, O> failure(Matcher<I, O> matcher) {
            this.acceptors.add(new Acceptor<>(WaiterState.FAILURE, matcher));
            return this;
        }

        /**
         * Add a matcher to the Waiter that will transition the waiter to a FAILURE state if matched.
         *
         * @param matcher acceptor to add
         * @return this builder
         */
        public Builder<I, O> retry(Matcher<I, O> matcher) {
            this.acceptors.add(new Acceptor<>(WaiterState.RETRY, matcher));
            return this;
        }

        /**
         * Backoff strategy to use when polling for resource state.
         *
         * @param backoffStrategy backoff strategy to use
         * @return this builder
         */
        public Builder<I, O> backoffStrategy(BackoffStrategy backoffStrategy) {
            this.backoffStrategy = Objects.requireNonNull(backoffStrategy, "backoffStrategy cannot be null");
            return this;
        }

        /**
         * Create an immutable {@link Waiter} instance.
         *
         * @return the built {@code Waiter} object.
         */
        public Waiter<I, O> build() {
            return new Waiter<>(this);
        }
    }

    /**
     * Interface representing a function that can be polled for the state of a resource.
     */
    @FunctionalInterface
    public interface Waitable<I extends SerializableStruct, O extends SerializableStruct> {
        O poll(I input, RequestOverrideConfig requestContext);
    }
}
