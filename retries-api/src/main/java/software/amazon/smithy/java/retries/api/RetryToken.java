/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.retries.api;

/**
 * An opaque token representing an in-progress execution.
 *
 * <p>Created via {@link RetryStrategy#acquireInitialToken} before a first attempt and refreshed
 * after each attempt failure via {@link RetryStrategy#refreshRetryToken}.
 *
 * <p>Released via {@link RetryStrategy#recordSuccess} after a successful attempt.
 */
public interface RetryToken {}
