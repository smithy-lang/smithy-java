/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.UnitTypeTrait;

/**
 * Structure representing the {@code smithy.api#Unit} shape.
 *
 * <p>This structure is used to represent union members or operation inputs or outputs
 * that have no meaningful value.
 *
 * @see <a href="https://smithy.io/2.0/spec/model.html#unit-type">Smithy Unit type</a>
 */
public final class Unit implements SerializableStruct {

    public static final ShapeId ID = ShapeId.from("smithy.api#Unit");
    public static final Schema SCHEMA = Schema.structureBuilder(ID, new UnitTypeTrait()).build();

    private static final Unit INSTANCE = new Unit();

    private Unit() {}

    public static Unit getInstance() {
        return INSTANCE;
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        // Unit types have no members
    }

    @Override
    public <T> T getMemberValue(Schema member) {
        return SchemaUtils.validateMemberInSchema(SCHEMA, member, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder implements ShapeBuilder<Unit> {

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public ShapeBuilder<Unit> deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, (b, m, d) -> {});
            return this;
        }

        @Override
        public ShapeBuilder<Unit> deserializeMember(ShapeDeserializer decoder, Schema member) {
            decoder.readStruct(member.assertMemberTargetIs(SCHEMA), this, (b, m, d) -> {});
            return this;
        }

        @Override
        public Unit build() {
            return INSTANCE;
        }

        private Builder() {}
    }
}
