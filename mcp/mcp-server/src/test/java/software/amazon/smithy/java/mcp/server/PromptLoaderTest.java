/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.mcp.model.PromptArgument;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

public class PromptLoaderTest {

    @Test
    public void testLoadPromptsWithEmptyModels() {
        List<Model> emptyModels = List.of(Model.builder().build());

        Map<String, PromptInfo> result =
                PromptLoader.loadPrompts(emptyModels);

        assertTrue(result.isEmpty());
    }

    @Test
    public void TestModelWithPrompts() {
        List<Model> models = List.of(Model.assembler()
                .addImport(getClass().getResource("/prompts/basic-prompt.smithy"))
                .discoverModels()
                .assemble()
                .unwrap());

        Map<String, PromptInfo> prompts = PromptLoader.loadPrompts(models);

        assertEquals(1, prompts.size());
        assertEquals("Example Prompt", prompts.get("test_prompt").getDescription());
        assertEquals("test_prompt", prompts.get("test_prompt").getName());
        assertEquals("TestTemplate {{requiredDocumentedField}}" + PromptLoader.TOOL_PREFERENCE_PREFIX + "TestString",
                prompts.get("test_prompt").getTemplate());
    }

    @Test
    public void testConvertArgumentShapeToPromptArgument() {
        // Create a simple structure shape for testing
        StructureShape.Builder structureBuilder = StructureShape.builder()
                .id("com.example#TestArgs");

        // Add a required member
        structureBuilder.addMember("requiredField",
                ShapeId.from("smithy.api#String"),
                memberBuilder -> memberBuilder.addTrait(new RequiredTrait()));

        // Add an optional member with documentation
        structureBuilder.addMember("optionalField",
                ShapeId.from("smithy.api#String"),
                memberBuilder -> memberBuilder.addTrait(
                        new DocumentationTrait("An optional field")));

        StructureShape structure = structureBuilder.build();

        Model model = Model.builder()
                .addShape(structure)
                .addShape(StringShape.builder()
                        .id("smithy.api#String")
                        .build())
                .build();

        List<PromptArgument> result = PromptLoader.convertArgumentShapeToPromptArgument(
                model,
                ShapeId.from("com.example#TestArgs"));

        assertEquals(2, result.size());

        // Find the required field
        PromptArgument requiredArg = result.stream()
                .filter(arg -> "requiredField".equals(arg.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(requiredArg.isRequired());

        // Find the optional field
        PromptArgument optionalArg = result.stream()
                .filter(arg -> "optionalField".equals(arg.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(!optionalArg.isRequired());
        assertEquals("An optional field", optionalArg.getDescription());
    }
}
