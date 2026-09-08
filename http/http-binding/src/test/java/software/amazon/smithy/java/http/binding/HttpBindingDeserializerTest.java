/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.MemberSubsetCodec;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeDeserializer;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.StreamingTrait;

public class HttpBindingDeserializerTest {
    private static final Codec NOOP_CODEC = new Codec() {
        @Override
        public ShapeSerializer createSerializer(OutputStream sink) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeDeserializer createDeserializer(ByteBuffer source) {
            throw new UnsupportedOperationException();
        }
    };

    @ParameterizedTest
    @MethodSource("contentTypeMatchProvider")
    void contentTypeTest(String actual, String expected, int expectedResult) {
        int result = HttpBindingDeserializer.compareMediaType(actual, expected);
        Assertions.assertEquals(expectedResult, result);
    }

    static List<Arguments> contentTypeMatchProvider() {
        return List.of(
                // Mismatches (return -1)
                Arguments.of("text/plain", "application/json", -1),
                Arguments.of("application/jsonp", "application/json", -1),
                Arguments.of("application/mson", "application/json", -1),

                // Exact matches (return 1)
                Arguments.of("application/json", "application/json", 1),
                Arguments.of("application/JSON", "application/json", 1),
                Arguments.of("application/Json", "application/json", 1),
                Arguments.of("APPLICATION/JSON", "application/json", 1),
                Arguments.of("APPLICATION/json", "application/json", 1),

                // Matches with parameters (return 1)
                Arguments.of("application/json; charset=utf-8", "application/json", 1),
                Arguments.of("application/json;charset=utf-8", "application/json", 1),
                Arguments.of("application/json ; charset=utf-8", "application/json", 1),
                Arguments.of("application/json ;", "application/json", 1),
                Arguments.of("application/json ; ", "application/json", 1),
                Arguments.of("application/json ", "application/json", 1),

                // Null cases
                Arguments.of(null, null, 1), // No validation needed
                Arguments.of(null, "application/json", 0), // Missing actual Content-Type on the wire
                Arguments.of("application/json", null, 1) // No expectation
        );
    }

    @Test
    void responseDeserializerDiscardsBodyForNonStreamingOutput() {
        var body = new TrackingDataStream();

        new ResponseDeserializer()
                .payloadCodec(NOOP_CODEC)
                .response(response(body))
                .outputShapeBuilder(new NonStreamingOutput.Builder())
                .deserialize();

        Assertions.assertEquals(1, body.discardCount);
    }

    @Test
    void responseDeserializerLeavesStreamingPayloadOpen() {
        var body = new TrackingDataStream();
        var builder = new StreamingOutput.Builder();

        new ResponseDeserializer()
                .payloadCodec(NOOP_CODEC)
                .response(response(body))
                .outputShapeBuilder(builder)
                .deserialize();

        Assertions.assertSame(body, builder.body);
        Assertions.assertEquals(0, body.discardCount);
    }

    @Test
    void directBodyDeserializationRequiresExplicitBuilderOptIn() {
        var codec = new SubsetCodec(true);
        var builder = new BodyOutput.Builder();
        var deserializer = HttpBindingDeserializer.builder()
                .payloadCodec(codec)
                .headers(HttpHeaders.of(Map.of()))
                .body(DataStream.ofString("body"))
                .isResponse(true)
                .build();

        builder.deserialize(deserializer);

        Assertions.assertEquals(0, codec.directCalls);
        Assertions.assertEquals(1, codec.fallbackCalls);
        Assertions.assertEquals("fallback", builder.value);
    }

    @Test
    void declinedDirectBodyDeserializationLeavesFallbackBufferReusable() {
        var codec = new SubsetCodec(false);
        var builder = new BodyOutput.Builder();

        new ResponseDeserializer()
                .payloadCodec(codec)
                .response(response(DataStream.ofString("body")))
                .outputShapeBuilder(builder)
                .deserialize();

        Assertions.assertEquals(1, codec.directCalls);
        Assertions.assertEquals(1, codec.fallbackCalls);
        Assertions.assertEquals(0, codec.fallbackPosition);
        Assertions.assertEquals("fallback", builder.value);
    }

    @Test
    void explicitlyOptedInBuilderCanBePopulatedDirectly() {
        var codec = new SubsetCodec(true);
        var builder = new BodyOutput.Builder();

        new ResponseDeserializer()
                .payloadCodec(codec)
                .response(response(DataStream.ofString("body")))
                .outputShapeBuilder(builder)
                .deserialize();

        Assertions.assertEquals(1, codec.directCalls);
        Assertions.assertEquals(0, codec.fallbackCalls);
        Assertions.assertEquals("direct", builder.value);
    }

    @Test
    void structuredPayloadCanPopulateItsConcreteBuilderDirectly() {
        var codec = new WholeShapeCodec();
        var builder = new PayloadOutput.Builder();

        new ResponseDeserializer()
                .payloadCodec(codec)
                .response(response(DataStream.ofString("{\"value\":\"direct\"}")))
                .outputShapeBuilder(builder)
                .deserialize();

        Assertions.assertEquals(1, codec.directCalls);
        Assertions.assertEquals(0, codec.fallbackCalls);
        Assertions.assertEquals("direct", builder.payload.value());
    }

