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

/**
 * A POJO with no validation constraints.
 */
public final class UnvalidatedPojo implements SerializableStruct {

    public static final ShapeId ID = ShapeId.from("smithy.example#UnvalidatedPojo");
    static final Schema SCHEMA = Schema.structureBuilder(ID)
            .putMember("string", PreludeSchemas.STRING)
            .putMember("boxedInteger", PreludeSchemas.INTEGER)
            .putMember("integer", PreludeSchemas.PRIMITIVE_INTEGER)
            .build();
    private static final Schema SCHEMA_STRING = SCHEMA.member("string");
    private static final Schema SCHEMA_BOXED_INTEGER = SCHEMA.member("boxedInteger");
    private static final Schema SCHEMA_INTEGER = SCHEMA.member("integer");

    private final String string;
    private final int integer;
    private final Integer boxedInteger;

    private UnvalidatedPojo(Builder builder) {
        this.string = builder.string;
        this.integer = builder.integer;
        this.boxedInteger = builder.boxedInteger;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String string() {
        return string;
    }

    public Integer boxedInteger() {
        return boxedInteger;
    }

    public int integer() {
        return integer;
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
        if (string != null) {
            serializer.writeString(SCHEMA_STRING, string);
        }
        if (boxedInteger != null) {
            serializer.writeInteger(SCHEMA_BOXED_INTEGER, boxedInteger);
        }
        serializer.writeInteger(SCHEMA_INTEGER, integer);
    }

    @Override
    public <T> T getMemberValue(Schema member) {
        throw new UnsupportedOperationException("Member value not supported: " + member);
    }

    public static final class Builder implements ShapeBuilder<UnvalidatedPojo> {

        private String string;
        private int integer;
        private Integer boxedInteger;

        private Builder() {}

        @Override
        public UnvalidatedPojo build() {
            return new UnvalidatedPojo(this);
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        public Builder string(String string) {
            this.string = string;
            return this;
        }

        public Builder boxedInteger(Integer boxedInteger) {
            this.boxedInteger = boxedInteger;
            return this;
        }

        public Builder integer(int integer) {
            this.integer = integer;
            return this;
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, (builder, member, de) -> {
                switch (member.memberIndex()) {
                    case 0 -> builder.string(de.readString(member));
                    case 1 -> builder.boxedInteger(de.readInteger(member));
                    case 2 -> builder.integer(de.readInteger(member));
                }
            });
            return this;
        }
    }
}
