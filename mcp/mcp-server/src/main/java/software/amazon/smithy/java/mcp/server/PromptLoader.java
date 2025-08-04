/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.ai.PromptTemplateDefinition;
import software.amazon.smithy.ai.PromptsTrait;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.mcp.model.PromptArgument;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.utils.SmithyUnstableApi;
import software.amazon.smithy.utils.StringUtils;

/**
 * Handles loading and parsing of prompts from Smithy models.
 */
@SmithyUnstableApi
final class PromptLoader {

    private static final TraitKey<PromptsTrait> PROMPTS_TRAIT_KEY = TraitKey.get(PromptsTrait.class);

    public static final String TOOL_PREFERENCE_PREFIX = ".Tool preference: ";

    /**
     * Loads prompts from the provided Smithy models.
     *
     * @return Map of prompt names to PromptInfo objects
     */
    public static Map<String, Prompt> loadPrompts(Collection<Service> services) {
        Map<String, Prompt> prompts = new LinkedHashMap<>();

        for (var service : services) {
            Map<String, PromptTemplateDefinition> promptDefinitions = new HashMap<>();
            var servicePromptTrait = service.schema().getTrait(PROMPTS_TRAIT_KEY);
            if (servicePromptTrait != null) {
                promptDefinitions.putAll(servicePromptTrait.getValues());
            }
            service.getAllOperations().forEach(operation -> {
                var operationPromptsTrait = operation.getApiOperation().schema().getTrait(PROMPTS_TRAIT_KEY);
                if (operationPromptsTrait != null) {
                    promptDefinitions.putAll(operationPromptsTrait.getValues());
                }

            });
            for (Map.Entry<String, PromptTemplateDefinition> entry : promptDefinitions.entrySet()) {
                var promptName = entry.getKey().toLowerCase();
                var promptTemplateDefinition = entry.getValue();
                var templateString = promptTemplateDefinition.getTemplate();

                var finalTemplateString = promptTemplateDefinition.getPreferWhen().isPresent()
                        ? templateString + TOOL_PREFERENCE_PREFIX
                                + promptTemplateDefinition.getPreferWhen().get()
                        : templateString;

                var schemaIndex = SchemaIndex.compose(service.schemaIndex(), SchemaIndex.getCombinedSchemaIndex());

                var promptInfo = PromptInfo
                        .builder()
                        .name(promptName)
                        .title(StringUtils.capitalize(promptName))
                        .description(promptTemplateDefinition.getDescription())
                        .arguments(promptTemplateDefinition.getArguments().isPresent()
                                ? convertArgumentShapeToPromptArgument(
                                        schemaIndex.getSchema(promptTemplateDefinition.getArguments().get()))
                                : List.of())
                        .build();

                prompts.put(
                        promptName,
                        new Prompt(promptInfo, finalTemplateString));
            }
        }
        return prompts;
    }

    /**
     * Converts a Smithy structure shape to a list of PromptArgument objects.
     *
     * @param argument The ShapeId of the structure to convert
     * @return List of PromptArgument objects representing the structure members
     */
    public static List<PromptArgument> convertArgumentShapeToPromptArgument(Schema argument) {
        List<PromptArgument> promptArguments = new ArrayList<>();

        for (var member : argument.members()) {
            String memberName = member.memberName();

            // Get description from documentation trait, use empty string if not present
            String description = "";
            var documentationTrait = member.getTrait(TraitKey.DOCUMENTATION_TRAIT);
            if (documentationTrait != null) {
                description = documentationTrait.getValue();
            }

            // Check if member is required
            boolean isRequired = member.getTrait(TraitKey.REQUIRED_TRAIT) != null;

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
