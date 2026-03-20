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
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;

/**
 * A POJO with a single string member constrained by both length and pattern (composite validation).
 */
public final class StringCompositePojo implements SerializableStruct {

    public static final ShapeId ID = ShapeId.from("smithy.example#StringCompositePojo");
    static final Schema SCHEMA = Schema.structureBuilder(ID)
            .putMember(
                    "value",
                    PreludeSchemas.STRING,
                    LengthTrait.builder().min(1L).max(100L).build(),
                    new PatternTrait("^[a-zA-Z0-9]+$"))
            .build();
    private static final Schema SCHEMA_VALUE = SCHEMA.member("value");

    private final String value;

    private StringCompositePojo(Builder builder) {
        this.value = builder.value;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String value() {
        return value;
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
    public void serializeMembers(ShapeSerializer serializer) {
        if (value != null) {
            serializer.writeString(SCHEMA_VALUE, value);
        }
    }

    @Override
    public <T> T getMemberValue(Schema member) {
        throw new UnsupportedOperationException("Member value not supported: " + member);
    }

    public static final class Builder implements ShapeBuilder<StringCompositePojo> {
        private String value;

        private Builder() {}

        @Override
        public StringCompositePojo build() {
            return new StringCompositePojo(this);
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, (builder, member, de) -> {
                if (member.memberIndex() == 0) {
                    builder.value(de.readString(member));
                }
            });
            return this;
        }
    }
}
