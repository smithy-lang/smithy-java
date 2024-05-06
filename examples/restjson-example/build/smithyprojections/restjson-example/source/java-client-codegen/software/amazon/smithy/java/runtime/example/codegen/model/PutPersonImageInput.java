/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.codegen.model;

import java.util.Objects;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class PutPersonImageInput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonImageInput");

    private static final SdkSchema SCHEMA_NAME = SdkSchema.memberBuilder("name", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new HttpLabelTrait(),
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_TAGS = SdkSchema.memberBuilder("tags", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new HttpHeaderTrait("Tags")
        )
        .build();

    private static final SdkSchema SCHEMA_MORE_TAGS = SdkSchema.memberBuilder("moreTags", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new HttpQueryTrait("MoreTags")
        )
        .build();

    private static final SdkSchema SCHEMA_IMAGE = SdkSchema.memberBuilder("image", SharedSchemas.STREAM)
        .id(ID)
        .traits(
            new HttpPayloadTrait(),
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.nullNode()
            )
        )
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
        serializer.writeStruct(SCHEMA, this, InnerSerializer.INSTANCE);
    }

    private static final class InnerSerializer implements BiConsumer<PutPersonImageInput, ShapeSerializer> {
        private static final InnerSerializer INSTANCE = new InnerSerializer();

        @Override
        public void accept(PutPersonImageInput shape, ShapeSerializer serializer) {
            serializer.writeString(SCHEMA_NAME, shape.name);
            if (shape.tags != null) {
                serializer.writeString(SCHEMA_TAGS, shape.tags);
            }
            if (shape.moreTags != null) {
                serializer.writeString(SCHEMA_MORE_TAGS, shape.moreTags);
            }
        }
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

        private static final InnerDeserializer INNER_DESERIALIZER = new InnerDeserializer();

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
            decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
            return this;
        }

        private static final class InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
            private static final InnerDeserializer INSTANCE = new InnerDeserializer();
            @Override
            public void accept(Builder builder, SdkSchema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.name(de.readString(member));
                    case 1 -> builder.tags(de.readString(member));
                    case 2 -> builder.moreTags(de.readString(member));
                }
            }
        }
    }
}

