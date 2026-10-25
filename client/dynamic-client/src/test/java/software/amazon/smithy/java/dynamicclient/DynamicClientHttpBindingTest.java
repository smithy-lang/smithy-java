/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Verifies that HTTP header, query, and label bindings serialize correctly when the input is a document-backed
 * struct (the DynamicClient path), including enum-typed members. This is the sibling of the path-label coverage in
 * {@code PathSerializerTest}: the header/query serializer is push-based (it dispatches typed {@code writeX} calls
 * via {@code serializeMembers}), so it should never see a raw {@code Document}/{@code SmithyEnum}, but we assert it
 * end-to-end to be sure.
 */
public class DynamicClientHttpBindingTest {

    private static final ShapeId SERVICE = ShapeId.from("smithy.example#Widgets");

    private static Model model;

    @BeforeAll
    public static void setup() {
        model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"
                        namespace smithy.example

                        use aws.protocols#restJson1

                        @restJson1
                        @smithy.rules#endpointRuleSet(
                            version: "1.0",
                            serviceId: "widgets",
                            parameters: {},
                            rules: [
                                {
                                    "documentation": "Static endpoint",
                                    "type": "endpoint",
                                    "conditions": [],
                                    "endpoint": {"url": "https://example.com"}
                                }
                            ]
                        )
                        service Widgets {
                            operations: [GetWidget, PutItem]
                        }

                        enum Color {
                            RED
                            GREEN
                        }

                        @http(method: "GET", uri: "/widgets/{id}")
                        operation GetWidget {
                            input := {
                                @required
                                @httpLabel
                                id: String

                                @httpHeader("x-color")
                                headerColor: Color

                                @httpHeader("x-name")
                                headerName: String

                                @httpQuery("color")
                                queryColor: Color

                                @httpQuery("count")
                                queryCount: Integer
                            }
                            output := {}
                        }

                        @http(method: "PUT", uri: "/items/{itemId}")
                        operation PutItem {
                            input := {
                                @required
                                @httpLabel
                                itemId: Long

                                @httpQuery("version")
                                version: Long

                                details: ItemDetails
                            }
                            output := {}
                        }

                        structure ItemDetails {
                            size: Long
                            weights: BigDecimalList
                        }

                        list BigDecimalList {
                            member: BigDecimal
                        }
                        """)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    public void serializesHeaderAndQueryBindingsFromDocument() {
        var captured = new AtomicReference<HttpRequest>();
        var client = DynamicClient.builder()
                .serviceId(SERVICE)
                .model(model)
                .protocol(new RestJsonClientProtocol(SERVICE))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .transport(capturingTransport(captured))
                .endpointResolver(EndpointResolver.staticEndpoint("https://example.com"))
                .build();

        client.call("GetWidget",
                Document.ofObject(Map.of(
                        "id",
                        "abc",
                        "headerColor",
                        "RED",
                        "headerName",
                        "gizmo",
                        "queryColor",
                        "GREEN",
                        "queryCount",
                        7)));

        var request = captured.get();
        // Enum and string headers serialize as their wire values, not toString() of a wrapper.
        assertThat(request.headers().firstValue("x-color"), equalTo("RED"));
        assertThat(request.headers().firstValue("x-name"), equalTo("gizmo"));

        // Path label.
        assertThat(request.uri().getPath(), equalTo("/widgets/abc"));

        // Query params (enum + integer).
        var query = request.uri().getQuery();
        assertThat(query, equalTo("color=GREEN&count=7"));
    }

    @Test
    public void coercesUntypedNumbersToModeledTypes() {
        var captured = new AtomicReference<HttpRequest>();
        var capturedBody = new AtomicReference<String>();
        var client = DynamicClient.builder()
                .serviceId(SERVICE)
                .model(model)
                .protocol(new RestJsonClientProtocol(SERVICE))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .transport(capturingTransport(captured, capturedBody))
                .endpointResolver(EndpointResolver.staticEndpoint("https://example.com"))
                .build();

        // Untyped documents (e.g. parsed from JSON) carry Integers for small numbers, while the members are
        // modeled as Long and BigDecimal.
        client.call("PutItem",
                Document.ofObject(Map.of(
                        "itemId",
                        42,
                        "version",
                        7,
                        "details",
                        Map.of("size", 3, "weights", List.of(1, 2)))));

        var request = captured.get();
        assertThat(request.uri().getPath(), equalTo("/items/42"));
        assertThat(request.uri().getQuery(), equalTo("version=7"));
        assertThat(capturedBody.get(), equalTo("{\"details\":{\"size\":3,\"weights\":[1.0,2.0]}}"));
    }

    private static ClientTransport<HttpRequest, HttpResponse> capturingTransport(AtomicReference<HttpRequest> sink) {
        return capturingTransport(sink, null);
    }

    private static ClientTransport<HttpRequest, HttpResponse> capturingTransport(
            AtomicReference<HttpRequest> sink,
            AtomicReference<String> bodySink
    ) {
        return new ClientTransport<>() {
            @Override
            public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
                return HttpMessageExchange.INSTANCE;
            }

            @Override
            public HttpResponse send(Context context, HttpRequest request) {
                sink.set(request);
                if (bodySink != null) {
                    // The request body is pooled and released when the call completes, so copy it now.
                    bodySink.set(StandardCharsets.UTF_8.decode(request.body().asByteBuffer()).toString());
                }
                return HttpResponse.create()
                        .setHttpVersion(HttpVersion.HTTP_1_1)
                        .setStatusCode(200)
                        .setBody(DataStream.ofString("{}"))
                        .toUnmodifiable();
            }
        };
    }
}
