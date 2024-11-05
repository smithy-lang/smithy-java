/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.dynamicclient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.runtime.client.core.ClientTransport;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.runtime.core.schema.ApiException;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpVersion;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public class DynamicClientTest {

    private static Model model;
    private static ShapeId service = ShapeId.from("smithy.example#Sprockets");

    @BeforeAll
    public static void setup() {
        model = Model.assembler()
            .addUnparsedModel("test.smithy", """
                $version: "2"
                namespace smithy.example

                service Sprockets {
                    operations: [CreateSprocket, GetSprocket]
                }

                operation CreateSprocket {
                    input := {}
                    output := {
                        id: String
                    }
                    errors: [InvalidSprocketId]
                }

                operation GetSprocket {
                    input := {
                        id: String
                    }
                    output := {
                        id: String
                    }
                }

                @error("client")
                structure InvalidSprocketId {
                    id: String
                }
                """)
            .assemble()
            .unwrap();
    }

    @Test
    public void requiresServiceAndModel() {
        Assertions.assertThrows(NullPointerException.class, () -> DynamicClient.builder().build());
    }

    @Test
    public void sendsRequestWithNoInput() throws Exception {
        var client = DynamicClient.builder()
            .service(service)
            .model(model)
            .protocol(new AwsJson1Protocol(service))
            .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
            .transport(mockTransport())
            .endpointResolver(EndpointResolver.staticEndpoint("https://foo.com"))
            .build();

        var result = client.call("CreateSprocket");
        assertThat(result.type(), is(ShapeType.STRUCTURE));
        assertThat(result.getMember("id").asString(), equalTo("1"));
    }

    private ClientTransport<SmithyHttpRequest, SmithyHttpResponse> mockTransport() {
        return new ClientTransport<>() {
            @Override
            public Class<SmithyHttpRequest> requestClass() {
                return SmithyHttpRequest.class;
            }

            @Override
            public Class<SmithyHttpResponse> responseClass() {
                return SmithyHttpResponse.class;
            }

            @Override
            public CompletableFuture<SmithyHttpResponse> send(Context context, SmithyHttpRequest request) {
                return CompletableFuture.completedFuture(
                    SmithyHttpResponse.builder()
                        .httpVersion(SmithyHttpVersion.HTTP_1_1)
                        .statusCode(200)
                        .body(DataStream.ofString("{\"id\":\"1\"}"))
                        .build()
                );
            }
        };
    }

    @Test
    public void sendsRequestWithInput() throws Exception {
        var client = DynamicClient.builder()
            .service(service)
            .model(model)
            .protocol(new AwsJson1Protocol(service))
            .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
            .transport(mockTransport())
            .endpointResolver(EndpointResolver.staticEndpoint("https://foo.com"))
            .addInterceptor(new ClientInterceptor() {
                @Override
                public void readBeforeTransmit(RequestHook<?, ?> hook) {
                    var input = hook.input();
                    assertThat(input, instanceOf(Document.class));
                    assertThat(((Document) input).getMember("id").asString(), equalTo("1"));
                }
            })
            .build();

        var result = client.callAsync("GetSprocket", Document.createFromObject(Map.of("id", "1"))).get();
        assertThat(result.type(), is(ShapeType.STRUCTURE));
        assertThat(result.getMember("id").asString(), equalTo("1"));
    }

    @Test
    public void errorHandling() throws Exception {
        var client = DynamicClient.builder()
            .service(service)
            .model(model)
            .protocol(new AwsJson1Protocol(service))
            .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
            .transport(new ClientTransport<SmithyHttpRequest, SmithyHttpResponse>() {
                @Override
                public Class<SmithyHttpRequest> requestClass() {
                    return SmithyHttpRequest.class;
                }

                @Override
                public Class<SmithyHttpResponse> responseClass() {
                    return SmithyHttpResponse.class;
                }

                @Override
                public CompletableFuture<SmithyHttpResponse> send(Context context, SmithyHttpRequest request) {
                    return CompletableFuture.completedFuture(
                        SmithyHttpResponse.builder()
                            .httpVersion(SmithyHttpVersion.HTTP_1_1)
                            .statusCode(400)
                            .body(
                                DataStream.ofString("{\"__type\":\"smithy.example#InvalidSprocketId\", \"id\":\"1\"}")
                            )
                            .build()
                    );
                }
            })
            .endpointResolver(EndpointResolver.staticEndpoint("https://foo.com"))
            .build();

        var e = Assertions.assertThrows(ApiException.class, () -> {
            client.call("GetSprocket", Document.createFromObject(Map.of("id", "1")));
        });
    }
}
