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
 *
 * @param token          A {@link RetryToken} acquired a previous {@link RetryStrategy#acquireInitialToken} or
 *                       {@link RetryStrategy#refreshRetryToken} call.
 * @param failure        The cause of the last attempt failure.
 * @param suggestedDelay A suggestion of how long to wait from the last attempt failure. For HTTP calls, this
 *                       is usually extracted from a "retry after" header from the downstream service.
 */
public record RefreshRetryTokenRequest(RetryToken token, Throwable failure, Duration suggestedDelay) {}
