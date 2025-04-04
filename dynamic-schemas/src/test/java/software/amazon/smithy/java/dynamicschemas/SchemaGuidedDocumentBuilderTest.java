/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeDeserializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.document.DocumentDeserializer;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public class SchemaGuidedDocumentBuilderTest {

    private static Model model;

    @BeforeAll
    public static void setup() {
        model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"
                        namespace smithy.example

                        document MyDocument
                        string MyString
                        boolean MyBoolean
                        timestamp MyTimestamp
                        blob MyBlob
                        byte MyByte
                        short MyShort
                        integer MyInteger
                        long MyLong
                        float MyFloat
                        double MyDouble
                        bigInteger MyBigInteger
                        bigDecimal MyBigDecimal

                        intEnum MyIntEnum {
                            foo = 1
                            bar = 2
                        }

                        enum MyEnum {
                            foo
                            bar
                        }

                        @sparse
                        list SimpleList {
                            member: String
                        }

                        map SimpleMap {
                            key: MyString
                            value: MyString
                        }

                        map DocumentMap {
                            key: MyString
                            value: MyDocument
                        }

                        structure SimpleStruct {
                            foo: String
                            baz: SimpleStruct
                        }

                        union SimpleUnion {
                            foo: String
                            baz: SimpleStruct
                        }

                        map StructMap {
                            key: String
                            value: Foo
                        }

                        structure Foo {
                            @jsonName("B")
                            b: String
                        }
                        """)
                .assemble()
                .unwrap();
    }

    @ParameterizedTest
    @MethodSource("deserializesShapesProvider")
    public void deserializesShapes(String name, Document source) {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#" + name)));

        var builder = SchemaConverter.createDocumentBuilder(schema, ShapeId.from("smithy.example#Foo"));
        source.deserializeInto(builder);

        var result = builder.build();

        assertThat(result.asObject(), equalTo(source.asObject()));
        assertThat(result.schema(), equalTo(schema));

        // Ensure structure and union members are set appropriately.
        if (schema.type() == ShapeType.STRUCTURE || schema.type() == ShapeType.UNION) {
            for (var member : result.getMemberNames()) {
                var memberValue = result.getMember(member);
                if (memberValue != null) {
                    var expectedMember = schema.member(member);
                    if (expectedMember != null) {
                        memberValue.serialize(new InterceptingSerializer() {
                            @Override
                            protected ShapeSerializer before(Schema s) {
                                assertThat(s, equalTo(expectedMember));
                                return ShapeSerializer.nullSerializer();
                            }
                        });
                    }
                }
            }
        }
    }

    @Test
    public void deserializesMember() {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleStruct")));
        var document = Document.ofObject(Map.of("foo", "bar"));

        var builder = SchemaConverter.createDocumentBuilder(schema, ShapeId.from("smithy.example#Foo"));
        builder.deserializeMember(new DocumentDeserializer(document), schema.member("baz"));

        var result = builder.build();

        assertThat(result.asObject(), equalTo(document.asObject()));
        assertThat(result.schema(), equalTo(schema));
    }

    @Test
    public void usesCorrectMemberSchemas() {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleStruct")));

        // Ensure that the schema used to deserialize the shape is pass through to the deserializer methods.
        var deser = new SpecificShapeDeserializer() {
            @Override
            public <T> void readStruct(Schema s1, T state, StructMemberConsumer<T> consumer) {
                assertThat(s1, equalTo(schema));
                consumer.accept(state, s1.member("foo"), new SpecificShapeDeserializer() {
                    @Override
                    public String readString(Schema s2) {
                        // We previously had a bug that always passed the same root schema over and over.
                        assertThat(s2, equalTo(schema.member("foo")));
                        return "bar";
                    }
                });
            }
        };

        var builder = SchemaConverter.createDocumentBuilder(schema, ShapeId.from("smithy.example#Foo"));
        builder.deserialize(deser);

        var result = builder.build();

        assertThat(result.asObject(), equalTo(Map.of("foo", "bar")));
        assertThat(result.schema(), equalTo(schema));

        result.getMember("foo").serialize(new SpecificShapeSerializer() {
            @Override
            public void writeString(Schema s, String value) {
                assertThat(s, equalTo(schema.member("foo")));
            }
        });
    }

    @Test
    public void cannotSetMemberWhenNotBuildingStructureOrMapOrUnion() {
        var converter = new SchemaConverter(model);
        var member = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleStruct"))).member("foo");
        var schema = PreludeSchemas.STRING;
        var builder = SchemaConverter.createDocumentBuilder(schema, ShapeId.from("smithy.example#Foo"));

        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.setMemberValue(member, "x"));
    }

    @Test
    public void deserializesMultipleMembersUsingDocuments() {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleStruct")));
        var builder = SchemaConverter.createDocumentBuilder(schema, ShapeId.from("smithy.example#Foo"));

        builder.setMemberValue(schema.member("foo"), Document.of("bar1"));
        builder.setMemberValue(schema.member("baz"), Document.ofObject(Map.of("foo", "bar2")));

        var result = builder.build();

        assertThat(result.asObject(), equalTo(Map.of("foo", "bar1", "baz", Map.of("foo", "bar2"))));
        assertThat(result.schema(), equalTo(schema));
    }

    @Test
    public void deserializesMultipleMembersUsingObjects() {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleStruct")));
        var builder = SchemaConverter.createDocumentBuilder(schema, ShapeId.from("smithy.example#Foo"));

        builder.setMemberValue(schema.member("foo"), "bar1");
        builder.setMemberValue(schema.member("baz"), Map.of("foo", "bar2"));

        var result = builder.build();

        assertThat(result.asObject(), equalTo(Map.of("foo", "bar1", "baz", Map.of("foo", "bar2"))));
        assertThat(result.schema(), equalTo(schema));
    }

    @Test
    public void throwsWhenSettingInvalidMember() {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleStruct")));
        var member = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleMap"))).member("key");
        var builder = SchemaConverter.createDocumentBuilder(schema, ShapeId.from("smithy.example#Foo"));

        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.setMemberValue(member, "bar1"));
    }

    @Test
    public void throwsWhenSettingInvalidMemberOnNonAggregate() {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleList")));
        var member = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleList"))).member("member");
        var builder = SchemaConverter.createDocumentBuilder(schema, ShapeId.from("smithy.example#Foo"));

        Assertions.assertThrows(IllegalArgumentException.class, () -> builder.setMemberValue(member, "bar1"));
    }

    @Test
    public void throwsWhenNoValueSetForScalar() {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleList")));
        var builder = SchemaConverter.createDocumentBuilder(schema, ShapeId.from("smithy.example#Foo"));

        Assertions.assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    public void throwsWhenNoValueSetForUnion() {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleUnion")));
        var builder = SchemaConverter.createDocumentBuilder(schema, ShapeId.from("smithy.example#Foo"));

        Assertions.assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    public void worksWithMapOfStructure() {
        var source = Document.ofObject(Map.of("a", Document.ofObject(Map.of("b", "str"))));

        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#StructMap")));

        var builder = SchemaConverter.createDocumentBuilder(schema, ShapeId.from("smithy.example#StructMap"));
        source.deserializeInto(builder);

        var result = builder.build();

        assertThat(result.asObject(), equalTo(source.asObject()));
        assertThat(result.schema(), equalTo(schema));

        var codec = JsonCodec.builder().useJsonName(true).build();

        assertThat(codec.serializeToString(result), equalTo("{\"a\":{\"B\":\"str\"}}"));
    }

    static List<Arguments> deserializesShapesProvider() {
        return List.of(
                Arguments.of("MyDocument", Document.ofObject(Map.of("a", "b"))),
                Arguments.of("MyString", Document.of("hi")),
                Arguments.of("MyBoolean", Document.of(true)),
                Arguments.of("MyTimestamp", Document.of(Instant.EPOCH)),
                Arguments.of("MyBlob", Document.of("foo".getBytes(StandardCharsets.UTF_8))),
                Arguments.of("MyByte", Document.of((byte) 1)),
                Arguments.of("MyShort", Document.of((short) 1)),
                Arguments.of("MyInteger", Document.of(1)),
                Arguments.of("MyLong", Document.of(1L)),
                Arguments.of("MyFloat", Document.of(1f)),
                Arguments.of("MyDouble", Document.of(1d)),
                Arguments.of("MyBigInteger", Document.of(BigInteger.ONE)),
                Arguments.of("MyBigDecimal", Document.of(BigDecimal.ONE)),
                Arguments.of("MyIntEnum", Document.of(1)),
                Arguments.of("MyEnum", Document.of("foo")),
                Arguments.of("SimpleList", Document.ofObject(List.of("a", "b"))),
                Arguments.of("SimpleMap", Document.ofObject(Map.of("foo", "bar"))),
                Arguments.of(
                        "SimpleStruct",
                        Document.ofObject(Map.of("foo", "bar", "baz", Document.ofObject(Map.of("foo", "hi"))))),
                Arguments.of("SimpleUnion", Document.ofObject(Map.of("foo", "bar"))));
    }
}
