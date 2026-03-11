/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.framework.transform;

import java.util.HashSet;
import java.util.Set;
import software.amazon.smithy.framework.traits.ImplicitErrorsTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitDefinition;
import software.amazon.smithy.model.transform.ModelTransformer;

/**
 * Adds any framework errors (error shapes found in `smithy.framework` namespace) to service shapes in the model.
 * TODO: Upstream this transform to directed codegen and a smithy-build transform
 */
public final class AddFrameworkErrorsTransform {
    private static final String SMITHY_FRAMEWORK_NAMESPACE = "smithy.framework";
    private static final ShapeId SYNTHETIC_TRAIT_ID = ShapeId.from("smithy.synthetic#frameworkErrors");

    private AddFrameworkErrorsTransform() {}

    public static Model transform(ModelTransformer transformer, Model model) {
        Set<ShapeId> frameworkErrors = new HashSet<>();
        for (var struct : model.getStructureShapes()) {
            if (struct.hasTrait(ErrorTrait.class) && struct.getId().getNamespace().equals(SMITHY_FRAMEWORK_NAMESPACE)) {
                frameworkErrors.add(struct.getId());
            }
        }
        var addedFrameworkErrors = ImplicitErrorsTrait.builder().values(frameworkErrors.stream().toList()).build();
        var syntheticFrameworkTraitShape = getSyntheticTraitBuilder().addTrait(addedFrameworkErrors)
                .build();
        Set<Shape> updated = new HashSet<>();
        updated.add(syntheticFrameworkTraitShape);
        for (var service : model.getServiceShapes()) {
            updated.add(service.toBuilder().addTrait(new SyntheticErrorTrait()).build());
        }

        return transformer.replaceShapes(model, updated);
    }

    private static StructureShape.Builder getSyntheticTraitBuilder() {
        return StructureShape.builder()
                .id(SYNTHETIC_TRAIT_ID)
                .addTrait(TraitDefinition.builder().build());
    }

    private static final class SyntheticErrorTrait implements Trait {

        @Override
        public Node toNode() {
            return Node.objectNode();
        }

        @Override
        public ShapeId toShapeId() {
            return SYNTHETIC_TRAIT_ID;
        }

        @Override
        public boolean isSynthetic() {
            return true;
        }
    }
}
