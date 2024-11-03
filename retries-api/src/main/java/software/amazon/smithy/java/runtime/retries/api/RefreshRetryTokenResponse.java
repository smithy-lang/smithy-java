/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.api;

import java.time.Duration;

/**
 * Response from the {@link RetryStrategy} after calling {@link RetryStrategy#refreshRetryToken(RefreshRetryTokenRequest)}.
 *
 * @param token A {@link RetryToken} acquired by this invocation, used in subsequent
 *              {@link RetryStrategy#refreshRetryToken} or {@link RetryStrategy#recordSuccess} calls.
 * @param delay The amount of time to wait before performing the next attempt.
 */
public record RefreshRetryTokenResponse(RetryToken token, Duration delay) {}
