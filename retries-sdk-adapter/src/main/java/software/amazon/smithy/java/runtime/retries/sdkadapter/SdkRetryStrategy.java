/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.sdkadapter;

import java.time.Duration;
import software.amazon.smithy.java.runtime.retries.api.AcquireInitialTokenRequest;
import software.amazon.smithy.java.runtime.retries.api.AcquireInitialTokenResponse;
import software.amazon.smithy.java.runtime.retries.api.RecordSuccessRequest;
import software.amazon.smithy.java.runtime.retries.api.RecordSuccessResponse;
import software.amazon.smithy.java.runtime.retries.api.RefreshRetryTokenRequest;
import software.amazon.smithy.java.runtime.retries.api.RefreshRetryTokenResponse;
import software.amazon.smithy.java.runtime.retries.api.RetryInfo;
import software.amazon.smithy.java.runtime.retries.api.RetryStrategy;
import software.amazon.smithy.java.runtime.retries.api.RetryToken;
import software.amazon.smithy.java.runtime.retries.api.TokenAcquisitionFailedException;

/**
 * A Smithy retry strategy that uses a retry strategy from the AWS SDK for Java V2.
 */
public class SdkRetryStrategy implements RetryStrategy {

    private final software.amazon.awssdk.retries.api.RetryStrategy delegate;

    /**
     * @param delegate The retry strategy to use from the AWS SDK.
     */
    public SdkRetryStrategy(software.amazon.awssdk.retries.api.RetryStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request) {
        try {
            var delegateRequest = software.amazon.awssdk.retries.api.AcquireInitialTokenRequest.create(request.scope());
            var delegateResponse = delegate.acquireInitialToken(delegateRequest);
            var adaptedToken = new SdkRetryToken(delegateResponse.token());
            return new AcquireInitialTokenResponse(adaptedToken, delegateResponse.delay());
        } catch (software.amazon.awssdk.retries.api.TokenAcquisitionFailedException e) {
            throw new TokenAcquisitionFailedException(e.getMessage(), e);
        }
    }

    @Override
    public RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request) {
        try {
            var suggestedDelay = request.suggestedDelay();
            if (suggestedDelay == null && request.failure() instanceof RetryInfo info) {
                suggestedDelay = info.retryAfter();
            }
            var delegateRequest = software.amazon.awssdk.retries.api.RefreshRetryTokenRequest.builder()
                .token(getDelegatedRetryToken(request.token()))
                .failure(request.failure())
                .suggestedDelay(suggestedDelay == null ? Duration.ZERO : suggestedDelay)
                .build();
            var delegateResponse = delegate.refreshRetryToken(delegateRequest);
            var adaptedToken = new SdkRetryToken(delegateResponse.token());
            return new RefreshRetryTokenResponse(adaptedToken, delegateResponse.delay());
        } catch (software.amazon.awssdk.retries.api.TokenAcquisitionFailedException e) {
            throw new TokenAcquisitionFailedException(e.getMessage(), e);
        }
    }

    private static software.amazon.awssdk.retries.api.RetryToken getDelegatedRetryToken(RetryToken token) {
        if (token instanceof SdkRetryToken t) {
            return t.delegate();
        } else {
            throw new IllegalArgumentException("Unexpected retry token: " + token);
        }
    }

    @Override
    public RecordSuccessResponse recordSuccess(RecordSuccessRequest request) {
        var token = getDelegatedRetryToken(request.token());
        var delegateRequest = software.amazon.awssdk.retries.api.RecordSuccessRequest.create(token);
        var delegateResponse = delegate.recordSuccess(delegateRequest);
        return new RecordSuccessResponse(new SdkRetryToken(delegateResponse.token()));
    }

    @Override
    public int maxAttempts() {
        return delegate.maxAttempts();
    }
}
