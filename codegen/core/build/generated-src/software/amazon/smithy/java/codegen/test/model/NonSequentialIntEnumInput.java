

package software.amazon.smithy.java.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class NonSequentialIntEnumInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.enums#NonSequentialIntEnumInput");

    private static final Schema SCHEMA_ENUM_MEMBER = Schema.memberBuilder("enum", NonSequential.SCHEMA)
        .id(ID)
        .build();

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_ENUM_MEMBER
        )
        .build();

    private transient final NonSequential enumMember;

    private NonSequentialIntEnumInput(Builder builder) {
        this.enumMember = builder.enumMember;
    }

    public NonSequential enumMember() {
        return enumMember;
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
        NonSequentialIntEnumInput that = (NonSequentialIntEnumInput) other;
        return Objects.equals(this.enumMember, that.enumMember);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enumMember);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (enumMember != null) {
            serializer.writeInteger(SCHEMA_ENUM_MEMBER, enumMember.value());
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link NonSequentialIntEnumInput}.
     */
    public static final class Builder implements ShapeBuilder<NonSequentialIntEnumInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private NonSequential enumMember;

        private Builder() {}

        public Builder enumMember(NonSequential enumMember) {
            this.enumMember = enumMember;
            return this;
        }

        @Override
        public NonSequentialIntEnumInput build() {
            return new NonSequentialIntEnumInput(this);
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
                    case 0 -> builder.enumMember(NonSequential.builder().deserialize(de).build());
                }
            }

        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.enumMember(this.enumMember);
        return builder;
    }

}

