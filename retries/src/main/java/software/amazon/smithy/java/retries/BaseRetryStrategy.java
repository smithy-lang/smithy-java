/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.retries;

import static software.amazon.smithy.java.retries.DefaultRetryToken.validateIsPositive;

import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.retries.api.AcquireInitialTokenRequest;
import software.amazon.smithy.java.retries.api.AcquireInitialTokenResponse;
import software.amazon.smithy.java.retries.api.Claimable;
import software.amazon.smithy.java.retries.api.RecordSuccessRequest;
import software.amazon.smithy.java.retries.api.RecordSuccessResponse;
import software.amazon.smithy.java.retries.api.RefreshRetryTokenRequest;
import software.amazon.smithy.java.retries.api.RefreshRetryTokenResponse;
import software.amazon.smithy.java.retries.api.RetryInfo;
import software.amazon.smithy.java.retries.api.RetrySafety;
import software.amazon.smithy.java.retries.api.RetryStrategy;
import software.amazon.smithy.java.retries.api.RetryToken;
import software.amazon.smithy.java.retries.api.TokenAcquisitionFailedException;

abstract class BaseRetryStrategy implements RetryStrategy, Claimable {
    private final AtomicBoolean claimed = new AtomicBoolean(false);
    protected final InternalLogger log;
    protected final int maxAttempts;
    protected final ExponentialDelayWithJitter backoffStrategy;
    protected final ExponentialDelayWithJitter throttlingBackoffStrategy;
    protected final int retryCost;
    protected final int throttlingRetryCost;
    protected final int initialRetryTokens;
    protected final int maxScopes;
    protected final TokenBucketStore tokenBucketStore;

    BaseRetryStrategy(InternalLogger log, Builder builder) {
        this.log = log;
        this.maxAttempts = validateIsPositive(builder.maxAttempts, "maxAttempts");
        if (builder.randomSupplier != null) {
            this.backoffStrategy = new ExponentialDelayWithJitter(
                    builder.randomSupplier,
                    builder.backoffBaseDelay,
                    builder.backoffMaxDelay);
            this.throttlingBackoffStrategy = new ExponentialDelayWithJitter(
                    builder.randomSupplier,
                    builder.throttlingBackoffBaseDelay,
                    builder.backoffMaxDelay);
        } else {
            this.backoffStrategy = new ExponentialDelayWithJitter(builder.backoffBaseDelay, builder.backoffMaxDelay);
            this.throttlingBackoffStrategy = new ExponentialDelayWithJitter(
                    builder.throttlingBackoffBaseDelay,
                    builder.backoffMaxDelay);
        }
        this.retryCost = Objects.requireNonNull(builder.retryCost, "retryCost");
        this.throttlingRetryCost = Objects.requireNonNull(builder.throttlingRetryCost, "throttlingRetryCost");
        this.initialRetryTokens = builder.initialRetryTokens;
        this.maxScopes = builder.maxScopes;
        this.tokenBucketStore = TokenBucketStore.create(initialRetryTokens, maxScopes);
    }

