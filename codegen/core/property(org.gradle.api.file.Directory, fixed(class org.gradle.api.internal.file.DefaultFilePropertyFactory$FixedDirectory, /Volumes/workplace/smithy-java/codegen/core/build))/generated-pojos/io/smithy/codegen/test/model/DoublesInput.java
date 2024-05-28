

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
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class DoublesInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#DoublesInput");

    private static final SdkSchema SCHEMA_REQUIRED_DOUBLE = SdkSchema.memberBuilder("requiredDouble", PreludeSchemas.DOUBLE)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_DOUBLE = SdkSchema.memberBuilder("optionalDouble", PreludeSchemas.DOUBLE)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_DOUBLE = SdkSchema.memberBuilder("defaultDouble", PreludeSchemas.DOUBLE)
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
            SCHEMA_REQUIRED_DOUBLE,
            SCHEMA_OPTIONAL_DOUBLE,
            SCHEMA_DEFAULT_DOUBLE
        )
        .build();

    private transient final double requiredDouble;
    private transient final Double optionalDouble;
    private transient final double defaultDouble;

    private DoublesInput(Builder builder) {
        this.requiredDouble = builder.requiredDouble;
        this.optionalDouble = builder.optionalDouble;
        this.defaultDouble = builder.defaultDouble;
    }

    public Double requiredDouble() {
        return requiredDouble;
    }

    public double optionalDouble() {
        return optionalDouble;
    }

    public Double defaultDouble() {
        return defaultDouble;
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
        DoublesInput that = (DoublesInput) other;
        return requiredDouble == that.requiredDouble
               && Objects.equals(optionalDouble, that.optionalDouble)
               && defaultDouble == that.defaultDouble;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredDouble, optionalDouble, defaultDouble);
    }

    @Override
    public SdkSchema schema() {
        return SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeDouble(SCHEMA_REQUIRED_DOUBLE, requiredDouble);

        if (optionalDouble != null) {
            serializer.writeDouble(SCHEMA_OPTIONAL_DOUBLE, optionalDouble);
        }

        serializer.writeDouble(SCHEMA_DEFAULT_DOUBLE, defaultDouble);

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DoublesInput}.
     */
    public static final class Builder implements SdkShapeBuilder<DoublesInput> {
        private double requiredDouble;
        private Double optionalDouble;
        private double defaultDouble = 1.0;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder requiredDouble(double requiredDouble) {
            this.requiredDouble = requiredDouble;
            tracker.setMember(SCHEMA_REQUIRED_DOUBLE);
            return this;
        }

        public Builder optionalDouble(double optionalDouble) {
            this.optionalDouble = optionalDouble;
            return this;
        }

        public Builder defaultDouble(double defaultDouble) {
            this.defaultDouble = defaultDouble;
            return this;
        }

        @Override
        public DoublesInput build() {
            tracker.validate();
            return new DoublesInput(this);
        }

        @Override
        public SdkShapeBuilder<DoublesInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }

            if (!tracker.checkMember(SCHEMA_REQUIRED_DOUBLE)) {
                tracker.setMember(SCHEMA_REQUIRED_DOUBLE);
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
                    case 0 -> builder.requiredDouble(de.readDouble(member));
                    case 1 -> builder.optionalDouble(de.readDouble(member));
                    case 2 -> builder.defaultDouble(de.readDouble(member));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredDouble(this.requiredDouble);
        builder.optionalDouble(this.optionalDouble);
        builder.defaultDouble(this.defaultDouble);
        return builder;
    }

}

