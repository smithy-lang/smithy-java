/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.http.mock.MockPlugin;
import software.amazon.smithy.java.client.http.mock.MockQueue;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.retries.api.AcquireInitialTokenRequest;
import software.amazon.smithy.java.retries.api.AcquireInitialTokenResponse;
import software.amazon.smithy.java.retries.api.RecordSuccessRequest;
import software.amazon.smithy.java.retries.api.RecordSuccessResponse;
import software.amazon.smithy.java.retries.api.RefreshRetryTokenRequest;
import software.amazon.smithy.java.retries.api.RefreshRetryTokenResponse;
import software.amazon.smithy.java.retries.api.RetryStrategy;
import software.amazon.smithy.java.retries.api.RetryToken;

public class AmzSdkRequestPluginTest {

    private static final class Token implements RetryToken {}

    @Test
    public void injectsHeader() {
        var mockQueue = new MockQueue();
        var mock = MockPlugin.builder().addQueue(mockQueue).build();

        var client = DynamicClient.builder()
                .serviceId(TestHarness.SERVICE)
                .model(TestHarness.MODEL)
                .protocol(new AwsJson1Protocol(TestHarness.SERVICE))
                .addPlugin(mock)
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .endpointResolver(EndpointResolver.staticEndpoint("https://foo.com"))
                .addPlugin(new AmzSdkRequestPlugin())
                .retryStrategy(new RetryStrategy() {
                    @Override
                    public AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request) {
                        return new AcquireInitialTokenResponse(new Token(), Duration.ZERO);
                    }

                    @Override
                    public RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request) {
                        return new RefreshRetryTokenResponse(new Token(), Duration.ZERO);
                    }

                    @Override
                    public RecordSuccessResponse recordSuccess(RecordSuccessRequest request) {
                        return new RecordSuccessResponse(request.token());
                    }

                    @Override
                    public int maxAttempts() {
                        return 3;
                    }

                    @Override
                    public Builder toBuilder() {
                        throw new UnsupportedOperationException();
                    }
                })
                .build();

        mockQueue.enqueue(
                HttpResponse.builder()
                        .statusCode(429)
                        .body(DataStream.ofString("{\"__type\":\"InvalidSprocketId\"}"))
                        .build());
        mockQueue.enqueue(
                HttpResponse.builder()
                        .statusCode(200)
                        .body(DataStream.ofString("{\"id\":\"1\"}"))
                        .build());

        client.call("CreateSprocket");

        var requests = mock.getRequests();
        assertThat(requests, hasSize(2));
        assertThat(requests.get(0).request().headers().firstValue("amz-sdk-request"), equalTo("attempt=1; max=3"));
        assertThat(requests.get(1).request().headers().firstValue("amz-sdk-request"), equalTo("attempt=2; max=3"));
    }
}
