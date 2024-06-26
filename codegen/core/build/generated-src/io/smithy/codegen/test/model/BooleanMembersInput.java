

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
public final class BooleanMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#BooleanMembersInput");

    private static final Schema SCHEMA_REQUIRED_BOOLEAN = Schema.memberBuilder("requiredBoolean", PreludeSchemas.BOOLEAN)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final Schema SCHEMA_DEFAULT_BOOLEAN = Schema.memberBuilder("defaultBoolean", PreludeSchemas.BOOLEAN)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(true)
            )
        )
        .build();

    private static final Schema SCHEMA_OPTIONAL_BOOLEAN = Schema.memberBuilder("optionalBoolean", PreludeSchemas.BOOLEAN)
        .id(ID)
        .build();

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_BOOLEAN,
            SCHEMA_DEFAULT_BOOLEAN,
            SCHEMA_OPTIONAL_BOOLEAN
        )
        .build();

    private transient final boolean requiredBoolean;
    private transient final boolean defaultBoolean;
    private transient final Boolean optionalBoolean;

    private BooleanMembersInput(Builder builder) {
        this.requiredBoolean = builder.requiredBoolean;
        this.defaultBoolean = builder.defaultBoolean;
        this.optionalBoolean = builder.optionalBoolean;
    }

    public boolean requiredBoolean() {
        return requiredBoolean;
    }

    public boolean defaultBoolean() {
        return defaultBoolean;
    }

    public Boolean optionalBoolean() {
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
        BooleanMembersInput that = (BooleanMembersInput) other;
        return this.requiredBoolean == that.requiredBoolean
               && this.defaultBoolean == that.defaultBoolean
               && Objects.equals(this.optionalBoolean, that.optionalBoolean);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredBoolean, defaultBoolean, optionalBoolean);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeBoolean(SCHEMA_REQUIRED_BOOLEAN, requiredBoolean);

        serializer.writeBoolean(SCHEMA_DEFAULT_BOOLEAN, defaultBoolean);

        if (optionalBoolean != null) {
            serializer.writeBoolean(SCHEMA_OPTIONAL_BOOLEAN, optionalBoolean);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link BooleanMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<BooleanMembersInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private boolean requiredBoolean;
        private boolean defaultBoolean = true;
        private Boolean optionalBoolean;

        private Builder() {}

        public Builder requiredBoolean(boolean requiredBoolean) {
            this.requiredBoolean = requiredBoolean;
            tracker.setMember(SCHEMA_REQUIRED_BOOLEAN);
            return this;
        }

        public Builder defaultBoolean(boolean defaultBoolean) {
            this.defaultBoolean = defaultBoolean;
            return this;
        }

        public Builder optionalBoolean(boolean optionalBoolean) {
            this.optionalBoolean = optionalBoolean;
            return this;
        }

        @Override
        public BooleanMembersInput build() {
            tracker.validate();
            return new BooleanMembersInput(this);
        }

        @Override
        public ShapeBuilder<BooleanMembersInput> errorCorrection() {
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
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.requiredBoolean(de.readBoolean(member));
                    case 1 -> builder.defaultBoolean(de.readBoolean(member));
                    case 2 -> builder.optionalBoolean(de.readBoolean(member));
                }
            }
        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredBoolean(this.requiredBoolean);
        builder.defaultBoolean(this.defaultBoolean);
        builder.optionalBoolean(this.optionalBoolean);
        return builder;
    }

}

