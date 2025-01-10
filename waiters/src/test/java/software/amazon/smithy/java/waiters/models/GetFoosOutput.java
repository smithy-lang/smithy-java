/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters.models;

import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

public record GetFoosOutput(String status) implements SerializableStruct {
    private static final ShapeId ID = ShapeId.from("smithy.example#GetFoosOutput");
    static final Schema SCHEMA = Schema.structureBuilder(ID)
            .putMember("status", PreludeSchemas.STRING)
            .build();
    private static final Schema SCHEMA_STATUS = SCHEMA.member("status");

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
        serializer.writeString(SCHEMA_STATUS, status);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember(SCHEMA_STATUS, member, status);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        };
    }
}
