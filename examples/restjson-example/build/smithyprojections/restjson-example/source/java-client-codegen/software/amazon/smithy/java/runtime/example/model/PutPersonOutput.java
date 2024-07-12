/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class PutPersonOutput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonOutput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("name", PreludeSchemas.STRING,
            new RequiredTrait())
        .putMember("favoriteColor", PreludeSchemas.STRING,
            new HttpHeaderTrait("X-Favorite-Color"))
        .putMember("age", PreludeSchemas.INTEGER,
            new JsonNameTrait("Age"),
            new DefaultTrait(Node.from(1)))
        .putMember("birthday", SharedSchemas.BIRTHDAY)
        .build();

    private static final Schema SCHEMA_NAME = SCHEMA.member("name");
    private static final Schema SCHEMA_FAVORITE_COLOR = SCHEMA.member("favoriteColor");
    private static final Schema SCHEMA_AGE = SCHEMA.member("age");
    private static final Schema SCHEMA_BIRTHDAY = SCHEMA.member("birthday");

    private transient final @NonNull String name;
    private transient final String favoriteColor;
    private transient final int age;
    private transient final Instant birthday;

    private PutPersonOutput(Builder builder) {
        this.name = builder.name;
        this.favoriteColor = builder.favoriteColor;
        this.age = builder.age;
        this.birthday = builder.birthday;
    }

    public @NonNull String name() {
        return name;
    }

    public String favoriteColor() {
        return favoriteColor;
    }

    public int age() {
        return age;
    }

    public Instant birthday() {
        return birthday;
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
        PutPersonOutput that = (PutPersonOutput) other;
        return Objects.equals(this.name, that.name)
               && Objects.equals(this.favoriteColor, that.favoriteColor)
               && this.age == that.age
               && Objects.equals(this.birthday, that.birthday);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, favoriteColor, age, birthday);
    }

    @Override
    public void serialize(@NonNull ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(@NonNull ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_NAME, name);
        if (favoriteColor != null) {
            serializer.writeString(SCHEMA_FAVORITE_COLOR, favoriteColor);
        }
        serializer.writeInteger(SCHEMA_AGE, age);
        if (birthday != null) {
            serializer.writeTimestamp(SCHEMA_BIRTHDAY, birthday);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link PutPersonOutput}.
     */
    public static final class Builder implements ShapeBuilder<PutPersonOutput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private String name;
        private String favoriteColor;
        private int age = 1;
        private Instant birthday;

        private Builder() {}

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            tracker.setMember(SCHEMA_NAME);
            return this;
        }

        public Builder favoriteColor(String favoriteColor) {
            this.favoriteColor = favoriteColor;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public Builder birthday(Instant birthday) {
            this.birthday = birthday;
            return this;
        }

        @Override
        public @NonNull PutPersonOutput build() {
            tracker.validate();
            return new PutPersonOutput(this);
        }

        @Override
        public ShapeBuilder<PutPersonOutput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_NAME)) {
                name("");
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
                    case 1 -> builder.favoriteColor(de.readString(member));
                    case 2 -> builder.age(de.readInteger(member));
                    case 3 -> builder.birthday(de.readTimestamp(member));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.name(this.name);
        builder.favoriteColor(this.favoriteColor);
        builder.age(this.age);
        builder.birthday(this.birthday);
        return builder;
    }

}

