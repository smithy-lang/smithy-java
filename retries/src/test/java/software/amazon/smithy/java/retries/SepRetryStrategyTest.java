/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.retries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Random;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.retries.api.AcquireInitialTokenFlags;
import software.amazon.smithy.java.retries.api.AcquireInitialTokenRequest;
import software.amazon.smithy.java.retries.api.RecordSuccessRequest;
import software.amazon.smithy.java.retries.api.RefreshRetryTokenRequest;
import software.amazon.smithy.java.retries.api.RetryInfo;
import software.amazon.smithy.java.retries.api.RetrySafety;
import software.amazon.smithy.java.retries.api.RetryToken;
import software.amazon.smithy.java.retries.api.TokenAcquisitionFailedException;

/**
 * Tests from the SEP Appendix A: Standard Mode Testcases.
 * All tests use exponential_base=1 (jitter always returns max value).
 */
class SepRetryStrategyTest {

    // Fixed jitter that always returns 0.0, so that (1.0 - nextDouble()) = 1.0 (exponential_base: 1)
    private static final Random FIXED_JITTER = new Random() {
        @Override
        public double nextDouble() {
            return 0.0;
        }
    };

    private StandardRetryStrategy strategy() {
        return strategy(500, 3, 20.0);
    }

    private StandardRetryStrategy strategy(int initialTokens, int maxAttempts, double maxBackoffSeconds) {
        var builder = StandardRetryStrategy.builder()
                .initialRetryTokens(initialTokens)
                .maxAttempts(maxAttempts)
                .backoffMaxBackoff(Duration.ofMillis((long) (maxBackoffSeconds * 1000)));
        builder.setRandomSupplier(() -> FIXED_JITTER);
        return builder.build();
    }

    private RetryToken acquireToken(StandardRetryStrategy s) {
        return acquireToken(s, 0);
    }

    private RetryToken acquireToken(StandardRetryStrategy s, int flags) {
        return s.acquireInitialToken(new AcquireInitialTokenRequest("scope", flags)).token();
    }

    // --- SEP Test: Retry eventually succeeds ---
    @Test
    void retryEventuallySucceeds() {
        var s = strategy();
        var token = acquireToken(s);

        // 500 → retry, quota=486, delay=0.05
        var r1 = s.refreshRetryToken(new RefreshRetryTokenRequest(token, transientError(), null));
        assertThat(r1.delay()).isEqualTo(Duration.ofMillis(50));
        assertQuota(r1.token(), 486);

        // 500 → retry, quota=472, delay=0.1
        var r2 = s.refreshRetryToken(new RefreshRetryTokenRequest(r1.token(), transientError(), null));
        assertThat(r2.delay()).isEqualTo(Duration.ofMillis(100));
        assertQuota(r2.token(), 472);

        // 200 → success, quota=486
        var success = s.recordSuccess(new RecordSuccessRequest(r2.token()));
        assertQuota(success.token(), 486);
    }

    // --- SEP Test: Fail due to max attempts reached ---
    @Test
    void failDueToMaxAttemptsReached() {
        var s = strategy();
        var token = acquireToken(s);

        // 502 → retry, quota=486, delay=0.05
        var r1 = s.refreshRetryToken(new RefreshRetryTokenRequest(token, transientError(), null));
        assertThat(r1.delay()).isEqualTo(Duration.ofMillis(50));
        assertQuota(r1.token(), 486);

        // 502 → retry, quota=472, delay=0.1
        var r2 = s.refreshRetryToken(new RefreshRetryTokenRequest(r1.token(), transientError(), null));
        assertThat(r2.delay()).isEqualTo(Duration.ofMillis(100));
        assertQuota(r2.token(), 472);

        // 502 → max_attempts_exceeded, quota=472
        assertThatThrownBy(() -> s.refreshRetryToken(new RefreshRetryTokenRequest(r2.token(), transientError(), null)))
                .isInstanceOf(TokenAcquisitionFailedException.class)
                .satisfies(e -> assertQuota(((TokenAcquisitionFailedException) e).token(), 472));
    }

