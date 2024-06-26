

package software.amazon.smithy.java.codegen.test.model;

import java.util.Objects;
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
public final class EnumMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#EnumMembersInput");

    private static final Schema SCHEMA_REQUIRED_ENUM = Schema.memberBuilder("requiredEnum", EnumType.SCHEMA)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final Schema SCHEMA_DEFAULT_ENUM = Schema.memberBuilder("defaultEnum", EnumType.SCHEMA)
        .id(ID)
        .traits(
            new DefaultTrait(Node.from("option-one"))
        )
        .build();

    private static final Schema SCHEMA_OPTIONAL_ENUM = Schema.memberBuilder("optionalEnum", EnumType.SCHEMA)
        .id(ID)
        .build();

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_ENUM,
            SCHEMA_DEFAULT_ENUM,
            SCHEMA_OPTIONAL_ENUM
        )
        .build();

    private transient final EnumType requiredEnum;
    private transient final EnumType defaultEnum;
    private transient final EnumType optionalEnum;

    private EnumMembersInput(Builder builder) {
        this.requiredEnum = builder.requiredEnum;
        this.defaultEnum = builder.defaultEnum;
        this.optionalEnum = builder.optionalEnum;
    }

    public EnumType requiredEnum() {
        return requiredEnum;
    }

    public EnumType defaultEnum() {
        return defaultEnum;
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
        EnumMembersInput that = (EnumMembersInput) other;
        return Objects.equals(this.requiredEnum, that.requiredEnum)
               && Objects.equals(this.defaultEnum, that.defaultEnum)
               && Objects.equals(this.optionalEnum, that.optionalEnum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredEnum, defaultEnum, optionalEnum);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_REQUIRED_ENUM, requiredEnum.value());

        serializer.writeString(SCHEMA_DEFAULT_ENUM, defaultEnum.value());

        if (optionalEnum != null) {
            serializer.writeString(SCHEMA_OPTIONAL_ENUM, optionalEnum.value());
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link EnumMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<EnumMembersInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private EnumType requiredEnum;
        private EnumType defaultEnum = EnumType.OPTION_ONE;
        private EnumType optionalEnum;

        private Builder() {}

        public Builder requiredEnum(EnumType requiredEnum) {
            this.requiredEnum = Objects.requireNonNull(requiredEnum, "requiredEnum cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_ENUM);
            return this;
        }

        public Builder defaultEnum(EnumType defaultEnum) {
            this.defaultEnum = Objects.requireNonNull(defaultEnum, "defaultEnum cannot be null");
            return this;
        }

        public Builder optionalEnum(EnumType optionalEnum) {
            this.optionalEnum = optionalEnum;
            return this;
        }

        @Override
        public EnumMembersInput build() {
            tracker.validate();
            return new EnumMembersInput(this);
        }

        @Override
        public ShapeBuilder<EnumMembersInput> errorCorrection() {
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
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.requiredEnum(EnumType.builder().deserialize(de).build());
                    case 1 -> builder.defaultEnum(EnumType.builder().deserialize(de).build());
                    case 2 -> builder.optionalEnum(EnumType.builder().deserialize(de).build());
                }
            }

        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredEnum(this.requiredEnum);
        builder.defaultEnum(this.defaultEnum);
        builder.optionalEnum(this.optionalEnum);
        return builder;
    }

}

