

package io.smithy.codegen.test.model;

import java.util.Objects;
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
public final class EnumsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#EnumsInput");

    private static final SdkSchema SCHEMA_REQUIRED_ENUM = SdkSchema.memberBuilder("requiredEnum", EnumType.SCHEMA)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_ENUM = SdkSchema.memberBuilder("optionalEnum", EnumType.SCHEMA)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_ENUM,
            SCHEMA_OPTIONAL_ENUM
        )
        .build();

    private transient final EnumType requiredEnum;
    private transient final EnumType optionalEnum;

    private EnumsInput(Builder builder) {
        this.requiredEnum = builder.requiredEnum;
        this.optionalEnum = builder.optionalEnum;
    }

    public EnumType requiredEnum() {
        return requiredEnum;
    }

    public EnumType optionalEnum() {
        return optionalEnum;
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
        EnumsInput that = (EnumsInput) other;
        return Objects.equals(requiredEnum, that.requiredEnum)
               && Objects.equals(optionalEnum, that.optionalEnum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredEnum, optionalEnum);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_REQUIRED_ENUM, requiredEnum.value());

        if (optionalEnum != null) {
            serializer.writeString(SCHEMA_OPTIONAL_ENUM, optionalEnum.value());
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link EnumsInput}.
     */
    public static final class Builder implements SdkShapeBuilder<EnumsInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private EnumType requiredEnum;
        private EnumType optionalEnum;

        private Builder() {}

        public Builder requiredEnum(EnumType requiredEnum) {
            this.requiredEnum = Objects.requireNonNull(requiredEnum, "requiredEnum cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_ENUM);
            return this;
        }

        public Builder optionalEnum(EnumType optionalEnum) {
            this.optionalEnum = optionalEnum;
            return this;
        }

        @Override
        public EnumsInput build() {
            tracker.validate();
            return new EnumsInput(this);
        }

        @Override
        public SdkShapeBuilder<EnumsInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_REQUIRED_ENUM)) {
                requiredEnum(EnumType.unknown(""));
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
                    case 0 -> builder.requiredEnum(EnumType.builder().deserialize(de).build());
                    case 1 -> builder.optionalEnum(EnumType.builder().deserialize(de).build());
                }
            }
        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredEnum(this.requiredEnum);
        builder.optionalEnum(this.optionalEnum);
        return builder;
    }

}

