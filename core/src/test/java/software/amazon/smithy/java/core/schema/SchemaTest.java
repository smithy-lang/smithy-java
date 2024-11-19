/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.SensitiveTrait;

public class SchemaTest {
    @Test
    public void enumWorksWithEnumSchema() {
        var schema = Schema.createEnum(ShapeId.from("smithy.example#Foo"), Set.of("a", "b"));

        assertThat(schema.stringEnumValues(), containsInAnyOrder("a", "b"));
    }

    @Test
    public void intEnumWorksWithIntEnumSchema() {
        var schema = Schema.createIntEnum(ShapeId.from("smithy.example#Foo"), Set.of(1, 2, 3));

        assertThat(schema.intEnumValues(), containsInAnyOrder(1, 2, 3));
    }

    static Schema directlyRecursiveSchema;
    static Schema mutuallyRecursiveA;
    static Schema mutuallyRecursiveB;

    @Test
    public void directlyRecursiveSchema() {
        var builder = Schema.structureBuilder(ShapeId.from("smithy.example#Foo"), new SensitiveTrait());
        directlyRecursiveSchema = builder
            .putMember("foo", builder, new DocumentationTrait("Hi"))
            .build();

        assertThat(directlyRecursiveSchema.members(), hasSize(1));
        assertThat(directlyRecursiveSchema.member("foo").memberTarget(), is(directlyRecursiveSchema));
        assertThat(directlyRecursiveSchema.member("foo").hasTrait(TraitKey.get(SensitiveTrait.class)), is(true));
        assertThat(directlyRecursiveSchema.member("foo").type(), is(ShapeType.STRUCTURE));
    }

    @Test
    public void supportsMutualRecursion() {
        var aBuilder = Schema.structureBuilder(ShapeId.from("smithy.example#A"));
        var bBuilder = Schema.structureBuilder(ShapeId.from("smithy.example#B"));

        mutuallyRecursiveA = aBuilder.putMember("b", bBuilder).build();
        mutuallyRecursiveB = bBuilder.putMember("a", mutuallyRecursiveA).build();

        assertThat(mutuallyRecursiveA.members(), hasSize(1));
        assertThat(mutuallyRecursiveB.members(), hasSize(1));
        assertThat(mutuallyRecursiveA.member("b").memberTarget(), equalTo(mutuallyRecursiveB));
        assertThat(mutuallyRecursiveB.member("a").memberTarget(), equalTo(mutuallyRecursiveA));
    }

    @Test
    public void supportsSupercedingTargetTraits() {
        var intSchema = Schema.createInteger(
            ShapeId.from("smithy.example#DocumentedInt"),
            new DocumentationTrait("Target")
        );
        var structWithMember = Schema.structureBuilder(ShapeId.from("smithy.example#StructWithMember"))
            .putMember("member", intSchema, new DocumentationTrait("Member"))
            .build();

        assertThat(
            structWithMember.member("member").expectTrait(TraitKey.get(DocumentationTrait.class)).getValue(),
            equalTo("Member")
        );
    }
}
