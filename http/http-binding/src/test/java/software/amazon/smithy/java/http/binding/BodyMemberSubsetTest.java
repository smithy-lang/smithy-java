/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenStats;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.MemberSubsetCodec;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.xml.XmlCodec;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

/**
 * The body of an HTTP message holds exactly the members that bind to it.
 *
 * <p>The shapes here carry a {@code shapeClass} and a builder, so a codec that specializes on a shape
 * can reach them. That is the point: the expected bytes below are absolute, and this class runs both
 * with and without runtime code generation, so the two paths are held to the same wire output rather
 * than to each other.
 */
public class BodyMemberSubsetTest {

    private static final Codec CODEC = JsonCodec.builder().build();
    private static final SmithyUri ENDPOINT = SmithyUri.of("https://example.com");

    @Test
    public void bodyHoldsOnlyBodyBoundMembers() {
        var request = serialize(
                BoundInput.OPERATION,
                new BoundInput("id-1", "secret", "red", "Phreddy", 3, new Nested("hi")));

        assertThat(body(request), equalTo("{\"name\":\"Phreddy\",\"count\":3,\"nested\":{\"note\":\"hi\"}}"));
        // The members the body left out are bound elsewhere, and are still written there.
        assertThat(request.uri().getPath(), equalTo("/op/id-1"));
        assertThat(request.uri().getQuery(), equalTo("color=red"));
        assertThat(request.headers().firstValue("x-token"), equalTo("secret"));
    }

    @Test
    public void nullBodyMembersAreOmittedNotNulled() {
        var request = serialize(
                BoundInput.OPERATION,
                new BoundInput("id-1", "secret", "red", null, null, null));

        assertThat(body(request), equalTo("{}"));
    }

    /**
     * A shape that is both the narrowed root and a value inside it keeps every member below the root.
     *
     * <p>Only the top level of a message has HTTP bindings: {@code token} is a header there, and a
     * plain body member one level down. A codec that specialized the shape once and reused it for both
     * positions would drop the nested {@code token}, so this asserts the nested object in full.
     */
    @Test
    public void recursiveShapeKeepsAllMembersBelowTheRoot() {
        var request = serialize(
                RecursiveInput.OPERATION,
                new RecursiveInput("outer-token", new RecursiveInput("inner-token", null)));

        assertThat(body(request), equalTo("{\"next\":{\"token\":\"inner-token\"}}"));
        assertThat(request.headers().firstValue("x-token"), equalTo("outer-token"));
    }

    /**
     * The bodies above come from a generated codec, not from the fallback.
     *
     * <p>Worth asserting because the fallback produces the same bytes: nothing else here would notice
     * if generation quietly stopped happening, and the narrowed body is only a win when it does.
     * A null return is the codec saying it did not generate anything for this shape.
     */
    @Test
    public void narrowedBodiesUseGeneratedCodecs() {
        assumeTrue(RuntimeCodegenFeature.available() && RuntimeCodegenFeature.enabled("json"),
                "Runtime code generation is not enabled for JSON in this JVM");
        var input = new BoundInput("id-1", "secret", "red", "Phreddy", 3, new Nested("hi"));

        var body = narrowed(input, BodyMemberSubset.REQUEST);

        assertThat(body, notNullValue());
        assertThat(utf8(body), equalTo("{\"name\":\"Phreddy\",\"count\":3,\"nested\":{\"note\":\"hi\"}}"));
        var stats = RuntimeCodegenStats.snapshot("json");
        assertThat(stats.generated(), greaterThan(0L));
        assertThat(stats.failed(), equalTo(0L));
    }

    /**
     * The generated body and the body the interpreted path writes are the same bytes.
     *
     * <p>Same shape, same members, same order. Passing the proxy is what forces the comparison: a codec
     * cannot specialize a class it was not given, so the proxy takes the interpreted path even here.
     */
    @Test
    public void generatedBodiesMatchInterpretedBodies() {
        assumeTrue(RuntimeCodegenFeature.available() && RuntimeCodegenFeature.enabled("json"),
                "Runtime code generation is not enabled for JSON in this JVM");
        var input = new BoundInput("id-1", "secret", "red", "Phreddy", 3, new Nested("hi"));
        var sparse = new BoundInput("id-1", "secret", "red", null, 3, null);
        var bindings = HttpBindingSchemaExtensions.structBindingsOf(BoundInput.SCHEMA);

        for (var value : List.of(input, sparse)) {
            assertThat(
                    utf8(narrowed(value, BodyMemberSubset.REQUEST)),
                    equalTo(utf8(CODEC.serialize(new StructBodyProxy(value, bindings.request().bindings)))));
            assertThat(
                    utf8(narrowed(value, BodyMemberSubset.RESPONSE)),
                    equalTo(utf8(CODEC.serialize(new StructBodyProxy(value, bindings.response().bindings)))));
        }
    }

