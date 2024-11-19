/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.retries.api;

/**
 * Request that the calling code makes to the {@link RetryStrategy} using
 * {@link RetryStrategy#recordSuccess(RecordSuccessRequest)} to notify that the attempted execution succeeded.
 *
 * @param token A {@link RetryToken} acquired a previous {@link RetryStrategy#acquireInitialToken} or
 *              {@link RetryStrategy#refreshRetryToken} call.
 */
public record RecordSuccessRequest(RetryToken token) {}
