/**
 * Header Line 1
 * Header Line 2
 */

package software.amazon.smithy.java.runtime.example.codegen.model;

import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;


public final class PutPersonImageOutput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonImageOutput");

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .traits(

        )
        .build();

    private PutPersonImageOutput(Builder builder) {
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        // Placeholder. Do nothing
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link PutPersonImageOutput}.
     */
    public static final class Builder implements SdkShapeBuilder<PutPersonImageOutput> {

        private Builder() {}

        @Override
        public PutPersonImageOutput build() {
            return new PutPersonImageOutput(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            // PLACEHOLDER. Needs implementation
            return this;
        }
    }
}

