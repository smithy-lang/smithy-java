/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.api;

import java.time.Duration;

/**
 * Encapsulates the response from the {@link RetryStrategy} to the request to start the attempts to be executed.
 *
 * @param token A {@link RetryToken} acquired by this invocation, used in subsequent
 *              {@link RetryStrategy#refreshRetryToken} or {@link RetryStrategy#recordSuccess} calls.
 * @param delay The amount of time to wait before performing the first attempt.
 */
public record AcquireInitialTokenResponse(RetryToken token, Duration delay) {}
