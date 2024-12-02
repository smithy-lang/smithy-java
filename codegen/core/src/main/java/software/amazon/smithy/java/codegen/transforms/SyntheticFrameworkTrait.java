/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.transforms;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.AnnotationTrait;
import software.amazon.smithy.model.traits.TraitDefinition;

final class SyntheticFrameworkTrait extends AnnotationTrait {
    static final ShapeId ID = ShapeId.from("smithy.synthetic#frameworkErrors");
    final StructureShape.Builder traitShapeBuilder = StructureShape.builder()
        .id(SyntheticFrameworkTrait.ID)
        .addTrait(TraitDefinition.builder().build());

    public SyntheticFrameworkTrait(ObjectNode node) {
        super(ID, node);
    }

    public SyntheticFrameworkTrait() {
        this(Node.objectNode());
    }

    @Override
    public boolean isSynthetic() {
        return true;
    }

    public static final class Provider extends AnnotationTrait.Provider<SyntheticFrameworkTrait> {
        public Provider() {
            super(ID, SyntheticFrameworkTrait::new);
        }
    }
}
