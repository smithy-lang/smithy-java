/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpTrait;

public class PayloadSerializerTest {

    private static final Codec CODEC = JsonCodec.builder().useJsonName(true).build();
    private static final SmithyUri ENDPOINT = SmithyUri.of("https://example.com");

    @Test
    public void blobPayloadIsWrittenVerbatim() {
        var body = serializeBody(BlobPayloadInput.OPERATION,
                new BlobPayloadInput("hello".getBytes(StandardCharsets.UTF_8)));

        assertThat(body, equalTo("hello"));
    }

    @Test
    public void stringPayloadIsWrittenAsRawBytes() {
        var body = serializeBody(StringPayloadInput.OPERATION, new StringPayloadInput("hello"));

        assertThat(body, equalTo("hello"));
    }

    @Test
    public void listPayloadIsSerializedThroughCodec() {
        var body = serializeBody(ListPayloadInput.OPERATION, new ListPayloadInput(List.of("a", "b", "c")));

        assertThat(body, equalTo("[\"a\",\"b\",\"c\"]"));
    }

    @Test
    public void structPayloadIsSerializedThroughCodec() {
        var body = serializeBody(StructPayloadInput.OPERATION, new StructPayloadInput("Phreddy"));

        assertThat(body, equalTo("{\"name\":\"Phreddy\"}"));
    }

    private static String serializeBody(ApiOperation<?, ?> operation, SerializableStruct input) {
        HttpRequest request = new HttpBinding().requestSerializer()
                .operation(operation)
                .payloadCodec(CODEC)
                .payloadMediaType("application/json")
                .shapeValue(input)
                .endpoint(ENDPOINT)
                .serializeRequest();

        var buffer = request.body().asByteBuffer();
        var bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static HttpTrait postTrait() {
        return HttpTrait.builder().method("POST").uri(UriPattern.parse("/op")).code(200).build();
    }

    private record BlobPayloadInput(byte[] data) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#BlobPayloadInput"))
                .putMember("data", PreludeSchemas.BLOB, new HttpPayloadTrait())
                .build();
        static final Schema DATA = SCHEMA.member("data");
        static final ApiOperation<?, ?> OPERATION = operation("BlobPayloadOperation", SCHEMA);

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBlob(DATA, data);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return "data".equals(member.memberName()) ? (T) data : null;
        }
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

    private record ListPayloadInput(List<String> items) implements SerializableStruct {
        static final Schema LIST = Schema.listBuilder(ShapeId.from("smithy.example#StringList"))
                .putMember("member", PreludeSchemas.STRING)
                .build();
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#ListPayloadInput"))
                .putMember("items", LIST, new HttpPayloadTrait())
                .build();
        static final Schema ITEMS = SCHEMA.member("items");
        static final ApiOperation<?, ?> OPERATION = operation("ListPayloadOperation", SCHEMA);

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            BiConsumer<List<String>, ShapeSerializer> writeItems = (list, ser) -> {
                for (var item : list) {
                    ser.writeString(LIST.member("member"), item);
                }
            };
            serializer.writeList(ITEMS, items, items.size(), writeItems);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return "items".equals(member.memberName()) ? (T) items : null;
        }
    }

    private record StructPayloadInput(String name) implements SerializableStruct {
        static final Schema PAYLOAD = Schema.structureBuilder(ShapeId.from("smithy.example#Greeting"))
                .putMember("name", PreludeSchemas.STRING)
                .build();
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#StructPayloadInput"))
                .putMember("greeting", PAYLOAD, new HttpPayloadTrait())
                .build();
        static final Schema GREETING = SCHEMA.member("greeting");
        static final ApiOperation<?, ?> OPERATION = operation("StructPayloadOperation", SCHEMA);

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct(GREETING, new Greeting(name));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return "greeting".equals(member.memberName()) ? (T) new Greeting(name) : null;
        }

        private record Greeting(String name) implements SerializableStruct {
            @Override
            public Schema schema() {
                return PAYLOAD;
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                serializer.writeString(PAYLOAD.member("name"), name);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getMemberValue(Schema member) {
                return "name".equals(member.memberName()) ? (T) name : null;
            }
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
                throw new UnsupportedOperationException();
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
