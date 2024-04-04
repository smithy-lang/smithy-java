/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.testmodels;

import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public final class Bird implements SerializableShape {

    public static final ShapeId ID = ShapeId.from("smithy.example#Bird");
    private static final SdkSchema SCHEMA_NAME = SdkSchema.memberBuilder(0, "name", PreludeSchemas.STRING)
            .id(ID)
            .build();
    static final SdkSchema SCHEMA = SdkSchema.builder()
            .id(ID)
            .type(ShapeType.STRUCTURE)
            .members(SCHEMA_NAME)
            .build();

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
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.beginStruct(SCHEMA, st -> {
            st.stringMember(SCHEMA_NAME, name);
        });
    }

    public static final class Builder implements SdkShapeBuilder<Bird> {

        private String name;

        private Builder() {}

        @Override
        public Bird build() {
            return new Bird(this);
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, (member, de) -> {
                int index = member.memberIndex() == -1
                            ? SCHEMA.member(member.memberName()).memberIndex()
                            : member.memberIndex();
                switch (index) {
                    case 0 -> name(de.readString(member));
                }
            });
            return this;
        }
    }
}
