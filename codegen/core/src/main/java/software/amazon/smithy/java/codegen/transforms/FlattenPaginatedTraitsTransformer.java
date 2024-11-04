/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.transforms;

import java.util.HashSet;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.PaginatedIndex;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.transform.ModelTransformer;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Flattens Pagination info into the paginated trait on operations.
 *
 * <p>Attaching the paginated trait to a service provides default pagination configuration settings
 * to all paginated operations bound within the closure of the service. This transform adds these
 * default settings to the Paginated Trait on operation shapes if applicable.
 *
 * @see <a href="https://smithy.io/2.0/spec/behavior-traits.html#pagination">Paginated Trait</a>
 */
// TODO: Upstream to Directed codegen standard transforms.
@SmithyInternalApi
public class FlattenPaginatedTraitsTransformer {

    public static Model transform(Model model, ShapeId service) {
        var paginatedIndex = PaginatedIndex.of(model);
        Set<Shape> updatedShapes = new HashSet<>();
        for (var shape : model.getOperationShapes()) {
            if (shape.isOperationShape()) {
                var operationShape = shape.asOperationShape().orElseThrow();
                var infoOptional = paginatedIndex.getPaginationInfo(service, operationShape);
                if (infoOptional.isEmpty()) {
                    continue;
                }
                var updatedTrait = infoOptional.get().getPaginatedTrait();
                var updatedShape = operationShape.toBuilder().addTrait(updatedTrait).build();
                updatedShapes.add(updatedShape);
            }
        }

        return ModelTransformer.create().replaceShapes(model, updatedShapes);
    }
}
