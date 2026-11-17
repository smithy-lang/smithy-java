/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.MemberSubsetCodec;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;

/**
 * A JSON body must be exactly one document, optionally surrounded by whitespace. Content after the document must be
 * rejected on both the generated (direct) and the interpreted (fallback) deserialization paths.
 */
public class TrailingBodyContentTest {

    enum Path {
        /** Generated runtime codec populating the builder directly via {@link MemberSubsetCodec}. */
        DIRECT,
        /** Interpreted {@link ShapeDeserializer} driven by {@code readStruct}. */
        FALLBACK
    }

    static List<Arguments> codecs() {
        return List.of(
                Arguments.of(Named.of("generated",
                        (Supplier<Codec>) () -> JsonCodec.builder()
                                .runtimeCodegen(true)
                                .build()),
                        Path.DIRECT),
                Arguments.of(Named.of("interpreted",
                        (Supplier<Codec>) () -> JsonCodec.builder()
                                .runtimeCodegen(false)
                                .build()),
                        Path.FALLBACK));
    }

    static final List<String> ACCEPTED = List.of(
            "{\"name\":\"n\"}",
            "{\"name\":\"n\"} \t\r\n ",
            " \n{\"name\":\"n\"}\n");

    static final List<String> REJECTED = List.of(
            "{\"name\":\"n\"}{}",
            "{\"name\":\"n\"}\n{}",
            "{\"name\":\"n\"} {\"name\":\"m\"}",
            "{\"name\":\"n\"}}",
            "{\"name\":\"n\"}]",
            "{\"name\":\"n\"},",
            "{\"name\":\"n\"}x",
            "{\"name\":\"n\"} null",
            "{\"name\":\"n\"}\n\"text\"",
            "{}{}",
            "{} {\"name\":\"n\"}");

    static List<Arguments> cases() {
        var result = new ArrayList<Arguments>();
        for (var codec : codecs()) {
            for (var body : ACCEPTED) {
                result.add(Arguments.of(codec.get()[0], codec.get()[1], body, true));
            }
            for (var body : REJECTED) {
                result.add(Arguments.of(codec.get()[0], codec.get()[1], body, false));
            }
        }
        return result;
    }

    @ParameterizedTest(name = "{0} {2}")
    @MethodSource("cases")
    void requestBody(Supplier<Codec> codec, Path path, String body, boolean accepted) {
        var tracking = tracking(codec, path);
        var builder = new NamedBody.Builder();
        var request = HttpRequest.create()
                .setMethod("POST")
                .setUri(URI.create("https://example.com/"))
                .setHeaders(HttpHeaders.of(Map.of("x-token", List.of("t"))))
                .setBody(DataStream.ofString(body));
        Runnable deserialize = () -> new RequestDeserializer()
                .payloadCodec(tracking)
                .request(request)
                .inputShapeBuilder(builder)
                .deserialize();

        verify(tracking, path, accepted, deserialize, () -> builder.build().name());
        if (accepted) {
            assertEquals("t", builder.build().token());
        }
    }

    @ParameterizedTest(name = "{0} {2}")
    @MethodSource("cases")
    void responseBody(Supplier<Codec> codec, Path path, String body, boolean accepted) {
        var tracking = tracking(codec, path);
        var builder = new NamedBody.Builder();
        var response = HttpResponse.of(
                HttpVersion.HTTP_1_1,
                200,
                HttpHeaders.of(Map.of("x-token", List.of("t"))),
                DataStream.ofString(body));
        Runnable deserialize = () -> new ResponseDeserializer()
                .payloadCodec(tracking)
                .response(response)
                .outputShapeBuilder(builder)
                .deserialize();

        verify(tracking, path, accepted, deserialize, () -> builder.build().name());
    }

    @ParameterizedTest(name = "{0} {2}")
    @MethodSource("cases")
    void structurePayload(Supplier<Codec> codec, Path path, String body, boolean accepted) {
        var tracking = tracking(codec, path);
        var builder = new WithNamedPayload.Builder();
        var request = HttpRequest.create()
                .setMethod("POST")
                .setUri(URI.create("https://example.com/"))
                .setBody(DataStream.ofString(body));
        Runnable deserialize = () -> new RequestDeserializer()
                .payloadCodec(tracking)
                .request(request)
                .inputShapeBuilder(builder)
                .deserialize();

        verify(tracking, path, accepted, deserialize, () -> builder.build().payload().name());
    }

