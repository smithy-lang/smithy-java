/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.api;

import java.time.Duration;

/**
 * Encapsulates the response from the {@link RetryStrategy} to the request to start the attempts to be executed.
 */
public interface AcquireInitialTokenResponse {
    /**
     * A {@link RetryToken} acquired by this invocation, used in subsequent {@link RetryStrategy#refreshRetryToken} or
     * {@link RetryStrategy#recordSuccess} calls.
     */
    RetryToken token();

    /**
     * The amount of time to wait before performing the first attempt.
     */
    Duration delay();

    /**
     * Creates a new {@link AcquireInitialTokenRequest} instance with the given scope.
     */
    static AcquireInitialTokenResponse create(RetryToken token, Duration delay) {
        return new AcquireInitialTokenResponseImpl(token, delay);
    }
}
