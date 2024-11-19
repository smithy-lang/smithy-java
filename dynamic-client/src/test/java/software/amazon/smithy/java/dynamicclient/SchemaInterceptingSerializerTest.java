/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public class SchemaInterceptingSerializerTest {

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
                    foo = "a"
                    bar = "b"
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
                """)
            .assemble()
            .unwrap();
    }

    @MethodSource("interceptsWithSchemaProvider")
    @ParameterizedTest
    public void interceptsWithSchema(String name, Document value) {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#" + name)));
        var wrapped = new WrappedDocument(ShapeId.from("smithy.example#S"), schema, value);

        Schema[] written = new Schema[1];

        wrapped.serialize(new InterceptingSerializer() {
            @Override
            protected ShapeSerializer before(Schema schema) {
                written[0] = schema;
                return ShapeSerializer.nullSerializer();
            }
        });

        assertThat(written[0], equalTo(schema));
    }

    static List<Arguments> interceptsWithSchemaProvider() {
        var bytes = ByteBuffer.wrap("hi".getBytes(StandardCharsets.UTF_8));
        return List.of(
            Arguments.arguments("MyBoolean", Document.createBoolean(true)),
            Arguments.arguments("MyString", Document.createString("hi")),
            Arguments.arguments("MyByte", Document.createByte((byte) 1)),
            Arguments.arguments("MyShort", Document.createShort((short) 1)),
            Arguments.arguments("MyInteger", Document.createInteger(1)),
            Arguments.arguments("MyLong", Document.createLong(1L)),
            Arguments.arguments("MyFloat", Document.createFloat(1f)),
            Arguments.arguments("MyDouble", Document.createDouble(1d)),
            Arguments.arguments("MyBigDecimal", Document.createBigDecimal(BigDecimal.ONE)),
            Arguments.arguments("MyBigInteger", Document.createBigInteger(BigInteger.ONE)),
            Arguments.arguments("MyTimestamp", Document.createTimestamp(Instant.EPOCH)),
            Arguments.arguments("MyBlob", Document.createBlob(bytes)),
            Arguments.arguments("MyDocument", Document.createString("hi"))
        );
    }

    @Test
    public void interceptsLists() {
        var converter = new SchemaConverter(model);
        var listSchema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleList")));
        var wrapped = new WrappedDocument(
            ShapeId.from("smithy.example#S"),
            listSchema,
            Document.createList(Arrays.asList(Document.createString("a"), null))
        );

        wrapped.serialize(new SpecificShapeSerializer() {
            @Override
            public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
                assertThat(schema, equalTo(listSchema));

                consumer.accept(listState, new SpecificShapeSerializer() {
                    @Override
                    public void writeString(Schema schema, String value) {
                        assertThat(value, equalTo("a"));
                    }

                    @Override
                    public void writeNull(Schema schema) {
                        assertThat(schema, equalTo(listSchema.listMember()));
                    }
                });
            }
        });
    }

    @Test
    public void interceptMaps() {
        var converter = new SchemaConverter(model);
        var mapSchema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleMap")));
        var wrapped = new WrappedDocument(
            ShapeId.from("smithy.example#S"),
            mapSchema,
            Document.createFromObject(Map.of("foo", "bar"))
        );

        wrapped.serialize(new SpecificShapeSerializer() {
            @Override
            public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
                assertThat(schema, equalTo(mapSchema));

                consumer.accept(mapState, new MapSerializer() {
                    @Override
                    public <T> void writeEntry(
                        Schema keySchema,
                        String key,
                        T state,
                        BiConsumer<T, ShapeSerializer> valueSerializer
                    ) {
                        assertThat(keySchema, equalTo(mapSchema.mapKeyMember()));
                        assertThat(key, equalTo("foo"));

                        valueSerializer.accept(state, new SpecificShapeSerializer() {
                            @Override
                            public void writeString(Schema schema, String value) {
                                assertThat(schema, equalTo(mapSchema.mapValueMember()));
                                assertThat(value, equalTo("bar"));
                            }
                        });
                    }
                });
            }
        });
    }

    @Test
    public void mapsCanWriteDocuments() {
        var converter = new SchemaConverter(model);
        var mapSchema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#DocumentMap")));
        var wrapped = new WrappedDocument(
            ShapeId.from("smithy.example#S"),
            mapSchema,
            Document.createFromObject(Map.of("foo", "bar"))
        );

        wrapped.serialize(new SpecificShapeSerializer() {
            @Override
            public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
                assertThat(schema, equalTo(mapSchema));

                consumer.accept(mapState, new MapSerializer() {
                    @Override
                    public <T> void writeEntry(
                        Schema keySchema,
                        String key,
                        T state,
                        BiConsumer<T, ShapeSerializer> valueSerializer
                    ) {
                        assertThat(keySchema, equalTo(mapSchema.mapKeyMember()));
                        assertThat(key, equalTo("foo"));

                        valueSerializer.accept(state, new SpecificShapeSerializer() {
                            @Override
                            public void writeDocument(Schema schema, Document value) {
                                assertThat(schema, equalTo(mapSchema.mapValueMember()));
                                assertThat(value.asString(), equalTo("bar"));
                            }
                        });
                    }
                });
            }
        });
    }

    @Test
    public void interceptsStructs() {
        var converter = new SchemaConverter(model);
        var structSchema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleStruct")));
        var wrapped = new WrappedDocument(
            ShapeId.from("smithy.example#S"),
            structSchema,
            Document.createFromObject(
                Map.of(
                    "foo",
                    "bar",
                    "baz",
                    Document.createFromObject(Map.of("foo", "hi"))
                )
            )
        );

        wrapped.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeStruct(Schema schema, SerializableStruct struct) {
                assertThat(schema, equalTo(structSchema));

                struct.serializeMembers(new SpecificShapeSerializer() {
                    @Override
                    public void writeString(Schema schema, String value) {
                        assertThat(schema, equalTo(structSchema.member("foo")));
                        assertThat(value, equalTo("bar"));
                    }

                    @Override
                    public void writeStruct(Schema schema, SerializableStruct struct) {
                        assertThat(schema, equalTo(structSchema.member("baz")));

                        struct.serializeMembers(new SpecificShapeSerializer() {
                            @Override
                            public void writeString(Schema schema, String value) {
                                assertThat(schema, equalTo(structSchema.member("foo")));
                                assertThat(value, equalTo("hi"));
                            }
                        });
                    }
                });
            }
        });
    }
}
