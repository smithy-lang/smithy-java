/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.myservice.model;

import software.amazon.smithy.java.runtime.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.serde.ToStringSerializer;
import software.amazon.smithy.java.runtime.shapes.SdkSchema;
import software.amazon.smithy.java.runtime.shapes.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.shapes.SerializableShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

public final class GetPersonImageOutput implements SerializableShape {

    static final ShapeId ID = ShapeId.from("smithy.example#GetPersonImageOutput");
    private static final SdkSchema SCHEMA_NAME = SdkSchema
            .memberBuilder(0, "name", SharedSchemas.STRING)
            .id(ID).traits(new HttpHeaderTrait("Person-Name"), new RequiredTrait()).build();
    private static final SdkSchema SCHEMA_IMAGE = SdkSchema
            .memberBuilder(1, "image", SharedSchemas.STREAM)
            .id(ID).traits(new HttpPayloadTrait()).build();
    static final SdkSchema SCHEMA = SdkSchema.builder()
            .id(ID)
            .type(ShapeType.STRUCTURE)
            .members(SCHEMA_NAME, SCHEMA_IMAGE)
            .build();

    private final String name;

    private GetPersonImageOutput(Builder builder) {
        this.name = builder.name;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.beginStruct(SCHEMA, st -> {
            st.stringMemberIf(SCHEMA_NAME, name);
        });
    }

    public static final class Builder implements SdkShapeBuilder<GetPersonImageOutput> {

        private String name;

        private Builder() {}

        @Override
        public GetPersonImageOutput build() {
            return new GetPersonImageOutput(this);
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, (member, de) -> {
                switch (member.memberIndex()) {
                    case 0 -> name(de.readString(member));
                }
            });
            return this;
        }
    }
}
