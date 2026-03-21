/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.codegen.generators.SyntheticTrait;
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
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Generates a synthetic service for a set of shapes.
 *
 * <p>Adds a set of shapes to the closure of a synthetic service shape. Operation shapes are added directly
 * to the service shape while all other shapes are added via a single synthetic operation with a synthetic input
 * that references each type as a member.
 *
 * <p>Directed codegen requires a root service shape to use for generating types. This service shape also
 * provides renames for a set of shapes as well as the list of protocols the shapes should support. This
 * transform creates a synthetic service that Directed codegen can use to generate the provided set of shapes.
 */
@SmithyInternalApi
public final class SyntheticServiceTransform {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(SyntheticServiceTransform.class);
    public static final String SYNTHETIC_NAMESPACE = "smithy.synthetic";
    static final ShapeId SYNTHETIC_SERVICE_ID = ShapeId.fromParts(SYNTHETIC_NAMESPACE, "TypesGenService");

    /**
     * Types-only mode: creates a new synthetic service wrapping closure shapes.
     */
    static Model transform(Model model, Set<Shape> closure, Map<ShapeId, String> renames) {

        ServiceShape.Builder serviceBuilder = ServiceShape.builder()
                .id(SYNTHETIC_SERVICE_ID)
                .addTrait(new SyntheticTrait());
        serviceBuilder.rename(renames);

        List<Shape> typesToWrap = new ArrayList<>();
        List<Shape> errorShapes = new ArrayList<>();

        for (Shape shape : closure) {
            switch (shape.getType()) {
                case SERVICE, RESOURCE -> LOGGER.debug(
                        "Skipping service-associated shape {} for type codegen...",
                        shape);
                case OPERATION -> serviceBuilder.addOperation(shape.asOperationShape().orElseThrow());
                case STRUCTURE, ENUM, INT_ENUM, UNION -> {
                    typesToWrap.add(shape);
                    if (shape.hasTrait(ErrorTrait.class)) {
                        errorShapes.add(shape);
                    }
                }
                default -> {
                    // All other shapes are skipped with no logging as they should be
                    // implicitly added by aggregate shapes.
                }
            }
        }

        Set<Shape> shapesToAdd = new HashSet<>();
        if (!typesToWrap.isEmpty()) {
            var syntheticShapes = createSyntheticShapes(typesToWrap, errorShapes);
            shapesToAdd.addAll(syntheticShapes);
            // Find the operation shape to add to the service
            for (Shape s : syntheticShapes) {
                if (s instanceof OperationShape op) {
                    serviceBuilder.addOperation(op);
                }
            }
        }

        shapesToAdd.add(serviceBuilder.build());
        return ModelTransformer.create().replaceShapes(model, shapesToAdd);
    }

    /**
     * Combined mode: adds a synthetic operation to an existing service to include additional shapes
     * in its closure.
     */
    static Model expandServiceClosure(Model model, ShapeId serviceId, Set<Shape> additionalShapes) {
        if (additionalShapes.isEmpty()) {
            return model;
        }

        List<Shape> errorShapes = new ArrayList<>();
        for (Shape shape : additionalShapes) {
            if (shape.hasTrait(ErrorTrait.class)) {
                errorShapes.add(shape);
            }
        }

        var syntheticShapes = createSyntheticShapes(new ArrayList<>(additionalShapes), errorShapes);

        var service = model.expectShape(serviceId, ServiceShape.class);
        var serviceBuilder = service.toBuilder();
        Set<Shape> shapesToAdd = new HashSet<>(syntheticShapes);
        for (Shape s : syntheticShapes) {
            if (s instanceof OperationShape op) {
                serviceBuilder.addOperation(op);
            }
        }
        shapesToAdd.add(serviceBuilder.build());

        return ModelTransformer.create().replaceShapes(model, shapesToAdd);
    }

    private static Set<Shape> createSyntheticShapes(List<Shape> typesToWrap, List<Shape> errorShapes) {
        var inputBuilder = StructureShape.builder()
                .id(ShapeId.fromParts(SYNTHETIC_NAMESPACE, "TypesOperationInput"))
                .addTrait(new SyntheticTrait());
        for (int i = 0; i < typesToWrap.size(); i++) {
            inputBuilder.addMember("m" + i, typesToWrap.get(i).getId());
        }
        var syntheticInput = inputBuilder.build();

        var syntheticOutput = StructureShape.builder()
                .id(ShapeId.fromParts(SYNTHETIC_NAMESPACE, "TypesOperationOutput"))
                .addTrait(new SyntheticTrait())
                .build();

        var opBuilder = OperationShape.builder()
                .id(ShapeId.fromParts(SYNTHETIC_NAMESPACE, "TypesOperation"))
                .addTrait(new SyntheticTrait())
                .addTrait(new PrivateTrait())
                .input(syntheticInput)
                .output(syntheticOutput);
        for (Shape error : errorShapes) {
            opBuilder.addError(error.toShapeId());
        }

        return Set.of(syntheticInput, syntheticOutput, opBuilder.build());
    }
}
