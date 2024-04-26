/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example.model;

import software.amazon.smithy.java.runtime.core.schema.ModeledSdkException;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public final class ValidationError extends ModeledSdkException {

    public static final ShapeId ID = ShapeId.from("smithy.example#ValidationError");

    private static final SdkSchema SCHEMA_MESSAGE = SdkSchema.memberBuilder("message", PreludeSchemas.STRING)
        .id(ID)
        .build();
    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(SCHEMA_MESSAGE)
        .build();

    private ValidationError(Builder builder) {
        super(ID, builder.message);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this, (pojo, st) -> {
            st.writeString(SCHEMA_MESSAGE, pojo.getMessage());
        });
    }

    public static final class Builder implements SdkShapeBuilder<ValidationError> {

        private String message;

        private Builder() {
        }

        @Override
        public ValidationError build() {
            return new ValidationError(this);
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, (builder, member, de) -> {
                switch (member.memberIndex()) {
                    case 0 -> builder.message(de.readString(member));
                }
            });
            return this;
        }
    }
}
