/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.UnitTypeTrait;

/**
 * TODO: Docs
 */
public final class Unit implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.api#Unit");

    public static final Schema SCHEMA = Schema.builder()
        .type(ShapeType.STRUCTURE)
        .id(ID)
        .traits(new UnitTypeTrait())
        .build();

    public static final Unit INSTANCE = new Unit();

    private Unit() {}

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        // Unit types have no members
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder implements ShapeBuilder<Unit> {

        @Override
        public ShapeBuilder<Unit> deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, (b, m, d) -> {});
            return this;
        }

        @Override
        public Unit build() {
            return INSTANCE;
        }

        private Builder() {}
    }
}
