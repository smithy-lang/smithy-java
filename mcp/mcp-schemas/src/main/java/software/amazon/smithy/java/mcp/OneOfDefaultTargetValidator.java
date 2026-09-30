/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.validation.AbstractValidator;
import software.amazon.smithy.model.validation.ValidationEvent;

public final class OneOfDefaultTargetValidator extends AbstractValidator {
    @Override
    public List<ValidationEvent> validate(Model model) {
        var events = new ArrayList<ValidationEvent>();
        for (var shape : model.getShapesWithTrait(OneOfTrait.class)) {
            var trait = shape.expectTrait(OneOfTrait.class);
            trait.getDefaultTarget().ifPresent(target -> {
                if (trait.getMembers().stream().filter(member -> member.getTarget().equals(target)).count() != 1) {
                    events.add(error(shape,
                            trait,
                            "The oneOf defaultTarget `" + target + "` must identify exactly one member."));
                }
            });
        }
        return events;
    }
}