    private static HttpResponse response(DataStream body) {
        return HttpResponse.of(HttpVersion.HTTP_1_1, 200, HttpHeaders.of(Map.of()), body);
    }

    private static final class TrackingDataStream implements DataStream {
        int discardCount;

        @Override
        public long contentLength() {
            return -1;
        }

        @Override
        public String contentType() {
            return null;
        }

        @Override
        public boolean isReplayable() {
            return false;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public InputStream asInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void discard() {
            discardCount++;
        }
    }

    private record NonStreamingOutput() implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#NonStreamingOutput"))
                .builderSupplier(Builder::new)
                .build();

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        private static final class Builder implements ShapeBuilder<NonStreamingOutput> {
            @Override
            public NonStreamingOutput build() {
                return new NonStreamingOutput();
            }

            @Override
            public ShapeBuilder<NonStreamingOutput> deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, (builder, member, deserializer) -> {});
                return this;
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    private record StreamingOutput(DataStream body) implements SerializableStruct {
        private static final Schema STREAMING_BLOB = Schema.createBlob(
                ShapeId.from("smithy.example#StreamingBlob"),
                new StreamingTrait());
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#StreamingOutput"))
                .putMember("body", STREAMING_BLOB, new HttpPayloadTrait())
                .builderSupplier(Builder::new)
                .build();

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        private static final class Builder implements ShapeBuilder<StreamingOutput> {
            private DataStream body;

            @Override
            public StreamingOutput build() {
                return new StreamingOutput(body);
            }

            @Override
            public ShapeBuilder<StreamingOutput> deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, (builder, member, deserializer) -> {
                    if (member.memberName().equals("body")) {
                        builder.body = deserializer.readDataStream(member);
                    }
                });
                return this;
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    private record BodyOutput(String value) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#BodyOutput"))
                .shapeClass(BodyOutput.class)
                .putMember("value", PreludeSchemas.STRING)
                .builderSupplier(Builder::new)
                .build();

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        private static final class Builder implements ShapeBuilder<BodyOutput> {
            private String value;

            @Override
            public BodyOutput build() {
                return new BodyOutput(value);
            }

            @Override
            public ShapeBuilder<BodyOutput> deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA,
                        this,
                        (builder, member, deserializer) -> builder.value = deserializer.readString(member));
                return this;
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    private record PayloadOutput(BodyOutput payload) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#PayloadOutput"))
                .putMember("payload", BodyOutput.SCHEMA, new HttpPayloadTrait())
                .builderSupplier(Builder::new)
                .build();

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        private static final class Builder implements ShapeBuilder<PayloadOutput> {
            private BodyOutput payload;

            @Override
            public PayloadOutput build() {
                return new PayloadOutput(payload);
            }

            @Override
            public ShapeBuilder<PayloadOutput> deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, (builder, member, deserializer) -> {
                    var payloadBuilder = new BodyOutput.Builder();
                    payloadBuilder.deserialize(deserializer);
                    builder.payload = payloadBuilder.build();
                });
                return this;
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    private static final class WholeShapeCodec implements Codec, MemberSubsetCodec {
        private int directCalls;
        private int fallbackCalls;

        @Override
        public ByteBuffer serialize(SerializableStruct struct, MemberSubset subset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deserialize(Schema schema, ShapeBuilder<?> builder, ByteBuffer source) {
            directCalls++;
            ((BodyOutput.Builder) builder).value = "direct";
            return true;
        }

        @Override
        public ShapeSerializer createSerializer(OutputStream sink) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeDeserializer createDeserializer(ByteBuffer source) {
            fallbackCalls++;
            return new SpecificShapeDeserializer() {
                @Override
                public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
                    consumer.accept(state, schema.member("value"), new SpecificShapeDeserializer() {
                        @Override
                        public String readString(Schema schema) {
                            return "fallback";
                        }
                    });
                }
            };
        }
    }

    private static final class SubsetCodec implements Codec, MemberSubsetCodec {
        private final boolean directResult;
        private int directCalls;
        private int fallbackCalls;
        private int fallbackPosition = -1;

        private SubsetCodec(boolean directResult) {
            this.directResult = directResult;
        }

        @Override
        public ByteBuffer serialize(SerializableStruct struct, MemberSubset subset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deserialize(
                Schema schema,
                ShapeBuilder<?> builder,
                ByteBuffer source,
                MemberSubset subset
        ) {
            directCalls++;
            if (directResult) {
                source.get();
                ((BodyOutput.Builder) builder).value = "direct";
            }
            return directResult;
        }

        @Override
        public ShapeSerializer createSerializer(OutputStream sink) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShapeDeserializer createDeserializer(ByteBuffer source) {
            return new SpecificShapeDeserializer() {
                @Override
                public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
                    fallbackCalls++;
                    fallbackPosition = source.position();
                    consumer.accept(state, schema.member("value"), new SpecificShapeDeserializer() {
                        @Override
                        public String readString(Schema schema) {
                            return "fallback";
                        }
                    });
                }
            };
        }
    }
}
