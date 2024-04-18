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
public final class PutPersonImageInput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonImageInput");

    private static final SdkSchema SCHEMA_NAME = SdkSchema.memberBuilder(0, "name", PreludeSchemas.STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_TAGS = SdkSchema.memberBuilder(1, "tags", PreludeSchemas.STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_MORE_TAGS = SdkSchema.memberBuilder(2, "moreTags", PreludeSchemas.STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_IMAGE = SdkSchema.memberBuilder(3, "image", SharedSchemas.STREAM)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_NAME,
            SCHEMA_TAGS,
            SCHEMA_MORE_TAGS,
            SCHEMA_IMAGE
        )
        .build();

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
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        PutPersonImageInput that = (PutPersonImageInput) other;
        return Objects.equals(name, that.name)
               && Objects.equals(tags, that.tags)
               && Objects.equals(moreTags, that.moreTags)
               && Objects.equals(image, that.image);

    }

    @Override
    public int hashCode() {
        return Objects.hash(name, tags, moreTags, image);
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
            decoder.readStruct(SCHEMA, (member, de) -> {
                int index = member.memberIndex() == -1
                    ? SCHEMA.member(member.memberName()).memberIndex()
                    : member.memberIndex();
                switch (index) {
                    case 0 -> name(de.readString(member));
                    case 1 -> tags(de.readString(member));
                    case 2 -> moreTags(de.readString(member));
                }
            });
            return this;
        }
    }
}

