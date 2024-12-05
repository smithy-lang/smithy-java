/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.document.DiscriminatorException;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public class WrappedDocumentTest {
    @Test
    public void wrapsDelegate() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
            .putMember("foo", PreludeSchemas.STRING)
            .build();
        var document = Document.ofObject(Map.of("__type", "foo#Bar", "foo", "bar"));

        var sd = new WrappedDocument(ShapeId.from("smithy.example#S"), schema, document);

        assertThat(sd.type(), is(ShapeType.STRUCTURE));
        assertThat(sd.discriminator().toString(), equalTo("foo#Bar"));
        assertThat(sd.size(), equalTo(2));
    }

    @Test
    public void unwrapsValuesWhenGettingMemberValue() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
            .putMember("foo", PreludeSchemas.STRING)
            .build();
        var document = Document.ofObject(Map.of("__type", "foo#Bar", "foo", "bar"));
        var sd = new WrappedDocument(ShapeId.from("smithy.example#S"), schema, document);

        assertThat(sd.getMemberValue(schema.member("foo")), equalTo("bar"));
    }

    @Test
    public void returnsNullWhenMemberDoesNotExist() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
            .putMember("foo", PreludeSchemas.STRING)
            .build();
        var document = Document.of(Map.of());
        var sd = new WrappedDocument(ShapeId.from("smithy.example#S"), schema, document);

        assertThat(sd.getMemberValue(schema.member("foo")), nullValue());
        assertThat(sd.getMember("foo"), nullValue());
    }

    @Test
    public void callsDelegateIfNoDiscriminatorFound() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
            .putMember("foo", PreludeSchemas.STRING)
            .build();
        var document = Document.ofObject(Map.of("foo", "bar"));

        var sd = new WrappedDocument(ShapeId.from("smithy.example#S"), schema, document);

        Assertions.assertThrows(DiscriminatorException.class, sd::discriminator);
    }

    @Test
    public void getMemberWrapsToo() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
            .putMember("foo", PreludeSchemas.STRING)
            .build();
        var document = Document.ofObject(Map.of("foo", "bar"));

        var sd = new WrappedDocument(ShapeId.from("smithy.example#S"), schema, document);

        assertThat(sd.getMemberNames(), contains("foo"));
        var member = sd.getMember("foo");
        assertThat(member.type(), is(ShapeType.STRING));
    }

    @ParameterizedTest
    @MethodSource("convertsToTypeProvider")
    public void convertsToType(Schema schema, Document value, Object expected, Function<Document, Object> mapper) {
        var wrapped = new WrappedDocument(ShapeId.from("smithy.example#S"), schema, value);

        assertThat(wrapped.type(), equalTo(schema.type()));
        assertThat(mapper.apply(wrapped), equalTo(expected));
    }

    static List<Arguments> convertsToTypeProvider() {
        var bytes = ByteBuffer.wrap("hi".getBytes(StandardCharsets.UTF_8));
        return List.of(
            Arguments.arguments(
                PreludeSchemas.BOOLEAN,
                Document.of(true),
                true,
                (Function<Document, Object>) Document::asBoolean
            ),
            Arguments.arguments(
                PreludeSchemas.STRING,
                Document.of("hi"),
                "hi",
                (Function<Document, Object>) Document::asString
            ),
            Arguments.arguments(
                PreludeSchemas.BYTE,
                Document.of((byte) 1),
                (byte) 1,
                (Function<Document, Object>) Document::asByte
            ),
            Arguments.arguments(
                PreludeSchemas.SHORT,
                Document.of((short) 1),
                (short) 1,
                (Function<Document, Object>) Document::asShort
            ),
            Arguments.arguments(
                PreludeSchemas.INTEGER,
                Document.of(1),
                1,
                (Function<Document, Object>) Document::asInteger
            ),
            Arguments.arguments(
                PreludeSchemas.LONG,
                Document.of(1L),
                1L,
                (Function<Document, Object>) Document::asLong
            ),
            Arguments.arguments(
                PreludeSchemas.FLOAT,
                Document.of(1f),
                1f,
                (Function<Document, Object>) Document::asFloat
            ),
            Arguments.arguments(
                PreludeSchemas.DOUBLE,
                Document.of(1d),
                1d,
                (Function<Document, Object>) Document::asDouble
            ),
            Arguments.arguments(
                PreludeSchemas.BIG_DECIMAL,
                Document.of(BigDecimal.ONE),
                BigDecimal.ONE,
                (Function<Document, Object>) Document::asBigDecimal
            ),
            Arguments.arguments(
                PreludeSchemas.BIG_INTEGER,
                Document.of(BigInteger.ONE),
                BigInteger.ONE,
                (Function<Document, Object>) Document::asBigInteger
            ),
            Arguments.arguments(
                PreludeSchemas.TIMESTAMP,
                Document.of(Instant.EPOCH),
                Instant.EPOCH,
                (Function<Document, Object>) Document::asTimestamp
            ),
            Arguments.arguments(
                PreludeSchemas.BIG_INTEGER,
                Document.of(BigInteger.ONE),
                BigInteger.ONE,
                (Function<Document, Object>) Document::asNumber
            ),
            Arguments.arguments(
                PreludeSchemas.BLOB,
                Document.of(bytes),
                bytes,
                (Function<Document, Object>) Document::asBlob
            )
        );
    }

    @Test
    public void convertingToListWrapsValuesInSchemas() {
        var schema = Schema.listBuilder(ShapeId.from("foo#Bar"))
            .putMember("member", PreludeSchemas.STRING)
            .build();
        var document = Document.ofObject(List.of("a", "b"));

        var sd = new WrappedDocument(ShapeId.from("smithy.example#S"), schema, document);

        assertThat(sd.type(), is(ShapeType.LIST));
        assertThat(sd.size(), equalTo(2));

        var list = sd.asList();
        assertThat(list, hasSize(2));
        assertThat(list.get(0), instanceOf(WrappedDocument.class));
        assertThat(list.get(1), instanceOf(WrappedDocument.class));
    }

    @Test
    public void convertingToMapWrapsValuesInSchemas() {
        var schema = Schema.mapBuilder(ShapeId.from("foo#Bar"))
            .putMember("key", PreludeSchemas.STRING)
            .putMember("value", PreludeSchemas.STRING)
            .build();
        var document = Document.ofObject(Map.of("a", "b"));

        var sd = new WrappedDocument(ShapeId.from("smithy.example#S"), schema, document);

        assertThat(sd.type(), is(ShapeType.MAP));
        assertThat(sd.size(), equalTo(1));

        var map = sd.asStringMap();
        assertThat(map.values(), hasSize(1));
        assertThat(map.get("a"), instanceOf(WrappedDocument.class));
    }

    @Test
    public void convertingToStructMapWrapsValuesInSchemas() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
            .putMember("a", PreludeSchemas.STRING)
            .putMember("b", PreludeSchemas.STRING)
            .build();
        var document = Document.ofObject(Map.of("a", "a", "b", "b", "c", "c"));

        var sd = new WrappedDocument(ShapeId.from("smithy.example#S"), schema, document);

        assertThat(sd.type(), is(ShapeType.STRUCTURE));
        assertThat(sd.getMember("a"), instanceOf(WrappedDocument.class));
        assertThat(sd.getMember("b"), instanceOf(WrappedDocument.class));
        assertThat(sd.getMember("c"), not(instanceOf(WrappedDocument.class)));

        var map = sd.asStringMap();
        assertThat(map.values(), hasSize(3));
        assertThat(map.get("a"), instanceOf(WrappedDocument.class));
        assertThat(map.get("b"), instanceOf(WrappedDocument.class));
        assertThat(map.get("c"), not(instanceOf(WrappedDocument.class)));

        // Convert to object does the same as the delegate.
        assertThat(sd.asObject(), equalTo(document.asObject()));
    }

    @Test
    public void serializesDocumentLikeShape() {
        var wrapped = new WrappedDocument(
            ShapeId.from("smithy.example#S"),
            PreludeSchemas.STRING,
            Document.of("hi")
        );
        Schema[] set = new Schema[1];

        wrapped.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeString(Schema schema, String value) {
                set[0] = schema;
            }
        });

        assertThat(set[0], is(PreludeSchemas.STRING));
    }

    @Test
    public void serializesStructDocumentLikeStruct() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
            .putMember("a", PreludeSchemas.STRING)
            .putMember("b", PreludeSchemas.STRING)
            .build();
        var document = Document.ofObject(Map.of("a", "a", "b", "b", "c", "c"));
        var sd = new WrappedDocument(ShapeId.from("smithy.example#S"), schema, document);
        Schema[] set = new Schema[3];

        sd.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeStruct(Schema schema, SerializableStruct struct) {
                set[2] = schema;
                struct.serializeMembers(new SpecificShapeSerializer() {
                    @Override
                    public void writeString(Schema schema, String value) {
                        set[schema.memberIndex()] = schema;
                    }
                });
            }
        });

        assertThat(set[2], equalTo(schema));
        assertThat(set[0], equalTo(schema.member("a")));
        assertThat(set[1], equalTo(schema.member("b")));
    }

    @Test
    public void serializesUnionDocumentLikeUnion() {
        var schema = Schema.unionBuilder(ShapeId.from("foo#Bar"))
            .putMember("a", PreludeSchemas.STRING)
            .putMember("b", PreludeSchemas.STRING)
            .build();
        var document = Document.ofObject(Map.of("a", "a"));
        var sd = new WrappedDocument(ShapeId.from("smithy.example#S"), schema, document);
        Schema[] set = new Schema[2];

        sd.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeStruct(Schema schema, SerializableStruct struct) {
                set[0] = schema;
                struct.serializeMembers(new SpecificShapeSerializer() {
                    @Override
                    public void writeString(Schema schema, String value) {
                        set[1] = schema;
                    }
                });
            }
        });

        assertThat(set[0], equalTo(schema));
        assertThat(set[1], equalTo(schema.member("a")));
    }
}
