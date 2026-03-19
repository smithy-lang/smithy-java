/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.framework.traits;

import java.util.List;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AbstractTraitBuilder;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.BuilderRef;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.SmithyGenerated;
import software.amazon.smithy.utils.ToSmithyBuilder;

@SmithyGenerated
public final class ImplicitErrorsTrait extends AbstractTrait implements ToSmithyBuilder<ImplicitErrorsTrait> {
    public static final ShapeId ID = ShapeId.from("smithy.framework#implicitErrors");

    private final List<ShapeId> values;

    private ImplicitErrorsTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.values = builder.values.copy();
    }

    @Override
    protected Node createNode() {
        var builder = ArrayNode.builder();
        for (var value : values) {
            builder.withValue(value.toString());
        }
        return builder.build();
    }

    /**
     * Creates a {@link ImplicitErrorsTrait} from a {@link Node}.
     *
     * @param node Node to create the AddsImplicitErrorsTrait from.
     * @return Returns the created AddsImplicitErrorsTrait.
     * @throws ExpectationNotMetException if the given Node is invalid.
     */
    public static ImplicitErrorsTrait fromNode(Node node) {
        Builder builder = builder();
        for (var element : node.expectArrayNode().getElements()) {
            builder.addValues(ShapeId.fromNode(element));
        }
        return builder.build();
    }

    public List<ShapeId> getValues() {
        return values;
    }

    /**
     * Creates a builder used to build a {@link ImplicitErrorsTrait}.
     */
    public SmithyBuilder<ImplicitErrorsTrait> toBuilder() {
        return builder().sourceLocation(getSourceLocation()).values(getValues());
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ImplicitErrorsTrait}.
     */
    public static final class Builder extends AbstractTraitBuilder<ImplicitErrorsTrait, Builder> {
        private final BuilderRef<List<ShapeId>> values = BuilderRef.forList();

        private Builder() {}

        public Builder values(List<ShapeId> values) {
            clearValues();
            this.values.get().addAll(values);
            return this;
        }

        public Builder clearValues() {
            values.get().clear();
            return this;
        }

        public Builder addValues(ShapeId value) {
            values.get().add(value);
            return this;
        }

        public Builder removeValues(ShapeId value) {
            values.get().remove(value);
            return this;
        }

        @Override
        public ImplicitErrorsTrait build() {
            return new ImplicitErrorsTrait(this);
        }
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            ImplicitErrorsTrait result = ImplicitErrorsTrait.fromNode(value);
            result.setNodeCache(value);
            return result;
        }
    }
}
