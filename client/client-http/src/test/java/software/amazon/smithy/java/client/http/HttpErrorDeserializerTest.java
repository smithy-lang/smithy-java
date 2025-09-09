/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.CallException;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.ErrorTrait;

public class HttpErrorDeserializerTest {

    private static final Codec CODEC = JsonCodec.builder().build();
    private static final ShapeId SERVICE = ShapeId.from("com.foo#Example");
    private static final ShapeId OPERATION = ShapeId.from("com.foo#PutFoo");

    @ParameterizedTest
    @MethodSource("genericErrorCases")
    public void createErrorFromHints(int status, String payload, String message) {
        var deserializer = HttpErrorDeserializer.builder()
                .codec(CODEC)
                .serviceId(SERVICE)
                .build();
        var registry = TypeRegistry.empty();
        var responseBuilder = HttpResponse.builder().statusCode(status);

        if (payload != null) {
            responseBuilder.body(DataStream.ofString(payload));
            responseBuilder.headers(
                    HttpHeaders.of(Map.of("content-length", List.of(Integer.toString(payload.length())))));
        }
        var response = responseBuilder.build();
        var result = deserializer.createError(Context.create(), OPERATION, registry, response);

        assertThat(result.getMessage(), containsString(message));
    }

    static List<Arguments> genericErrorCases() {
        return List.of(
                Arguments.of(400, null, "Client HTTP/1.1 400 response from operation com.foo#PutFoo."),
                Arguments.of(500, null, "Server HTTP/1.1 500 response from operation com.foo#PutFoo."),
                Arguments.of(600, null, "Unknown HTTP/1.1 600 response from operation com.foo#PutFoo."),
                Arguments.of(400, "foo", "Client HTTP/1.1 400 response from operation com.foo#PutFoo."),
                Arguments.of(500, "foo", "Server HTTP/1.1 500 response from operation com.foo#PutFoo."),
                Arguments.of(600, "foo", "Unknown HTTP/1.1 600 response from operation com.foo#PutFoo."),
                Arguments.of(400, "{}", "Client HTTP/1.1 400 response from operation com.foo#PutFoo."),
                Arguments.of(500, "{}", "Server HTTP/1.1 500 response from operation com.foo#PutFoo."),
                Arguments.of(600, "{}", "Unknown HTTP/1.1 600 response from operation com.foo#PutFoo."),
                Arguments.of(400, "", "Client HTTP/1.1 400 response from operation com.foo#PutFoo."),
                Arguments.of(500, "", "Server HTTP/1.1 500 response from operation com.foo#PutFoo."),
                Arguments.of(600, "", "Unknown HTTP/1.1 600 response from operation com.foo#PutFoo."));
    }

    @Test
    public void deserializesIntoErrorBasedOnHeaders() {
        var deserializer = HttpErrorDeserializer.builder()
                .codec(CODEC)
                .serviceId(SERVICE)
                .headerErrorExtractor(new AmznErrorHeaderExtractor())
                .build();
        var registry = TypeRegistry.builder()
                .putType(Baz.SCHEMA.id(), Baz.class, Baz.Builder::new)
                .build();
        var responseBuilder = HttpResponse.builder()
                .statusCode(400)
                .headers(
                        HttpHeaders.of(
                                Map.of(
                                        "content-length",
                                        List.of("2"),
                                        "x-amzn-errortype",
                                        List.of(Baz.SCHEMA.id().toString()))))
                .body(DataStream.ofString("{}"));
        var response = responseBuilder.build();
        var result = deserializer.createError(Context.create(), OPERATION, registry, response);

        assertThat(result, instanceOf(Baz.class));
    }

    @Test
    public void deserializesUsingDocumentViaPayloadWithNoContentLength() {
        var deserializer = HttpErrorDeserializer.builder()
                .codec(CODEC)
                .serviceId(SERVICE)
                .headerErrorExtractor(new AmznErrorHeaderExtractor())
                .build();
        var registry = TypeRegistry.builder()
                .putType(Baz.SCHEMA.id(), Baz.class, Baz.Builder::new)
                .build();
        var responseBuilder = HttpResponse.builder()
                .statusCode(400)
                .body(DataStream.ofString("{\"__type\": \"com.foo#Baz\"}"));
        var response = responseBuilder.build();
        var result = deserializer.createError(Context.create(), OPERATION, registry, response);

        assertThat(result, instanceOf(Baz.class));
    }

    @Test
    public void usesGenericErrorWhenPayloadTypeIsUnknown() {
        var deserializer = HttpErrorDeserializer.builder()
                .codec(CODEC)
                .serviceId(SERVICE)
                .unknownErrorFactory((fault, message, response) -> new CallException("Hi!", fault))
                .build();
        var registry = TypeRegistry.builder()
                .putType(Baz.SCHEMA.id(), Baz.class, Baz.Builder::new)
                .build();
        var responseBuilder = HttpResponse.builder()
                .statusCode(400)
                .body(DataStream.ofString("{\"__type\": \"com.foo#SomeUnknownError\"}"));
        var response = responseBuilder.build();
        var result = deserializer.createError(Context.create(), OPERATION, registry, response);

        assertThat(result, instanceOf(CallException.class));
        assertThat(result.getMessage(), equalTo("Hi!"));
    }

    @Test
    public void usesGenericErrorWhenHeaderTypeIsUnknown() {
        var deserializer = HttpErrorDeserializer.builder()
                .codec(CODEC)
                .serviceId(SERVICE)
                .headerErrorExtractor(new AmznErrorHeaderExtractor())
                .unknownErrorFactory((fault, message, response) -> new CallException("Hi!", fault))
                .build();
        var registry = TypeRegistry.builder()
                .putType(Baz.SCHEMA.id(), Baz.class, Baz.Builder::new)
                .build();
        var responseBuilder = HttpResponse.builder()
                .statusCode(400)
                .headers(
                        HttpHeaders.of(
                                Map.of(
                                        "content-length",
                                        List.of("2"),
                                        "x-amzn-errortype",
                                        List.of("com.foo#SomeUnknownError"))))
                .body(DataStream.ofString("{}"));
        var response = responseBuilder.build();
        var result = deserializer.createError(Context.create(), OPERATION, registry, response);

        assertThat(result, instanceOf(CallException.class));
        assertThat(result.getMessage(), equalTo("Hi!"));
    }

    static final class Baz extends ModeledException {

        static final Schema SCHEMA = Schema
                .structureBuilder(ShapeId.from("com.foo#Baz"), new ErrorTrait("client"))
                .build();

        public Baz(String message) {
            super(SCHEMA, message);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }

        static final class Builder implements ShapeBuilder<Baz> {
            private String message;

            @Override
            public Baz build() {
                return new Baz(message == null ? "" : message);
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }

            public Builder message(String message) {
                this.message = message;
                return this;
            }

            @Override
            public ShapeBuilder<Baz> deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, null, (n, m, d) -> {});
                return this;
            }
        }
    }
}
