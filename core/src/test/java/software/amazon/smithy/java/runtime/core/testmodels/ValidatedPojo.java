/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.testmodels;

import java.math.BigDecimal;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

public final class ValidatedPojo implements SerializableStruct {

    public static final ShapeId ID = ShapeId.from("smithy.example#ValidatedPojo");
    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember(
            "string",
            PreludeSchemas.STRING,
            new RequiredTrait(),
            LengthTrait.builder().min(1L).max(100L).build()
        )
        .putMember("boxedInteger", PreludeSchemas.INTEGER, new RequiredTrait())
        .putMember(
            "integer",
            PreludeSchemas.PRIMITIVE_INTEGER,
            new RequiredTrait(),
            RangeTrait.builder().min(BigDecimal.valueOf(0)).build()
        )
        .build();
    private static final Schema SCHEMA_STRING = SCHEMA.member("string");
    private static final Schema SCHEMA_BOXED_INTEGER = SCHEMA.member("boxedInteger");
    private static final Schema SCHEMA_INTEGER = SCHEMA.member("integer");

    private final String string;
    private final int integer;
    private final Integer boxedInteger;

    private ValidatedPojo(Builder builder) {
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
    public void serializeMembers(ShapeSerializer st) {
        if (string != null) {
            st.writeString(SCHEMA_STRING, string);
        }
        if (boxedInteger != null) {
            st.writeInteger(SCHEMA_BOXED_INTEGER, boxedInteger);
        }
        st.writeInteger(SCHEMA_INTEGER, integer);
    }

    public static final class Builder implements ShapeBuilder<ValidatedPojo> {

        private String string;
        private int integer;
        private Integer boxedInteger;

        private Builder() {}

        @Override
        public ValidatedPojo build() {
            return new ValidatedPojo(this);
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
                    default -> {
                        // TODO: Log periodically
                    }
                }
            });
            return this;
        }
    }
}
