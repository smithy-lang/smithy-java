/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public class SchemaConverterTest {

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

                list SimpleList {
                    member: String
                }

                map SimpleMap {
                    key: MyString
                    value: MyDocument
                }

                structure SimpleStruct {
                    foo: String
                }

                union SimpleUnion {
                    foo: String
                }

                list RecursiveList {
                    member: RecursiveStructure
                }

                map RecursiveMap {
                    key: String
                    value: RecursiveStructure
                }

                structure RecursiveStructure {
                    foo: RecursiveStructure
                }
                """)
            .assemble()
            .unwrap();
    }

    @MethodSource("convertsSimpleSchemasSource")
    @ParameterizedTest
    public void convertsSimpleSchemas(ShapeType type, String name) {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#" + name)));

        assertThat(schema.type(), is(type));
        assertThat(schema.id().getName(), equalTo(name));
    }

    static List<Arguments> convertsSimpleSchemasSource() {
        return List.of(
            Arguments.of(ShapeType.DOCUMENT, "MyDocument"),
            Arguments.of(ShapeType.STRING, "MyString"),
            Arguments.of(ShapeType.BOOLEAN, "MyBoolean"),
            Arguments.of(ShapeType.TIMESTAMP, "MyTimestamp"),
            Arguments.of(ShapeType.BLOB, "MyBlob"),
            Arguments.of(ShapeType.BYTE, "MyByte"),
            Arguments.of(ShapeType.SHORT, "MyShort"),
            Arguments.of(ShapeType.INTEGER, "MyInteger"),
            Arguments.of(ShapeType.LONG, "MyLong"),
            Arguments.of(ShapeType.FLOAT, "MyFloat"),
            Arguments.of(ShapeType.DOUBLE, "MyDouble"),
            Arguments.of(ShapeType.BIG_INTEGER, "MyBigInteger"),
            Arguments.of(ShapeType.BIG_DECIMAL, "MyBigDecimal"),
            Arguments.of(ShapeType.ENUM, "MyEnum"),
            Arguments.of(ShapeType.INT_ENUM, "MyIntEnum")
        );
    }

    @MethodSource("convertsAggregateSchemasSource")
    @ParameterizedTest
    public void convertsAggregateSchemas(ShapeType type, String name) {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#" + name)));

        assertThat(schema.type(), is(type));
        assertThat(schema.id().getName(), equalTo(name));
        assertThat(schema.members(), not(empty()));
    }

    static List<Arguments> convertsAggregateSchemasSource() {
        return List.of(
            Arguments.of(ShapeType.LIST, "SimpleList"),
            Arguments.of(ShapeType.MAP, "SimpleMap"),
            Arguments.of(ShapeType.STRUCTURE, "SimpleStruct"),
            Arguments.of(ShapeType.UNION, "SimpleUnion"),
            Arguments.of(ShapeType.LIST, "RecursiveList"),
            Arguments.of(ShapeType.MAP, "RecursiveMap"),
            Arguments.of(ShapeType.STRUCTURE, "RecursiveStructure")
        );
    }
}
