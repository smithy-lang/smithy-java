/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.http.api.HttpRequest;
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
        AtomicInteger attempt = new AtomicInteger(0);

        var client = DynamicClient.builder()
            .service(SERVICE)
            .model(MODEL)
            .protocol(new AwsJson1Protocol(SERVICE))
            .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
            .transport(new ClientTransport<HttpRequest, HttpResponse>() {
                @Override
                public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
                    return HttpMessageExchange.INSTANCE;
                }

                @Override
                public CompletableFuture<HttpResponse> send(Context context, HttpRequest request) {
                    var i = attempt.incrementAndGet();
                    if (i == 1) {
                        return CompletableFuture.completedFuture(
                            HttpResponse.builder()
                                .statusCode(429)
                                .body(DataStream.ofString("{\"__type\":\"InvalidSprocketId\"}"))
                                .build()
                        );
                    } else if (i == 2) {
                        return CompletableFuture.completedFuture(
                            HttpResponse.builder()
                                .statusCode(200)
                                .body(DataStream.ofString("{\"id\":\"1\"}"))
                                .build()
                        );
                    } else {
                        throw new IllegalStateException("Unexpected attempt " + i);
                    }
                }
            })
            .endpointResolver(EndpointResolver.staticEndpoint("https://foo.com"))
            .addPlugin(new AmzSdkRequestPlugin())
            .addInterceptor(new ClientInterceptor() {
                @Override
                public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
                    var request = (HttpRequest) hook.request();
                    var i = attempt.get();
                    assertThat(
                        request.headers().allValues("amz-sdk-request"),
                        contains("attempt=" + (i + 1) + "; max=3")
                    );
                }
            })
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

        client.call("CreateSprocket");
    }
}
