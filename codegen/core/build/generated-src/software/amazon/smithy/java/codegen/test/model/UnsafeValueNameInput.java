

package software.amazon.smithy.java.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class UnsafeValueNameInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.enums#UnsafeValueNameInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("enum", UnsafeValueEnum.SCHEMA)
        .build();

    private static final Schema SCHEMA_ENUM_MEMBER = SCHEMA.member("enum");

    private transient final UnsafeValueEnum enumMember;

    private UnsafeValueNameInput(Builder builder) {
        this.enumMember = builder.enumMember;
    }

    public UnsafeValueEnum enumMember() {
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
        UnsafeValueNameInput that = (UnsafeValueNameInput) other;
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
            serializer.writeString(SCHEMA_ENUM_MEMBER, enumMember.value());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link UnsafeValueNameInput}.
     */
    public static final class Builder implements ShapeBuilder<UnsafeValueNameInput> {
        private UnsafeValueEnum enumMember;

        private Builder() {}

        public Builder enumMember(UnsafeValueEnum enumMember) {
            this.enumMember = enumMember;
            return this;
        }

        @Override
        public UnsafeValueNameInput build() {
            return new UnsafeValueNameInput(this);
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
                    case 0 -> builder.enumMember(UnsafeValueEnum.builder().deserialize(de).build());
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

