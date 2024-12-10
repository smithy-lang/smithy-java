/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.PrivateTrait;
import software.amazon.smithy.model.transform.ModelTransformer;

/**
 * Generates a synthetic service for a set of shapes. 
 *
 * <p>Adds a set of shapes to the closure of a synthetic service shape. Operations shapes are added directly
 * to the service shape while all other shapes are added to the service via synthetic operations with synthetic inputs.
 *
 * <p>Directed codegen requires a root service shape to use for generating types. This service shape also
 * provides renames for a set of shapes as well as the list of protocols the shapes should support. This
 * transform creates a synthetic service that Directed codegen can use to generate the provided set of shapes.
 */
final class SyntheticServiceTransform {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(SyntheticServiceTransform.class);
    static final String SYNTHETIC_NAMESPACE = "smithy.synthetic";
    static final ShapeId SYNTHETIC_SERVICE_ID = ShapeId.fromParts(SYNTHETIC_NAMESPACE, "TypesGenService");

    static Model transform(Model model, Set<Shape> closure, Map<ShapeId, String> renames) {

        Set<Shape> shapesToAdd = new HashSet<>();

        ServiceShape.Builder serviceBuilder = ServiceShape.builder().id(SYNTHETIC_SERVICE_ID);
        serviceBuilder.rename(renames);

        for (Shape shape : closure) {
            switch (shape.getType()) {
                case SERVICE, RESOURCE -> LOGGER.debug("Skipping service-associated shape {} for type codegen...", shape);
                case OPERATION -> serviceBuilder.addOperation(shape.asOperationShape().orElseThrow());
                case STRUCTURE, ENUM, INT_ENUM, UNION -> {
                    var syntheticInput = createSyntheticInput(shape);
                    shapesToAdd.add(syntheticInput);
                    var syntheticOperation = createSyntheticOperation(syntheticInput);
                    shapesToAdd.add(syntheticOperation);
                    serviceBuilder.addOperation(syntheticOperation);
                }
                default -> {
                    // All other shapes are skipped with no logging as they should be
                    // implicitly added by aggregate shapes.
                }
            }
        }
        shapesToAdd.add(serviceBuilder.build());

        return ModelTransformer.create().replaceShapes(model, shapesToAdd);
    }

    private static OperationShape createSyntheticOperation(Shape shape) {
        var id = ShapeId.fromParts(SYNTHETIC_NAMESPACE, shape.getId().getName() + "Operation");
        var operationBuilder = OperationShape.builder()
            .id(id)
            .addTrait(new PrivateTrait());
        if (shape.hasTrait(ErrorTrait.class)) {
            operationBuilder.addError(shape.toShapeId());
        } else {
            operationBuilder.input(shape.toShapeId());
        }
        return operationBuilder.build();
    }

    private static StructureShape createSyntheticInput(Shape shape) {
        var id = ShapeId.fromParts(SYNTHETIC_NAMESPACE, shape.getId().getName() + "Input");
        return StructureShape.builder()
            .id(id)
            .addMember("syntheticMember", shape.getId())
            .build();
    }
}
