/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.waiters.models;

import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

public record GetFoosInput(String id) implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.example#GetFoosInput");
    public static final Schema SCHEMA = Schema.structureBuilder(ID)
            .putMember("id", PreludeSchemas.STRING)
            .build();
    public static final Schema SCHEMA_ID = SCHEMA.member("id");

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_ID, id);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember(SCHEMA_ID, member, id);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        };
    }
}
