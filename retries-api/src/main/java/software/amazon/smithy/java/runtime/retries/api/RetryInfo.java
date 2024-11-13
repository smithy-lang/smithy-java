/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.api;

import java.time.Duration;

/**
 * Provides retry-specific information about an error.
 */
public interface RetryInfo {
    /**
     * Get the decision about whether it's safe to retry the encountered error.
     *
     * <p>If the decision is {@link RetrySafety#YES}, it does not mean that a retry will occur, but rather than a
     * retry is allowed to occur.
     *
     * @return whether it's safe to retry.
     */
    RetrySafety isRetrySafe();

    /**
     * Check if the error is a throttling error.
     *
     * @return the error type.
     */
    boolean isThrottle();

    /**
     * Get the amount of time to wait before retrying.
     *
     * @return the time to wait before retrying, or null if no hint for a retry-after was detected.
     */
    Duration retryAfter();
}
