/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.testmodels;

import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * A POJO with no validation constraints.
 */
public final class UnvalidatedPojo implements SerializableStruct {

    public static final ShapeId ID = ShapeId.from("smithy.example#UnvalidatedPojo");
    private static final Schema SCHEMA_STRING = Schema.memberBuilder("string", () -> PreludeSchemas.STRING)
        .id(ID)
        .build();
    private static final Schema SCHEMA_BOXED_INTEGER = Schema
        .memberBuilder("boxedInteger", () -> PreludeSchemas.INTEGER)
        .id(ID)
        .build();
    private static final Schema SCHEMA_INTEGER = Schema.memberBuilder("integer", () -> PreludeSchemas.PRIMITIVE_INTEGER)
        .id(ID)
        .build();
    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(SCHEMA_STRING, SCHEMA_BOXED_INTEGER, SCHEMA_INTEGER)
        .build();

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
    public void serialize(ShapeSerializer encoder) {
        encoder.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (boxedInteger != null) {
            serializer.writeInteger(SCHEMA_BOXED_INTEGER, boxedInteger);
        }
        serializer.writeInteger(SCHEMA_INTEGER, integer);
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
