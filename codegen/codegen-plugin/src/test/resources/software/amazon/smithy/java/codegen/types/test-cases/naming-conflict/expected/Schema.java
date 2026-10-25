package software.amazon.smithy.java.example.standalone.model;

import java.util.Objects;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class Schema implements SerializableStruct {

    public static final software.amazon.smithy.java.core.schema.Schema $SCHEMA = Schemas.SCHEMA;
    private static final software.amazon.smithy.java.core.schema.Schema $0 = $SCHEMA.member(0); // oneMember

    public static final ShapeId $ID = $SCHEMA.id();

    private final transient String oneMember;

    private Schema(Builder builder) {
        this.oneMember = builder.oneMember;
    }

    public String getOneMember() {
        return oneMember;
    }

    @Override
    public String toString() {
        return SchemaUtils.toString(this);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Schema that)) {
            return false;
        }
        return Objects.equals(this.oneMember, that.oneMember);
    }

    @Override
    public int hashCode() {
        int $hc = Objects.hashCode(oneMember);
        return $hc;
    }

    @Override
    public software.amazon.smithy.java.core.schema.Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (oneMember != null) {
            serializer.writeString($0, oneMember);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(software.amazon.smithy.java.core.schema.Schema member) {
        SchemaUtils.validateMemberOf($SCHEMA, member);
        return switch (member.memberIndex()) {
            case 0 -> (T) oneMember;
            default -> throw SchemaUtils.unknownMember(member);
        };
    }

    /**
     * Create a new builder containing all the current property values of this object.
     *
     * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
     *
     * @return a builder for {@link Schema}.
     */
    public Builder toBuilder() {
        var builder = new Builder();
        builder.oneMember(this.oneMember);
        return builder;
    }

    /**
     * @return returns a new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link Schema}.
     */
    public static final class Builder implements ShapeBuilder<Schema> {
        private String oneMember;

        private Builder() {}

        @Override
        public software.amazon.smithy.java.core.schema.Schema schema() {
            return $SCHEMA;
        }

        /**
         * @return this builder.
         */
        public Builder oneMember(String oneMember) {
            this.oneMember = oneMember;
            return this;
        }

        @Override
        public Schema build() {
            return new Schema(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(software.amazon.smithy.java.core.schema.Schema member, Object value) {
            SchemaUtils.validateMemberOf($SCHEMA, member);
            switch (member.memberIndex()) {
                case 0 -> oneMember((String) value);
                default -> throw SchemaUtils.unknownMember(member);
            }
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct($SCHEMA, this, Builder::$deserializeMember);
            return this;
        }

        @Override
        public Builder deserializeMember(ShapeDeserializer decoder, software.amazon.smithy.java.core.schema.Schema schema) {
            decoder.readStruct(schema.assertMemberTargetIs($SCHEMA), this, Builder::$deserializeMember);
            return this;
        }

        private static void $deserializeMember(
                Builder builder,
                software.amazon.smithy.java.core.schema.Schema member,
                ShapeDeserializer de
        ) {
            switch (member.memberIndex()) {
                case 0 -> builder.oneMember(de.readString(member));
                default -> throw SchemaUtils.unknownMember(member);
            }
        }
    }
}
