/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;

class CodegenUtilsTest {

    @Test
    void sortedMemberIndexMatchesRuntimeMemberIndex() {
        var model = Model.assembler()
                .addUnparsedModel("members.smithy", """
                        $version: "2"
                        namespace smithy.example

                        structure Scrambled {
                            optionalString: String

                            @required
                            requiredString: String

                            @required
                            @default("x")
                            requiredWithDefault: String

                            @required
                            @default(null)
                            requiredWithNullDefault: PrimitiveInteger

                            optionalInteger: Integer

                            @default(5)
                            optionalWithDefault: Integer

                            @required
                            requiredInteger: Integer

                            @default(0)
                            optionalPrimitive: PrimitiveInteger
                        }
                        """)
                .assemble()
                .unwrap();
        var shape = model.expectShape(ShapeId.from("smithy.example#Scrambled"));
        var targets = Map.of(
                "smithy.api#String",
                PreludeSchemas.STRING,
                "smithy.api#Integer",
                PreludeSchemas.INTEGER,
                "smithy.api#PrimitiveInteger",
                PreludeSchemas.PRIMITIVE_INTEGER);

        var builder = Schema.structureBuilder(shape.getId());
        for (var member : shape.members()) {
            builder.putMember(
                    member.getMemberName(),
                    targets.get(member.getTarget().toString()),
                    member.getAllTraits().values().toArray(Trait[]::new));
        }
        var runtime = builder.build();

        var sorted = CodegenUtils.getSortedMembers(shape);
        assertThat(sorted).hasSize(runtime.members().size());
        assertThat(sorted).extracting(m -> m.getMemberName())
                .containsExactly("requiredString",
                        "requiredWithNullDefault",
                        "requiredInteger",
                        "optionalString",
                        "requiredWithDefault",
                        "optionalInteger",
                        "optionalWithDefault",
                        "optionalPrimitive");
        for (int i = 0; i < sorted.size(); i++) {
            var member = sorted.get(i);
            assertThat(runtime.member(i).memberName()).isEqualTo(member.getMemberName());
            assertThat(CodegenUtils.toMemberSchemaName(shape, member))
                    .isEqualTo("$" + runtime.member(member.getMemberName()).memberIndex());
        }
    }
}