    @Override
    public void claim(Object owner) {
        Objects.requireNonNull(owner, "owner");
        if (!claimed.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "This retry strategy instance has already been claimed. "
                            + "Use toBuilder() to create a separate instance.");
        }
    }

    /**
     * This method implements the logic of {@link RetryStrategy#acquireInitialToken(AcquireInitialTokenRequest)}.
     *
     * @see RetryStrategy#acquireInitialToken(AcquireInitialTokenRequest)
     */
    @Override
    public final AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request) {
        logAcquireInitialToken(request);
        var token = DefaultRetryToken.builder().scope(request.scope()).flags(request.flags()).build();
        return new AcquireInitialTokenResponse(token, computeInitialBackoff(request));
    }

    /**
     * This method implements the logic of  {@link RetryStrategy#refreshRetryToken(RefreshRetryTokenRequest)}.
     *
     * @see RetryStrategy#refreshRetryToken(RefreshRetryTokenRequest)
     */
    @Override
    public final RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request) {
        var token = asDefaultRetryToken(request.token());

        // Check if we meet the preconditions needed for retrying.
        // 1) is retryable?
        throwOnNonRetryableException(request);

        // 2) max attempts reached?
        throwOnMaxAttemptsReached(request);

        // 3) can we acquire a token?
        AcquireResponse acquireResponse = requestAcquireCapacity(request, token);
        throwOnAcquisitionFailure(request, acquireResponse);

        // All the conditions required to retry were meet, update the internal state before retrying.
        updateStateForRetry(request);

        // Refresh the retry token and compute the backoff delay.
        var refreshedToken = refreshToken(request, acquireResponse);
        var backoff = computeBackoff(request, refreshedToken);

        logRefreshTokenSuccess(refreshedToken, acquireResponse, backoff);
        return new RefreshRetryTokenResponse(refreshedToken, backoff);
    }

    /**
     * This method implements the logic of {@link RetryStrategy#recordSuccess(RecordSuccessRequest)}.
     *
     * @see RetryStrategy#recordSuccess(RecordSuccessRequest)
     */
    @Override
    public final RecordSuccessResponse recordSuccess(RecordSuccessRequest request) {
        var token = asDefaultRetryToken(request.token());

        // Update the circuit breaker token bucket.
        var releaseResponse = releaseTokenBucketCapacity(token);

        // Refresh the retry token and return.
        var refreshedToken = refreshRetryTokenAfterSuccess(token, releaseResponse);

        // Update the state for the specific retry strategy.
        updateStateForSuccess(token);

        // Log success and return.
        logRecordSuccess(token, releaseResponse);
        return new RecordSuccessResponse(refreshedToken);
    }

    @Override
    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * Computes the backoff before the first attempt, by default {@link Duration#ZERO}. Extending classes can override
     * this method to compute a different backoff depending on their logic.
     */
    protected Duration computeInitialBackoff(AcquireInitialTokenRequest request) {
        return Duration.ZERO;
    }

    /**
     * Computes the backoff before a retry using the configured backoff strategy. Extending classes can override this
     * method to compute a different backoff depending on their logic.
     */
    protected Duration computeBackoff(RefreshRetryTokenRequest request, DefaultRetryToken token) {
        var isThrottle = treatAsThrottling(request.failure());
        var strategy = isThrottle ? throttlingBackoffStrategy : backoffStrategy;
        var backoff = strategy.computeDelay(token.attempt());
        var suggested = request.suggestedDelay();
        if (suggested == null) {
            return backoff;
        }
        // Clamp suggested delay: min bound is t_i, max bound is 5s + t_i
        var maxBound = backoff.plus(Duration.ofSeconds(5));
        return minOf(maxOf(suggested, backoff), maxBound);
    }

    /**
     * Called inside {@link #recordSuccess} to allow extending classes to update their internal state after a successful
     * request.
     */
    protected void updateStateForSuccess(DefaultRetryToken token) {}

    /**
     * Called inside {@link #refreshRetryToken} to allow extending classes to update their internal state before
     * retrying a request.
     */
    protected void updateStateForRetry(RefreshRetryTokenRequest request) {}

    /**
     * Returns the amount of tokens to withdraw from the token bucket. Extending classes can override this method to
     * tailor this amount for the specific kind of failure.
     */
    protected int retryCost(RefreshRetryTokenRequest request) {
        if (treatAsThrottling(request.failure())) {
            return throttlingRetryCost;
        }
        return retryCost;
    }

    private DefaultRetryToken refreshToken(RefreshRetryTokenRequest request, AcquireResponse acquireResponse) {
        var token = asDefaultRetryToken(request.token());
        return token.toBuilder()
                .increaseAttempt()
                .capacityAcquired(acquireResponse.capacityAcquired())
                .capacityRemaining(acquireResponse.capacityRemaining())
                .addFailure(request.failure())
                .build();
    }

    private AcquireResponse requestAcquireCapacity(RefreshRetryTokenRequest request, DefaultRetryToken token) {
        var tokenBucket = tokenBucketStore.tokenBucketForScope(token.scope());
        return tokenBucket.tryAcquire(retryCost(request));
    }

    private ReleaseResponse releaseTokenBucketCapacity(DefaultRetryToken token) {
        var bucket = tokenBucketStore.tokenBucketForScope(token.scope());
        // Make sure that we release at least one token to allow the token bucket
        // to replenish its tokens.
        int capacityReleased = Math.max(token.capacityAcquired(), 1);
        return bucket.release(capacityReleased);
    }

    private DefaultRetryToken refreshRetryTokenAfterSuccess(DefaultRetryToken token, ReleaseResponse releaseResponse) {
        return token.toBuilder()
                .capacityRemaining(releaseResponse.currentCapacity())
                .build();
    }

    private void throwOnNonRetryableException(RefreshRetryTokenRequest request) {
        var token = asDefaultRetryToken(request.token());
        var failure = request.failure();
        if (isNonRetryableException(request)) {
            var message = nonRetryableExceptionMessage(token);
            log.debug(message, failure);
            var tokenBucket = tokenBucketStore.tokenBucketForScope(token.scope());
            var refreshedToken = token.toBuilder()
                    .capacityRemaining(tokenBucket.currentCapacity())
                    .addFailure(failure)
                    .build();
            throw new TokenAcquisitionFailedException(message, refreshedToken, failure);
        }
        int attempt = token.attempt();
        if (log.isDebugEnabled()) {
            log.debug(String.format("Request attempt %d encountered retryable failure.", attempt), failure);
        }
    }

    private void throwOnMaxAttemptsReached(RefreshRetryTokenRequest request) {
        var token = asDefaultRetryToken(request.token());
        if (maxAttemptsReached(token)) {
            var failure = request.failure();
            var tokenBucket = tokenBucketStore.tokenBucketForScope(token.scope());
            var refreshedToken = token.toBuilder()
                    .capacityRemaining(tokenBucket.currentCapacity())
                    .addFailure(failure)
                    .build();
            var message = maxAttemptsReachedMessage(refreshedToken);
            log.debug(message, failure);
            throw new TokenAcquisitionFailedException(message, refreshedToken, failure);
        }
    }

    private void throwOnAcquisitionFailure(RefreshRetryTokenRequest request, AcquireResponse acquireResponse) {
        var token = asDefaultRetryToken(request.token());
        if (acquireResponse.acquisitionFailed()) {
            var failure = request.failure();
            // For long-polling operations, compute backoff delay before returning
            if (token.isLongPolling()) {
                var refreshedToken = token.toBuilder()
                        .increaseAttempt()
                        .capacityRemaining(acquireResponse.capacityRemaining())
                        .capacityAcquired(acquireResponse.capacityAcquired())
                        .addFailure(failure)
                        .build();
                var backoff = computeBackoff(request, refreshedToken);
                var message = acquisitionFailedForLongPollOperationMessage(acquireResponse);
                log.debug(message, failure);
                throw new TokenAcquisitionFailedException(message, refreshedToken, failure, backoff);
            }
            var refreshedToken = token.toBuilder()
                    .capacityRemaining(acquireResponse.capacityRemaining())
                    .capacityAcquired(acquireResponse.capacityAcquired())
                    .addFailure(failure)
                    .build();
            var message = acquisitionFailedMessage(acquireResponse);
            log.debug(message, failure);
            throw new TokenAcquisitionFailedException(message, refreshedToken, failure);
        }
    }

    private String nonRetryableExceptionMessage(DefaultRetryToken token) {
        return String.format("Request attempt %d encountered non-retryable failure", token.attempt());
    }

    private String maxAttemptsReachedMessage(DefaultRetryToken token) {
        return String.format("Request will not be retried. Retries have been exhausted "
                + "(cost: 0, capacity: %d/%d)",
                token.capacityAcquired(),
                token.capacityRemaining());
    }

    private String acquisitionFailedForLongPollOperationMessage(AcquireResponse response) {
        return String.format("Request will be retried for long poll operation despite failing to acquire retry quota. "
                + "The cost of retrying (%d) "
                + "exceeds the available retry capacity (%d/%d).",
                response.capacityRequested(),
                response.capacityRemaining(),
                response.maxCapacity());
    }

    private String acquisitionFailedMessage(AcquireResponse response) {
        return String.format("Request will not be retried to protect the caller and downstream service. "
                + "The cost of retrying (%d) "
                + "exceeds the available retry capacity (%d/%d).",
                response.capacityRequested(),
                response.capacityRemaining(),
                response.maxCapacity());
    }

    private void logAcquireInitialToken(AcquireInitialTokenRequest request) {
        if (!log.isDebugEnabled()) {
            return;
        }
        var tokenBucket = tokenBucketStore.tokenBucketForScope(request.scope());
        log.debug("Request attempt 1 token acquired "
                + "(backoff: 0ms, cost: 0, capacity: {}/{})",
                tokenBucket.currentCapacity(),
                tokenBucket.maxCapacity());
    }

    private void logRefreshTokenSuccess(DefaultRetryToken token, AcquireResponse acquireResponse, Duration delay) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("Request attempt {} token acquired "
                + "(backoff: {}ms, cost: {}, capacity: {}/{})",
                token.attempt(),
                delay.toMillis(),
                acquireResponse.capacityAcquired(),
                acquireResponse.capacityRemaining(),
                acquireResponse.maxCapacity());
    }

    private void logRecordSuccess(DefaultRetryToken token, ReleaseResponse release) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("Request attempt {} succeeded (cost: -{}, capacity: {}/{})",
                token.attempt(),
                release.capacityReleased(),
                release.currentCapacity(),
                release.maxCapacity());
    }

    private boolean maxAttemptsReached(DefaultRetryToken token) {
        return token.attempt() >= maxAttempts;
    }

    protected boolean treatAsThrottling(Throwable e) {
        var ri = getInfo(e);
        return ri != null && ri.isThrottle();
    }

    private boolean isNonRetryableException(RefreshRetryTokenRequest request) {
        var ri = getInfo(request.failure());
        return ri == null || ri.isRetrySafe() != RetrySafety.YES;
    }

    static RetryInfo getInfo(Throwable e) {
        var current = e;
        while (current != null) {
            if (current instanceof RetryInfo r) {
                return r;
            }
            current = current.getCause();
        }
        return null;
    }

    static Duration maxOf(Duration left, Duration right) {
        if (left.compareTo(right) >= 0) {
            return left;
        }
        return right;
    }

    static Duration minOf(Duration left, Duration right) {
        if (left.compareTo(right) <= 0) {
            return left;
        }
        return right;
    }

    static DefaultRetryToken asDefaultRetryToken(RetryToken token) {
        if (token instanceof DefaultRetryToken t) {
            return t;
        }
        throw new IllegalArgumentException(String.format(
                "RetryToken is of unexpected class (%s), "
                        + "This token was not created by this retry strategy.",
                token.getClass().getName()));
    }

    @Override
    public String toString() {
        return "BaseRetryStrategy{" +
                ", maxAttempts=" + maxAttempts +
                ", backoffStrategy=" + backoffStrategy +
                ", exceptionCost=" + retryCost +
                ", tokenBucketStore=" + tokenBucketStore +
                '}';
    }

    public abstract static class Builder implements RetryStrategy.Builder {
        protected int maxAttempts;
        protected Integer retryCost;
        protected Integer throttlingRetryCost;
        protected int initialRetryTokens;
        protected int maxScopes;
        protected Duration backoffBaseDelay;
        protected Duration throttlingBackoffBaseDelay;
        protected Duration backoffMaxDelay;
        private Supplier<Random> randomSupplier;

        Builder() {
            this.initialRetryTokens = Constants.Standard.INITIAL_RETRY_TOKENS;
            this.maxScopes = Constants.Standard.MAX_SCOPES;
        }

        Builder(BaseRetryStrategy strategy) {
            this.maxAttempts = strategy.maxAttempts;
            this.retryCost = strategy.retryCost;
            this.throttlingRetryCost = strategy.throttlingRetryCost;
            this.initialRetryTokens = strategy.initialRetryTokens;
            this.maxScopes = strategy.maxScopes;
            this.backoffBaseDelay = strategy.backoffStrategy.baseDelay();
            this.throttlingBackoffBaseDelay = strategy.throttlingBackoffStrategy.baseDelay();
            this.backoffMaxDelay = strategy.backoffStrategy.maxDelay();
        }

        void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        void setRetryCost(int retryCost) {
            this.retryCost = retryCost;
        }

        void setThrottlingRetryCost(int throttlingRetryCost) {
            this.throttlingRetryCost = throttlingRetryCost;
        }

        void setInitialRetryTokens(int initialRetryTokens) {
            this.initialRetryTokens = initialRetryTokens;
        }

        void setMaxScopes(int maxScopes) {
            this.maxScopes = maxScopes;
        }

        void setBackoffBaseDelay(Duration backoffBaseDelay) {
            this.backoffBaseDelay = backoffBaseDelay;
        }

        void setThrottlingBackoffBaseDelay(Duration throttlingBackoffBaseDelay) {
            this.throttlingBackoffBaseDelay = throttlingBackoffBaseDelay;
        }

        void setBackoffMaxDelay(Duration backoffMaxDelay) {
            this.backoffMaxDelay = backoffMaxDelay;
        }

        void setRandomSupplier(Supplier<Random> randomSupplier) {
            this.randomSupplier = randomSupplier;
        }
    }
}
