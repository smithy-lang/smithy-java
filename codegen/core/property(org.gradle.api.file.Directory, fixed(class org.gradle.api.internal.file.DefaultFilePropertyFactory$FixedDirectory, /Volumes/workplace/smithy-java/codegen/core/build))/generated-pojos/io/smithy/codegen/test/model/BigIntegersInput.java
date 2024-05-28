

package io.smithy.codegen.test.model;

import java.math.BigInteger;
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
public final class BigIntegersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#BigIntegersInput");

    private static final SdkSchema SCHEMA_REQUIRED_BIG_INTEGER = SdkSchema.memberBuilder("requiredBigInteger", PreludeSchemas.BIG_INTEGER)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_BIG_INTEGER = SdkSchema.memberBuilder("optionalBigInteger", PreludeSchemas.BIG_INTEGER)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_BIG_INTEGER = SdkSchema.memberBuilder("defaultBigInteger", PreludeSchemas.BIG_INTEGER)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1)
            )
        )
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_BIG_INTEGER,
            SCHEMA_OPTIONAL_BIG_INTEGER,
            SCHEMA_DEFAULT_BIG_INTEGER
        )
        .build();

    private transient final BigInteger requiredBigInteger;
    private transient final BigInteger optionalBigInteger;
    private transient final BigInteger defaultBigInteger;

    private BigIntegersInput(Builder builder) {
        this.requiredBigInteger = builder.requiredBigInteger;
        this.optionalBigInteger = builder.optionalBigInteger;
        this.defaultBigInteger = builder.defaultBigInteger;
    }

    public BigInteger requiredBigInteger() {
        return requiredBigInteger;
    }

    public BigInteger optionalBigInteger() {
        return optionalBigInteger;
    }

    public BigInteger defaultBigInteger() {
        return defaultBigInteger;
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
        BigIntegersInput that = (BigIntegersInput) other;
        return Objects.equals(requiredBigInteger, that.requiredBigInteger)
               && Objects.equals(optionalBigInteger, that.optionalBigInteger)
               && Objects.equals(defaultBigInteger, that.defaultBigInteger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredBigInteger, optionalBigInteger, defaultBigInteger);
    }

    @Override
    public SdkSchema schema() {
        return SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeBigInteger(SCHEMA_REQUIRED_BIG_INTEGER, requiredBigInteger);

        if (optionalBigInteger != null) {
            serializer.writeBigInteger(SCHEMA_OPTIONAL_BIG_INTEGER, optionalBigInteger);
        }

        serializer.writeBigInteger(SCHEMA_DEFAULT_BIG_INTEGER, defaultBigInteger);

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link BigIntegersInput}.
     */
    public static final class Builder implements SdkShapeBuilder<BigIntegersInput> {
        private static final BigInteger DEFAULT_BIG_INTEGER_DEFAULT = BigInteger.valueOf(1);
        private BigInteger requiredBigInteger;
        private BigInteger optionalBigInteger;
        private BigInteger defaultBigInteger = DEFAULT_BIG_INTEGER_DEFAULT;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder requiredBigInteger(BigInteger requiredBigInteger) {
            this.requiredBigInteger = Objects.requireNonNull(requiredBigInteger, "requiredBigInteger cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_BIG_INTEGER);
            return this;
        }

        public Builder optionalBigInteger(BigInteger optionalBigInteger) {
            this.optionalBigInteger = optionalBigInteger;
            return this;
        }

        public Builder defaultBigInteger(BigInteger defaultBigInteger) {
            this.defaultBigInteger = Objects.requireNonNull(defaultBigInteger, "defaultBigInteger cannot be null");
            return this;
        }

        @Override
        public BigIntegersInput build() {
            tracker.validate();
            return new BigIntegersInput(this);
        }

        @Override
        public SdkShapeBuilder<BigIntegersInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }

            if (!tracker.checkMember(SCHEMA_REQUIRED_BIG_INTEGER)) {
                requiredBigInteger(BigInteger.ZERO);
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
                    case 0 -> builder.requiredBigInteger(de.readBigInteger(member));
                    case 1 -> builder.optionalBigInteger(de.readBigInteger(member));
                    case 2 -> builder.defaultBigInteger(de.readBigInteger(member));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredBigInteger(this.requiredBigInteger);
        builder.optionalBigInteger(this.optionalBigInteger);
        builder.defaultBigInteger(this.defaultBigInteger);
        return builder;
    }

}

