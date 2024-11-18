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
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.http.api.HttpRequest;
import software.amazon.smithy.java.runtime.http.api.HttpResponse;
import software.amazon.smithy.java.runtime.http.api.HttpVersion;
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

                @aws.protocols#awsJson1_0
                service Sprockets {
                    operations: [CreateSprocket, GetSprocket]
                    errors: [ServiceFooError]
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
                    errors: [InvalidSprocketId]
                }

                @error("client")
                structure InvalidSprocketId {
                    id: String
                }

                @error("server")
                structure ServiceFooError {
                    why: String
                }
                """)
            .discoverModels()
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

    private ClientTransport<HttpRequest, HttpResponse> mockTransport() {
        return new ClientTransport<>() {
            @Override
            public Class<HttpRequest> requestClass() {
                return HttpRequest.class;
            }

            @Override
            public Class<HttpResponse> responseClass() {
                return HttpResponse.class;
            }

            @Override
            public CompletableFuture<HttpResponse> send(Context context, HttpRequest request) {
                return CompletableFuture.completedFuture(
                    HttpResponse.builder()
                        .httpVersion(HttpVersion.HTTP_1_1)
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
                public void readBeforeTransmit(RequestHook<?, ?, ?> hook) {
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
    public void deserializesDynamicErrorsWithAbsoluteId() {
        var client = createErrorClient("{\"__type\":\"smithy.example#InvalidSprocketId\", \"id\":\"1\"}");
        var e = Assertions.assertThrows(ApiException.class, () -> {
            client.call("GetSprocket", Document.createFromObject(Map.of("id", "1")));
        });

        assertThat(e, instanceOf(ModeledApiException.class));
        assertThat(e, instanceOf(DocumentException.class));

        var de = (DocumentException) e;
        assertThat(de.schema().id().getName(), equalTo("InvalidSprocketId"));
        var doc = de.getContents();
        assertThat(doc.getMember("id").asString(), equalTo("1"));
        assertThat(doc.type(), equalTo(ShapeType.STRUCTURE));
    }

    @Test
    public void deserializesDynamicErrorsWithRelativeId() {
        var client = createErrorClient("{\"__type\":\"InvalidSprocketId\", \"id\":\"1\"}");
        var e = Assertions.assertThrows(ApiException.class, () -> {
            client.call("GetSprocket", Document.createFromObject(Map.of("id", "1")));
        });

        assertThat(e, instanceOf(ModeledApiException.class));
        assertThat(e, instanceOf(DocumentException.class));

        var de = (DocumentException) e;
        assertThat(de.schema().id().getName(), equalTo("InvalidSprocketId"));
        var doc = de.getContents();
        assertThat(doc.getMember("id").asString(), equalTo("1"));
        assertThat(doc.type(), equalTo(ShapeType.STRUCTURE));
    }

    @Test
    public void deserializesDynamicErrorsWithRelativeIdFromService() {
        var client = createErrorClient("{\"__type\":\"ServiceFooError\", \"why\":\"IDK\"}");
        var e = Assertions.assertThrows(ApiException.class, () -> {
            client.call("GetSprocket", Document.createFromObject(Map.of("id", "1")));
        });

        assertThat(e, instanceOf(ModeledApiException.class));
        assertThat(e, instanceOf(DocumentException.class));

        var de = (DocumentException) e;
        assertThat(de.schema().id().getName(), equalTo("ServiceFooError"));
        var doc = de.getContents();
        assertThat(doc.getMember("why").asString(), equalTo("IDK"));
        assertThat(doc.type(), equalTo(ShapeType.STRUCTURE));
    }

    private static DynamicClient createErrorClient(String payload) {
        return DynamicClient.builder()
            .service(service)
            .model(model)
            .protocol(new AwsJson1Protocol(service))
            .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
            .transport(createErrorTransport(payload))
            .endpointResolver(EndpointResolver.staticEndpoint("https://foo.com"))
            .build();
    }

    private static ClientTransport<HttpRequest, HttpResponse> createErrorTransport(String payload) {
        return new ClientTransport<>() {
            @Override
            public Class<HttpRequest> requestClass() {
                return HttpRequest.class;
            }

            @Override
            public Class<HttpResponse> responseClass() {
                return HttpResponse.class;
            }

            @Override
            public CompletableFuture<HttpResponse> send(Context context, HttpRequest request) {
                return CompletableFuture.completedFuture(
                    HttpResponse.builder()
                        .httpVersion(HttpVersion.HTTP_1_1)
                        .statusCode(400)
                        .body(DataStream.ofString(payload))
                        .build()
                );
            }
        };
    }

    @Test
    public void detectsClientProtocol() {
        var client = DynamicClient.builder()
            .service(service)
            .model(model)
            .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
            .endpointResolver(EndpointResolver.staticEndpoint("https://foo.com"))
            .build();

        assertThat(client.config().protocol(), instanceOf(AwsJson1Protocol.class));
    }
}
