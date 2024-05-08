/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

public class CodegenUtilsTest {
    @Test
    void requiredMemberOrdering() {
        Shape stringShape = StringShape.builder().id("foo.bar#Baz").build();
        MemberShape requiredA = MemberShape.builder()
            .id("foo.baz#Container$requiredA")
            .addTrait(new RequiredTrait())
            .target(stringShape)
            .build();
        MemberShape requiredB = MemberShape.builder()
            .id("foo.baz#Container$requiredB")
            .addTrait(new RequiredTrait())
            .target(stringShape)
            .build();
        MemberShape requiredWithDefault = MemberShape.builder()
            .id("foo.baz#Container$requiredDefault")
            .addTrait(new RequiredTrait())
            .addTrait(new DefaultTrait(StringNode.from("default")))
            .target(stringShape)
            .build();
        MemberShape defaultMember = MemberShape.builder()
            .id("foo.baz#Container$default")
            .addTrait(new RequiredTrait())
            .addTrait(new DefaultTrait(StringNode.from("default")))
            .target(stringShape)
            .build();
        MemberShape optionalA = MemberShape.builder()
            .id("foo.baz#Container$optionalA")
            .target(stringShape)
            .build();
        MemberShape optionalB = MemberShape.builder()
            .id("foo.baz#Container$optionalB")
            .target(stringShape)
            .build();
        var exampleShape = StructureShape.builder()
            .id("foo.baz#Container")
            .addMember(optionalA)
            .addMember(defaultMember)
            .addMember(requiredWithDefault)
            .addMember(requiredB)
            .addMember(requiredA)
            .build();
        var result = CodegenUtils.getSortedMembers(exampleShape);
        assertEquals(result, List.of(requiredB, requiredA, optionalA, defaultMember, requiredWithDefault));
    }
}
