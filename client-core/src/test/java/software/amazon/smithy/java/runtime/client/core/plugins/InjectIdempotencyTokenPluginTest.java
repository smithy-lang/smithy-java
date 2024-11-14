/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.plugins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.runtime.client.core.ClientTransport;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.dynamicclient.DynamicClient;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public class InjectIdempotencyTokenPluginTest {

    private static final Model MODEL = Model.assembler()
        .addUnparsedModel("test.smithy", """
            $version: "2"
            namespace smithy.example

            service Sprockets {
                operations: [CreateSprocket, CreateSprocketNoToken]
            }

            @http(method: "POST", uri: "/{id}")
            operation CreateSprocket {
                input := {
                    @required
                    @httpLabel
                    id: String

                    @idempotencyToken
                    @httpHeader("x-token")
                    token: String
                }
                output := {}
            }

            @http(method: "POST", uri: "/no-token/{id}")
            operation CreateSprocketNoToken {
                input := {
                    @required
                    @httpLabel
                    id: String
                }
                output := {}
            }
            """)
        .assemble()
        .unwrap();

    private static final ShapeId SERVICE = ShapeId.from("smithy.example#Sprockets");

    @Test
    public void injectsToken() {
        var token = callAndGetToken("CreateSprocket", Document.createFromObject(Map.of("id", "1")));

        assertThat(token, not(nullValue()));
        assertThat(token.length(), greaterThan(0));
    }

    private String callAndGetToken(String operation, Document input) {
        AtomicReference<String> ref = new AtomicReference<>();
        var client = DynamicClient.builder()
            .service(SERVICE)
            .model(MODEL)
            .protocol(new RestJsonClientProtocol(SERVICE))
            .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
            .transport(new ClientTransport<SmithyHttpRequest, SmithyHttpResponse>() {
                @Override
                public CompletableFuture<SmithyHttpResponse> send(Context context, SmithyHttpRequest request) {
                    return CompletableFuture.completedFuture(
                        SmithyHttpResponse.builder()
                            .statusCode(200)
                            .headers(HttpHeaders.of(Map.of("content-type", List.of("application/json"))))
                            .body(DataStream.ofString("{}"))
                            .build()
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
            .addPlugin(new InjectIdempotencyTokenPlugin())
            .addInterceptor(new ClientInterceptor() {
                @Override
                public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
                    ref.set(((SmithyHttpRequest) hook.request()).headers().firstValue("x-token"));
                }
            })
            .build();

        client.call(operation, Document.createFromObject(input));

        return ref.get();
    }

    @Test
    public void doesNotInjectToken() {
        var token = callAndGetToken("CreateSprocketNoToken", Document.createFromObject(Map.of("id", "1")));

        assertThat(token, nullValue());
    }

    @Test
    public void usesProvidedToken() {
        var token = callAndGetToken("CreateSprocket", Document.createFromObject(Map.of("id", "1", "token", "xyz")));

        assertThat(token, equalTo("xyz"));
    }

    @Test
    public void ignoresEmptyStringToken() {
        var token = callAndGetToken("CreateSprocket", Document.createFromObject(Map.of("id", "1", "token", "")));

        assertThat(token, notNullValue());
        assertThat(token, not(equalTo("")));
    }
}
