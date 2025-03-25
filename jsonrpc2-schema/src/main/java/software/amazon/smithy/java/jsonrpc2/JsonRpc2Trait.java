/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jsonrpc2;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.ToSmithyBuilder;

public final class JsonRpc2Trait extends AbstractTrait implements ToSmithyBuilder<JsonRpc2Trait> {
    public static final ShapeId ID = ShapeId.from("smithy.protocols#jsonRpc2");

    private JsonRpc2Trait(Builder builder) {
        super(ID, builder.getSourceLocation());
    }

    @Override
    protected Node createNode() {
        return Node.objectNode();
    }

    @Override
    public SmithyBuilder<JsonRpc2Trait> toBuilder() {
        return new Builder();
    }

    public static final class Builder extends AbstractTraitBuilder<JsonRpc2Trait, Builder> {
        @Override
        public JsonRpc2Trait build() {
            return new JsonRpc2Trait(this);
        }
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            JsonRpc2Trait result = new JsonRpc2Trait.Builder().build();
            result.setNodeCache(value);
            return result;
        }
    }
}
