/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.model;

import edu.umd.cs.findbugs.annotations.NonNull;
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
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class GetPersonImageOutput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.example#GetPersonImageOutput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("name", PreludeSchemas.STRING,
            new HttpHeaderTrait("Person-Name"),
            new RequiredTrait())
        .putMember("image", SharedSchemas.STREAM,
            new HttpPayloadTrait(),
            new RequiredTrait())
        .build();

    private static final Schema SCHEMA_NAME = SCHEMA.member("name");
    private static final Schema SCHEMA_IMAGE = SCHEMA.member("image");

    private transient final @NonNull String name;
    private transient final @NonNull DataStream image;

    private GetPersonImageOutput(Builder builder) {
        this.name = builder.name;
        this.image = builder.image;
    }

    public @NonNull String name() {
        return name;
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
        GetPersonImageOutput that = (GetPersonImageOutput) other;
        return Objects.equals(this.name, that.name)
               && Objects.equals(this.image, that.image);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, image);
    }

    @Override
    public void serialize(@NonNull ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(@NonNull ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_NAME, name);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link GetPersonImageOutput}.
     */
    public static final class Builder implements ShapeBuilder<GetPersonImageOutput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private String name;
        private DataStream image;

        private Builder() {}

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            tracker.setMember(SCHEMA_NAME);
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
        public @NonNull GetPersonImageOutput build() {
            tracker.validate();
            return new GetPersonImageOutput(this);
        }

        @Override
        public ShapeBuilder<GetPersonImageOutput> errorCorrection() {
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
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.name(this.name);
        builder.image(this.image);
        return builder;
    }

}

