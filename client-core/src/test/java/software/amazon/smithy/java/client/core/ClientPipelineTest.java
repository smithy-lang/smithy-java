/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.client.http.mock.MockPlugin;
import software.amazon.smithy.java.client.http.mock.MockQueue;
import software.amazon.smithy.java.core.serde.document.Document;
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
import software.amazon.smithy.java.retries.api.TokenAcquisitionFailedException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public class ClientPipelineTest {

    private static final Model MODEL = Model.assembler()
            .addUnparsedModel("test.smithy", """
                    $version: "2"
                    namespace smithy.example

                    @aws.protocols#restJson1
                    service Sprockets {
                        operations: [GetSprocket]
                        errors: [ServiceFooError]
                    }

                    @http(method: "POST", uri: "/s")
                    operation GetSprocket {
                        input := {
                            id: String
                        }
                        output := {
                            id: String
                        }
                        errors: [InvalidSprocketId]
                    }

                    @error("client")
                    @httpError(429)
                    @retryable
                    structure InvalidSprocketId {}

                    @error("server")
                    @httpError(500)
                    structure ServiceFooError {}
                    """)
            .discoverModels()
            .assemble()
            .unwrap();

    @Test
    public void canShortCircuitRequests() {
        var service = ShapeId.from("smithy.example#Sprockets");
        var client = DynamicClient.builder()
                .service(service)
                .model(MODEL)
                .protocol(new RestJsonClientProtocol(service))
                .transport(new JavaHttpClientTransport())
                .endpointResolver(EndpointResolver.staticEndpoint("https://localhost:8081"))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .retryStrategy(new RetryStrategy() {
                    @Override
                    public AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request) {
                        throw new TokenAcquisitionFailedException("Short circuit");
                    }

                    @Override
                    public RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public RecordSuccessResponse recordSuccess(RecordSuccessRequest request) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public int maxAttempts() {
                        return 1;
                    }

                    @Override
                    public Builder toBuilder() {
                        throw new UnsupportedOperationException();
                    }
                })
                .build();

        var e = Assertions.assertThrows(TokenAcquisitionFailedException.class, () -> client.call("GetSprocket"));

        assertThat(e.getMessage(), equalTo("Short circuit"));
    }

    @Test
    public void canRetryRequests() {
        var service = ShapeId.from("smithy.example#Sprockets");
        var calls = new ArrayList<>();

        var mockQueue = new MockQueue()
                .enqueue(
                        HttpResponse.builder()
                                .statusCode(429)
                                .body(DataStream.ofString("{\"__type\":\"InvalidSprocketId\"}"))
                                .build())
                .enqueue(
                        HttpResponse.builder()
                                .statusCode(200)
                                .body(DataStream.ofString("{\"id\":\"1\"}"))
                                .build());
        var mock = MockPlugin.builder().addQueue(mockQueue).build();

        var client = DynamicClient.builder()
                .service(service)
                .model(MODEL)
                .addPlugin(mock)
                .endpointResolver(EndpointResolver.staticEndpoint("https://localhost:8081"))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .retryStrategy(new RetryStrategy() {
                    @Override
                    public AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request) {
                        calls.add("Acquire");
                        return new AcquireInitialTokenResponse(new Token(0), Duration.ZERO);
                    }

                    @Override
                    public RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request) {
                        calls.add("Refresh");
                        if (request.token() instanceof Token t) {
                            return new RefreshRetryTokenResponse(new Token(t.retry + 1), Duration.ZERO);
                        }
                        throw new IllegalArgumentException();
                    }

                    @Override
                    public RecordSuccessResponse recordSuccess(RecordSuccessRequest request) {
                        if (request.token() instanceof Token t) {
                            calls.add("Success: " + t.retry);
                            return new RecordSuccessResponse(request.token());
                        } else {
                            throw new IllegalArgumentException();
                        }
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

        var response = client.call("GetSprocket", Document.ofObject(Map.of("id", "1")));

        assertThat(mockQueue.remaining(), is(0));
        assertThat(response.getMember("id").asString(), equalTo("1"));
        assertThat(response, instanceOf(Document.class));
        assertThat(calls, contains("Acquire", "Refresh", "Success: 1"));
    }

    private static final class Token implements RetryToken {
        int retry;

        Token(int retry) {
            this.retry = retry;
        }
    }
}
