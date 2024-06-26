

package io.smithy.codegen.test.model;

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
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class LongMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#LongMembersInput");

    private static final Schema SCHEMA_REQUIRED_LONGS = Schema.memberBuilder("requiredLongs", PreludeSchemas.LONG)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final Schema SCHEMA_OPTIONAL_LONGS = Schema.memberBuilder("optionalLongs", PreludeSchemas.LONG)
        .id(ID)
        .build();

    private static final Schema SCHEMA_DEFAULT_SHORT = Schema.memberBuilder("defaultShort", PreludeSchemas.LONG)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1)
            )
        )
        .build();

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_LONGS,
            SCHEMA_OPTIONAL_LONGS,
            SCHEMA_DEFAULT_SHORT
        )
        .build();

    private transient final long requiredLongs;
    private transient final Long optionalLongs;
    private transient final long defaultShort;

    private LongMembersInput(Builder builder) {
        this.requiredLongs = builder.requiredLongs;
        this.optionalLongs = builder.optionalLongs;
        this.defaultShort = builder.defaultShort;
    }

    public long requiredLongs() {
        return requiredLongs;
    }

    public Long optionalLongs() {
        return optionalLongs;
    }

    public long defaultShort() {
        return defaultShort;
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
        LongMembersInput that = (LongMembersInput) other;
        return this.requiredLongs == that.requiredLongs
               && Objects.equals(this.optionalLongs, that.optionalLongs)
               && this.defaultShort == that.defaultShort;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredLongs, optionalLongs, defaultShort);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeLong(SCHEMA_REQUIRED_LONGS, requiredLongs);

        if (optionalLongs != null) {
            serializer.writeLong(SCHEMA_OPTIONAL_LONGS, optionalLongs);
        }

        serializer.writeLong(SCHEMA_DEFAULT_SHORT, defaultShort);

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link LongMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<LongMembersInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private long requiredLongs;
        private Long optionalLongs;
        private long defaultShort = 1L;

        private Builder() {}

        public Builder requiredLongs(long requiredLongs) {
            this.requiredLongs = requiredLongs;
            tracker.setMember(SCHEMA_REQUIRED_LONGS);
            return this;
        }

        public Builder optionalLongs(long optionalLongs) {
            this.optionalLongs = optionalLongs;
            return this;
        }

        public Builder defaultShort(long defaultShort) {
            this.defaultShort = defaultShort;
            return this;
        }

        @Override
        public LongMembersInput build() {
            tracker.validate();
            return new LongMembersInput(this);
        }

        @Override
        public ShapeBuilder<LongMembersInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_REQUIRED_LONGS)) {
                tracker.setMember(SCHEMA_REQUIRED_LONGS);
            }
            return this;
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
            return this;
        }

        private static final class InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
            private static final InnerDeserializer INSTANCE = new InnerDeserializer();

            @Override
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.requiredLongs(de.readLong(member));
                    case 1 -> builder.optionalLongs(de.readLong(member));
                    case 2 -> builder.defaultShort(de.readLong(member));
                }
            }
        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredLongs(this.requiredLongs);
        builder.optionalLongs(this.optionalLongs);
        builder.defaultShort(this.defaultShort);
        return builder;
    }

}

