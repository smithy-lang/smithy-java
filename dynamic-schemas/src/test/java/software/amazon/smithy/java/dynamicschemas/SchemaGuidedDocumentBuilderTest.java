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

public class SchemaGuidedDocumentBuilderTest {

    private static Model model;

    @BeforeAll
    public static void setup() {
        model = Model.assembler()
                .addUnparsedModel("test.smithy", """
                        $version: "2"
                        namespace smithy.example

                        string MyString

                        map SimpleMap {
                            key: MyString
                            value: MyString
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

                        structure StructMapWrapper {
                            map: StructMap
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
    public void deserializesShapes(String modelText, Document source) {
        var testModel = Model.assembler().addUnparsedModel("test.smithy", modelText).assemble().unwrap();
        var converter = new SchemaConverter(testModel);
        var schema = converter.getSchema(testModel.expectShape(ShapeId.from("smithy.example#TestShape")));

        var builder = SchemaConverter.createDocumentBuilder(schema, ShapeId.from("smithy.example#Foo"));
        source.deserializeInto(builder);

        var result = builder.build();

        assertThat(result.asObject(), equalTo(source.asObject()));
        assertThat(result.schema(), equalTo(schema));

        // Ensure structure and union members are set appropriately.
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

    static List<Arguments> deserializesShapesProvider() {
        return List.of(
                Arguments.of(
                        """
                                $version: "2"
                                namespace smithy.example

                                structure TestShape {
                                    foo: MyDocument
                                }

                                document MyDocument
                                """,
                        Document.ofObject(Map.of("foo", "hi"))),
                Arguments.of(
                        """
                                $version: "2"
                                namespace smithy.example

                                structure TestShape {
                                    foo: String
                                    myEnum: MyEnum
                                }

                                enum MyEnum {
                                    foo
                                    bar
                                }
                                """,
                        Document.ofObject(Map.of("foo", "hi", "myEnum", "bar"))),
                Arguments.of(
                        """
                                $version: "2"
                                namespace smithy.example

                                structure TestShape {
                                    foo: MyBoolean
                                }

                                boolean MyBoolean
                                """,
                        Document.ofObject(Map.of("foo", true))),
                Arguments.of(
                        """
                                $version: "2"
                                namespace smithy.example

                                structure TestShape {
                                    foo: MyTimestamp
                                }

                                timestamp MyTimestamp
                                """,
                        Document.ofObject(Map.of("foo", Document.of(Instant.EPOCH)))),
                Arguments.of(
                        """
                                $version: "2"
                                namespace smithy.example

                                structure TestShape {
                                    foo: MyBlob
                                }

                                blob MyBlob
                                """,
                        Document.ofObject(Map.of("foo", Document.of("foo".getBytes(StandardCharsets.UTF_8))))),
                Arguments.of(
                        """
                                $version: "2"
                                namespace smithy.example

                                structure TestShape {
                                    byte: Byte
                                    short: Short
                                    integer: Integer
                                    long: Long
                                    float: Float
                                    double: Double
                                    bigInteger: BigInteger
                                    bigDecimal: BigDecimal
                                    intEnum: MyIntEnum
                                }

                                intEnum MyIntEnum {
                                    foo = 1
                                    bar = 2
                                }
                                """,
                        Document.ofObject(Map.of(
                                "byte",
                                Document.of((byte) 1),
                                "short",
                                Document.of((short) 2),
                                "integer",
                                Document.of(3),
                                "long",
                                Document.of((long) 4),
                                "float",
                                Document.of((float) 5),
                                "double",
                                Document.of((double) 6),
                                "bigInteger",
                                Document.of(BigInteger.TEN),
                                "bigDecimal",
                                Document.of(BigDecimal.TEN),
                                "intEnum",
                                Document.of(2)))),
                Arguments.of(
                        """
                                $version: "2"
                                namespace smithy.example

                                structure TestShape {
                                    values: MyList
                                }

                                list MyList {
                                    member: MyStruct
                                }

                                structure MyStruct {
                                    foo: String
                                    bar: Integer
                                }
                                """,
                        Document.ofObject(Map.of(
                                "values",
                                Document.ofObject(
                                        List.of(
                                                Document.ofObject(Map.of("foo", "hi", "bar", 1)),
                                                Document.ofObject(Map.of("foo", "bye", "bar", 2))))))),
                Arguments.of(
                        """
                                $version: "2"
                                namespace smithy.example

                                structure TestShape {
                                    values: MyMap
                                }

                                map MyMap {
                                    key: String
                                    value: MyStruct
                                }

                                structure MyStruct {
                                    foo: String
                                    bar: Integer
                                }
                                """,
                        Document.ofObject(Map.of(
                                "values",
                                Document.ofObject(
                                        Map.of(
                                                "hi",
                                                Document.ofObject(Map.of("foo", "hi", "bar", 1)),
                                                "bye",
                                                Document.ofObject(Map.of("foo", "bye", "bar", 2))))))),
                Arguments.of(
                        """
                                $version: "2"
                                namespace smithy.example

                                union TestShape {
                                    foo: String
                                    bar: Integer
                                }
                                """,
                        Document.ofObject(Map.of("foo", Document.ofObject("hi")))));
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
    public void throwsWhenNoValueSetForUnion() {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleUnion")));
        var builder = SchemaConverter.createDocumentBuilder(schema, ShapeId.from("smithy.example#Foo"));

        Assertions.assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    public void worksWithMapOfStructure() {
        var source = Document.ofObject(Map.of("map", Map.of("a", Document.ofObject(Map.of("b", "str")))));

        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#StructMapWrapper")));

        var builder = SchemaConverter.createDocumentBuilder(schema);
        source.deserializeInto(builder);

        var result = builder.build();

        assertThat(result.asObject(), equalTo(source.asObject()));
        assertThat(result.schema(), equalTo(schema));

        var codec = JsonCodec.builder().useJsonName(true).build();

        assertThat(codec.serializeToString(result), equalTo("{\"map\":{\"a\":{\"B\":\"str\"}}}"));
    }

    @Test
    public void throwsIfNotStructureOrUnion() {
        var schema = Schema.createString(ShapeId.from("foo#Bar"));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SchemaGuidedDocumentBuilder(schema, schema.id()));
    }

    @Test
    public void usesStructDocumentForSetMember() {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleStruct")));

        // Make a SimpleStruct StructDocument to see if setValue for "foo" uses it as-is.
        var struct = StructDocument.of(schema, Document.ofObject(Map.of("foo", "bar")));

        var builder = SchemaConverter.createDocumentBuilder(schema);
        builder.setMemberValue(schema.member("foo"), "hi");
        builder.setMemberValue(schema.member("baz"), struct);

        var result = builder.build();

        assertThat(result.getMemberValue(schema.member("foo")), equalTo("hi"));
        assertThat(result.getMemberValue(schema.member("baz")), equalTo(Map.of("foo", "bar")));
        assertThat(StructDocumentTest.getDocumentSchema(result.getMember("foo")), equalTo(schema.member("foo")));
        assertThat(StructDocumentTest.getDocumentSchema(result.getMember("baz")), equalTo(schema.member("baz")));
    }
}
