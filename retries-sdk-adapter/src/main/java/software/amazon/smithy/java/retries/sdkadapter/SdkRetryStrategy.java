/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.retries.sdkadapter;

import java.time.Duration;
import software.amazon.smithy.java.retries.api.AcquireInitialTokenRequest;
import software.amazon.smithy.java.retries.api.AcquireInitialTokenResponse;
import software.amazon.smithy.java.retries.api.RecordSuccessRequest;
import software.amazon.smithy.java.retries.api.RecordSuccessResponse;
import software.amazon.smithy.java.retries.api.RefreshRetryTokenRequest;
import software.amazon.smithy.java.retries.api.RefreshRetryTokenResponse;
import software.amazon.smithy.java.retries.api.RetryInfo;
import software.amazon.smithy.java.retries.api.RetrySafety;
import software.amazon.smithy.java.retries.api.RetryStrategy;
import software.amazon.smithy.java.retries.api.RetryToken;
import software.amazon.smithy.java.retries.api.TokenAcquisitionFailedException;

/**
 * A Smithy retry strategy that uses a retry strategy from the AWS SDK for Java V2.
 */
public class SdkRetryStrategy implements RetryStrategy {

    private final software.amazon.awssdk.retries.api.RetryStrategy delegate;

    private SdkRetryStrategy(software.amazon.awssdk.retries.api.RetryStrategy delegate) {
        this.delegate = delegate;
    }

    /**
     * Create a wrapper for the given SDK RetryStrategy strategy that makes it work as a Smithy RetryStrategy.
     *
     * <p>This method <em>does not</em> attempt to modify the given retry strategy to make it account for
     * {@link RetryInfo} properties; it assumes you've already accounted for that somehow.
     *
     * @param delegate The SDK RetryStrategy to wrap.
     * @return the created Smithy RetryStrategy.
     */
    public static SdkRetryStrategy ofPrepared(software.amazon.awssdk.retries.api.RetryStrategy delegate) {
        return new SdkRetryStrategy(delegate);
    }

    /**
     * Create a wrapped retry strategy that looks at the populated {@link RetryInfo} of Smithy exceptions and
     * makes calls to the AWS SDK retry strategy's builder to use {@link RetryInfo#isRetrySafe()} and
     * {@link RetryInfo#isThrottle()}.
     *
     * @param delegate The retry strategy to use from the AWS SDK.
     * @return the created Smithy RetryStrategy.
     */
    public static SdkRetryStrategy of(software.amazon.awssdk.retries.api.RetryStrategy delegate) {
        var builder = delegate.toBuilder();
        builder.retryOnException(e -> {
            var ri = getInfo(e);
            return ri != null && ri.isRetrySafe() == RetrySafety.YES;
        });
        builder.treatAsThrottling(e -> {
            var ri = getInfo(e);
            return ri != null && ri.isThrottle();
        });
        return ofPrepared(builder.build());
    }

    private static RetryInfo getInfo(Throwable e) {
        if (e instanceof RetryInfo r) {
            return r;
        } else if (e.getCause() != null) {
            return getInfo(e.getCause());
        } else {
            return null;
        }
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

    @Override
    public Builder toBuilder() {
        var rebuild = delegate.toBuilder();
        return new Builder() {
            @Override
            public RetryStrategy build() {
                return new SdkRetryStrategy(rebuild.build());
            }

            @Override
            public Builder maxAttempts(int maxAttempts) {
                rebuild.maxAttempts(maxAttempts);
                return this;
            }
        };
    }
}
