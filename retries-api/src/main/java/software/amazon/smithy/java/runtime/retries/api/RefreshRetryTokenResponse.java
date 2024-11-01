/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.api;

import java.time.Duration;

/**
 * Response from the {@link RetryStrategy} after calling {@link RetryStrategy#refreshRetryToken(RefreshRetryTokenRequest)}.
 */
public interface RefreshRetryTokenResponse {
    /**
     * A {@link RetryToken} acquired by this invocation, used in subsequent {@link RetryStrategy#refreshRetryToken} or
     * {@link RetryStrategy#recordSuccess} calls.
     */
    RetryToken token();

    /**
     * The amount of time to wait before performing the next attempt.
     */
    Duration delay();

    /**
     * Creates a new {@link RefreshRetryTokenResponse} with the given token and delay.
     */
    static RefreshRetryTokenResponse create(RetryToken token, Duration delay) {
        return new RefreshRetryTokenResponseImpl(token, delay);
    }
}
