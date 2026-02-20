
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

/**
 * All members in this shape use boxed java types
 */
@SmithyGenerated
public final class PrimitivesNullable implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.PRIMITIVES_NULLABLE;
    private static final Schema $SCHEMA_BYTE_MEMBER = $SCHEMA.member("byte");
    private static final Schema $SCHEMA_SHORT_MEMBER = $SCHEMA.member("short");
    private static final Schema $SCHEMA_INT_MEMBER = $SCHEMA.member("int");
    private static final Schema $SCHEMA_LONG_MEMBER = $SCHEMA.member("long");
    private static final Schema $SCHEMA_FLOAT_MEMBER = $SCHEMA.member("float");
    private static final Schema $SCHEMA_DOUBLE_MEMBER = $SCHEMA.member("double");
    private static final Schema $SCHEMA_BOOLEAN_MEMBER = $SCHEMA.member("boolean");

    public static final ShapeId $ID = $SCHEMA.id();

    private final transient Byte byteMember;
    private final transient Short shortMember;
    private final transient Integer intMember;
    private final transient Long longMember;
    private final transient Float floatMember;
    private final transient Double doubleMember;
    private final transient Boolean booleanMember;

    private PrimitivesNullable(Builder builder) {
        this.byteMember = builder.byteMember;
        this.shortMember = builder.shortMember;
        this.intMember = builder.intMember;
        this.longMember = builder.longMember;
        this.floatMember = builder.floatMember;
        this.doubleMember = builder.doubleMember;
        this.booleanMember = builder.booleanMember;
    }

    public Byte getByte() {
        return byteMember;
    }

    public Short getShort() {
        return shortMember;
    }

    public Integer getInt() {
        return intMember;
    }

    public Long getLong() {
        return longMember;
    }

    public Float getFloat() {
        return floatMember;
    }

    public Double getDouble() {
        return doubleMember;
    }

    public Boolean isBoolean() {
        return booleanMember;
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
        PrimitivesNullable that = (PrimitivesNullable) other;
        return Objects.equals(this.byteMember, that.byteMember)
               && Objects.equals(this.shortMember, that.shortMember)
               && Objects.equals(this.intMember, that.intMember)
               && Objects.equals(this.longMember, that.longMember)
               && Objects.equals(this.floatMember, that.floatMember)
               && Objects.equals(this.doubleMember, that.doubleMember)
               && Objects.equals(this.booleanMember, that.booleanMember);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byteMember, shortMember, intMember, longMember, floatMember, doubleMember, booleanMember);
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (byteMember != null) {
            serializer.writeByte($SCHEMA_BYTE_MEMBER, byteMember);
        }
        if (shortMember != null) {
            serializer.writeShort($SCHEMA_SHORT_MEMBER, shortMember);
        }
        if (intMember != null) {
            serializer.writeInteger($SCHEMA_INT_MEMBER, intMember);
        }
        if (longMember != null) {
            serializer.writeLong($SCHEMA_LONG_MEMBER, longMember);
        }
        if (floatMember != null) {
            serializer.writeFloat($SCHEMA_FLOAT_MEMBER, floatMember);
        }
        if (doubleMember != null) {
            serializer.writeDouble($SCHEMA_DOUBLE_MEMBER, doubleMember);
        }
        if (booleanMember != null) {
            serializer.writeBoolean($SCHEMA_BOOLEAN_MEMBER, booleanMember);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember($SCHEMA_BYTE_MEMBER, member, byteMember);
            case 1 -> (T) SchemaUtils.validateSameMember($SCHEMA_SHORT_MEMBER, member, shortMember);
            case 2 -> (T) SchemaUtils.validateSameMember($SCHEMA_INT_MEMBER, member, intMember);
            case 3 -> (T) SchemaUtils.validateSameMember($SCHEMA_LONG_MEMBER, member, longMember);
            case 4 -> (T) SchemaUtils.validateSameMember($SCHEMA_FLOAT_MEMBER, member, floatMember);
            case 5 -> (T) SchemaUtils.validateSameMember($SCHEMA_DOUBLE_MEMBER, member, doubleMember);
            case 6 -> (T) SchemaUtils.validateSameMember($SCHEMA_BOOLEAN_MEMBER, member, booleanMember);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        };
    }

    /**
     * Create a new builder containing all the current property values of this object.
     *
     * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
     *
     * @return a builder for {@link PrimitivesNullable}.
     */
    public Builder toBuilder() {
        var builder = new Builder();
        builder.byteMember(this.byteMember);
        builder.shortMember(this.shortMember);
        builder.intMember(this.intMember);
        builder.longMember(this.longMember);
        builder.floatMember(this.floatMember);
        builder.doubleMember(this.doubleMember);
        builder.booleanMember(this.booleanMember);
        return builder;
    }

    /**
     * @return returns a new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link PrimitivesNullable}.
     */
    public static final class Builder implements ShapeBuilder<PrimitivesNullable> {
        private Byte byteMember;
        private Short shortMember;
        private Integer intMember;
        private Long longMember;
        private Float floatMember;
        private Double doubleMember;
        private Boolean booleanMember;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        /**
         * @return this builder.
         */
        public Builder byteMember(Byte byteMember) {
            this.byteMember = byteMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder shortMember(Short shortMember) {
            this.shortMember = shortMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder intMember(Integer intMember) {
            this.intMember = intMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder longMember(Long longMember) {
            this.longMember = longMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder floatMember(Float floatMember) {
            this.floatMember = floatMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder doubleMember(Double doubleMember) {
            this.doubleMember = doubleMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder booleanMember(Boolean booleanMember) {
            this.booleanMember = booleanMember;
            return this;
        }

        @Override
        public PrimitivesNullable build() {
            return new PrimitivesNullable(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> byteMember((Byte) SchemaUtils.validateSameMember($SCHEMA_BYTE_MEMBER, member, value));
                case 1 -> shortMember((Short) SchemaUtils.validateSameMember($SCHEMA_SHORT_MEMBER, member, value));
                case 2 -> intMember((Integer) SchemaUtils.validateSameMember($SCHEMA_INT_MEMBER, member, value));
                case 3 -> longMember((Long) SchemaUtils.validateSameMember($SCHEMA_LONG_MEMBER, member, value));
                case 4 -> floatMember((Float) SchemaUtils.validateSameMember($SCHEMA_FLOAT_MEMBER, member, value));
                case 5 -> doubleMember((Double) SchemaUtils.validateSameMember($SCHEMA_DOUBLE_MEMBER, member, value));
                case 6 -> booleanMember((Boolean) SchemaUtils.validateSameMember($SCHEMA_BOOLEAN_MEMBER, member, value));
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
                    case 0 -> builder.byteMember(de.readByte(member));
                    case 1 -> builder.shortMember(de.readShort(member));
                    case 2 -> builder.intMember(de.readInteger(member));
                    case 3 -> builder.longMember(de.readLong(member));
                    case 4 -> builder.floatMember(de.readFloat(member));
                    case 5 -> builder.doubleMember(de.readDouble(member));
                    case 6 -> builder.booleanMember(de.readBoolean(member));
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }
        }
    }
}

