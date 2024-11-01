/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.api;

/**
 * Response given to the calling code by the {@link RetryStrategy} after calling
 * {@link RetryStrategy#recordSuccess(RecordSuccessRequest)}.
 */
public interface RecordSuccessResponse {
    /**
     * A {@link RetryToken} acquired a previous {@link RetryStrategy#acquireInitialToken} or
     * {@link RetryStrategy#refreshRetryToken} call.
     */
    RetryToken token();

    /**
     * Creates a new {@link RecordSuccessResponseImpl} with the given token.
     */
    static RecordSuccessResponse create(RetryToken token) {
        return new RecordSuccessResponseImpl(token);
    }
}
