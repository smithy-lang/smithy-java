/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.retries.api;

/**
 * Response given to the calling code by the {@link RetryStrategy} after calling
 * {@link RetryStrategy#recordSuccess(RecordSuccessRequest)}.
 *
 * @param token A {@link RetryToken} acquired a previous {@link RetryStrategy#acquireInitialToken} or
 *              {@link RetryStrategy#refreshRetryToken} call.
 */
public record RecordSuccessResponse(RetryToken token) {}
