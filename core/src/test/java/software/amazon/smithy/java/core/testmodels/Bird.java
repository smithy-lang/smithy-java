/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.testmodels;

import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

public final class Bird implements SerializableStruct {

    public static final ShapeId ID = ShapeId.from("smithy.example#Bird");
    public static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("name", PreludeSchemas.STRING)
        .build();
    public static final Schema SCHEMA_NAME = SCHEMA.member("name");

    private final String name;

    private Bird(Builder builder) {
        this.name = builder.name;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String name() {
        return name;
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_NAME, name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        if (member.memberName().equals("name")) {
            return (T) name;
        } else {
            throw new IllegalArgumentException("Unknown member " + member);
        }
    }

    public static final class Builder implements ShapeBuilder<Bird> {

        private String name;

        private Builder() {}

        @Override
        public Bird build() {
            return new Bird(this);
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, (builder, member, de) -> {
                switch (member.memberIndex()) {
                    case 0 -> builder.name(de.readString(member));
                }
            });
            return this;
        }
    }
}
