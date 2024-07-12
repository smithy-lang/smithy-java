/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class PutPersonImageInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonImageInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("name", PreludeSchemas.STRING,
            new HttpLabelTrait(),
            new RequiredTrait())
        .putMember("tags", SharedSchemas.LIST_OF_STRING,
            new HttpHeaderTrait("Tags"))
        .putMember("moreTags", SharedSchemas.LIST_OF_STRING,
            new HttpQueryTrait("MoreTags"))
        .putMember("image", SharedSchemas.STREAM,
            new HttpPayloadTrait(),
            new RequiredTrait())
        .build();

    private static final Schema SCHEMA_NAME = SCHEMA.member("name");
    private static final Schema SCHEMA_TAGS = SCHEMA.member("tags");
    private static final Schema SCHEMA_MORE_TAGS = SCHEMA.member("moreTags");
    private static final Schema SCHEMA_IMAGE = SCHEMA.member("image");

    private transient final @NonNull String name;
    private transient final List<String> tags;
    private transient final List<String> moreTags;
    private transient final @NonNull DataStream image;

    private PutPersonImageInput(Builder builder) {
        this.name = builder.name;
        this.tags = builder.tags == null ? null : Collections.unmodifiableList(builder.tags);
        this.moreTags = builder.moreTags == null ? null : Collections.unmodifiableList(builder.moreTags);
        this.image = builder.image;
    }

    public @NonNull String name() {
        return name;
    }

    public List<String> tags() {
        if (tags == null) {
            return Collections.emptyList();
        }
        return tags;
    }

    public boolean hasTags() {
        return tags != null;
    }

    public List<String> moreTags() {
        if (moreTags == null) {
            return Collections.emptyList();
        }
        return moreTags;
    }

    public boolean hasMoreTags() {
        return moreTags != null;
    }

    public @NonNull DataStream image() {
        return image;
    }

    @Override
    public @NonNull String toString() {
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
        return Objects.equals(this.name, that.name)
               && Objects.equals(this.tags, that.tags)
               && Objects.equals(this.moreTags, that.moreTags)
               && Objects.equals(this.image, that.image);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, tags, moreTags, image);
    }

    @Override
    public void serialize(@NonNull ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(@NonNull ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_NAME, name);
        if (tags != null) {
            serializer.writeList(SCHEMA_TAGS, tags, SharedSerde.ListOfStringSerializer.INSTANCE);
        }
        if (moreTags != null) {
            serializer.writeList(SCHEMA_MORE_TAGS, moreTags, SharedSerde.ListOfStringSerializer.INSTANCE);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link PutPersonImageInput}.
     */
    public static final class Builder implements ShapeBuilder<PutPersonImageInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private String name;
        private List<String> tags;
        private List<String> moreTags;
        private DataStream image;

        private Builder() {}

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            tracker.setMember(SCHEMA_NAME);
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder moreTags(List<String> moreTags) {
            this.moreTags = moreTags;
            return this;
        }

        @Override
        public void setDataStream(DataStream stream) {
            image(stream);
        }

        public Builder image(DataStream image) {
            this.image = Objects.requireNonNull(image, "image cannot be null");
            tracker.setMember(SCHEMA_IMAGE);
            return this;
        }

        @Override
        public @NonNull PutPersonImageInput build() {
            tracker.validate();
            return new PutPersonImageInput(this);
        }

        @Override
        public ShapeBuilder<PutPersonImageInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_NAME)) {
                name("");
            }
            if (!tracker.checkMember(SCHEMA_IMAGE)) {
                image(DataStream.ofEmpty());
            }
            return this;
        }

        @Override
        public Builder deserialize(@NonNull ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
            return this;
        }

        private static final class InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
            private static final InnerDeserializer INSTANCE = new InnerDeserializer();

            @Override
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.name(de.readString(member));
                    case 2 -> builder.tags(SharedSerde.deserializeListOfString(member, de));
                    case 3 -> builder.moreTags(SharedSerde.deserializeListOfString(member, de));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.name(this.name);
        builder.tags(this.tags);
        builder.moreTags(this.moreTags);
        builder.image(this.image);
        return builder;
    }

}

