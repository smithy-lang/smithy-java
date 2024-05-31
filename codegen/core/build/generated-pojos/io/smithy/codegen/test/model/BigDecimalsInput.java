

package io.smithy.codegen.test.model;

import java.math.BigDecimal;
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
public final class BigDecimalsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#BigDecimalsInput");

    private static final SdkSchema SCHEMA_REQUIRED_BIG_DECIMAL = SdkSchema.memberBuilder("requiredBigDecimal", PreludeSchemas.BIG_DECIMAL)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_BIG_DECIMAL = SdkSchema.memberBuilder("optionalBigDecimal", PreludeSchemas.BIG_DECIMAL)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_BIG_DECIMAL = SdkSchema.memberBuilder("defaultBigDecimal", PreludeSchemas.BIG_DECIMAL)
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
            SCHEMA_REQUIRED_BIG_DECIMAL,
            SCHEMA_OPTIONAL_BIG_DECIMAL,
            SCHEMA_DEFAULT_BIG_DECIMAL
        )
        .build();

    private transient final BigDecimal requiredBigDecimal;
    private transient final BigDecimal optionalBigDecimal;
    private transient final BigDecimal defaultBigDecimal;

    private BigDecimalsInput(Builder builder) {
        this.requiredBigDecimal = builder.requiredBigDecimal;
        this.optionalBigDecimal = builder.optionalBigDecimal;
        this.defaultBigDecimal = builder.defaultBigDecimal;
    }

    public BigDecimal requiredBigDecimal() {
        return requiredBigDecimal;
    }

    public BigDecimal optionalBigDecimal() {
        return optionalBigDecimal;
    }

    public BigDecimal defaultBigDecimal() {
        return defaultBigDecimal;
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
        BigDecimalsInput that = (BigDecimalsInput) other;
        return Objects.equals(requiredBigDecimal, that.requiredBigDecimal)
               && Objects.equals(optionalBigDecimal, that.optionalBigDecimal)
               && Objects.equals(defaultBigDecimal, that.defaultBigDecimal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredBigDecimal, optionalBigDecimal, defaultBigDecimal);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeBigDecimal(SCHEMA_REQUIRED_BIG_DECIMAL, requiredBigDecimal);

        if (optionalBigDecimal != null) {
            serializer.writeBigDecimal(SCHEMA_OPTIONAL_BIG_DECIMAL, optionalBigDecimal);
        }

        serializer.writeBigDecimal(SCHEMA_DEFAULT_BIG_DECIMAL, defaultBigDecimal);

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link BigDecimalsInput}.
     */
    public static final class Builder implements SdkShapeBuilder<BigDecimalsInput> {
        private static final BigDecimal DEFAULT_BIG_DECIMAL_DEFAULT = BigDecimal.valueOf(1.0);
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private BigDecimal requiredBigDecimal;
        private BigDecimal optionalBigDecimal;
        private BigDecimal defaultBigDecimal = DEFAULT_BIG_DECIMAL_DEFAULT;

        private Builder() {}

        public Builder requiredBigDecimal(BigDecimal requiredBigDecimal) {
            this.requiredBigDecimal = Objects.requireNonNull(requiredBigDecimal, "requiredBigDecimal cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_BIG_DECIMAL);
            return this;
        }

        public Builder optionalBigDecimal(BigDecimal optionalBigDecimal) {
            this.optionalBigDecimal = optionalBigDecimal;
            return this;
        }

        public Builder defaultBigDecimal(BigDecimal defaultBigDecimal) {
            this.defaultBigDecimal = Objects.requireNonNull(defaultBigDecimal, "defaultBigDecimal cannot be null");
            return this;
        }

        @Override
        public BigDecimalsInput build() {
            tracker.validate();
            return new BigDecimalsInput(this);
        }

        @Override
        public SdkShapeBuilder<BigDecimalsInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_REQUIRED_BIG_DECIMAL)) {
                requiredBigDecimal(BigDecimal.ZERO);
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
                    case 0 -> builder.requiredBigDecimal(de.readBigDecimal(member));
                    case 1 -> builder.optionalBigDecimal(de.readBigDecimal(member));
                    case 2 -> builder.defaultBigDecimal(de.readBigDecimal(member));
                }
            }
        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredBigDecimal(this.requiredBigDecimal);
        builder.optionalBigDecimal(this.optionalBigDecimal);
        builder.defaultBigDecimal(this.defaultBigDecimal);
        return builder;
    }

}

