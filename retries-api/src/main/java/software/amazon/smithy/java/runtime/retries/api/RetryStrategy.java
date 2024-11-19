/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.api;

/**
 * A strategy used to determine when something should be retried.
 *
 * <p>Terminology:
 * <ol>
 *     <li>An <b>attempt</b> is a single invocation of an action.
 *     <li>The <b>attempt count</b> is which attempt (starting with 1) the client is attempting to make.
 * </ol>
 */
public interface RetryStrategy {
    /**
     * Invoked before the first request attempt.
     *
     * <p>Callers MUST wait for the {@code delay} returned by this call before making the first attempt. Callers that
     * wish to retry a failed attempt MUST call {@link #refreshRetryToken} before doing so.
     *
     * <p>If the attempt was successful, callers MUST call {@link #recordSuccess}.
     *
     * @throws NullPointerException            if a required parameter is not specified
     * @throws TokenAcquisitionFailedException if a token cannot be acquired
     */
    AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request);

    /**
     * Invoked before each subsequent (non-first) request attempt.
     *
     * <p>Callers MUST wait for the {@code delay} returned by this call before making the next attempt. If the next
     * attempt fails, callers MUST re-call {@link #refreshRetryToken} before attempting another retry. This call
     * invalidates the provided token and returns a new one. Callers MUST use the new token.
     *
     * <p>If the attempt was successful, callers MUST call {@link #recordSuccess}.
     *
     * @throws NullPointerException            if a required parameter is not specified
     * @throws IllegalArgumentException        if the provided token was not issued by this strategy or the provided
     *                                         token was already used for a previous refresh or success call.
     * @throws TokenAcquisitionFailedException if a token cannot be acquired
     */
    RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request);

    /**
     * Invoked after an attempt succeeds.
     *
     * @throws NullPointerException     if a required parameter is not specified
     * @throws IllegalArgumentException if the provided token was not issued by this strategy or the provided token was
     *                                  already used for a previous refresh or success call.
     */
    RecordSuccessResponse recordSuccess(RecordSuccessRequest request);

    /**
     * Returns the maximum numbers attempts that this retry strategy will allow.
     *
     * @return the maximum numbers attempts that this retry strategy will allow.
     */
    int maxAttempts();

    /**
     * Create a builder for this strategy, allowing things like {@link #maxAttempts()} to be customized.
     *
     * @return the builder.
     */
    Builder toBuilder();

    /**
     * Create a RetryStrategy that only allows the first request, but does not allow retries.
     *
     * @return the created strategy.
     */
    static RetryStrategy noRetries() {
        return NoRetryImpl.INSTANCE;
    }

    /**
     * A builder used to create or modify a RetryStrategy.
     */
    interface Builder {
        /**
         * Create the retry strategy.
         *
         * @return the created strategy.
         */
        RetryStrategy build();

        /**
         * Set the max attempts of the strategy.
         *
         * @param maxAttempts Max attempts to use before giving up (1 means one attempt and no retries, 2 is a single
         *                    retry if the first call fails, etc).
         * @return the builder.
         */
        Builder maxAttempts(int maxAttempts);
    }
}
