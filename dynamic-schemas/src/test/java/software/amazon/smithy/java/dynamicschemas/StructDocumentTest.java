/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.document.DiscriminatorException;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public class StructDocumentTest {

    static Schema getDocumentSchema(Document document) {
        var serializer = new InterceptingSerializer() {
            Schema result;
            @Override
            protected ShapeSerializer before(Schema schema) {
                result = schema;
                return ShapeSerializer.nullSerializer();
            }
        };
        document.serialize(serializer);
        return serializer.result;
    }

    @Test
    public void onlySupportsStructAndUnionSchemas() {
        var schema = Schema.createString(ShapeId.from("foo#Bar"));
        var document = Document.of("hi");

        Assertions.assertThrows(IllegalArgumentException.class, () -> StructDocument.of(schema, document));
    }

    @Test
    public void onlySupportsCompatibleDocuments() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
                .putMember("foo", PreludeSchemas.STRING)
                .build();
        var document = Document.of("hi");

        Assertions.assertThrows(IllegalArgumentException.class, () -> StructDocument.of(schema, document));
    }

    @Test
    public void wrapsDelegate() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
                .putMember("foo", PreludeSchemas.STRING)
                .build();
        var document = Document.ofObject(Map.of("__type", "foo#Bar", "foo", "bar"));

        var sd = StructDocument.of(schema, document, ShapeId.from("smithy.example#S"));

        assertThat(sd.type(), is(ShapeType.STRUCTURE));
        assertThat(sd.expectDiscriminator().toString(), equalTo("foo#Bar"));
        assertThat(sd.size(), equalTo(1));
    }

    @Test
    public void throwsWhenGettingUnionDiscriminator() {
        var schema = Schema.unionBuilder(ShapeId.from("foo#Bar"))
                .putMember("foo", PreludeSchemas.STRING)
                .build();
        var document = Document.ofObject(Map.of("foo", "bar"));
        var sd = StructDocument.of(schema, document, ShapeId.from("smithy.example#S"));

        Assertions.assertThrows(DiscriminatorException.class, sd::expectDiscriminator);
    }

    @Test
    public void unwrapsValuesWhenGettingMemberValue() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
                .putMember("foo", PreludeSchemas.STRING)
                .build();
        var document = Document.ofObject(Map.of("__type", "foo#Bar", "foo", "bar"));
        var sd = StructDocument.of(schema, document, ShapeId.from("smithy.example#S"));

        assertThat(sd.getMemberValue(schema.member("foo")), equalTo("bar"));
    }

    @Test
    public void returnsNullWhenMemberDoesNotExist() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
                .putMember("foo", PreludeSchemas.STRING)
                .build();
        var document = Document.of(Map.of());
        var sd = StructDocument.of(schema, document, ShapeId.from("smithy.example#S"));

        assertThat(sd.getMemberValue(schema.member("foo")), nullValue());
        assertThat(sd.getMember("foo"), nullValue());
    }

    @Test
    public void getMemberWrapsToo() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
                .putMember("foo", PreludeSchemas.STRING)
                .build();
        var document = Document.ofObject(Map.of("foo", "bar"));

        var sd = StructDocument.of(schema, document, ShapeId.from("smithy.example#S"));

        assertThat(sd.getMemberNames(), contains("foo"));
        var member = sd.getMember("foo");
        assertThat(member.type(), is(ShapeType.STRING));
    }

    @ParameterizedTest
    @MethodSource("convertsToTypeProvider")
    public void convertsToType(Schema schema, Document value, Object expected, Function<Document, Object> mapper) {
        // Wrap the test document so it's always the same kind of structure schema and document value.
        var wrappingSchema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
                .putMember("value", schema)
                .build();
        var wrappedDocument = Document.ofObject(Map.of("value", value));
        var wrapped = StructDocument.of(wrappingSchema, wrappedDocument, ShapeId.from("smithy.example#S"));

        assertThat(wrapped.type(), equalTo(ShapeType.STRUCTURE));

        var innerValue = wrapped.getMember("value");
        assertThat(innerValue.type(), equalTo(schema.type()));
        assertThat(mapper.apply(innerValue), equalTo(expected));
    }

    static List<Arguments> convertsToTypeProvider() {
        var bytes = ByteBuffer.wrap("hi".getBytes(StandardCharsets.UTF_8));
        return List.of(
                Arguments.arguments(
                        PreludeSchemas.BOOLEAN,
                        Document.of(true),
                        true,
                        (Function<Document, Object>) Document::asBoolean),
                Arguments.arguments(
                        PreludeSchemas.STRING,
                        Document.of("hi"),
                        "hi",
                        (Function<Document, Object>) Document::asString),
                Arguments.arguments(
                        PreludeSchemas.BYTE,
                        Document.of((byte) 1),
                        (byte) 1,
                        (Function<Document, Object>) Document::asByte),
                Arguments.arguments(
                        PreludeSchemas.SHORT,
                        Document.of((short) 1),
                        (short) 1,
                        (Function<Document, Object>) Document::asShort),
                Arguments.arguments(
                        PreludeSchemas.INTEGER,
                        Document.of(1),
                        1,
                        (Function<Document, Object>) Document::asInteger),
                Arguments.arguments(
                        PreludeSchemas.LONG,
                        Document.of(1L),
                        1L,
                        (Function<Document, Object>) Document::asLong),
                Arguments.arguments(
                        PreludeSchemas.FLOAT,
                        Document.of(1f),
                        1f,
                        (Function<Document, Object>) Document::asFloat),
                Arguments.arguments(
                        PreludeSchemas.DOUBLE,
                        Document.of(1d),
                        1d,
                        (Function<Document, Object>) Document::asDouble),
                Arguments.arguments(
                        PreludeSchemas.BIG_DECIMAL,
                        Document.of(BigDecimal.ONE),
                        BigDecimal.ONE,
                        (Function<Document, Object>) Document::asBigDecimal),
                Arguments.arguments(
                        PreludeSchemas.BIG_INTEGER,
                        Document.of(BigInteger.ONE),
                        BigInteger.ONE,
                        (Function<Document, Object>) Document::asBigInteger),
                Arguments.arguments(
                        PreludeSchemas.TIMESTAMP,
                        Document.of(Instant.EPOCH),
                        Instant.EPOCH,
                        (Function<Document, Object>) Document::asTimestamp),
                Arguments.arguments(
                        PreludeSchemas.BIG_INTEGER,
                        Document.of(BigInteger.ONE),
                        BigInteger.ONE,
                        (Function<Document, Object>) Document::asNumber),
                Arguments.arguments(
                        PreludeSchemas.BLOB,
                        Document.of(bytes),
                        bytes,
                        (Function<Document, Object>) Document::asBlob));
    }

    @Test
    public void convertingToListWrapsValuesInSchemas() {
        var listSchema = Schema.listBuilder(ShapeId.from("foo#Bar"))
                .putMember("member", PreludeSchemas.STRING)
                .build();
        var wrapper = Schema.structureBuilder(ShapeId.from("foo#Wrapper"))
                .putMember("value", listSchema)
                .build();

        var document = Document.ofObject(Map.of("value", List.of("a", "b")));
        var sd = StructDocument.of(wrapper, document, ShapeId.from("smithy.example#S"));

        assertThat(sd.type(), is(ShapeType.STRUCTURE));
        assertThat(getDocumentSchema(sd), equalTo(wrapper));

        var value = sd.getMember("value");
        assertThat(value.type(), is(ShapeType.LIST));
        assertThat(value.size(), equalTo(2));
        assertThat(getDocumentSchema(value), equalTo(wrapper.member("value")));

        var list = value.asList();
        assertThat(list, hasSize(2));
        assertThat(getDocumentSchema(list.get(0)), equalTo(listSchema.listMember()));
        assertThat(list.get(0).asString(), equalTo("a"));
        assertThat(getDocumentSchema(list.get(0)), equalTo(listSchema.listMember()));
        assertThat(list.get(1).asString(), equalTo("b"));
    }

    @Test
    public void convertingToMapWrapsValuesInSchemas() {
        var mapSchema = Schema.mapBuilder(ShapeId.from("foo#Bar"))
                .putMember("key", PreludeSchemas.STRING)
                .putMember("value", PreludeSchemas.STRING)
                .build();
        var wrapper = Schema.structureBuilder(ShapeId.from("foo#Wrapper"))
                .putMember("value", mapSchema)
                .build();

        var document = Document.ofObject(Map.of("value", Map.of("a", "b")));
        var sd = StructDocument.of(wrapper, document, ShapeId.from("smithy.example#S"));

        assertThat(getDocumentSchema(sd), equalTo(wrapper));
        assertThat(sd.type(), is(ShapeType.STRUCTURE));

        var value = sd.getMember("value");
        assertThat(getDocumentSchema(value), equalTo(wrapper.member("value")));
        assertThat(value.type(), is(ShapeType.MAP));
        assertThat(value.size(), equalTo(1));

        var map = value.asStringMap();
        assertThat(map.values(), hasSize(1));
        assertThat(getDocumentSchema(map.get("a")), equalTo(mapSchema.mapValueMember()));
    }

    @Test
    public void convertingToStructMapWrapsValuesInSchemas() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
                .putMember("a", PreludeSchemas.STRING)
                .putMember("b", PreludeSchemas.STRING)
                .build();
        var document = Document.ofObject(Map.of("a", "a", "b", "b", "c", "c"));

        var sd = StructDocument.of(schema, document, ShapeId.from("smithy.example#S"));

        assertThat(sd.type(), is(ShapeType.STRUCTURE));
        assertThat(getDocumentSchema(sd), equalTo(schema));
        assertThat(getDocumentSchema(sd.getMember("a")), equalTo(schema.member("a")));
        assertThat(getDocumentSchema(sd.getMember("b")), equalTo(schema.member("b")));

        var map = sd.asStringMap();
        assertThat(map.values(), hasSize(2));
        assertThat(getDocumentSchema(map.get("a")), equalTo(schema.member("a")));
        assertThat(getDocumentSchema(map.get("b")), equalTo(schema.member("b")));
    }

    @Test
    public void serializesDocumentLikeShape() {
        var wrapper = Schema.structureBuilder(ShapeId.from("foo#Wrapper"))
                .putMember("value", PreludeSchemas.STRING)
                .build();

        var wrapped = StructDocument.of(
                wrapper,
                Document.ofObject(Map.of("value", "hi")),
                ShapeId.from("smithy.example#S"));
        Schema[] set = new Schema[1];

        wrapped.getMember("value").serialize(new SpecificShapeSerializer() {
            @Override
            public void writeString(Schema schema, String value) {
                set[0] = schema;
            }
        });

        assertThat(set[0], equalTo(wrapper.member("value")));
    }

    @Test
    public void serializesStructDocumentLikeStruct() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
                .putMember("a", PreludeSchemas.STRING)
                .putMember("b", PreludeSchemas.STRING)
                .build();
        var document = Document.ofObject(Map.of("a", "a", "b", "b", "c", "c"));
        var sd = StructDocument.of(schema, document, ShapeId.from("smithy.example#S"));
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
        var sd = StructDocument.of(schema, document, ShapeId.from("smithy.example#S"));
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

    @Test
    public void parsesIsoStringForTimestampMember() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
                .putMember("when", PreludeSchemas.TIMESTAMP)
                .build();
        var document = Document.ofObject(Map.of("when", "2024-01-01T00:00:00Z"));

        var sd = StructDocument.of(schema, document, ShapeId.from("smithy.example#S"));

        var when = sd.getMember("when");
        assertThat(when.type(), is(ShapeType.TIMESTAMP));
        assertThat(when.asTimestamp(), equalTo(Instant.parse("2024-01-01T00:00:00Z")));
    }

    @Test
    public void readsEpochNumberForTimestampMember() {
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
                .putMember("when", PreludeSchemas.TIMESTAMP)
                .build();
        var document = Document.ofObject(Map.of("when", 1704067200));

        var sd = StructDocument.of(schema, document, ShapeId.from("smithy.example#S"));

        assertThat(sd.getMember("when").asTimestamp(), equalTo(Instant.parse("2024-01-01T00:00:00Z")));
    }

    @Test
    public void parsesIsoStringsForNestedTimestamps() {
        var model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"
                        namespace smithy.example

                        structure Foo {
                            items: ItemList
                        }

                        list ItemList {
                            member: Item
                        }

                        structure Item {
                            when: Timestamp
                        }
                        """)
                .assemble()
                .unwrap();
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#Foo")));

        var input = Document.ofObject(Map.of(
                "items",
                List.of(
                        Map.of("when", "2024-01-01T00:00:00Z"),
                        Map.of("when", "2024-06-22T12:00:00Z"))));
        var sd = StructDocument.of(schema, input);

        var items = sd.getMember("items").asList();
        assertThat(items, hasSize(2));
        assertThat(items.get(0).getMember("when").asTimestamp(), equalTo(Instant.parse("2024-01-01T00:00:00Z")));
        assertThat(items.get(1).getMember("when").asTimestamp(), equalTo(Instant.parse("2024-06-22T12:00:00Z")));
    }

    @Test
    public void ignoresTimestampFormatTraitWhenCoercingInput() {
        // @timestampFormat is a wire-serialization concern; the protocol-agnostic input model always treats
        // a string as date-time (ISO-8601), regardless of any trait on the member.
        var model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"
                        namespace smithy.example

                        structure Foo {
                            @timestampFormat("epoch-seconds")
                            when: Timestamp
                        }
                        """)
                .assemble()
                .unwrap();
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#Foo")));

        var input = Document.ofObject(Map.of("when", "2024-01-01T00:00:00Z"));
        var sd = StructDocument.of(schema, input);

        assertThat(sd.getMember("when").asTimestamp(), equalTo(Instant.parse("2024-01-01T00:00:00Z")));
    }

    @Test
    public void convertDocumentPassesThroughDataStream() {
        var model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"
                        namespace smithy.example

                        structure Foo {
                            @required
                            body: StreamBlob
                        }

                        @streaming
                        blob StreamBlob
                        """)
                .assemble()
                .unwrap();
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#Foo")));

        var ds = DataStream.ofString("hello");
        var input = Document.ofObject(Map.of("body", ds));
        var struct = StructDocument.of(schema, input);

        assertThat(struct.getMember("body").asDataStream(), is(ds));
    }

    @Test
    public void convertDocumentPassesThroughEventStream() {
        var model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"
                        namespace smithy.example

                        structure Foo {
                            events: Events
                        }

                        @streaming
                        union Events {
                            data: Data
                        }

                        structure Data {
                            value: String
                        }
                        """)
                .assemble()
                .unwrap();
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#Foo")));

        var es = EventStream.newWriter();
        var input = Document.ofObject(Map.of("events", es));
        var struct = StructDocument.of(schema, input);

        assertThat(struct.getMember("events").asEventStream(), is(es));
    }

    @Test
    public void dataStreamSerializesMemberCorrectly() {
        var model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"
                        namespace smithy.example

                        structure Foo {
                            @required
                            body: StreamBlob
                        }

                        @streaming
                        blob StreamBlob
                        """)
                .assemble()
                .unwrap();
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#Foo")));

        var ds = DataStream.ofString("hello");
        var input = Document.ofObject(Map.of("body", ds));
        var struct = StructDocument.of(schema, input);

        DataStream[] captured = new DataStream[1];
        struct.serializeMembers(new SpecificShapeSerializer() {
            @Override
            public void writeDataStream(Schema s, DataStream value) {
                captured[0] = value;
            }
        });

        assertThat(captured[0], is(ds));
    }
}
