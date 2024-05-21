

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
public final class IntegersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#IntegersInput");

    private static final SdkSchema SCHEMA_REQUIRED_INT = SdkSchema.memberBuilder("requiredInt", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_INT = SdkSchema.memberBuilder("optionalInt", PreludeSchemas.INTEGER)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_INT = SdkSchema.memberBuilder("defaultInt", PreludeSchemas.INTEGER)
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
            SCHEMA_REQUIRED_INT,
            SCHEMA_OPTIONAL_INT,
            SCHEMA_DEFAULT_INT
        )
        .build();

    private transient final int requiredInt;
    private transient final Integer optionalInt;
    private transient final int defaultInt;

    private IntegersInput(Builder builder) {
        this.requiredInt = builder.requiredInt;
        this.optionalInt = builder.optionalInt;
        this.defaultInt = builder.defaultInt;
    }

    public Integer requiredInt() {
        return requiredInt;
    }

    public int optionalInt() {
        return optionalInt;
    }

    public Integer defaultInt() {
        return defaultInt;
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
        IntegersInput that = (IntegersInput) other;
        return requiredInt == that.requiredInt
               && Objects.equals(optionalInt, that.optionalInt)
               && defaultInt == that.defaultInt;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredInt, optionalInt, defaultInt);
    }

    @Override
    public SdkSchema schema() {
        return SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeInteger(SCHEMA_REQUIRED_INT, requiredInt);

        if (optionalInt != null) {
            serializer.writeInteger(SCHEMA_OPTIONAL_INT, optionalInt);
        }

        serializer.writeInteger(SCHEMA_DEFAULT_INT, defaultInt);

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link IntegersInput}.
     */
    public static final class Builder implements SdkShapeBuilder<IntegersInput> {
        private int requiredInt;
        private Integer optionalInt;
        private int defaultInt = 1;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder requiredInt(int requiredInt) {
            this.requiredInt = requiredInt;
            tracker.setMember(SCHEMA_REQUIRED_INT);
            return this;
        }

        public Builder optionalInt(int optionalInt) {
            this.optionalInt = optionalInt;
            return this;
        }

        public Builder defaultInt(int defaultInt) {
            this.defaultInt = defaultInt;
            return this;
        }

        @Override
        public IntegersInput build() {
            tracker.validate();
            return new IntegersInput(this);
        }

        @Override
        public SdkShapeBuilder<IntegersInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }

            if (!tracker.checkMember(SCHEMA_REQUIRED_INT)) {
                tracker.setMember(SCHEMA_REQUIRED_INT);
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
                    case 0 -> builder.requiredInt(de.readInteger(member));
                    case 1 -> builder.optionalInt(de.readInteger(member));
                    case 2 -> builder.defaultInt(de.readInteger(member));
                }
            }
        }
    }
}

