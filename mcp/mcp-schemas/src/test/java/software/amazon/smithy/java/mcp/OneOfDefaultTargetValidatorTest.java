/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

class OneOfDefaultTargetValidatorTest {
    static Stream<Arguments> defaults() {
        return Stream.of(
                Arguments.of("", false, 0),
                Arguments.of("", true, 0),
                Arguments.of("defaultTarget: Base", false, 0),
                Arguments.of("defaultTarget: Child", false, 0),
                Arguments.of("defaultTarget: Other", false, 1),
                Arguments.of("defaultTarget: Base", true, 1));
    }

    @ParameterizedTest
    @MethodSource("defaults")
    void validatesOnlyConfiguredDefaults(String defaultTarget, boolean duplicateTarget, int expectedErrors) {
        var result = Model.assembler()
                .discoverModels()
                .addUnparsedModel("defaults.smithy", """
                        $version: "2"
                        namespace test
                        use smithy.mcp#oneOf
                        structure Base {}
                        structure Child {}
                        structure Other {}
                        @oneOf(
                            discriminator: "__type"
                            members: [
                                {name: "base", target: Base}
                                {name: "child", target: %s}
                            ]
                            %s
                        )
                        document Polymorphic
                        """.formatted(duplicateTarget ? "Base" : "Child", defaultTarget))
                .assemble();

        var errors = result.getValidationEvents()
                .stream()
                .filter(event -> event.getId().equals("OneOfDefaultTarget"))
                .toList();
        assertEquals(expectedErrors, errors.size(), result.getValidationEvents().toString());
        if (expectedErrors == 0) {
            var trait = result.unwrap().expectShape(ShapeId.from("test#Polymorphic")).expectTrait(OneOfTrait.class);
            assertEquals(defaultTarget.isEmpty(), trait.getDefaultTarget().isEmpty());
            assertEquals(trait.getDefaultTarget(), OneOfTrait.fromNode(trait.toNode()).getDefaultTarget());
        } else {
            assertTrue(errors.getFirst().getMessage().contains("exactly one member"));
        }
    }
}
