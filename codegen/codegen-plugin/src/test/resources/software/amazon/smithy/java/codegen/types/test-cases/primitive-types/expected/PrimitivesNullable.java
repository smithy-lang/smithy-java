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

/**
 * All members in this shape use boxed java types
 */
@SmithyGenerated
public final class PrimitivesNullable implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.$1;
    private static final Schema $0 = $SCHEMA.member(0); // byte
    private static final Schema $1 = $SCHEMA.member(1); // short
    private static final Schema $2 = $SCHEMA.member(2); // int
    private static final Schema $3 = $SCHEMA.member(3); // long
    private static final Schema $4 = $SCHEMA.member(4); // float
    private static final Schema $5 = $SCHEMA.member(5); // double
    private static final Schema $6 = $SCHEMA.member(6); // boolean

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
        return SchemaUtils.toString(this);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof PrimitivesNullable that)) {
            return false;
        }
        return Objects.equals(this.booleanMember, that.booleanMember)
               && Objects.equals(this.byteMember, that.byteMember)
               && Objects.equals(this.shortMember, that.shortMember)
               && Objects.equals(this.intMember, that.intMember)
               && Objects.equals(this.longMember, that.longMember)
               && Objects.equals(this.floatMember, that.floatMember)
               && Objects.equals(this.doubleMember, that.doubleMember);
    }

    @Override
    public int hashCode() {
        int $hc = Objects.hashCode(byteMember);
        $hc = 31 * $hc + Objects.hashCode(shortMember);
        $hc = 31 * $hc + Objects.hashCode(intMember);
        $hc = 31 * $hc + Objects.hashCode(longMember);
        $hc = 31 * $hc + Objects.hashCode(floatMember);
        $hc = 31 * $hc + Objects.hashCode(doubleMember);
        $hc = 31 * $hc + Objects.hashCode(booleanMember);
        return $hc;
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (byteMember != null) {
            serializer.writeByte($0, byteMember);
        }
        if (shortMember != null) {
            serializer.writeShort($1, shortMember);
        }
        if (intMember != null) {
            serializer.writeInteger($2, intMember);
        }
        if (longMember != null) {
            serializer.writeLong($3, longMember);
        }
        if (floatMember != null) {
            serializer.writeFloat($4, floatMember);
        }
        if (doubleMember != null) {
            serializer.writeDouble($5, doubleMember);
        }
        if (booleanMember != null) {
            serializer.writeBoolean($6, booleanMember);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        SchemaUtils.validateMemberOf($SCHEMA, member);
        return switch (member.memberIndex()) {
            case 0 -> (T) byteMember;
            case 1 -> (T) shortMember;
            case 2 -> (T) intMember;
            case 3 -> (T) longMember;
            case 4 -> (T) floatMember;
            case 5 -> (T) doubleMember;
            case 6 -> (T) booleanMember;
            default -> throw SchemaUtils.unknownMember(member);
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
            SchemaUtils.validateMemberOf($SCHEMA, member);
            switch (member.memberIndex()) {
                case 0 -> byteMember((Byte) value);
                case 1 -> shortMember((Short) value);
                case 2 -> intMember((Integer) value);
                case 3 -> longMember((Long) value);
                case 4 -> floatMember((Float) value);
                case 5 -> doubleMember((Double) value);
                case 6 -> booleanMember((Boolean) value);
                default -> throw SchemaUtils.unknownMember(member);
            }
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct($SCHEMA, this, Builder::$deserializeMember);
            return this;
        }

        @Override
        public Builder deserializeMember(ShapeDeserializer decoder, Schema schema) {
            decoder.readStruct(schema.assertMemberTargetIs($SCHEMA), this, Builder::$deserializeMember);
            return this;
        }

        private static void $deserializeMember(
                Builder builder,
                Schema member,
                ShapeDeserializer de
        ) {
            switch (member.memberIndex()) {
                case 0 -> builder.byteMember(de.readByte(member));
                case 1 -> builder.shortMember(de.readShort(member));
                case 2 -> builder.intMember(de.readInteger(member));
                case 3 -> builder.longMember(de.readLong(member));
                case 4 -> builder.floatMember(de.readFloat(member));
                case 5 -> builder.doubleMember(de.readDouble(member));
                case 6 -> builder.booleanMember(de.readBoolean(member));
                default -> throw SchemaUtils.unknownMember(member);
            }
        }
    }
}
