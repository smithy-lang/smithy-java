/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.pagination.models;

import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

public record GetFoosOutput(ResultWrapper result) implements SerializableStruct {
    private static final ShapeId ID = ShapeId.from("smithy.example#GetFoosOutput");
    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("result", ResultWrapper.SCHEMA)
        .build();
    private static final Schema SCHEMA_RESULT = SCHEMA.member("result");

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
        serializer.writeStruct(SCHEMA_RESULT, result);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember(SCHEMA_RESULT, member, result);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        };
    }
}
