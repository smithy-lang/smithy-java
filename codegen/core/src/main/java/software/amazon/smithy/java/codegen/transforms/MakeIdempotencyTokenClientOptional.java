/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.transforms;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.ClientOptionalTrait;
import software.amazon.smithy.model.traits.IdempotencyTokenTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.transform.ModelTransformer;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Idempotency tokens that are required should fail validation, but shouldn't be required to create a type, allowing
 * for a default value to get injected when missing.
 */
@SmithyInternalApi
public final class MakeIdempotencyTokenClientOptional {
    public static Model transform(Model model) {
        return ModelTransformer.create().mapShapes(model, shape -> {
            if (shape.isMemberShape()
                && shape.hasTrait(RequiredTrait.class)
                && shape.hasTrait(IdempotencyTokenTrait.class)
                && !shape.hasTrait(ClientOptionalTrait.class)) {
                return Shape.shapeToBuilder(shape).addTrait(new ClientOptionalTrait()).build();
            }
            return shape;
        });
    }
}
