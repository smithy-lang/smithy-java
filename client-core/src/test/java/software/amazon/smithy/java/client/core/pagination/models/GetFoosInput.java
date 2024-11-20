/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.pagination.models;

import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

public final class GetFoosInput implements SerializableStruct {

    public static final ShapeId ID = ShapeId.from("smithy.example#GetFoosInput");
    public static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("maxResults", PreludeSchemas.INTEGER)
        .putMember("nextToken", PreludeSchemas.STRING)
        .build();
    public static final Schema SCHEMA_MAX_RESULTS = SCHEMA.member("maxResults");
    public static final Schema SCHEMA_NEXT_TOKEN = SCHEMA.member("nextToken");

    private final int maxResults;
    private final String nextToken;

    private GetFoosInput(Builder builder) {
        this.maxResults = builder.maxResults;
        this.nextToken = builder.nextToken;
    }

    public String nextToken() {
        return nextToken;
    }

    public int maxResults() {
        return maxResults;
    }

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
        serializer.writeInteger(SCHEMA_MAX_RESULTS, maxResults);
        if (nextToken != null) {
            serializer.writeString(SCHEMA_NEXT_TOKEN, nextToken);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        switch (member.id()) {
            case 0 -> ;
            case 1 -> ;
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder implements ShapeBuilder<GetFoosInput> {
        private int maxResults;
        private String nextToken;

        private Builder() {}

        public Builder nextToken(String nextToken) {
            this.nextToken = nextToken;
            return this;
        }

        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        @Override
        public GetFoosInput build() {
            return new GetFoosInput(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
            return this;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        private static final class InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
            private static final InnerDeserializer INSTANCE = new InnerDeserializer();

            @Override
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.maxResults(de.readInteger(member));
                    case 1 -> builder.nextToken(de.readString(member));
                }
            }
        }
    }
}
