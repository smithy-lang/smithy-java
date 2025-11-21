/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.sdkv2.shapes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public class SdkDocumentWriterTest {

    private static final Model MODEL = Model.assembler()
            .addUnparsedModel("test.smithy", """
                    $version: "2"
                    namespace smithy.example

                    @aws.protocols#restJson1
                    service Sprockets {}

                    structure Foo {
                        bar: String

                        @jsonName("BAZ")
                        baz: Integer

                        @timestampFormat("date-time")
                        date: Timestamp
                    }
                    """)
            .discoverModels()
            .assemble()
            .unwrap();

    private static final ShapeId SERVICE = ShapeId.from("smithy.example#Sprockets");

    @MethodSource("restJsonTestCases")
    @ParameterizedTest
    public void convertsDocument(Document sdk, software.amazon.awssdk.core.document.Document smithy) {
        var result = AwsJsonProtocols.REST_JSON_1.smithyToSdk(sdk);

        assertThat(result, equalTo(smithy));
    }

    public static List<Arguments> restJsonTestCases() {
        List<Arguments> result = new ArrayList<>();
        result.add(Arguments.of(Document.of(10), software.amazon.awssdk.core.document.Document.fromNumber(10)));

        result.add(
                Arguments.of(
                        Document.of("hi"),
                        software.amazon.awssdk.core.document.Document.fromString("hi")));

        result.add(
                Arguments.of(
                        Document.of(true),
                        software.amazon.awssdk.core.document.Document.fromBoolean(true)));

        result.add(
                Arguments.of(
                        Document.of(List.of(Document.of("hi"), Document.of(10))),
                        software.amazon.awssdk.core.document.Document.fromList(
                                List.of(
                                        software.amazon.awssdk.core.document.Document.fromString("hi"),
                                        software.amazon.awssdk.core.document.Document.fromNumber(10)))));

        result.add(
                Arguments.of(
                        Document.of(sparseList()),
                        software.amazon.awssdk.core.document.Document.fromList(
                                List.of(software.amazon.awssdk.core.document.Document.fromNull()))));

        result.add(
                Arguments.of(
                        Document.of("hi".getBytes(StandardCharsets.UTF_8)),
                        software.amazon.awssdk.core.document.Document.fromString(
                                ByteBufferUtils.base64Encode(ByteBuffer.wrap("hi".getBytes(StandardCharsets.UTF_8))))));

        result.add(
                Arguments.of(
                        Document.of(Instant.EPOCH),
                        software.amazon.awssdk.core.document.Document.fromNumber(0.0)));

        result.add(
                Arguments.of(
                        Document.of(Map.of("hi", Document.of("there"))),
                        software.amazon.awssdk.core.document.Document.fromMap(
                                Map.of("hi", software.amazon.awssdk.core.document.Document.fromString("there")))));

        result.add(
                Arguments.of(
                        null,
                        software.amazon.awssdk.core.document.Document.fromNull()));

        var client = createClient();
        var foo = createFoo(client);
        result.add(
                Arguments.of(
                        foo,
                        software.amazon.awssdk.core.document.Document.fromMap(
                                Map.of(
                                        "bar",
                                        software.amazon.awssdk.core.document.Document.fromString("a"),
                                        "BAZ",
                                        software.amazon.awssdk.core.document.Document.fromNumber(2),
                                        "date",
                                        software.amazon.awssdk.core.document.Document
                                                .fromString(Instant.EPOCH.toString())))));

        return result;
    }

    private static DynamicClient createClient() {
        return DynamicClient.builder()
                .protocol(new RestJsonClientProtocol(SERVICE))
                .model(MODEL)
                .serviceId(SERVICE)
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .endpointResolver(EndpointResolver.staticHost("localhost"))
                .build();
    }

    private static SerializableStruct createFoo(DynamicClient client) {
        return client.createStruct(
                ShapeId.from("smithy.example#Foo"),
                Document.of(
                        Map.of(
                                "bar",
                                Document.of("a"),
                                "baz",
                                Document.of(2),
                                "date",
                                Document.of(Instant.EPOCH))));
    }

    @Test
    public void awsJsonDoesNotUseJsonName() {
        var client = createClient();
        var foo = createFoo(client);
        // Using "awsJsonDocument" means jsonName is ignored and timestampFormat is ignored.
        var result = AwsJsonProtocols.AWS_JSON_1_1.smithyToSdk((Document) foo);

        assertThat(
                result,
                equalTo(
                        software.amazon.awssdk.core.document.Document.fromMap(
                                Map.of(
                                        "bar",
                                        software.amazon.awssdk.core.document.Document.fromString("a"),
                                        "baz",
                                        software.amazon.awssdk.core.document.Document.fromNumber(2),
                                        "date",
                                        software.amazon.awssdk.core.document.Document.fromNumber(0.0)))));
    }

    private static <T> List<T> sparseList() {
        List<T> result = new ArrayList<>(1);
        result.add(null);
        return result;
    }

    @Test
    public void loadsConverterFromSpi() {
        var converter = DocumentConverter.getConverter(RestJson1Trait.ID);

        assertThat(converter, instanceOf(AwsJsonProtocols.RestJson1DocumentConverter.class));
    }
}
