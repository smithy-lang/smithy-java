/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.runtime.client.core.ClientTransport;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.runtime.dynamicclient.DynamicClient;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.java.runtime.retries.api.AcquireInitialTokenRequest;
import software.amazon.smithy.java.runtime.retries.api.AcquireInitialTokenResponse;
import software.amazon.smithy.java.runtime.retries.api.RecordSuccessRequest;
import software.amazon.smithy.java.runtime.retries.api.RecordSuccessResponse;
import software.amazon.smithy.java.runtime.retries.api.RefreshRetryTokenRequest;
import software.amazon.smithy.java.runtime.retries.api.RefreshRetryTokenResponse;
import software.amazon.smithy.java.runtime.retries.api.RetryStrategy;
import software.amazon.smithy.java.runtime.retries.api.RetryToken;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public class AmzSdkRequestPluginTest {

    private static final Model MODEL = Model.assembler()
        .addUnparsedModel("test.smithy", """
            $version: "2"
            namespace smithy.example

            service Sprockets {
                operations: [CreateSprocket]
            }

            operation CreateSprocket {
                input := {}
                output := {}
            }
            """)
        .assemble()
        .unwrap();

    private static final ShapeId SERVICE = ShapeId.from("smithy.example#Sprockets");

    private static final class Token implements RetryToken {}

    @Test
    public void injectsHeader() {
        AtomicReference<SmithyHttpRequest> ref = new AtomicReference<>();

        var client = DynamicClient.builder()
            .service(SERVICE)
            .model(MODEL)
            .protocol(new AwsJson1Protocol(SERVICE))
            .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
            .transport(new ClientTransport<SmithyHttpRequest, SmithyHttpResponse>() {
                @Override
                public CompletableFuture<SmithyHttpResponse> send(Context context, SmithyHttpRequest request) {
                    return CompletableFuture.completedFuture(
                        SmithyHttpResponse.builder().statusCode(200).body(DataStream.ofString("{}")).build()
                    );
                }

                @Override
                public Class<SmithyHttpRequest> requestClass() {
                    return SmithyHttpRequest.class;
                }

                @Override
                public Class<SmithyHttpResponse> responseClass() {
                    return SmithyHttpResponse.class;
                }
            })
            .endpointResolver(EndpointResolver.staticEndpoint("https://foo.com"))
            .addPlugin(new AmzSdkRequestPlugin())
            .addInterceptor(new ClientInterceptor() {
                @Override
                public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
                    ref.set(((SmithyHttpRequest) hook.request()));
                }
            })
            .retryStrategy(new RetryStrategy() {
                @Override
                public AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request) {
                    return new AcquireInitialTokenResponse(new Token(), Duration.ZERO);
                }

                @Override
                public RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public RecordSuccessResponse recordSuccess(RecordSuccessRequest request) {
                    return new RecordSuccessResponse(new Token());
                }

                @Override
                public int maxAttempts() {
                    return 3;
                }
            })
            .build();

        client.call("CreateSprocket");

        assertThat(ref.get().headers().firstValue("amz-sdk-request"), equalTo("attempt=1; max=3"));
    }
}
