/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.dynamicclient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.core.serde.document.DocumentDeserializer;
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

                structure SimpleUnion {
                    foo: String
                    baz: SimpleStruct
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

        var builder = new SchemaGuidedDocumentBuilder(ShapeId.from("smithy.example#Foo"), schema);
        source.deserializeInto(builder);

        var result = builder.build();

        assertThat(result.asObject(), equalTo(source.asObject()));
        assertThat(result.schema(), equalTo(schema));
    }

    @Test
    public void deserializesMember() {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#SimpleStruct")));
        var document = Document.createFromObject(Map.of("foo", "bar"));

        var builder = new SchemaGuidedDocumentBuilder(ShapeId.from("smithy.example#Foo"), schema);
        builder.deserializeMember(new DocumentDeserializer(document), schema.member("baz"));

        var result = builder.build();

        assertThat(result.asObject(), equalTo(document.asObject()));
        assertThat(result.schema(), equalTo(schema));
    }

    static List<Arguments> deserializesShapesProvider() {
        return List.of(
            Arguments.of("MyDocument", Document.createFromObject(Map.of("a", "b"))),
            Arguments.of("MyString", Document.createString("hi")),
            Arguments.of("MyBoolean", Document.createBoolean(true)),
            Arguments.of("MyTimestamp", Document.createTimestamp(Instant.EPOCH)),
            Arguments.of("MyBlob", Document.createBlob("foo".getBytes(StandardCharsets.UTF_8))),
            Arguments.of("MyByte", Document.createByte((byte) 1)),
            Arguments.of("MyShort", Document.createShort((short) 1)),
            Arguments.of("MyInteger", Document.createInteger(1)),
            Arguments.of("MyLong", Document.createLong(1L)),
            Arguments.of("MyFloat", Document.createFloat(1f)),
            Arguments.of("MyDouble", Document.createDouble(1d)),
            Arguments.of("MyBigInteger", Document.createBigInteger(BigInteger.ONE)),
            Arguments.of("MyBigDecimal", Document.createBigDecimal(BigDecimal.ONE)),
            Arguments.of("MyIntEnum", Document.createInteger(1)),
            Arguments.of("MyEnum", Document.createString("foo")),
            Arguments.of("SimpleList", Document.createFromObject(List.of("a", "b"))),
            Arguments.of("SimpleMap", Document.createFromObject(Map.of("foo", "bar"))),
            Arguments.of(
                "SimpleStruct",
                Document.createFromObject(Map.of("foo", "bar", "baz", Document.createFromObject(Map.of("foo", "hi"))))
            ),
            Arguments.of("SimpleUnion", Document.createFromObject(Map.of("foo", "bar")))
        );
    }
}
