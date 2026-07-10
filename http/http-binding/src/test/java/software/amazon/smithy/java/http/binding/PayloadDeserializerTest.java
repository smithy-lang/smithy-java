/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpTrait;

public class PayloadDeserializerTest {
    private static final Codec CODEC = JsonCodec.builder().useJsonName(true).build();

    @Test
    public void stringPayloadIsReadWhenResponseIsReadonlyBuffer() {
        var data = deserializeBody(StringPayloadInput.OPERATION, "string value");

        assertThat(data.getMemberValue(StringPayloadInput.VALUE), equalTo("string value"));
    }

    private static SerializableStruct deserializeBody(ApiOperation<?, ?> operation, String bodyContent) {
        var builder = operation.outputBuilder();
        var bodyReadOnlyBuffer = DataStream.ofString(bodyContent).asByteBuffer().asReadOnlyBuffer();
        var response = HttpResponse.of(HttpVersion.HTTP_1_0,
                200,
                HttpHeaders.ofModifiable().toUnmodifiable(),
                DataStream.ofByteBuffer(bodyReadOnlyBuffer));
        new HttpBinding().responseDeserializer()
                .payloadCodec(CODEC)
                .payloadMediaType("application/json")
                .outputShapeBuilder(builder)
                .response(response)
                .deserialize();

        return builder.build();
    }

    private static HttpTrait postTrait() {
        return HttpTrait.builder().method("POST").uri(UriPattern.parse("/op")).code(200).build();
    }

    private record StringPayloadInput(String value) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#StringPayloadInput"))
                .putMember("value", PreludeSchemas.STRING, new HttpPayloadTrait())
                .build();
        static final Schema VALUE = SCHEMA.member("value");
        static final ApiOperation<?, ?> OPERATION = operation("StringPayloadOperation", SCHEMA);

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(VALUE, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return "value".equals(member.memberName()) ? (T) value : null;
        }
    }

    /**
     * Build a minimal POST operation whose input is {@code inputSchema}. The operation schema carries the
     * {@code @http} trait that drives request-direction HTTP binding.
     */
    private static ApiOperation<?, ?> operation(String name, Schema inputSchema) {
        var operationSchema = Schema.createOperation(ShapeId.from("smithy.example#" + name), postTrait());
        return new ApiOperation<>() {
            @Override
            public ShapeBuilder<SerializableStruct> inputBuilder() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ShapeBuilder<SerializableStruct> outputBuilder() {
                return new ShapeBuilder<>() {
                    private AtomicReference<String> value = new AtomicReference<>();

                    @Override
                    public SerializableStruct build() {
                        return new StringPayloadInput(value.get());
                    }

                    @Override
                    public ShapeBuilder<SerializableStruct> deserialize(ShapeDeserializer decoder) {
                        decoder.readStruct(StringPayloadInput.SCHEMA,
                                value,
                                (
                                        AtomicReference<String> state,
                                        Schema memberSchema,
                                        ShapeDeserializer memberDeserializer) -> {
                                    state.set(memberDeserializer.readString(StringPayloadInput.VALUE));
                                });
                        return this;
                    }

                    @Override
                    public Schema schema() {
                        return inputSchema;
                    }
                };
            }

            @Override
            public Schema schema() {
                return operationSchema;
            }

            @Override
            public Schema inputSchema() {
                return inputSchema;
            }

            @Override
            public Schema outputSchema() {
                return inputSchema;
            }

            @Override
            public TypeRegistry errorRegistry() {
                return TypeRegistry.builder().build();
            }

            @Override
            public List<ShapeId> effectiveAuthSchemes() {
                return List.of();
            }

            @Override
            public List<Schema> errorSchemas() {
                return List.of();
            }

            @Override
            public ApiService service() {
                return null;
            }
        };
    }
}
