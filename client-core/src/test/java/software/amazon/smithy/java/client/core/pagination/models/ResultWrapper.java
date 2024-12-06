/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.pagination.models;

import java.util.List;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

public record ResultWrapper(String nextToken, List<String> foos) implements SerializableStruct {
    private static final Schema LIST_OF_STRINGS = Schema.listBuilder(ShapeId.from("smithy.example#ListOfStrings"))
        .putMember("member", PreludeSchemas.STRING)
        .build();
    static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#ResultWrapper"))
        .putMember("nextToken", PreludeSchemas.STRING)
        .putMember("foos", LIST_OF_STRINGS)
        .build();
    private static final Schema SCHEMA_NEXT_TOKEN = SCHEMA.member("nextToken");
    private static final Schema SCHEMA_FOOS = SCHEMA.member("foos");

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
        if (nextToken != null) {
            serializer.writeString(SCHEMA_NEXT_TOKEN, nextToken);
        }
        if (foos != null) {
            serializer.writeList(SCHEMA_FOOS, foos, foos.size(), (a, b) -> {});
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember(SCHEMA_NEXT_TOKEN, member, nextToken);
            case 1 -> (T) SchemaUtils.validateSameMember(SCHEMA_FOOS, member, foos);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        };
    }
}
