
package software.amazon.smithy.java.example.standalone.model;

import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

/**
 * Union with a mix of primitive and non-primitive variants
 */
@SmithyGenerated
@NullMarked
public sealed interface MixedUnion extends SerializableStruct {
    Schema $SCHEMA = Schemas.MIXED_UNION;

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
    record IntVariantMember(int intVariant) implements MixedUnion {
        private static final Schema $SCHEMA_INT_VARIANT = $SCHEMA.member("intVariant");
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger($SCHEMA_INT_VARIANT, intVariant);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Integer getValue() {
            return intVariant;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record StringVariantMember(String stringVariant) implements MixedUnion {
        private static final Schema $SCHEMA_STRING_VARIANT = $SCHEMA.member("stringVariant");
        public StringVariantMember {
            Objects.requireNonNull(stringVariant, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString($SCHEMA_STRING_VARIANT, stringVariant);
        }

        @Override
        public String getValue() {
            return stringVariant;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record BoolVariantMember(boolean boolVariant) implements MixedUnion {
        private static final Schema $SCHEMA_BOOL_VARIANT = $SCHEMA.member("boolVariant");
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBoolean($SCHEMA_BOOL_VARIANT, boolVariant);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Boolean getValue() {
            return boolVariant;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    record $Unknown(String memberName) implements MixedUnion {
        @Override
        public void serialize(ShapeSerializer serializer) {
            throw new UnsupportedOperationException("Cannot serialize union with unknown member " + this.memberName);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public String getValue() {
            return memberName;
        }

        private record $Hidden() implements MixedUnion {
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
        MixedUnion build();
    }

    /**
     * @return returns a new Builder.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link MixedUnion}.
     */
    final class Builder implements ShapeBuilder<MixedUnion>, BuildStage {
        private MixedUnion value;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        public BuildStage intVariant(int value) {
            return setValue(new IntVariantMember(value));
        }

        public BuildStage stringVariant(String value) {
            return setValue(new StringVariantMember(value));
        }

        public BuildStage boolVariant(boolean value) {
            return setValue(new BoolVariantMember(value));
        }

        public BuildStage $unknownMember(String memberName) {
            return setValue(new $Unknown(memberName));
        }

        private BuildStage setValue(MixedUnion value) {
            if (this.value != null) {
                throw new IllegalArgumentException("Only one value may be set for unions");
            }
            this.value = value;
            return this;
        }

        @Override
        public MixedUnion build() {
            return Objects.requireNonNull(value, "no union value set");
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> intVariant((int) SchemaUtils.validateSameMember(IntVariantMember.$SCHEMA_INT_VARIANT, member, value));
                case 1 -> stringVariant((String) SchemaUtils.validateSameMember(StringVariantMember.$SCHEMA_STRING_VARIANT, member, value));
                case 2 -> boolVariant((boolean) SchemaUtils.validateSameMember(BoolVariantMember.$SCHEMA_BOOL_VARIANT, member, value));
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
            @SuppressWarnings("unchecked")
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.intVariant(de.readInteger(member));
                    case 1 -> builder.stringVariant(de.readString(member));
                    case 2 -> builder.boolVariant(de.readBoolean(member));
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

