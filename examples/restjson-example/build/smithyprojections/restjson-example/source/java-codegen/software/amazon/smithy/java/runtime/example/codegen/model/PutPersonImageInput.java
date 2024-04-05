/**
 * Header Line 1
 * Header Line 2
 */

package software.amazon.smithy.java.runtime.example.codegen.model;

import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyBuilder;


public final class PutPersonImageInput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonImageInput");

    private final String name;
    private final String tags;
    private final String moreTags;
    private final DataStream image;

    private PutPersonImageInput(Builder builder) {
        this.name = SmithyBuilder.requiredState("name", builder.name);
        this.tags = builder.tags;
        this.moreTags = builder.moreTags;
        this.image = builder.image;
    }

    public String name() {
        return name;
    }

    public String tags() {
        return tags;
    }

    public String moreTags() {
        return moreTags;
    }

    public DataStream image() {
        return image;
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
     * Builder for {@link PutPersonImageInput}.
     */
    public static final class Builder implements SdkShapeBuilder<PutPersonImageInput> {
        private String name;
        private String tags;
        private String moreTags;
        private DataStream image = DataStream.ofEmpty();

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder tags(String tags) {
            this.tags = tags;
            return this;
        }

        public Builder moreTags(String moreTags) {
            this.moreTags = moreTags;
            return this;
        }

        public Builder image(DataStream image) {
            this.image = image;
            return this;
        }

        @Override
        public void setDataStream(DataStream stream) {
            image(stream);
        }

        @Override
        public PutPersonImageInput build() {
            return new PutPersonImageInput(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            // PLACEHOLDER. Needs implementation
            return this;
        }
    }
}

