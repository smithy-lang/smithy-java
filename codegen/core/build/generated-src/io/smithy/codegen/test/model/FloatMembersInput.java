

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
public final class FloatMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#FloatMembersInput");

    private static final Schema SCHEMA_REQUIRED_FLOAT = Schema.memberBuilder("requiredFloat", PreludeSchemas.FLOAT)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final Schema SCHEMA_OPTIONAL_FLOAT = Schema.memberBuilder("optionalFloat", PreludeSchemas.FLOAT)
        .id(ID)
        .build();

    private static final Schema SCHEMA_DEFAULT_FLOAT = Schema.memberBuilder("defaultFloat", PreludeSchemas.FLOAT)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1.0)
            )
        )
        .build();

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_FLOAT,
            SCHEMA_OPTIONAL_FLOAT,
            SCHEMA_DEFAULT_FLOAT
        )
        .build();

    private transient final float requiredFloat;
    private transient final Float optionalFloat;
    private transient final float defaultFloat;

    private FloatMembersInput(Builder builder) {
        this.requiredFloat = builder.requiredFloat;
        this.optionalFloat = builder.optionalFloat;
        this.defaultFloat = builder.defaultFloat;
    }

    public float requiredFloat() {
        return requiredFloat;
    }

    public Float optionalFloat() {
        return optionalFloat;
    }

    public float defaultFloat() {
        return defaultFloat;
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
        FloatMembersInput that = (FloatMembersInput) other;
        return this.requiredFloat == that.requiredFloat
               && Objects.equals(this.optionalFloat, that.optionalFloat)
               && this.defaultFloat == that.defaultFloat;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredFloat, optionalFloat, defaultFloat);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeFloat(SCHEMA_REQUIRED_FLOAT, requiredFloat);

        if (optionalFloat != null) {
            serializer.writeFloat(SCHEMA_OPTIONAL_FLOAT, optionalFloat);
        }

        serializer.writeFloat(SCHEMA_DEFAULT_FLOAT, defaultFloat);

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link FloatMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<FloatMembersInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private float requiredFloat;
        private Float optionalFloat;
        private float defaultFloat = 1.0f;

        private Builder() {}

        public Builder requiredFloat(float requiredFloat) {
            this.requiredFloat = requiredFloat;
            tracker.setMember(SCHEMA_REQUIRED_FLOAT);
            return this;
        }

        public Builder optionalFloat(float optionalFloat) {
            this.optionalFloat = optionalFloat;
            return this;
        }

        public Builder defaultFloat(float defaultFloat) {
            this.defaultFloat = defaultFloat;
            return this;
        }

        @Override
        public FloatMembersInput build() {
            tracker.validate();
            return new FloatMembersInput(this);
        }

        @Override
        public ShapeBuilder<FloatMembersInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_REQUIRED_FLOAT)) {
                tracker.setMember(SCHEMA_REQUIRED_FLOAT);
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
                    case 0 -> builder.requiredFloat(de.readFloat(member));
                    case 1 -> builder.optionalFloat(de.readFloat(member));
                    case 2 -> builder.defaultFloat(de.readFloat(member));
                }
            }
        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredFloat(this.requiredFloat);
        builder.optionalFloat(this.optionalFloat);
        builder.defaultFloat(this.defaultFloat);
        return builder;
    }

}

