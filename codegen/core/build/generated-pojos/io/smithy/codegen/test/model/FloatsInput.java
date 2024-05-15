

package io.smithy.codegen.test.model;

import java.util.Objects;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
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
public final class FloatsInput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#FloatsInput");

    private static final SdkSchema SCHEMA_REQUIRED_FLOAT = SdkSchema.memberBuilder("requiredFloat", PreludeSchemas.FLOAT)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_FLOAT = SdkSchema.memberBuilder("optionalFloat", PreludeSchemas.FLOAT)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_FLOAT = SdkSchema.memberBuilder("defaultFloat", PreludeSchemas.FLOAT)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1.0)
            )
        )
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
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

    private FloatsInput(Builder builder) {
        this.requiredFloat = builder.requiredFloat;
        this.optionalFloat = builder.optionalFloat;
        this.defaultFloat = builder.defaultFloat;
    }

    public Float requiredFloat() {
        return requiredFloat;
    }

    public float optionalFloat() {
        return optionalFloat;
    }

    public Float defaultFloat() {
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
        FloatsInput that = (FloatsInput) other;
        return requiredFloat == that.requiredFloat
               && Objects.equals(optionalFloat, that.optionalFloat)
               && defaultFloat == that.defaultFloat;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredFloat, optionalFloat, defaultFloat);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this, InnerSerializer.INSTANCE);
    }

    static final class InnerSerializer implements BiConsumer<FloatsInput, ShapeSerializer> {
        static final InnerSerializer INSTANCE = new InnerSerializer();

        @Override
        public void accept(FloatsInput shape, ShapeSerializer serializer) {
            serializer.writeFloat(SCHEMA_REQUIRED_FLOAT, shape.requiredFloat);
            if (shape.optionalFloat != null) {
                serializer.writeFloat(SCHEMA_OPTIONAL_FLOAT, shape.optionalFloat);
            }
            serializer.writeFloat(SCHEMA_DEFAULT_FLOAT, shape.defaultFloat);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link FloatsInput}.
     */
    public static final class Builder implements SdkShapeBuilder<FloatsInput> {
        private float requiredFloat;
        private Float optionalFloat;
        private float defaultFloat = 1.0f;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

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
        public FloatsInput build() {
            tracker.validate();
            return new FloatsInput(this);
        }

        @Override
        public SdkShapeBuilder<FloatsInput> errorCorrection() {
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
            public void accept(Builder builder, SdkSchema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.requiredFloat(de.readFloat(member));
                    case 1 -> builder.optionalFloat(de.readFloat(member));
                    case 2 -> builder.defaultFloat(de.readFloat(member));
                }
            }
        }
    }
}

