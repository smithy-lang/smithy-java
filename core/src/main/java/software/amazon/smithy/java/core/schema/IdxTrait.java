/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * The {@code smithy.protocols#idx} trait assigns a structure or union member its position in an indexed
 * protocol's field-index space. Values start at 1 and increase monotonically with no gaps within a shape.
 *
 * <p>Members carrying this trait sort by its value within their wire category when schemas are built (see
 * {@link SchemaUtils#memberSortRank}), which makes member dispatch order match the on-wire field order of
 * indexed binary protocols such as Sparrowhawk and keeps payloads byte-compatible with other
 * implementations that key off the same trait.
 *
 * <p>This is a local copy of the trait defined by the Sparrowhawk format so smithy-java is self-contained
 * and does not depend on an external traits artifact.
 */
@SmithyUnstableApi
public final class IdxTrait extends AbstractTrait {

    public static final ShapeId ID = ShapeId.from("smithy.protocols#idx");

    private final int value;

    public IdxTrait(int value, SourceLocation sourceLocation) {
        super(ID, sourceLocation);
        this.value = value;
    }

    public IdxTrait(int value) {
        this(value, SourceLocation.NONE);
    }

    public int getValue() {
        return value;
    }

    @Override
    protected Node createNode() {
        return new NumberNode(value, getSourceLocation());
    }

    public static final class Provider implements TraitService {
        @Override
        public Trait createTrait(ShapeId target, Node value) {
            IdxTrait result = new IdxTrait(value.expectNumberNode().getValue().intValue(), value.getSourceLocation());
            result.setNodeCache(value);
            return result;
        }

        @Override
        public ShapeId getShapeId() {
            return ID;
        }
    }
}
