/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.ai.PromptTemplateDefinition;
import software.amazon.smithy.ai.PromptsTrait;
import software.amazon.smithy.java.mcp.model.PromptArgument;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Handles loading and parsing of prompts from Smithy models.
 */
@SmithyUnstableApi
public final class PromptLoader {

    public static final String TOOL_PREFERENCE_PREFIX = ".Tool preference: ";

    /**
     * Loads prompts from the provided Smithy models.
     *
     * @param models List of Smithy models to extract prompts from
     * @return Map of prompt names to PromptInfo objects
     */
    public static Map<String, PromptInfo> loadPrompts(List<Model> models) {
        Map<String, PromptInfo> promptInfos = new LinkedHashMap<>();

        for (Model model : models) {
            Set<Shape> promptShapes = model.getShapesWithTrait(PromptsTrait.ID);
            for (Shape prompt : promptShapes) {

                Map<String, PromptTemplateDefinition> promptDefinitions =
                        prompt.expectTrait(PromptsTrait.class).getValues();
                for (Map.Entry<String, PromptTemplateDefinition> entry : promptDefinitions.entrySet()) {
                    var promptName = entry.getKey().toLowerCase();
                    var promptTemplateDefinition = entry.getValue();
                    var templateString = promptTemplateDefinition.getTemplate();

                    promptInfos.put(
                            promptName,
                            PromptInfo
                                    .builder()
                                    .name(promptName)
                                    .description(promptTemplateDefinition.getDescription())
                                    .template(
                                            promptTemplateDefinition.getPreferWhen().isPresent()
                                                    ? templateString + TOOL_PREFERENCE_PREFIX
                                                            + promptTemplateDefinition.getPreferWhen().get()
                                                    : templateString)
                                    .arguments(promptTemplateDefinition.getArguments().isPresent()
                                            ? convertArgumentShapeToPromptArgument(model,
                                                    promptTemplateDefinition.getArguments().get())
                                            : List.of())
                                    .build());
                }
            }
        }

        return promptInfos;
    }

    /**
     * Converts a Smithy structure shape to a list of PromptArgument objects.
     *
     * @param model The Smithy model containing the shape
     * @param argumentShapeId The ShapeId of the structure to convert
     * @return List of PromptArgument objects representing the structure members
     */
    public static List<PromptArgument> convertArgumentShapeToPromptArgument(Model model, ShapeId argumentShapeId) {
        StructureShape argument = model.expectShape(argumentShapeId, StructureShape.class);
        List<PromptArgument> promptArguments = new ArrayList<>();

        for (var member : argument.getAllMembers().entrySet()) {
            String memberName = member.getKey();
            var memberShape = member.getValue();

            // Get description from documentation trait, use empty string if not present
            String description = "";
            var documentationTrait = memberShape.getTrait(DocumentationTrait.class);
            if (documentationTrait.isPresent()) {
                description = documentationTrait.get().getValue();
            }

            // Check if member is required
            boolean isRequired = memberShape.hasTrait(RequiredTrait.class);

            // Build the PromptArgument
            PromptArgument promptArgument = PromptArgument.builder()
                    .name(memberName)
                    .description(description)
                    .required(isRequired)
                    .build();

            promptArguments.add(promptArgument);
        }

        return promptArguments;
    }
}
