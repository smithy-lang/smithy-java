/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class SyntheticTrait implements Trait {
    @Override
    public ShapeId toShapeId() {
        return null;
    }

    @Override
    public boolean isSynthetic() {
        return true;
    }

    @Override
    public Node toNode() {
        return null;
    }
}
