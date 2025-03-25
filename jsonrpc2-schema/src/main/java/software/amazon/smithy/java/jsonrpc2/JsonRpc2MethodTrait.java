/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jsonrpc2;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.ToSmithyBuilder;

public final class JsonRpc2MethodTrait extends AbstractTrait implements ToSmithyBuilder<JsonRpc2MethodTrait> {
    public static final ShapeId ID = ShapeId.from("smithy.protocols#jsonRpc2Method");

    private final String method;

    private JsonRpc2MethodTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.method = builder.method;
    }

    public String method() {
        return method;
    }

    @Override
    protected Node createNode() {
        return ObjectNode.builder().withMember("method", ObjectNode.from(method)).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static JsonRpc2MethodTrait fromNode(Node node) {
        ObjectNode objectNode = node.expectObjectNode();
        return builder().sourceLocation(node.getSourceLocation())
                .method(objectNode.expectStringMember("method").getValue())
                .build();
    }

    @Override
    public SmithyBuilder<JsonRpc2MethodTrait> toBuilder() {
        return new Builder().method(method);
    }

    public static final class Builder extends AbstractTraitBuilder<JsonRpc2MethodTrait, Builder> {
        private String method;

        @Override
        public JsonRpc2MethodTrait build() {
            return new JsonRpc2MethodTrait(this);
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            JsonRpc2MethodTrait trait = JsonRpc2MethodTrait.fromNode(value);
            trait.setNodeCache(value);
            return trait;
        }
    }
}
