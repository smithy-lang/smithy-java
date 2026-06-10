/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

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
                            operations: [GetWidget]
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

    private static ClientTransport<HttpRequest, HttpResponse> capturingTransport(AtomicReference<HttpRequest> sink) {
        return new ClientTransport<>() {
            @Override
            public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
                return HttpMessageExchange.INSTANCE;
            }

            @Override
            public HttpResponse send(Context context, HttpRequest request) {
                sink.set(request);
                return HttpResponse.create()
                        .setHttpVersion(HttpVersion.HTTP_1_1)
                        .setStatusCode(200)
                        .setBody(DataStream.ofString("{}"))
                        .toUnmodifiable();
            }
        };
    }
}