    private static TrackingCodec tracking(Supplier<Codec> codec, Path path) {
        if (path == Path.DIRECT) {
            assumeTrue(RuntimeCodegenFeature.available(), "Runtime code generation is not available in this JVM");
        }
        return new TrackingCodec(codec.get());
    }

    private static void verify(
            TrackingCodec codec,
            Path path,
            boolean accepted,
            Runnable deserialize,
            Supplier<String> name
    ) {
        if (accepted) {
            deserialize.run();
            assertEquals("n", name.get());
        } else {
            var e = assertThrows(SerializationException.class, deserialize::run);
            assertEquals(SerializationException.class, e.getClass(), () -> "Unexpected exception: " + e);
        }
        // Make sure the intended path actually ran; otherwise the test proves nothing about it.
        if (path == Path.DIRECT) {
            assertEquals(1, codec.directCalls, "direct calls");
            assertEquals(0, codec.fallbackCalls, "fallback calls");
        } else {
            assertEquals(0, codec.directAccepted, "direct accepted");
            assertEquals(1, codec.fallbackCalls, "fallback calls");
        }
    }

    /** Delegates to a real codec while recording which deserialization path was taken. */
    private static final class TrackingCodec implements Codec, MemberSubsetCodec {
        private final Codec delegate;
        private final MemberSubsetCodec subsetDelegate;
        int directCalls;
        int directAccepted;
        int fallbackCalls;

        TrackingCodec(Codec delegate) {
            this.delegate = delegate;
            this.subsetDelegate = (MemberSubsetCodec) delegate;
        }

        @Override
        public ShapeSerializer createSerializer(OutputStream sink) {
            return delegate.createSerializer(sink);
        }

        @Override
        public ShapeDeserializer createDeserializer(ByteBuffer source) {
            fallbackCalls++;
            return delegate.createDeserializer(source);
        }

        @Override
        public ByteBuffer serialize(SerializableStruct struct, MemberSubset subset) {
            return subsetDelegate.serialize(struct, subset);
        }

        @Override
        public boolean deserialize(Schema schema, ShapeBuilder<?> builder, ByteBuffer source) {
            return record(() -> subsetDelegate.deserialize(schema, builder, source));
        }

        @Override
        public boolean deserialize(Schema schema, ShapeBuilder<?> builder, ByteBuffer source, MemberSubset subset) {
            return record(() -> subsetDelegate.deserialize(schema, builder, source, subset));
        }

        private boolean record(Supplier<Boolean> call) {
            directCalls++;
            boolean result = call.get();
            if (result) {
                directAccepted++;
            }
            return result;
        }
    }

    public record NamedBody(String token, String name) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#NamedBody"))
                .shapeClass(NamedBody.class)
                .builderSupplier(Builder::new)
                .putMember("token", PreludeSchemas.STRING, new HttpHeaderTrait("x-token"))
                .putMember("name", PreludeSchemas.STRING)
                .build();

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (token != null) {
                serializer.writeString(SCHEMA.member("token"), token);
            }
            if (name != null) {
                serializer.writeString(SCHEMA.member("name"), name);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) switch (member.memberName()) {
                case "token" -> token;
                case "name" -> name;
                default -> null;
            };
        }

        public static final class Builder implements ShapeBuilder<NamedBody> {
            private String token;
            private String name;

            public Builder token(String token) {
                this.token = token;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            @Override
            public NamedBody build() {
                return new NamedBody(token, name);
            }

            @Override
            public Builder deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, (builder, member, de) -> {
                    switch (member.memberName()) {
                        case "token" -> builder.token(de.readString(member));
                        case "name" -> builder.name(de.readString(member));
                        default -> {
                        }
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

    public record WithNamedPayload(NamedBody payload) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#WithNamedPayload"))
                .shapeClass(WithNamedPayload.class)
                .builderSupplier(Builder::new)
                .putMember("payload", NamedBody.SCHEMA, new HttpPayloadTrait())
                .build();

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (payload != null) {
                serializer.writeStruct(SCHEMA.member("payload"), payload);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return "payload".equals(member.memberName()) ? (T) payload : null;
        }

        public static final class Builder implements ShapeBuilder<WithNamedPayload> {
            private NamedBody payload;

            public Builder payload(NamedBody payload) {
                this.payload = payload;
                return this;
            }

            @Override
            public WithNamedPayload build() {
                return new WithNamedPayload(payload);
            }

            @Override
            public Builder deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, (builder, member, de) -> {
                    if ("payload".equals(member.memberName())) {
                        builder.payload(new NamedBody.Builder().deserialize(de).build());
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
