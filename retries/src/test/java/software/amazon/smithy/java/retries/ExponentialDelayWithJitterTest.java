/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.retries;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ExponentialDelayWithJitterTest {
    // After 1.0 - nextDouble(): 1.0 - 1.0 = 0.0 (min jitter output)
    static final ComputedNextDouble MIN_VALUE_RND = new ComputedNextDouble(1.0);
    static final ComputedNextDouble MID_VALUE_RND = new ComputedNextDouble(0.5);
    // After 1.0 - nextDouble(): 1.0 - 0.0 = 1.0 (max jitter output)
    static final ComputedNextDouble MAX_VALUE_RND = new ComputedNextDouble(0.0);
    static final Duration BASE_DELAY = Duration.ofMillis(23);
    static final Duration MAX_DELAY = Duration.ofSeconds(20);

    @ParameterizedTest
    @MethodSource("parameters")
    void testCase(TestCase testCase) {
        assertThat(testCase.run()).isEqualTo(testCase.expected());
    }

    static Collection<TestCase> parameters() {
        return Arrays.asList(
                // --- Using random that returns: 1.0 (max jitter)
                new TestCase()
                        .configureRandom(MAX_VALUE_RND)
                        .givenAttempt(1)
                        .expectDelayInMs(0),
                new TestCase()
                        .configureRandom(MAX_VALUE_RND)
                        .givenAttempt(2)
                        .expectDelayInMs(23),
                new TestCase()
                        .configureRandom(MAX_VALUE_RND)
                        .givenAttempt(3)
                        .expectDelayInMs(46),
                new TestCase()
                        .configureRandom(MAX_VALUE_RND)
                        .givenAttempt(5)
                        .expectDelayInMs(184),
                new TestCase()
                        .configureRandom(MAX_VALUE_RND)
                        .givenAttempt(7)
                        .expectDelayInMs(736),
                new TestCase()
                        .configureRandom(MAX_VALUE_RND)
                        .givenAttempt(11)
                        .expectDelayInMs(11776),
                new TestCase()
                        .configureRandom(MAX_VALUE_RND)
                        .givenAttempt(13)
                        .expectDelayInMs(20000),
                // --- Using random that returns: 0.5 (mid jitter)
                new TestCase()
                        .configureRandom(MID_VALUE_RND)
                        .givenAttempt(1)
                        .expectDelayInMs(0),
                new TestCase()
                        .configureRandom(MID_VALUE_RND)
                        .givenAttempt(2)
                        .expectDelayInMs(11),
                new TestCase()
                        .configureRandom(MID_VALUE_RND)
                        .givenAttempt(3)
                        .expectDelayInMs(23),
                new TestCase()
                        .configureRandom(MID_VALUE_RND)
                        .givenAttempt(5)
                        .expectDelayInMs(92),
                new TestCase()
                        .configureRandom(MID_VALUE_RND)
                        .givenAttempt(7)
                        .expectDelayInMs(368),
                new TestCase()
                        .configureRandom(MID_VALUE_RND)
                        .givenAttempt(11)
                        .expectDelayInMs(5888),
                new TestCase()
                        .configureRandom(MID_VALUE_RND)
                        .givenAttempt(13)
                        .expectDelayInMs(10000),
                // --- Using random that returns: 0.0 (min jitter, floor of 1ms)
                new TestCase()
                        .configureRandom(MIN_VALUE_RND)
                        .givenAttempt(1)
                        .expectDelayInMs(0),
                new TestCase()
                        .configureRandom(MIN_VALUE_RND)
                        .givenAttempt(2)
                        .expectDelayInMs(1),
                new TestCase()
                        .configureRandom(MIN_VALUE_RND)
                        .givenAttempt(3)
                        .expectDelayInMs(1),
                new TestCase()
                        .configureRandom(MIN_VALUE_RND)
                        .givenAttempt(5)
                        .expectDelayInMs(1),
                new TestCase()
                        .configureRandom(MIN_VALUE_RND)
                        .givenAttempt(7)
                        .expectDelayInMs(1),
                new TestCase()
                        .configureRandom(MIN_VALUE_RND)
                        .givenAttempt(11)
                        .expectDelayInMs(1),
                new TestCase()
                        .configureRandom(MIN_VALUE_RND)
                        .givenAttempt(13)
                        .expectDelayInMs(1));
    }

    static class TestCase {
        Random random;
        int attempt;
        long expectedDelayMs;

        TestCase configureRandom(Random random) {
            this.random = random;
            return this;
        }

        TestCase givenAttempt(int attempt) {
            this.attempt = attempt;
            return this;
        }

        TestCase expectDelayInMs(long expectedDelayMs) {
            this.expectedDelayMs = expectedDelayMs;
            return this;
        }

        Duration run() {
            return new ExponentialDelayWithJitter(() -> random, BASE_DELAY, MAX_DELAY)
                    .computeDelay(this.attempt);
        }

        Duration expected() {
            return Duration.ofMillis(expectedDelayMs);
        }
    }

    static class ComputedNextDouble extends Random {
        final double value;

        ComputedNextDouble(double value) {
            this.value = value;
        }

        @Override
        public double nextDouble() {
            return value;
        }
    }
}
