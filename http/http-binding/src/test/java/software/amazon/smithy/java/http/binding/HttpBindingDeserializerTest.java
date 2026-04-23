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
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
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
}
