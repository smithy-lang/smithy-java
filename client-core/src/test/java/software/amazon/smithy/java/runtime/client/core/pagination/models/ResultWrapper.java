/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.pagination.models;

import java.util.List;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
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
}
