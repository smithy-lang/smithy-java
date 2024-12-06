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
public final class AddsImplicitErrorsTrait extends AbstractTrait implements ToSmithyBuilder<AddsImplicitErrorsTrait> {
    public static final ShapeId ID = ShapeId.from("smithy.framework#implicitErrors");

    private final List<ShapeId> values;

    private AddsImplicitErrorsTrait(Builder builder) {
        super(ID, builder.getSourceLocation());
        this.values = builder.values.copy();
    }

    @Override
    protected Node createNode() {
        return values.stream()
            .map(s -> Node.from(s.toString()))
            .collect(ArrayNode.collect(getSourceLocation()));
    }

    /**
     * Creates a {@link AddsImplicitErrorsTrait} from a {@link Node}.
     *
     * @param node Node to create the AddsImplicitErrorsTrait from.
     * @return Returns the created AddsImplicitErrorsTrait.
     * @throws ExpectationNotMetException if the given Node is invalid.
     */
    public static AddsImplicitErrorsTrait fromNode(Node node) {
        Builder builder = builder();
        node.expectArrayNode()
            .getElements()
            .stream()
            .map(ShapeId::fromNode)
            .forEach(builder::addValues);
        return builder.build();
    }

    public List<ShapeId> getValues() {
        return values;
    }

    /**
     * Creates a builder used to build a {@link AddsImplicitErrorsTrait}.
     */
    public SmithyBuilder<AddsImplicitErrorsTrait> toBuilder() {
        return builder().sourceLocation(getSourceLocation())
            .values(getValues());
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link AddsImplicitErrorsTrait}.
     */
    public static final class Builder extends AbstractTraitBuilder<AddsImplicitErrorsTrait, Builder> {
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
        public AddsImplicitErrorsTrait build() {
            return new AddsImplicitErrorsTrait(this);
        }
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            AddsImplicitErrorsTrait result = AddsImplicitErrorsTrait.fromNode(value);
            result.setNodeCache(value);
            return result;
        }
    }
}
