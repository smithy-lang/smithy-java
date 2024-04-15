/**
 * Header Line 1
 * Header Line 2
 */

package software.amazon.smithy.java.runtime.example.codegen.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.SmithyGenerated;


@SmithyGenerated
public final class GetPersonImageOutput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#GetPersonImageOutput");

    private static final SdkSchema SCHEMA_NAME = SdkSchema.memberBuilder(0, "name", PreludeSchemas.STRING)
        .id(ID)
        .traits(

        )
        .build();

    private static final SdkSchema SCHEMA_IMAGE = SdkSchema.memberBuilder(1, "image", SharedSchemas.STREAM)
        .id(ID)
        .traits(

        )
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .traits(

        )
        .members(
            SCHEMA_NAME,
            SCHEMA_IMAGE
        )
        .build();

    private final String name;
    private final DataStream image;

    private GetPersonImageOutput(Builder builder) {
        this.name = SmithyBuilder.requiredState("name", builder.name);
        this.image = builder.image;
    }

    public String name() {
        return name;
    }

    public DataStream image() {
        return image;
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        GetPersonImageOutput that = (GetPersonImageOutput) other;
        return Objects.equals(name, that.name)
               && Objects.equals(image, that.image);

    }

    @Override
    public int hashCode() {
        return Objects.hash(name, image);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        // Placeholder. Do nothing
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link GetPersonImageOutput}.
     */
    public static final class Builder implements SdkShapeBuilder<GetPersonImageOutput> {
        private String name;
        private DataStream image = DataStream.ofEmpty();

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
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
        public GetPersonImageOutput build() {
            return new GetPersonImageOutput(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            // PLACEHOLDER. Needs implementation
            return this;
        }
    }
}

