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
 * Reproduces the AWS Connect failure that motivated the PathSerializer fix, using the real production
 * Connect model from api-models-aws. {@code DescribeContact} is a restJson1 GET with two {@code @httpLabel}
 * path variables ({@code /contacts/{InstanceId}/{ContactId}}). Before the fix, serializing these labels from
 * a document-backed DynamicClient struct threw
 * {@code ClassCastException: ContentDocument cannot be cast to String} at PathSerializer.formatLabelValue.
 *
 * <p>This is the sibling of {@link DynamicClientHttpBindingTest}, but against a live service model rather than a
 * hand-written one. It uses a capturing transport so no credentials or network are needed: we intercept the
 * fully-serialized HTTP request and assert the path was built correctly.
 */
public class ConnectDescribeContactPathTest {

    private static final ShapeId SERVICE = ShapeId.from("com.amazonaws.connect#AmazonConnectService");

    private static Model model;

    @BeforeAll
    public static void setup() {
        // discoverModels() loads AWS trait definitions (restJson1, sigv4, endpoint rules functions like
        // aws.partition) off the classpath so the real Connect model resolves. We only need the path-label
        // serialization to work, so unknown traits are tolerated rather than failing assembly.
        model = Model.assembler()
                .discoverModels(ConnectDescribeContactPathTest.class.getClassLoader())
                .putProperty(software.amazon.smithy.model.loader.ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .addImport(ConnectDescribeContactPathTest.class.getResource("/connect-2017-08-08.json"))
                .assemble()
                .unwrap();
    }

    @Test
    public void serializesPathLabelsFromDocumentInput() {
        var captured = new AtomicReference<HttpRequest>();
        var client = DynamicClient.builder()
                .serviceId(SERVICE)
                .model(model)
                .protocol(new RestJsonClientProtocol(SERVICE))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .transport(capturingTransport(captured))
                .endpointResolver(EndpointResolver.staticEndpoint("https://connect.us-east-1.amazonaws.com"))
                .build();

        // Document-backed input: this is exactly the shape AWS Connect's ProxyService supplies via MCP.
        client.call("DescribeContact",
                Document.ofObject(Map.of(
                        "InstanceId", "inst-1234567890abcdef",
                        "ContactId", "contact-0987654321fedcba")));

        var request = captured.get();
        // The two @httpLabel path variables must serialize into the URI, not throw ClassCastException.
        assertThat(
                request.uri().getPath(),
                equalTo("/contacts/inst-1234567890abcdef/contact-0987654321fedcba"));
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
