/**
 * Header Line 1
 * Header Line 2
 */

package software.amazon.smithy.java.runtime.example.codegen.model;

import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyBuilder;


public final class GetPersonImageInput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#GetPersonImageInput");

    private final String name;

    private GetPersonImageInput(Builder builder) {
        this.name = SmithyBuilder.requiredState("name", builder.name);
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
        // Placeholder. Do nothing
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link GetPersonImageInput}.
     */
    public static final class Builder implements SdkShapeBuilder<GetPersonImageInput> {
        private String name;

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public GetPersonImageInput build() {
            return new GetPersonImageInput(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            // PLACEHOLDER. Needs implementation
            return this;
        }
    }
}

