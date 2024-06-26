

package software.amazon.smithy.java.codegen.test.model;

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
public final class DoubleMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#DoubleMembersInput");

    private static final Schema SCHEMA_REQUIRED_DOUBLE = Schema.memberBuilder("requiredDouble", PreludeSchemas.DOUBLE)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final Schema SCHEMA_OPTIONAL_DOUBLE = Schema.memberBuilder("optionalDouble", PreludeSchemas.DOUBLE)
        .id(ID)
        .build();

    private static final Schema SCHEMA_DEFAULT_DOUBLE = Schema.memberBuilder("defaultDouble", PreludeSchemas.DOUBLE)
        .id(ID)
        .traits(
            new DefaultTrait(Node.from(1.0))
        )
        .build();

    static final Schema SCHEMA = Schema.builder()
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

    private DoubleMembersInput(Builder builder) {
        this.requiredDouble = builder.requiredDouble;
        this.optionalDouble = builder.optionalDouble;
        this.defaultDouble = builder.defaultDouble;
    }

    public double requiredDouble() {
        return requiredDouble;
    }

    public Double optionalDouble() {
        return optionalDouble;
    }

    public double defaultDouble() {
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
        DoubleMembersInput that = (DoubleMembersInput) other;
        return this.requiredDouble == that.requiredDouble
               && Objects.equals(this.optionalDouble, that.optionalDouble)
               && this.defaultDouble == that.defaultDouble;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredDouble, optionalDouble, defaultDouble);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
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
     * Builder for {@link DoubleMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<DoubleMembersInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private double requiredDouble;
        private Double optionalDouble;
        private double defaultDouble = 1.0;

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
        public DoubleMembersInput build() {
            tracker.validate();
            return new DoubleMembersInput(this);
        }

        @Override
        public ShapeBuilder<DoubleMembersInput> errorCorrection() {
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
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
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

