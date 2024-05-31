

package io.smithy.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class BooleansInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#BooleansInput");

    private static final SdkSchema SCHEMA_REQUIRED_BOOLEAN = SdkSchema.memberBuilder("requiredBoolean", PreludeSchemas.BOOLEAN)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_BOOLEAN = SdkSchema.memberBuilder("optionalBoolean", PreludeSchemas.BOOLEAN)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_BOOLEAN,
            SCHEMA_OPTIONAL_BOOLEAN
        )
        .build();

    private transient final boolean requiredBoolean;
    private transient final Boolean optionalBoolean;

    private BooleansInput(Builder builder) {
        this.requiredBoolean = builder.requiredBoolean;
        this.optionalBoolean = builder.optionalBoolean;
    }

    public Boolean requiredBoolean() {
        return requiredBoolean;
    }

    public boolean optionalBoolean() {
        return optionalBoolean;
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
        BooleansInput that = (BooleansInput) other;
        return requiredBoolean == that.requiredBoolean
               && Objects.equals(optionalBoolean, that.optionalBoolean);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredBoolean, optionalBoolean);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeBoolean(SCHEMA_REQUIRED_BOOLEAN, requiredBoolean);

        if (optionalBoolean != null) {
            serializer.writeBoolean(SCHEMA_OPTIONAL_BOOLEAN, optionalBoolean);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link BooleansInput}.
     */
    public static final class Builder implements SdkShapeBuilder<BooleansInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private boolean requiredBoolean;
        private Boolean optionalBoolean;

        private Builder() {}

        public Builder requiredBoolean(boolean requiredBoolean) {
            this.requiredBoolean = requiredBoolean;
            tracker.setMember(SCHEMA_REQUIRED_BOOLEAN);
            return this;
        }

        public Builder optionalBoolean(boolean optionalBoolean) {
            this.optionalBoolean = optionalBoolean;
            return this;
        }

        @Override
        public BooleansInput build() {
            tracker.validate();
            return new BooleansInput(this);
        }

        @Override
        public SdkShapeBuilder<BooleansInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_REQUIRED_BOOLEAN)) {
                tracker.setMember(SCHEMA_REQUIRED_BOOLEAN);
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
                    case 0 -> builder.requiredBoolean(de.readBoolean(member));
                    case 1 -> builder.optionalBoolean(de.readBoolean(member));
                }
            }
        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredBoolean(this.requiredBoolean);
        builder.optionalBoolean(this.optionalBoolean);
        return builder;
    }

}

