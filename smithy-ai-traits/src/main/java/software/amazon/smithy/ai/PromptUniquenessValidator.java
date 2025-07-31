/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.validation.AbstractValidator;
import software.amazon.smithy.model.validation.ValidationEvent;

/**
 * Validates that prompt names are unique within each service when compared case-insensitively.
 * This validator checks both service-level and operation-level prompts to ensure no conflicts.
 * 
 * For example, "search_users" and "Search_Users" would be considered conflicting prompt names
 * because they are identical when compared case-insensitively.
 */
public final class PromptUniquenessValidator extends AbstractValidator {

    @Override
    public List<ValidationEvent> validate(Model model) {
        List<ValidationEvent> events = new ArrayList<>();

        for (ServiceShape service : model.getServiceShapes()) {
            validatePrompts(service, model, events);
        }

        return events;
    }

    private void validatePrompts(ServiceShape service, Model model, List<ValidationEvent> events) {
        // Track prompt names to detect case-insensitive conflicts
        Map<String, Shape> seenPromptNames = new HashMap<>();

        service.getTrait(PromptsTrait.class).ifPresent(promptsTrait -> {
            for (String promptName : promptsTrait.getValues().keySet()) {
                checkPromptUniqueness(promptName, service, seenPromptNames, events);
            }
        });

        for (OperationShape operation : service.getOperations()
                .stream()
                .map(shapeId -> model.expectShape(shapeId, OperationShape.class))
                .toList()) {

            operation.getTrait(PromptsTrait.class).ifPresent(promptsTrait -> {
                for (String promptName : promptsTrait.getValues().keySet()) {
                    checkPromptUniqueness(promptName, operation, seenPromptNames, events);
                }
            });
        }
    }

    private void checkPromptUniqueness(
            String promptName,
            Shape shape,
            Map<String, Shape> seenPromptNames,
            List<ValidationEvent> events
    ) {
        String normalizedName = promptName.toLowerCase();

        if (seenPromptNames.containsKey(normalizedName)) {
            Shape conflictingShape = seenPromptNames.get(normalizedName);
            events.add(error(shape,
                    String.format(
                            "Duplicate prompt name detected: '%s' conflicts with an existing prompt " +
                                    "defined on %s when compared case-insensitively. Prompt names must be unique " +
                                    "within a service regardless of case.",
                            promptName,
                            conflictingShape.getId())));
        } else {
            seenPromptNames.put(normalizedName, shape);
        }
    }
}
