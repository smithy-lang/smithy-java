

package io.smithy.codegen.test.model;

import java.time.Instant;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
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
public final class TimestampsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#TimestampsInput");

    private static final SdkSchema SCHEMA_REQUIRED_TIMESTAMP = SdkSchema.memberBuilder("requiredTimestamp", PreludeSchemas.TIMESTAMP)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_TIMESTAMP = SdkSchema.memberBuilder("optionalTimestamp", PreludeSchemas.TIMESTAMP)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_TIMESTAMP = SdkSchema.memberBuilder("defaultTimestamp", PreludeSchemas.TIMESTAMP)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from("1985-04-12T23:20:50.52Z")
            )
        )
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_TIMESTAMP,
            SCHEMA_OPTIONAL_TIMESTAMP,
            SCHEMA_DEFAULT_TIMESTAMP
        )
        .build();

    private transient final Instant requiredTimestamp;
    private transient final Instant optionalTimestamp;
    private transient final Instant defaultTimestamp;

    private TimestampsInput(Builder builder) {
        this.requiredTimestamp = builder.requiredTimestamp;
        this.optionalTimestamp = builder.optionalTimestamp;
        this.defaultTimestamp = builder.defaultTimestamp;
    }

    public Instant requiredTimestamp() {
        return requiredTimestamp;
    }

    public Instant optionalTimestamp() {
        return optionalTimestamp;
    }

    public Instant defaultTimestamp() {
        return defaultTimestamp;
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
        TimestampsInput that = (TimestampsInput) other;
        return Objects.equals(requiredTimestamp, that.requiredTimestamp)
               && Objects.equals(optionalTimestamp, that.optionalTimestamp)
               && Objects.equals(defaultTimestamp, that.defaultTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredTimestamp, optionalTimestamp, defaultTimestamp);
    }

    @Override
    public SdkSchema schema() {
        return SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeTimestamp(SCHEMA_REQUIRED_TIMESTAMP, requiredTimestamp);

        if (optionalTimestamp != null) {
            serializer.writeTimestamp(SCHEMA_OPTIONAL_TIMESTAMP, optionalTimestamp);
        }

        serializer.writeTimestamp(SCHEMA_DEFAULT_TIMESTAMP, defaultTimestamp);

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link TimestampsInput}.
     */
    public static final class Builder implements SdkShapeBuilder<TimestampsInput> {
        private static final Instant DEFAULT_TIMESTAMP_DEFAULT = Instant.ofEpochMilli(482196050520L);
        private Instant requiredTimestamp;
        private Instant optionalTimestamp;
        private Instant defaultTimestamp = DEFAULT_TIMESTAMP_DEFAULT;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder requiredTimestamp(Instant requiredTimestamp) {
            this.requiredTimestamp = Objects.requireNonNull(requiredTimestamp, "requiredTimestamp cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_TIMESTAMP);
            return this;
        }

        public Builder optionalTimestamp(Instant optionalTimestamp) {
            this.optionalTimestamp = optionalTimestamp;
            return this;
        }

        public Builder defaultTimestamp(Instant defaultTimestamp) {
            this.defaultTimestamp = Objects.requireNonNull(defaultTimestamp, "defaultTimestamp cannot be null");
            return this;
        }

        @Override
        public TimestampsInput build() {
            tracker.validate();
            return new TimestampsInput(this);
        }

        @Override
        public SdkShapeBuilder<TimestampsInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }

            if (!tracker.checkMember(SCHEMA_REQUIRED_TIMESTAMP)) {
                requiredTimestamp(Instant.EPOCH);
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
            public void accept(Builder builder, SdkSchema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.requiredTimestamp(de.readTimestamp(member));
                    case 1 -> builder.optionalTimestamp(de.readTimestamp(member));
                    case 2 -> builder.defaultTimestamp(de.readTimestamp(member));
                }
            }
        }
    }
}

