/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http.binding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.http.AmznErrorHeaderExtractor;
import software.amazon.smithy.java.runtime.client.http.HttpErrorDeserializer;
import software.amazon.smithy.java.runtime.core.schema.ApiException;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.TypeRegistry;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.java.runtime.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.ErrorTrait;

public class HttpBindingErrorDeserializerTest {

    private static final Codec CODEC = JsonCodec.builder().build();
    private static final ShapeId SERVICE = ShapeId.from("com.foo#Example");
    private static final ShapeId OPERATION = ShapeId.from("com.foo#PutFoo");

    @Test
    public void deserializesErrorsWithHttpBindingsToo() throws Exception {
        var deserializer = HttpErrorDeserializer.builder()
            .codec(CODEC)
            .serviceId(SERVICE)
            .knownErrorFactory(new HttpBindingErrorFactory())
            .build();
        var registry = TypeRegistry.builder()
            .putType(Baz.SCHEMA.id(), Baz.class, Baz.Builder::new)
            .build();
        var responseBuilder = SmithyHttpResponse.builder()
            .statusCode(400)
            .body(DataStream.ofString("{\"__type\": \"com.foo#Baz\"}"));
        var response = responseBuilder.build();
        var result = deserializer.createError(Context.create(), OPERATION, registry, response).get();

        assertThat(result, instanceOf(Baz.class));
    }

    @Test
    public void usesGenericErrorWhenPayloadTypeIsUnknown() throws Exception {
        var deserializer = HttpErrorDeserializer.builder()
            .codec(CODEC)
            .serviceId(SERVICE)
            .knownErrorFactory(new HttpBindingErrorFactory())
            .unknownErrorFactory(
                (fault, message, response) -> CompletableFuture.completedFuture(new ApiException("Hi!", fault))
            )
            .build();
        var registry = TypeRegistry.builder()
            .putType(Baz.SCHEMA.id(), Baz.class, Baz.Builder::new)
            .build();
        var responseBuilder = SmithyHttpResponse.builder()
            .statusCode(400)
            .body(DataStream.ofString("{\"__type\": \"com.foo#SomeUnknownError\"}"));
        var response = responseBuilder.build();
        var result = deserializer.createError(Context.create(), OPERATION, registry, response).get();

        assertThat(result, instanceOf(ApiException.class));
        assertThat(result.getMessage(), equalTo("Hi!"));
    }

    @Test
    public void usesGenericErrorWhenHeaderTypeIsUnknown() throws Exception {
        var deserializer = HttpErrorDeserializer.builder()
            .codec(CODEC)
            .serviceId(SERVICE)
            .knownErrorFactory(new HttpBindingErrorFactory())
            .headerErrorExtractor(new AmznErrorHeaderExtractor())
            .unknownErrorFactory(
                (fault, message, response) -> CompletableFuture.completedFuture(new ApiException("Hi!", fault))
            )
            .build();
        var registry = TypeRegistry.builder()
            .putType(Baz.SCHEMA.id(), Baz.class, Baz.Builder::new)
            .build();
        var responseBuilder = SmithyHttpResponse.builder()
            .statusCode(400)
            .headers(
                HttpHeaders.of(
                    Map.of(
                        "content-length",
                        List.of("2"),
                        "x-amzn-errortype",
                        List.of("com.foo#SomeUnknownError")
                    )
                )
            )
            .body(DataStream.ofString("{}"));
        var response = responseBuilder.build();
        var result = deserializer.createError(Context.create(), OPERATION, registry, response).get();

        assertThat(result, instanceOf(ApiException.class));
        assertThat(result.getMessage(), equalTo("Hi!"));
    }

    static final class Baz extends ModeledApiException {

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
