/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.testmodels;

import java.math.BigDecimal;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

public final class ValidatedPojo implements SerializableShape {

    public static final ShapeId ID = ShapeId.from("smithy.example#ValidatedPojo");
    private static final SdkSchema SCHEMA_STRING = SdkSchema.memberBuilder("string", PreludeSchemas.STRING)
        .id(ID)
        .traits(new RequiredTrait(), LengthTrait.builder().min(1L).max(100L).build())
        .build();
    private static final SdkSchema SCHEMA_BOXED_INTEGER = SdkSchema
        .memberBuilder("boxedInteger", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(new RequiredTrait())
        .build();
    private static final SdkSchema SCHEMA_INTEGER = SdkSchema.memberBuilder("integer", PreludeSchemas.PRIMITIVE_INTEGER)
        .id(ID)
        .traits(new RequiredTrait(), RangeTrait.builder().min(BigDecimal.valueOf(0)).build())
        .build();
    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(SCHEMA_STRING, SCHEMA_BOXED_INTEGER, SCHEMA_INTEGER)
        .build();

    private final String string;
    private final int integer;
    private final Integer boxedInteger;
    private final InnerSerializer innerSerializer = new InnerSerializer();

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
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this, innerSerializer);
    }

    private static final class InnerSerializer implements BiConsumer<ValidatedPojo, ShapeSerializer> {
        @Override
        public void accept(ValidatedPojo pojo, ShapeSerializer st) {
            if (pojo.string != null) {
                st.writeString(SCHEMA_STRING, pojo.string);
            }
            if (pojo.boxedInteger != null) {
                st.writeInteger(SCHEMA_BOXED_INTEGER, pojo.boxedInteger);
            }
            st.writeInteger(SCHEMA_INTEGER, pojo.integer);
        }
    }

    public static final class Builder implements SdkShapeBuilder<ValidatedPojo> {

        private String string;
        private int integer;
        private Integer boxedInteger;

        private Builder() {}

        @Override
        public ValidatedPojo build() {
            return new ValidatedPojo(this);
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