    /**
     * A response body holds the members the response direction binds to it, which is not the same set.
     *
     * <p>Only headers and the status line exist to bind to on the way back, so a member that was a label
     * or a query parameter in a request belongs in the body of a response.
     */
    @Test
    public void responseBodiesUseTheResponseDirection() {
        var response = new HttpBinding().responseSerializer()
                .operation(BoundInput.OPERATION)
                .payloadCodec(CODEC)
                .payloadMediaType("application/json")
                .shapeValue(new BoundInput("id-1", "secret", "red", "Phreddy", 3, new Nested("hi")))
                .serializeResponse();

        assertThat(
                utf8(response.body().asByteBuffer()),
                equalTo("{\"id\":\"id-1\",\"color\":\"red\",\"name\":\"Phreddy\",\"count\":3,\"nested\":{\"note\":\"hi\"}}"));
        assertThat(response.headers().firstValue("x-token"), equalTo("secret"));
    }

    @Test
    public void generatedXmlResponseReadsOnlyBodyMembers() {
        assumeTrue(RuntimeCodegenFeature.available() && RuntimeCodegenFeature.enabled("xml"),
                "Runtime code generation is not enabled for XML in this JVM");

        var builder = BoundInput.builder();
        var headers = HttpHeaders.of(Map.of(
                "content-type",
                List.of("application/xml"),
                "x-token",
                List.of("header-token")));
        var body = DataStream.ofString(
                "<BoundInput>"
                        + "<id>body-id</id>"
                        + "<token>body-token-must-be-ignored</token>"
                        + "<color>body-color</color>"
                        + "<name>body-name</name>"
                        + "<count>3</count>"
                        + "</BoundInput>");
        var response = HttpResponse.of(HttpVersion.HTTP_1_1, 200, headers, body);

        RuntimeCodegenStats.reset();
        try (var codec = XmlCodec.builder().build()) {
            new ResponseDeserializer()
                    .payloadCodec(codec)
                    .payloadMediaType("application/xml")
                    .outputShapeBuilder(builder)
                    .response(response)
                    .deserialize();
        }

        assertThat(
                builder.build(),
                equalTo(new BoundInput("body-id", "header-token", "body-color", "body-name", 3, null)));
        var stats = RuntimeCodegenStats.snapshot("xml");
        assertThat(stats.generated(), greaterThan(0L));
        assertThat(stats.failed(), equalTo(0L));
    }

    /**
     * A shape reachable as a value inside itself gets no narrowed codec at all.
     *
     * <p>One generated class per shape means one member list per shape, and the root's list is the
     * narrowed one. Declining is the only correct answer; {@link #recursiveShapeKeepsAllMembersBelowTheRoot()}
     * covers the bytes the caller then produces.
     */
    @Test
    public void recursiveShapesDeclineNarrowedCodecs() {
        assumeTrue(RuntimeCodegenFeature.available() && RuntimeCodegenFeature.enabled("json"),
                "Runtime code generation is not enabled for JSON in this JVM");

        assertThat(narrowed(new RecursiveInput("t", null), BodyMemberSubset.REQUEST), nullValue());
    }

    /**
     * A shape whose Java class generated code cannot name still serializes.
     *
     * <p>Generated code lives in the codec's own package, so a non-public shape class is out of its
     * reach. That has to be a decision made before anything is generated: the alternative is an
     * {@link IllegalAccessError} thrown from generated bytecode on the first request, where nothing is
     * left to fall back to.
     */
    @Test
    public void inaccessibleShapeClassesStillSerialize() {
        var request = serialize(HiddenInput.OPERATION, new HiddenInput("secret", "Phreddy"));

        assertThat(body(request), equalTo("{\"name\":\"Phreddy\"}"));
        assertThat(request.headers().firstValue("x-token"), equalTo("secret"));
        assertThat(narrowed(new HiddenInput("secret", "Phreddy"), BodyMemberSubset.REQUEST), nullValue());
    }

    private static ByteBuffer narrowed(SerializableStruct struct, BodyMemberSubset subset) {
        return ((MemberSubsetCodec) CODEC).serialize(struct, subset);
    }

    private static HttpRequest serialize(ApiOperation<?, ?> operation, SerializableStruct input) {
        return new HttpBinding().requestSerializer()
                .operation(operation)
                .payloadCodec(CODEC)
                .payloadMediaType("application/json")
                .shapeValue(input)
                .endpoint(ENDPOINT)
                .serializeRequest();
    }

    private static String body(HttpRequest request) {
        return utf8(request.body().asByteBuffer());
    }

