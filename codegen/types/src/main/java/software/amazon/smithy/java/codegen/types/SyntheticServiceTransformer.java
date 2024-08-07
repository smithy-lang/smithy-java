/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import java.util.HashSet;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.transform.ModelTransformer;

// TODO: Add renames to service shape
final class SyntheticServiceTransformer {
    static final ShapeId SYNTHETIC_SERVICE_ID = ShapeId.from("smithy.synthetic#TypesService");

    static Model transform(Model model, Set<Shape> typeClosure) {

        Set<Shape> shapesToAdd = new HashSet<>();

        // Create a synthetic service builder to add operations to
        ServiceShape.Builder serviceBuilder = ServiceShape.builder().id(SYNTHETIC_SERVICE_ID);

        // TODO: Update this to use the `shapes` property to add a shape to the service closure once
        // that is available
        // Create a synthetic operation for each shape and add to the synthetic service to add
        // type to service closure
        for (Shape type : typeClosure) {
            OperationShape op = OperationShape.builder()
                .id(type.getId().toString() + "SyntheticOperation")
                .input(type.toShapeId())
                .build();
            shapesToAdd.add(op);
            serviceBuilder.addOperation(op.toShapeId());
        }
        shapesToAdd.add(serviceBuilder.build());

        return ModelTransformer.create().replaceShapes(model, shapesToAdd);
    }
}
