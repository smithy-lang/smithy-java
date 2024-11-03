/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.api;

import java.time.Duration;

final class NoRetryImpl implements RetryStrategy {

    static final RetryStrategy INSTANCE = new NoRetryImpl();
    private static final RetryToken TOKEN = new RetryToken() {};
    private static final AcquireInitialTokenResponse ACQUIRE = new AcquireInitialTokenResponse(TOKEN, Duration.ZERO);
    private static final RecordSuccessResponse SUCCESS = new RecordSuccessResponse(TOKEN);

    @Override
    public AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request) {
        return ACQUIRE;
    }

    @Override
    public RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request) {
        throw new TokenAcquisitionFailedException("No retries available");
    }

    @Override
    public RecordSuccessResponse recordSuccess(RecordSuccessRequest request) {
        return SUCCESS;
    }

    @Override
    public int maxAttempts() {
        return 1;
    }
}