    // --- SEP Test: Retry Quota reached after a single retry ---
    @Test
    void retryQuotaReachedAfterSingleRetry() {
        var s = strategy(14, 3, 20.0);
        var token = acquireToken(s);

        // 500 → retry, quota=0, delay=0.05
        var r1 = s.refreshRetryToken(new RefreshRetryTokenRequest(token, transientError(), null));
        assertThat(r1.delay()).isEqualTo(Duration.ofMillis(50));
        assertQuota(r1.token(), 0);

        // 500 → retry_quota_exceeded, quota=0
        assertThatThrownBy(() -> s.refreshRetryToken(new RefreshRetryTokenRequest(r1.token(), transientError(), null)))
                .isInstanceOf(TokenAcquisitionFailedException.class)
                .satisfies(e -> assertQuota(((TokenAcquisitionFailedException) e).token(), 0));
    }

    // --- SEP Test: No retries at all if retry quota is 0 ---
    @Test
    void noRetriesIfRetryQuotaIsZero() {
        var s = strategy(0, 3, 20.0);
        var token = acquireToken(s);

        // 500 → retry_quota_exceeded, quota=0
        assertThatThrownBy(() -> s.refreshRetryToken(new RefreshRetryTokenRequest(token, transientError(), null)))
                .isInstanceOf(TokenAcquisitionFailedException.class)
                .satisfies(e -> assertQuota(((TokenAcquisitionFailedException) e).token(), 0));
    }

    // --- SEP Test: Verifying exponential backoff timing ---
    @Test
    void exponentialBackoffTiming() {
        var s = strategy(500, 5, 20.0);
        var token = acquireToken(s);

        // i=0: delay=0.05
        var r1 = s.refreshRetryToken(new RefreshRetryTokenRequest(token, transientError(), null));
        assertThat(r1.delay()).isEqualTo(Duration.ofMillis(50));
        assertQuota(r1.token(), 486);

        // i=1: delay=0.1
        var r2 = s.refreshRetryToken(new RefreshRetryTokenRequest(r1.token(), transientError(), null));
        assertThat(r2.delay()).isEqualTo(Duration.ofMillis(100));
        assertQuota(r2.token(), 472);

        // i=2: delay=0.2
        var r3 = s.refreshRetryToken(new RefreshRetryTokenRequest(r2.token(), transientError(), null));
        assertThat(r3.delay()).isEqualTo(Duration.ofMillis(200));
        assertQuota(r3.token(), 458);

        // i=3: delay=0.4
        var r4 = s.refreshRetryToken(new RefreshRetryTokenRequest(r3.token(), transientError(), null));
        assertThat(r4.delay()).isEqualTo(Duration.ofMillis(400));
        assertQuota(r4.token(), 444);

        // max_attempts_exceeded
        assertThatThrownBy(() -> s.refreshRetryToken(new RefreshRetryTokenRequest(r4.token(), transientError(), null)))
                .isInstanceOf(TokenAcquisitionFailedException.class)
                .satisfies(e -> assertQuota(((TokenAcquisitionFailedException) e).token(), 444));
    }

    // --- SEP Test: Verify max backoff time ---
    @Test
    void maxBackoffTime() {
        var s = strategy(500, 5, 0.2);
        var token = acquireToken(s);

        // i=0: delay=0.05
        var r1 = s.refreshRetryToken(new RefreshRetryTokenRequest(token, transientError(), null));
        assertThat(r1.delay()).isEqualTo(Duration.ofMillis(50));

        // i=1: delay=0.1
        var r2 = s.refreshRetryToken(new RefreshRetryTokenRequest(r1.token(), transientError(), null));
        assertThat(r2.delay()).isEqualTo(Duration.ofMillis(100));

        // i=2: delay=0.2 (capped by max_backoff)
        var r3 = s.refreshRetryToken(new RefreshRetryTokenRequest(r2.token(), transientError(), null));
        assertThat(r3.delay()).isEqualTo(Duration.ofMillis(200));

        // i=3: delay=0.2 (still capped)
        var r4 = s.refreshRetryToken(new RefreshRetryTokenRequest(r3.token(), transientError(), null));
        assertThat(r4.delay()).isEqualTo(Duration.ofMillis(200));
    }

