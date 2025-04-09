/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.plugins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.http.mock.MockPlugin;
import software.amazon.smithy.java.client.http.mock.MockedResult;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public class InjectIdempotencyTokenPluginTest {

    private static final Model MODEL = Model.assembler()
            .addUnparsedModel("test.smithy", """
                    $version: "2"
                    namespace smithy.example

                    @aws.protocols#restJson1
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
            .discoverModels()
            .assemble()
            .unwrap();

    private static final ShapeId SERVICE = ShapeId.from("smithy.example#Sprockets");

    @Test
    public void injectsToken() {
        var token = callAndGetToken("CreateSprocket", Document.ofObject(Map.of("id", "1")));

        assertThat(token, not(nullValue()));
        assertThat(token.length(), greaterThan(0));
    }

    private String callAndGetToken(String operation, Document input) {
        var mock = MockPlugin.builder()
                .addMatcher(
                        request -> new MockedResult.Response(
                                HttpResponse.builder()
                                        .statusCode(200)
                                        .headers(HttpHeaders.of(Map.of("content-type", List.of("application/json"))))
                                        .body(DataStream.ofString("{}"))
                                        .build()))
                .build();

        var client = DynamicClient.builder()
                .service(SERVICE)
                .model(MODEL)
                .addPlugin(mock)
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .endpointResolver(EndpointResolver.staticEndpoint("https://foo.com"))
                .addPlugin(new InjectIdempotencyTokenPlugin())
                .addPlugin(mock)
                .build();

        client.call(operation, input);
        assertThat(mock.getRequests(), not(empty()));
        return mock.getRequests().get(0).request().headers().firstValue("x-token");
    }

    @Test
    public void doesNotInjectToken() {
        var token = callAndGetToken("CreateSprocketNoToken", Document.ofObject(Map.of("id", "1")));

        assertThat(token, nullValue());
    }

    @Test
    public void usesProvidedToken() {
        var token = callAndGetToken("CreateSprocket", Document.ofObject(Map.of("id", "1", "token", "xyz")));

        assertThat(token, equalTo("xyz"));
    }

    @Test
    public void ignoresEmptyStringToken() {
        var token = callAndGetToken("CreateSprocket", Document.ofObject(Map.of("id", "1", "token", "")));

        assertThat(token, notNullValue());
        assertThat(token, not(equalTo("")));
    }
}
