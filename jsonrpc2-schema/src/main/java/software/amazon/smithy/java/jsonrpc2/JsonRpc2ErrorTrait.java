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

public final class JsonRpc2ErrorTrait extends AbstractTrait implements ToSmithyBuilder<JsonRpc2ErrorTrait> {
    public static final ShapeId ID = ShapeId.from("smithy.protocols#jsonRpc2Error");

    private final int code;
    private final String message;

    private JsonRpc2ErrorTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.code = builder.code;
        this.message = builder.message;
    }

    @Override
    protected Node createNode() {
        ObjectNode.Builder node = ObjectNode.builder().withMember("code", ObjectNode.from(code));
        if (message != null) {
            node = node.withMember("message", ObjectNode.from(message));
        }
        return node.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static JsonRpc2ErrorTrait fromNode(Node node) {
        ObjectNode objectNode = node.expectObjectNode();
        return builder().sourceLocation(node.getSourceLocation())
                .code(objectNode.expectNumberMember("code").getValue().intValue())
                .build();
    }

    @Override
    public SmithyBuilder<JsonRpc2ErrorTrait> toBuilder() {
        return new Builder()
                .code(code)
                .message(message);
    }

    public static final class Builder extends AbstractTraitBuilder<JsonRpc2ErrorTrait, Builder> {
        private int code;
        private String message;

        @Override
        public JsonRpc2ErrorTrait build() {
            return new JsonRpc2ErrorTrait(this);
        }

        public Builder code(int code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            JsonRpc2ErrorTrait trait = JsonRpc2ErrorTrait.fromNode(value);
            trait.setNodeCache(value);
            return trait;
        }
    }
}
