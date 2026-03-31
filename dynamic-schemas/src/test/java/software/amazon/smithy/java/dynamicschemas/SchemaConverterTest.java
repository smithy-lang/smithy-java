/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

                        structure NestedStruct {
                            foo: SimpleStruct
                            bar: SimpleStruct
                        }

                        structure ParentStruct {
                            foo: NestedStruct
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

                        structure MutualA {
                            toB: MutualB
                            name: String
                        }

                        structure MutualB {
                            toA: MutualA
                            count: Integer
                        }

                        structure ContainsRecursive {
                            recursive: RecursiveStructure
                            label: String
                            size: Integer
                        }

                        structure RecursiveWithPrimitives {
                            self: RecursiveWithPrimitives
                            name: String
                            age: Integer
                            tags: SimpleList
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
                Arguments.of(ShapeType.INT_ENUM, "MyIntEnum"));
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
                Arguments.of(ShapeType.STRUCTURE, "NestedStruct"),
                Arguments.of(ShapeType.STRUCTURE, "ParentStruct"),
                Arguments.of(ShapeType.UNION, "SimpleUnion"),
                Arguments.of(ShapeType.LIST, "RecursiveList"),
                Arguments.of(ShapeType.MAP, "RecursiveMap"),
                Arguments.of(ShapeType.STRUCTURE, "RecursiveStructure"),
                Arguments.of(ShapeType.STRUCTURE, "MutualA"),
                Arguments.of(ShapeType.STRUCTURE, "MutualB"),
                Arguments.of(ShapeType.STRUCTURE, "ContainsRecursive"),
                Arguments.of(ShapeType.STRUCTURE, "RecursiveWithPrimitives"));
    }

    @MethodSource("detectsRecursiveSchemasSource")
    @ParameterizedTest
    public void detectsRecursiveSchemas(String name) {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#" + name)));

        // Detecting that SchemaConverter handled this schema as recursive by checking classname is
        // hacky, but public API doesn't expose a cleaner way, and probably shouldn't.
        assertThat(schema.getClass().getSimpleName(), is("DeferredRootSchema"));
    }

    static List<Arguments> detectsRecursiveSchemasSource() {
        return List.of(
                Arguments.of("RecursiveStructure"),
                Arguments.of("RecursiveWithPrimitives"));
    }

    @MethodSource("detectsNonRecursiveSchemasSource")
    @ParameterizedTest
    public void detectsNonRecursiveSchemas(String name) {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#" + name)));

        // Detecting that SchemaConverter handled this schema as non-recursive by checking classname is
        // hacky, but public API doesn't expose a cleaner way, and probably shouldn't.
        assertThat(schema.getClass().getSimpleName(), not("DeferredRootSchema"));
    }

    static List<Arguments> detectsNonRecursiveSchemasSource() {
        return List.of(
                Arguments.of("MyDocument"),
                Arguments.of("MyString"),
                Arguments.of("MyBoolean"),
                Arguments.of("MyTimestamp"),
                Arguments.of("MyBlob"),
                Arguments.of("MyByte"),
                Arguments.of("MyShort"),
                Arguments.of("MyInteger"),
                Arguments.of("MyLong"),
                Arguments.of("MyFloat"),
                Arguments.of("MyDouble"),
                Arguments.of("MyBigInteger"),
                Arguments.of("MyBigDecimal"),
                Arguments.of("MyEnum"),
                Arguments.of("MyIntEnum"),
                Arguments.of("SimpleList"),
                Arguments.of("SimpleMap"),
                Arguments.of("SimpleStruct"),
                Arguments.of("NestedStruct"),
                Arguments.of("RecursiveList"),
                Arguments.of("RecursiveMap"),
                Arguments.of("ParentStruct"),
                Arguments.of("SimpleUnion"),
                Arguments.of("ContainsRecursive"),
                Arguments.of("MutualA"),
                Arguments.of("MutualB"));
    }

    @Test
    public void mutuallyRecursiveStructures() {
        var converter = new SchemaConverter(model);
        var schemaA = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#MutualA")));

        assertThat(schemaA.type(), is(ShapeType.STRUCTURE));
        assertThat(schemaA.members().size(), is(2));

        // Check MutualA.toB — MutualB is the inner shape that references back, so it is DeferredRootSchema
        var toB = schemaA.member("toB");
        assertThat(toB.isMember(), is(true));
        assertThat(toB.memberTarget().type(), is(ShapeType.STRUCTURE));
        assertThat(toB.memberTarget().id().getName(), equalTo("MutualB"));
        assertThat(toB.memberTarget().getClass().getSimpleName(), is("DeferredRootSchema"));

        // Check MutualA.name
        var name = schemaA.member("name");
        assertThat(name.memberTarget().type(), is(ShapeType.STRING));

        // Navigate MutualA -> toB -> toA -> back to MutualA
        var backToA = toB.memberTarget().member("toA").memberTarget();
        assertThat(backToA.id().getName(), equalTo("MutualA"));
        assertThat(backToA.type(), is(ShapeType.STRUCTURE));
        assertThat(backToA.member("name").memberTarget().type(), is(ShapeType.STRING));

        // Navigate MutualA -> toB -> count
        var count = toB.memberTarget().member("count");
        assertThat(count.memberTarget().type(), is(ShapeType.INTEGER));
    }

    @Test
    public void structureContainingRecursiveStructure() {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(model.expectShape(ShapeId.from("smithy.example#ContainsRecursive")));

        assertThat(schema.getClass().getSimpleName(), not("DeferredRootSchema"));
        assertThat(schema.members().size(), is(3));

        // Check recursive member
        var recursive = schema.member("recursive").memberTarget();
        assertThat(recursive.type(), is(ShapeType.STRUCTURE));
        assertThat(recursive.id().getName(), equalTo("RecursiveStructure"));
        assertThat(recursive.getClass().getSimpleName(), is("DeferredRootSchema"));

        // Check primitive members
        assertThat(schema.member("label").memberTarget().type(), is(ShapeType.STRING));
        assertThat(schema.member("size").memberTarget().type(), is(ShapeType.INTEGER));

        // Navigate into the recursive structure
        var inner = schema.member("recursive").memberTarget().member("foo").memberTarget();
        assertThat(inner.id().getName(), equalTo("RecursiveStructure"));
    }

    @Test
    public void recursiveStructureWithPrimitiveMembers() {
        var converter = new SchemaConverter(model);
        var schema = converter.getSchema(
                model.expectShape(ShapeId.from("smithy.example#RecursiveWithPrimitives")));

        assertThat(schema.getClass().getSimpleName(), is("DeferredRootSchema"));
        assertThat(schema.members().size(), is(4));

        // Check self-referencing member
        var self = schema.member("self").memberTarget();
        assertThat(self.id().getName(), equalTo("RecursiveWithPrimitives"));
        assertThat(self.type(), is(ShapeType.STRUCTURE));

        // Check primitive members
        assertThat(schema.member("name").memberTarget().type(), is(ShapeType.STRING));
        assertThat(schema.member("age").memberTarget().type(), is(ShapeType.INTEGER));

        // Check list member
        var tags = schema.member("tags").memberTarget();
        assertThat(tags.type(), is(ShapeType.LIST));
        assertThat(tags.id().getName(), equalTo("SimpleList"));
        assertThat(tags.listMember().memberTarget().type(), is(ShapeType.STRING));
    }
}