    private static String utf8(ByteBuffer buffer) {
        var bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** An input with one member per binding: a label, a header, a query param, and three body members. */
    public record BoundInput(String id, String token, String color, String name, Integer count, Nested nested)
            implements SerializableStruct {

        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#BoundInput"))
                .shapeClass(BoundInput.class)
                .builderSupplier(Builder::new)
                .putMember("id", PreludeSchemas.STRING, new HttpLabelTrait(), new RequiredTrait())
                .putMember("token", PreludeSchemas.STRING, new HttpHeaderTrait("x-token"))
                .putMember("color", PreludeSchemas.STRING, new HttpQueryTrait("color"))
                .putMember("name", PreludeSchemas.STRING)
                .putMember("count", PreludeSchemas.INTEGER)
                .putMember("nested", Nested.SCHEMA)
                .build();
        static final ApiOperation<?, ?> OPERATION = operation("BoundOperation", SCHEMA, "/op/{id}");

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (id != null) {
                serializer.writeString(SCHEMA.member("id"), id);
            }
            if (token != null) {
                serializer.writeString(SCHEMA.member("token"), token);
            }
            if (color != null) {
                serializer.writeString(SCHEMA.member("color"), color);
            }
            if (name != null) {
                serializer.writeString(SCHEMA.member("name"), name);
            }
            if (count != null) {
                serializer.writeInteger(SCHEMA.member("count"), count);
            }
            if (nested != null) {
                serializer.writeStruct(SCHEMA.member("nested"), nested);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) switch (member.memberName()) {
                case "id" -> id;
                case "token" -> token;
                case "color" -> color;
                case "name" -> name;
                case "count" -> count;
                case "nested" -> nested;
                default -> null;
            };
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder implements ShapeBuilder<BoundInput> {
            private String id;
            private String token;
            private String color;
            private String name;
            private Integer count;
            private Nested nested;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder token(String token) {
                this.token = token;
                return this;
            }

            public Builder color(String color) {
                this.color = color;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder count(Integer count) {
                this.count = count;
                return this;
            }

            public Builder nested(Nested nested) {
                this.nested = nested;
                return this;
            }

            @Override
            public BoundInput build() {
                return new BoundInput(id, token, color, name, count, nested);
            }

            @Override
            public ShapeBuilder<BoundInput> deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, (builder, member, de) -> {
                    switch (member.memberIndex()) {
                        case 0 -> builder.id(de.readString(member));
                        case 1 -> builder.token(de.readString(member));
                        case 2 -> builder.color(de.readString(member));
                        case 3 -> builder.name(de.readString(member));
                        case 4 -> builder.count(de.readInteger(member));
                        case 5 -> builder.nested(Nested.builder().deserializeMember(de, member).build());
                        default -> throw new AssertionError(member);
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

    public record Nested(String note) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#Nested"))
                .shapeClass(Nested.class)
                .builderSupplier(Builder::new)
                .putMember("note", PreludeSchemas.STRING)
                .build();

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (note != null) {
                serializer.writeString(SCHEMA.member("note"), note);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return "note".equals(member.memberName()) ? (T) note : null;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder implements ShapeBuilder<Nested> {
            private String note;

            public Builder note(String note) {
                this.note = note;
                return this;
            }

            @Override
            public Nested build() {
                return new Nested(note);
            }

            @Override
            public ShapeBuilder<Nested> deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(
                        SCHEMA,
                        this,
                        (builder, member, de) -> builder.note(de.readString(member)));
                return this;
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    /** Deliberately not public: a shape whose class a codec in another package cannot name. */
    record HiddenInput(String token, String name) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#HiddenInput"))
                .shapeClass(HiddenInput.class)
                .builderSupplier(Builder::new)
                .putMember("token", PreludeSchemas.STRING, new HttpHeaderTrait("x-token"))
                .putMember("name", PreludeSchemas.STRING)
                .build();
        static final ApiOperation<?, ?> OPERATION = operation("HiddenOperation", SCHEMA, "/op");

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

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder implements ShapeBuilder<HiddenInput> {
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
            public HiddenInput build() {
                return new HiddenInput(token, name);
            }

            @Override
            public ShapeBuilder<HiddenInput> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    /** An input that holds itself, so the same shape is both the narrowed root and a body member's value. */
    public record RecursiveInput(String token, RecursiveInput next) implements SerializableStruct {
        static final Schema SCHEMA = recursiveSchema();
        static final ApiOperation<?, ?> OPERATION = operation("RecursiveOperation", SCHEMA, "/op");

        private static Schema recursiveSchema() {
            var builder = Schema.structureBuilder(ShapeId.from("smithy.example#RecursiveInput"))
                    .shapeClass(RecursiveInput.class)
                    .builderSupplier(Builder::new)
                    .putMember("token", PreludeSchemas.STRING, new HttpHeaderTrait("x-token"));
            return builder.putMember("next", builder).build();
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (token != null) {
                serializer.writeString(SCHEMA.member("token"), token);
            }
            if (next != null) {
                serializer.writeStruct(SCHEMA.member("next"), next);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) switch (member.memberName()) {
                case "token" -> token;
                case "next" -> next;
                default -> null;
            };
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder implements ShapeBuilder<RecursiveInput> {
            private String token;
            private RecursiveInput next;

            public Builder token(String token) {
                this.token = token;
                return this;
            }

            public Builder next(RecursiveInput next) {
                this.next = next;
                return this;
            }

            @Override
            public RecursiveInput build() {
                return new RecursiveInput(token, next);
            }

            @Override
            public ShapeBuilder<RecursiveInput> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    /** A minimal POST operation whose input carries the {@code @http} trait that drives request bindings. */
    private static ApiOperation<?, ?> operation(String name, Schema inputSchema, String uri) {
        var httpTrait = HttpTrait.builder().method("POST").uri(UriPattern.parse(uri)).code(200).build();
        var operationSchema = Schema.createOperation(ShapeId.from("smithy.example#" + name), httpTrait);
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
