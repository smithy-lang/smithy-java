

package io.smithy.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public abstract class OtherUnion implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#OtherUnion");

    private static final SdkSchema SCHEMA_STR = SdkSchema.memberBuilder("str", PreludeSchemas.STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_INT_MEMBER = SdkSchema.memberBuilder("intMember", PreludeSchemas.INTEGER)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.UNION)
        .members(
            SCHEMA_STR,
            SCHEMA_INT_MEMBER
        )
        .build();

    private final Member type;

    private OtherUnion(Member type) {
        this.type = type;
    }

    public Member type() {
        return type;
    }

    public enum Member {
        $UNKNOWN,
        STR,
        INT_MEMBER
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    public String str() {
        return null;
    }

    public Integer intMember() {
        return null;
    }

    @SmithyGenerated
    public static final class StrMember extends OtherUnion {
        private final transient String value;

        public StrMember(String value) {
            super(Member.STR);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SCHEMA_STR, value);
        }

        @Override
        public String str() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            StrMember that = (StrMember) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class IntMemberMember extends OtherUnion {
        private final transient int value;

        public IntMemberMember(int value) {
            super(Member.INT_MEMBER);
            this.value = value;
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger(SCHEMA_INT_MEMBER, value);
        }

        @Override
        public Integer intMember() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            IntMemberMember that = (IntMemberMember) other;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link OtherUnion}.
     */
    public static final class Builder implements SdkShapeBuilder<OtherUnion> {
        private OtherUnion value;

        private Builder() {}

        public Builder str(String value) {
            checkForExistingValue();
            this.value = new StrMember(value);
            return this;
        }

        public Builder intMember(int value) {
            checkForExistingValue();
            this.value = new IntMemberMember(value);
            return this;
        }

        private void checkForExistingValue() {
            if (this.value != null) {
                throw new SdkSerdeException("Only one value may be set for unions");
            }
        }

        @Override
        public OtherUnion build() {
            return Objects.requireNonNull(value, "no union value set");
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
                    case 0 -> builder.str(de.readString(member));
                    case 1 -> builder.intMember(de.readInteger(member));
                }
            }
        }

    }
}

