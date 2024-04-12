/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example.model;

import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

// Example of a potentially generated empty structure shape.
public final class PutPersonImageOutput implements SerializableShape {

    static final ShapeId ID = ShapeId.from("smithy.example#PutPersonImageOutput");
    static final SdkSchema SCHEMA = SdkSchema.builder().id(ID).type(ShapeType.STRUCTURE).build();

    private PutPersonImageOutput(Builder builder) {}

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.beginStruct(SCHEMA).endStruct();
    }

    public static final class Builder implements SdkShapeBuilder<PutPersonImageOutput> {

        private Builder() {
        }

        @Override
        public PutPersonImageOutput build() {
            return new PutPersonImageOutput(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, (member, de) -> {
            });
            return this;
        }
    }
}
