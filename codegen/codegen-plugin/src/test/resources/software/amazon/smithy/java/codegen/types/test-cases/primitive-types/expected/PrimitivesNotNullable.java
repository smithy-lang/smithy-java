package software.amazon.smithy.java.example.standalone.model;

import software.amazon.smithy.java.core.schema.PresenceTracker;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

/**
 * All members in this shape use primitive java types
 */
@SmithyGenerated
public final class PrimitivesNotNullable implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.PRIMITIVES_NOT_NULLABLE;
    private static final Schema $0 = $SCHEMA.member(0); // byte
    private static final Schema $1 = $SCHEMA.member(1); // short
    private static final Schema $2 = $SCHEMA.member(2); // int
    private static final Schema $3 = $SCHEMA.member(3); // long
    private static final Schema $4 = $SCHEMA.member(4); // float
    private static final Schema $5 = $SCHEMA.member(5); // double
    private static final Schema $6 = $SCHEMA.member(6); // boolean

    public static final ShapeId $ID = $SCHEMA.id();

    private final transient byte byteMember;
    private final transient short shortMember;
    private final transient int intMember;
    private final transient long longMember;
    private final transient float floatMember;
    private final transient double doubleMember;
    private final transient boolean booleanMember;

    private PrimitivesNotNullable(Builder builder) {
        this.byteMember = builder.byteMember;
        this.shortMember = builder.shortMember;
        this.intMember = builder.intMember;
        this.longMember = builder.longMember;
        this.floatMember = builder.floatMember;
        this.doubleMember = builder.doubleMember;
        this.booleanMember = builder.booleanMember;
    }

    public byte getByte() {
        return byteMember;
    }

    public short getShort() {
        return shortMember;
    }

    public int getInt() {
        return intMember;
    }

    public long getLong() {
        return longMember;
    }

    public float getFloat() {
        return floatMember;
    }

    public double getDouble() {
        return doubleMember;
    }

    public boolean isBoolean() {
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
        if (!(other instanceof PrimitivesNotNullable that)) {
            return false;
        }
        return this.booleanMember == that.booleanMember
               && this.byteMember == that.byteMember
               && this.shortMember == that.shortMember
               && this.intMember == that.intMember
               && this.longMember == that.longMember
               && Float.compare(this.floatMember, that.floatMember) == 0
               && Double.compare(this.doubleMember, that.doubleMember) == 0;
    }

    @Override
    public int hashCode() {
        int $hc = Byte.hashCode(byteMember);
        $hc = 31 * $hc + Short.hashCode(shortMember);
        $hc = 31 * $hc + Integer.hashCode(intMember);
        $hc = 31 * $hc + Long.hashCode(longMember);
        $hc = 31 * $hc + Float.hashCode(floatMember);
        $hc = 31 * $hc + Double.hashCode(doubleMember);
        $hc = 31 * $hc + Boolean.hashCode(booleanMember);
        return $hc;
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeByte($0, byteMember);
        serializer.writeShort($1, shortMember);
        serializer.writeInteger($2, intMember);
        serializer.writeLong($3, longMember);
        serializer.writeFloat($4, floatMember);
        serializer.writeDouble($5, doubleMember);
        serializer.writeBoolean($6, booleanMember);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        SchemaUtils.validateMemberOf($SCHEMA, member);
        return switch (member.memberIndex()) {
            case 0 -> (T) (Object) byteMember;
            case 1 -> (T) (Object) shortMember;
            case 2 -> (T) (Object) intMember;
            case 3 -> (T) (Object) longMember;
            case 4 -> (T) (Object) floatMember;
            case 5 -> (T) (Object) doubleMember;
            case 6 -> (T) (Object) booleanMember;
            default -> throw SchemaUtils.unknownMember(member);
        };
    }

    /**
     * Create a new builder containing all the current property values of this object.
     *
     * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
     *
     * @return a builder for {@link PrimitivesNotNullable}.
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
     * Builder for {@link PrimitivesNotNullable}.
     */
    public static final class Builder implements ShapeBuilder<PrimitivesNotNullable> {
        private long $setMembers;
        private byte byteMember;
        private short shortMember;
        private int intMember;
        private long longMember;
        private float floatMember;
        private double doubleMember;
        private boolean booleanMember;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder byteMember(byte byteMember) {
            this.byteMember = byteMember;
            $setMembers |= 0x1L;
            return this;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder shortMember(short shortMember) {
            this.shortMember = shortMember;
            $setMembers |= 0x2L;
            return this;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder intMember(int intMember) {
            this.intMember = intMember;
            $setMembers |= 0x4L;
            return this;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder longMember(long longMember) {
            this.longMember = longMember;
            $setMembers |= 0x8L;
            return this;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder floatMember(float floatMember) {
            this.floatMember = floatMember;
            $setMembers |= 0x10L;
            return this;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder doubleMember(double doubleMember) {
            this.doubleMember = doubleMember;
            $setMembers |= 0x20L;
            return this;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder booleanMember(boolean booleanMember) {
            this.booleanMember = booleanMember;
            $setMembers |= 0x40L;
            return this;
        }

        @Override
        public PrimitivesNotNullable build() {
            if ($setMembers != 0x7fL) {
                PresenceTracker.validateRequiredMembers($SCHEMA, $setMembers);
            }
            return new PrimitivesNotNullable(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            SchemaUtils.validateMemberOf($SCHEMA, member);
            switch (member.memberIndex()) {
                case 0 -> byteMember((byte) value);
                case 1 -> shortMember((short) value);
                case 2 -> intMember((int) value);
                case 3 -> longMember((long) value);
                case 4 -> floatMember((float) value);
                case 5 -> doubleMember((double) value);
                case 6 -> booleanMember((boolean) value);
                default -> throw SchemaUtils.unknownMember(member);
            }
        }

        @Override
        public ShapeBuilder<PrimitivesNotNullable> errorCorrection() {
            if ($setMembers == 0x7fL) {
                return this;
            }
            if (($setMembers & 0x1L) == 0L) {
                $setMembers |= 0x1L;
            }
            if (($setMembers & 0x2L) == 0L) {
                $setMembers |= 0x2L;
            }
            if (($setMembers & 0x4L) == 0L) {
                $setMembers |= 0x4L;
            }
            if (($setMembers & 0x8L) == 0L) {
                $setMembers |= 0x8L;
            }
            if (($setMembers & 0x10L) == 0L) {
                $setMembers |= 0x10L;
            }
            if (($setMembers & 0x20L) == 0L) {
                $setMembers |= 0x20L;
            }
            if (($setMembers & 0x40L) == 0L) {
                $setMembers |= 0x40L;
            }
            return this;
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
