/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.validation.AbstractValidator;
import software.amazon.smithy.model.validation.ValidationEvent;

/**
 * Validates that {@code mcpHeader} is only applied to members targeting strings.
 */
public final class McpHeaderValidator extends AbstractValidator {

    @Override
    public List<ValidationEvent> validate(Model model) {
        List<ValidationEvent> events = new ArrayList<>();
        for (Shape shape : model.toSet()) {
            Optional<McpHeaderTrait> trait = shape.getTrait(McpHeaderTrait.class);
            if (!trait.isPresent()) {
                continue;
            }

            MemberShape member = shape.asMemberShape().orElseThrow(IllegalStateException::new);
            Shape target = model.expectShape(member.getTarget());
            if (target.getType() != ShapeType.STRING) {
                events.add(error(
                        member,
                        "The smithy.ai#mcpHeader trait can only be applied to members targeting strings."));
            }
        }
        return events;
    }
}