    // --- SEP Test: Retry Stops After Retry Quota Exhaustion ---
    @Test
    void retryStopsAfterRetryQuotaExhaustion() {
        var s = strategy(20, 5, 20.0);
        var token = acquireToken(s);

        // 500 → retry, quota=6, delay=0.05
        var r1 = s.refreshRetryToken(new RefreshRetryTokenRequest(token, transientError(), null));
        assertThat(r1.delay()).isEqualTo(Duration.ofMillis(50));
        assertQuota(r1.token(), 6);

        // 502 → retry_quota_exceeded, quota=6
        assertThatThrownBy(() -> s.refreshRetryToken(new RefreshRetryTokenRequest(r1.token(), transientError(), null)))
                .isInstanceOf(TokenAcquisitionFailedException.class)
                .satisfies(e -> assertQuota(((TokenAcquisitionFailedException) e).token(), 6));
    }

    // --- SEP Test: Retry quota Recovery After Successful Responses ---
    @Test
    void retryQuotaRecoveryAfterSuccess() {
        var s = strategy(30, 5, 20.0);

        // First invocation: 500 → 502 → 200
        var token = acquireToken(s);
        var r1 = s.refreshRetryToken(new RefreshRetryTokenRequest(token, transientError(), null));
        assertThat(r1.delay()).isEqualTo(Duration.ofMillis(50));
        assertQuota(r1.token(), 16);

        var r2 = s.refreshRetryToken(new RefreshRetryTokenRequest(r1.token(), transientError(), null));
        assertThat(r2.delay()).isEqualTo(Duration.ofMillis(100));
        assertQuota(r2.token(), 2);

        var success1 = s.recordSuccess(new RecordSuccessRequest(r2.token()));
        assertQuota(success1.token(), 16); // returns 14

        // Second invocation: 500 → 200
        var token2 = acquireToken(s);
        var r3 = s.refreshRetryToken(new RefreshRetryTokenRequest(token2, transientError(), null));
        assertThat(r3.delay()).isEqualTo(Duration.ofMillis(50));
        assertQuota(r3.token(), 2);

        var success2 = s.recordSuccess(new RecordSuccessRequest(r3.token()));
        assertQuota(success2.token(), 16); // returns 14
    }

    // --- SEP Test: Throttling Error Token Bucket Drain (5 tokens) and Backoff Duration (1000ms) ---
    @Test
    void throttlingErrorUsesHigherBaseDelay() {
        var s = strategy();
        var token = acquireToken(s);

        // Throttling → retry, quota=495, delay=1.0 (1000ms base)
        var r1 = s.refreshRetryToken(new RefreshRetryTokenRequest(token, throttlingError(), null));
        assertThat(r1.delay()).isEqualTo(Duration.ofMillis(1000));
        assertQuota(r1.token(), 495);

        // 200 → success, quota=500
        var success = s.recordSuccess(new RecordSuccessRequest(r1.token()));
        assertQuota(success.token(), 500);
    }

    // --- SEP Test: Long-Polling Backoff After Transient Error When Token Bucket Empty ---
    @Test
    void longPollingBackoffAfterTransientErrorWhenTokenBucketEmpty() {
        var s = strategy(0, 3, 20.0);
        var token = acquireToken(s, AcquireInitialTokenFlags.IS_LONG_POLLING);

        // 500 → retry_quota_exceeded with delay=0.05
        assertThatThrownBy(() -> s.refreshRetryToken(new RefreshRetryTokenRequest(token, transientError(), null)))
                .isInstanceOf(TokenAcquisitionFailedException.class)
                .satisfies(e -> {
                    var tafe = (TokenAcquisitionFailedException) e;
                    assertQuota(tafe.token(), 0);
                    assertThat(tafe.delay()).isEqualTo(Duration.ofMillis(50));
                });
    }

    // --- SEP Test: Long-Polling Backoff After Throttling Error When Token Bucket Empty ---
    @Test
    void longPollingBackoffAfterThrottlingErrorWhenTokenBucketEmpty() {
        var s = strategy(0, 3, 20.0);
        var token = acquireToken(s, AcquireInitialTokenFlags.IS_LONG_POLLING);

        // Throttling → retry_quota_exceeded with delay=1.0 (throttling base)
        assertThatThrownBy(() -> s.refreshRetryToken(new RefreshRetryTokenRequest(token, throttlingError(), null)))
                .isInstanceOf(TokenAcquisitionFailedException.class)
                .satisfies(e -> {
                    var tafe = (TokenAcquisitionFailedException) e;
                    assertQuota(tafe.token(), 0);
                    assertThat(tafe.delay()).isEqualTo(Duration.ofMillis(1000));
                });
    }

