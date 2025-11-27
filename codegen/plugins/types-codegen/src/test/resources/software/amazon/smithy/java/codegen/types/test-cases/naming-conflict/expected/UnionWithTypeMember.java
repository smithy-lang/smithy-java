
package software.amazon.smithy.java.example.standalone.model;

import java.util.Objects;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public sealed interface UnionWithTypeMember extends SerializableStruct {
    Schema $SCHEMA = Schemas.UNION_WITH_TYPE_MEMBER;

    ShapeId $ID = $SCHEMA.id();

    <T> T getValue();

    @Override
    default Schema schema() {
        return $SCHEMA;
    }

    @Override
    default <T> T getMemberValue(Schema member) {
        return SchemaUtils.validateMemberInSchema($SCHEMA, member, getValue());
    }

    @SmithyGenerated
    record TypeMember(Type type) implements UnionWithTypeMember {
        private static final Schema $SCHEMA_TYPE = $SCHEMA.member("type");
        public TypeMember {
            Objects.requireNonNull(type, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct($SCHEMA_TYPE, type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getValue() {
            return (T) type;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    record $Unknown(String memberName) implements UnionWithTypeMember {
        @Override
        public void serialize(ShapeSerializer serializer) {
            throw new UnsupportedOperationException("Cannot serialize union with unknown member " + this.memberName);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getValue() {
            return (T) memberName;
        }

        private record $Hidden() implements UnionWithTypeMember {
            @Override
            public void serializeMembers(ShapeSerializer serializer) {}

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getValue() {
                return null;
            }
        }
    }

    interface BuildStage {
        UnionWithTypeMember build();
    }

    /**
     * @return returns a new Builder.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link UnionWithTypeMember}.
     */
    final class Builder implements ShapeBuilder<UnionWithTypeMember>, BuildStage {
        private UnionWithTypeMember value;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        public BuildStage type(Type value) {
            return setValue(new TypeMember(value));
        }

        public BuildStage $unknownMember(String memberName) {
            return setValue(new $Unknown(memberName));
        }

        private BuildStage setValue(UnionWithTypeMember value) {
            if (this.value != null) {
                throw new IllegalArgumentException("Only one value may be set for unions");
            }
            this.value = value;
            return this;
        }

        @Override
        public UnionWithTypeMember build() {
            return Objects.requireNonNull(value, "no union value set");
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> type((Type) SchemaUtils.validateSameMember(TypeMember.$SCHEMA_TYPE, member, value));
                default -> ShapeBuilder.super.setMemberValue(member, value);
            }
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct($SCHEMA, this, $InnerDeserializer.INSTANCE);
            return this;
        }

        @Override
        public Builder deserializeMember(ShapeDeserializer decoder, Schema schema) {
            decoder.readStruct(schema.assertMemberTargetIs($SCHEMA), this, $InnerDeserializer.INSTANCE);
            return this;
        }

        private static final class $InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
            private static final $InnerDeserializer INSTANCE = new $InnerDeserializer();

            @Override
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.type(Type.builder().deserializeMember(de, member).build());
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }

            @Override
            public void unknownMember(Builder builder, String memberName) {
                builder.$unknownMember(memberName);
            }
        }
    }
}

