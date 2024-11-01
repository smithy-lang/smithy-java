/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.api;

import java.time.Duration;

/**
 * Request that the calling code makes to the {@link RetryStrategy} using
 * {@link RetryStrategy#refreshRetryToken(RefreshRetryTokenRequest)} to notify that the attempted execution failed and
 * the {@link RetryToken} needs to be refreshed.
 */
public interface RefreshRetryTokenRequest {
    /**
     * A {@link RetryToken} acquired a previous {@link RetryStrategy#acquireInitialToken} or
     * {@link RetryStrategy#refreshRetryToken} call.
     */
    RetryToken token();

    /**
     * A suggestion of how long to wait from the last attempt failure. For HTTP calls, this is usually extracted from
     * a "retry after" header from the downstream service.
     *
     * @return the non-nullable suggested delay, or {@link Duration#ZERO}.
     */
    Duration suggestedDelay();

    /**
     * The cause of the last attempt failure.
     *
     * @return the last failure.
     */
    Throwable failure();

    /**
     * Create a RefreshRetryTokenRequest.
     *
     * @param token The token to use.
     * @param failure The cause of the last attempt failure.
     * @return the created request.
     */
    static RefreshRetryTokenRequest create(RetryToken token, Throwable failure) {
        return create(token, failure, null);
    }

    /**
     * Create a RefreshRetryTokenRequest.
     *
     * @param token The token to use.
     * @param failure The cause of the last attempt failure.
     * @param suggestedDelay The suggested delay.
     * @return the created request.
     */
    static RefreshRetryTokenRequest create(RetryToken token, Throwable failure, Duration suggestedDelay) {
        return new RefreshRetryTokenRequestImpl(token, failure, suggestedDelay);
    }
}
