package software.amazon.smithy.java.example.standalone.model;

import java.util.Objects;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public sealed interface InnerUnion extends SerializableStruct {
    Schema $SCHEMA = Schemas.$1;

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
    record DeepestMember(DeepestUnion deepest) implements InnerUnion {
        private static final Schema $0 = $SCHEMA.member(0); // deepest
        public DeepestMember {
            Objects.requireNonNull(deepest, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct($0, deepest);
        }

        @Override
        public DeepestUnion getValue() {
            return deepest;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record NumberMember(int number) implements InnerUnion {
        private static final Schema $1 = $SCHEMA.member(1); // number
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger($1, number);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Integer getValue() {
            return number;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    record $Unknown(String memberName) implements InnerUnion {
        @Override
        public void serialize(ShapeSerializer serializer) {
            throw new UnsupportedOperationException("Cannot serialize union with unknown member ".concat(this.memberName));
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public String getValue() {
            return memberName;
        }

        private record $Hidden() implements InnerUnion {
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
        InnerUnion build();
    }

    /**
     * @return returns a new Builder.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link InnerUnion}.
     */
    final class Builder implements ShapeBuilder<InnerUnion>, BuildStage {
        private InnerUnion value;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        public BuildStage deepest(DeepestUnion value) {
            return setValue(new DeepestMember(value));
        }

        public BuildStage number(int value) {
            return setValue(new NumberMember(value));
        }

        public BuildStage $unknownMember(String memberName) {
            return setValue(new $Unknown(memberName));
        }

        private BuildStage setValue(InnerUnion value) {
            if (this.value != null) {
                throw new IllegalArgumentException("Only one value may be set for unions");
            }
            this.value = value;
            return this;
        }

        @Override
        public InnerUnion build() {
            return Objects.requireNonNull(value, "no union value set");
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            SchemaUtils.validateMemberOf($SCHEMA, member);
            switch (member.memberIndex()) {
                case 0 -> deepest((DeepestUnion) value);
                case 1 -> number((int) value);
                default -> throw SchemaUtils.unknownMember(member);
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

        private static void $deserializeMember(
                Builder builder,
                Schema member,
                ShapeDeserializer de
        ) {
            switch (member.memberIndex()) {
                case 0 -> builder.deepest(DeepestUnion.builder().deserializeMember(de, member).build());
                case 1 -> builder.number(de.readInteger(member));
                default -> throw SchemaUtils.unknownMember(member);
            }
        }

        private static final class $InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
            private static final $InnerDeserializer INSTANCE = new $InnerDeserializer();

            @Override
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                Builder.$deserializeMember(builder, member, de);
            }

            @Override
            public void unknownMember(Builder builder, String memberName) {
                builder.$unknownMember(memberName);
            }
        }
    }
}