    // --- SEP Test: Long-Polling Max Attempts Exceeded Must NOT Delay ---
    @Test
    void longPollingMaxAttemptsExceededMustNotDelay() {
        var s = strategy(500, 2, 20.0);
        var token = acquireToken(s, AcquireInitialTokenFlags.IS_LONG_POLLING);

        // 500 → retry, delay=0.05
        var r1 = s.refreshRetryToken(new RefreshRetryTokenRequest(token, transientError(), null));
        assertThat(r1.delay()).isEqualTo(Duration.ofMillis(50));

        // 500 → max_attempts_exceeded, no delay
        assertThatThrownBy(() -> s.refreshRetryToken(new RefreshRetryTokenRequest(r1.token(), transientError(), null)))
                .isInstanceOf(TokenAcquisitionFailedException.class)
                .satisfies(e -> {
                    var tafe = (TokenAcquisitionFailedException) e;
                    assertThat(tafe.delay()).isEqualTo(Duration.ZERO);
                });
    }

    // --- SEP Test: Honor x-amz-retry-after Header ---
    @Test
    void honorXAmzRetryAfterHeader() {
        var s = strategy();
        var token = acquireToken(s);

        // 500 with x-amz-retry-after: 1500 → delay=1.5s
        var r1 = s.refreshRetryToken(
                new RefreshRetryTokenRequest(token, transientError(), Duration.ofMillis(1500)));
        assertThat(r1.delay()).isEqualTo(Duration.ofMillis(1500));
        assertQuota(r1.token(), 486);

        var success = s.recordSuccess(new RecordSuccessRequest(r1.token()));
        assertQuota(success.token(), 500);
    }

    // --- SEP Test: x-amz-retry-after minimum is exponential backoff duration ---
    @Test
    void xAmzRetryAfterMinimumIsExponentialBackoff() {
        var s = strategy();
        var token = acquireToken(s);

        // x-amz-retry-after: 0 → clamped to t_i=0.05
        var r1 = s.refreshRetryToken(
                new RefreshRetryTokenRequest(token, transientError(), Duration.ofMillis(0)));
        assertThat(r1.delay()).isEqualTo(Duration.ofMillis(50));
        assertQuota(r1.token(), 486);
    }

    // --- SEP Test: x-amz-retry-after maximum is 5+exponential backoff duration ---
    @Test
    void xAmzRetryAfterMaximumIs5PlusExponentialBackoff() {
        var s = strategy();
        var token = acquireToken(s);

        // x-amz-retry-after: 10000 → clamped to 5+t_i = 5.05s
        var r1 = s.refreshRetryToken(
                new RefreshRetryTokenRequest(token, transientError(), Duration.ofMillis(10000)));
        assertThat(r1.delay()).isEqualTo(Duration.ofMillis(5050));
        assertQuota(r1.token(), 486);
    }

    // --- SEP Test: Invalid x-amz-retry-after Falls Back to Exponential Backoff ---
    // (Invalid values are handled at the HTTP layer; at the strategy level, null suggestedDelay = fallback)
    @Test
    void invalidRetryAfterFallsBackToExponentialBackoff() {
        var s = strategy();
        var token = acquireToken(s);

        // null suggestedDelay → uses exponential backoff = 0.05
        var r1 = s.refreshRetryToken(new RefreshRetryTokenRequest(token, transientError(), null));
        assertThat(r1.delay()).isEqualTo(Duration.ofMillis(50));
        assertQuota(r1.token(), 486);
    }

    private void assertQuota(RetryToken token, int expected) {
        var t = (DefaultRetryToken) token;
        assertThat(t.capacityRemaining()).isEqualTo(expected);
    }

    private static Throwable transientError() {
        return new TestException(RetrySafety.YES, false);
    }

    private static Throwable throttlingError() {
        return new TestException(RetrySafety.YES, true);
    }

    private static class TestException extends RuntimeException implements RetryInfo {
        private final RetrySafety safety;
        private final boolean throttle;

        TestException(RetrySafety safety, boolean throttle) {
            super("test");
            this.safety = safety;
            this.throttle = throttle;
        }

        @Override
        public RetrySafety isRetrySafe() {
            return safety;
        }

        @Override
        public boolean isThrottle() {
            return throttle;
        }

        @Override
        public Duration retryAfter() {
            return null;
        }
    }
}
