

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
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class IntEnumMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#IntEnumMembersInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("requiredEnum", IntEnumType.SCHEMA,
            new RequiredTrait())
        .putMember("defaultEnum", IntEnumType.SCHEMA,
            new DefaultTrait(Node.from(1)))
        .putMember("optionalEnum", IntEnumType.SCHEMA)
        .build();

    private static final Schema SCHEMA_REQUIRED_ENUM = SCHEMA.member("requiredEnum");
    private static final Schema SCHEMA_DEFAULT_ENUM = SCHEMA.member("defaultEnum");
    private static final Schema SCHEMA_OPTIONAL_ENUM = SCHEMA.member("optionalEnum");

    private transient final IntEnumType requiredEnum;
    private transient final IntEnumType defaultEnum;
    private transient final IntEnumType optionalEnum;

    private IntEnumMembersInput(Builder builder) {
        this.requiredEnum = builder.requiredEnum;
        this.defaultEnum = builder.defaultEnum;
        this.optionalEnum = builder.optionalEnum;
    }

    public IntEnumType requiredEnum() {
        return requiredEnum;
    }

    public IntEnumType defaultEnum() {
        return defaultEnum;
    }

    public IntEnumType optionalEnum() {
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
        IntEnumMembersInput that = (IntEnumMembersInput) other;
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
        serializer.writeInteger(SCHEMA_REQUIRED_ENUM, requiredEnum.value());
        serializer.writeInteger(SCHEMA_DEFAULT_ENUM, defaultEnum.value());
        if (optionalEnum != null) {
            serializer.writeInteger(SCHEMA_OPTIONAL_ENUM, optionalEnum.value());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link IntEnumMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<IntEnumMembersInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private IntEnumType requiredEnum;
        private IntEnumType defaultEnum = IntEnumType.OPTION_ONE;
        private IntEnumType optionalEnum;

        private Builder() {}

        public Builder requiredEnum(IntEnumType requiredEnum) {
            this.requiredEnum = Objects.requireNonNull(requiredEnum, "requiredEnum cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_ENUM);
            return this;
        }

        public Builder defaultEnum(IntEnumType defaultEnum) {
            this.defaultEnum = Objects.requireNonNull(defaultEnum, "defaultEnum cannot be null");
            return this;
        }

        public Builder optionalEnum(IntEnumType optionalEnum) {
            this.optionalEnum = optionalEnum;
            return this;
        }

        @Override
        public IntEnumMembersInput build() {
            tracker.validate();
            return new IntEnumMembersInput(this);
        }

        @Override
        public ShapeBuilder<IntEnumMembersInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_REQUIRED_ENUM)) {
                requiredEnum(IntEnumType.unknown(0));
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
                    case 0 -> builder.requiredEnum(IntEnumType.builder().deserialize(de).build());
                    case 1 -> builder.defaultEnum(IntEnumType.builder().deserialize(de).build());
                    case 2 -> builder.optionalEnum(IntEnumType.builder().deserialize(de).build());
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

