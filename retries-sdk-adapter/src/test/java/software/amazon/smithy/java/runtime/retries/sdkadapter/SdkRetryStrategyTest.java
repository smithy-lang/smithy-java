/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.sdkadapter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

import java.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.retries.DefaultRetryStrategy;
import software.amazon.awssdk.retries.api.RetryStrategy;
import software.amazon.smithy.java.runtime.retries.api.AcquireInitialTokenRequest;
import software.amazon.smithy.java.runtime.retries.api.RecordSuccessRequest;
import software.amazon.smithy.java.runtime.retries.api.RefreshRetryTokenRequest;
import software.amazon.smithy.java.runtime.retries.api.TokenAcquisitionFailedException;

public class SdkRetryStrategyTest {
    @Test
    public void bridgesAwsSdk() {
        RetryStrategy sdk = DefaultRetryStrategy.doNotRetry();
        var adapted = new SdkRetryStrategy(sdk);

        assertThat(adapted.maxAttempts(), equalTo(sdk.maxAttempts()));
    }

    @Test
    public void acquiresToken() {
        var adapted = new SdkRetryStrategy(DefaultRetryStrategy.doNotRetry());
        var attempt = AcquireInitialTokenRequest.create("foo");
        var result = adapted.acquireInitialToken(attempt);

        assertThat(result.delay(), equalTo(Duration.ZERO));
        assertThat(result.token(), instanceOf(SdkRetryToken.class));
    }

    @Test
    public void refreshesToken() {
        var adapted = new SdkRetryStrategy(DefaultRetryStrategy.doNotRetry());
        var acquire = adapted.acquireInitialToken(AcquireInitialTokenRequest.create("foo"));

        var refresh = RefreshRetryTokenRequest.create(acquire.token(), new RuntimeException("hi"));

        // Throws when the exception isn't retryable.
        Assertions.assertThrows(TokenAcquisitionFailedException.class, () -> {
            adapted.refreshRetryToken(refresh);
        });
    }

    @Test
    public void returnsTokens() {
        var adapted = new SdkRetryStrategy(DefaultRetryStrategy.doNotRetry());
        var acquire = adapted.acquireInitialToken(AcquireInitialTokenRequest.create("foo"));

        var result = adapted.recordSuccess(RecordSuccessRequest.create(acquire.token()));

        assertThat(result.token(), instanceOf(SdkRetryToken.class));
    }
}
