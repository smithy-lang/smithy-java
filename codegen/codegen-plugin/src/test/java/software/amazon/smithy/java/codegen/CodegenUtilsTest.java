/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.traits.RequiredTrait;

public class CodegenUtilsTest {
    @Test
    void sortsMembersByWireCategory() {
        var stringShape = StringShape.builder().id("foo.bar#Str").build();
        var intShape = IntegerShape.builder().id("foo.bar#Int").build();
        var boolShape = BooleanShape.builder().id("foo.bar#Bool").build();
        var floatShape = FloatShape.builder().id("foo.bar#Float").build();
        var doubleShape = DoubleShape.builder().id("foo.bar#Double").build();
        var timestampShape = TimestampShape.builder().id("foo.bar#Time").build();
        var listShape = ListShape.builder()
                .id("foo.bar#StrList")
                .member(stringShape.getId())
                .build();

        MemberShape stringMember = MemberShape.builder()
                .id("foo.baz#Container$stringMember")
                .addTrait(new RequiredTrait())
                .target(stringShape)
                .build();
        MemberShape listMember = MemberShape.builder()
                .id("foo.baz#Container$listMember")
                .target(listShape)
                .build();
        MemberShape intMember = MemberShape.builder()
                .id("foo.baz#Container$intMember")
                .target(intShape)
                .build();
        MemberShape boolMember = MemberShape.builder()
                .id("foo.baz#Container$boolMember")
                .addTrait(new RequiredTrait())
                .target(boolShape)
                .build();
        MemberShape floatMember = MemberShape.builder()
                .id("foo.baz#Container$floatMember")
                .target(floatShape)
                .build();
        MemberShape doubleMember = MemberShape.builder()
                .id("foo.baz#Container$doubleMember")
                .target(doubleShape)
                .build();
        MemberShape timeMember = MemberShape.builder()
                .id("foo.baz#Container$timeMember")
                .target(timestampShape)
                .build();

        var exampleShape = StructureShape.builder()
                .id("foo.baz#Container")
                .addMember(stringMember)
                .addMember(doubleMember)
                .addMember(listMember)
                .addMember(intMember)
                .addMember(floatMember)
                .addMember(timeMember)
                .addMember(boolMember)
                .build();
        var model = Model.builder()
                .addShapes(
                        stringShape,
                        intShape,
                        boolShape,
                        floatShape,
                        doubleShape,
                        timestampShape,
                        listShape,
                        exampleShape)
                .build();

        var result = CodegenUtils.getSortedMembers(model, exampleShape);

        // Varint scalars first, then four-byte, then eight-byte, then length-delimited values, and the
        // sort is stable within each category.
        assertEquals(
                List.of(intMember, boolMember, floatMember, doubleMember, timeMember, stringMember, listMember),
                result);
    }
}
