/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.transforms;

import java.util.HashSet;
import java.util.Set;
import software.amazon.smithy.framework.traits.AddsImplicitErrorsTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.InternalTrait;
import software.amazon.smithy.model.transform.ModelTransformer;

/**
 * Adds any framework errors (error shapes found in `smithy.framework` namespace) to service shapes in the model.
 */
public final class AddFrameworkErrorsTransform {
    private static final String SMITHY_FRAMEWORK_NAMESPACE = "smithy.framework";

    private final Mode mode;

    public enum Mode {
        CLIENT,
        SERVER
    }

    public AddFrameworkErrorsTransform(Mode mode) {
        this.mode = mode;
    }

    public Model transform(ModelTransformer transformer, Model model) {
        Set<ShapeId> frameworkErrors = new HashSet<>();
        for (var struct : model.getStructureShapes()) {
            if (struct.hasTrait(ErrorTrait.class) && struct.getId().getNamespace().equals(SMITHY_FRAMEWORK_NAMESPACE)) {
                if (Mode.CLIENT.equals(mode) && struct.hasTrait(InternalTrait.class)) {
                    continue;
                }
                frameworkErrors.add(struct.getId());
            }
        }
        var syntheticFrameworkTrait = new SyntheticFrameworkTrait();
        var addedFrameworkErrors = AddsImplicitErrorsTrait.builder().values(frameworkErrors.stream().toList()).build();
        var syntheticFrameworkTraitShape = syntheticFrameworkTrait.traitShapeBuilder.addTrait(addedFrameworkErrors)
            .build();
        Set<Shape> updated = new HashSet<>();
        updated.add(syntheticFrameworkTraitShape);
        for (var service : model.getServiceShapes()) {
            updated.add(service.toBuilder().addTrait(syntheticFrameworkTrait).build());
        }

        return transformer.replaceShapes(model, updated);
    }
}
