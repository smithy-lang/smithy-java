
package software.amazon.smithy.java.example.standalone.model;

import java.util.Objects;
import software.amazon.smithy.java.core.schema.PresenceTracker;
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
 * All members in this shape use primitive java types
 */
@SmithyGenerated
public final class PrimitivesNotNullable implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.PRIMITIVES_NOT_NULLABLE;
    private static final Schema $SCHEMA_BYTE_MEMBER = $SCHEMA.member("byte");
    private static final Schema $SCHEMA_SHORT_MEMBER = $SCHEMA.member("short");
    private static final Schema $SCHEMA_INT_MEMBER = $SCHEMA.member("int");
    private static final Schema $SCHEMA_LONG_MEMBER = $SCHEMA.member("long");
    private static final Schema $SCHEMA_FLOAT_MEMBER = $SCHEMA.member("float");
    private static final Schema $SCHEMA_DOUBLE_MEMBER = $SCHEMA.member("double");
    private static final Schema $SCHEMA_BOOLEAN_MEMBER = $SCHEMA.member("boolean");

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
        PrimitivesNotNullable that = (PrimitivesNotNullable) other;
        return this.byteMember == that.byteMember
               && this.shortMember == that.shortMember
               && this.intMember == that.intMember
               && this.longMember == that.longMember
               && this.floatMember == that.floatMember
               && this.doubleMember == that.doubleMember
               && this.booleanMember == that.booleanMember;
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
        serializer.writeByte($SCHEMA_BYTE_MEMBER, byteMember);
        serializer.writeShort($SCHEMA_SHORT_MEMBER, shortMember);
        serializer.writeInteger($SCHEMA_INT_MEMBER, intMember);
        serializer.writeLong($SCHEMA_LONG_MEMBER, longMember);
        serializer.writeFloat($SCHEMA_FLOAT_MEMBER, floatMember);
        serializer.writeDouble($SCHEMA_DOUBLE_MEMBER, doubleMember);
        serializer.writeBoolean($SCHEMA_BOOLEAN_MEMBER, booleanMember);
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
        private final PresenceTracker tracker = PresenceTracker.of($SCHEMA);
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
            tracker.setMember($SCHEMA_BYTE_MEMBER);
            return this;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder shortMember(short shortMember) {
            this.shortMember = shortMember;
            tracker.setMember($SCHEMA_SHORT_MEMBER);
            return this;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder intMember(int intMember) {
            this.intMember = intMember;
            tracker.setMember($SCHEMA_INT_MEMBER);
            return this;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder longMember(long longMember) {
            this.longMember = longMember;
            tracker.setMember($SCHEMA_LONG_MEMBER);
            return this;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder floatMember(float floatMember) {
            this.floatMember = floatMember;
            tracker.setMember($SCHEMA_FLOAT_MEMBER);
            return this;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder doubleMember(double doubleMember) {
            this.doubleMember = doubleMember;
            tracker.setMember($SCHEMA_DOUBLE_MEMBER);
            return this;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder booleanMember(boolean booleanMember) {
            this.booleanMember = booleanMember;
            tracker.setMember($SCHEMA_BOOLEAN_MEMBER);
            return this;
        }

        @Override
        public PrimitivesNotNullable build() {
            tracker.validate();
            return new PrimitivesNotNullable(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> byteMember((byte) SchemaUtils.validateSameMember($SCHEMA_BYTE_MEMBER, member, value));
                case 1 -> shortMember((short) SchemaUtils.validateSameMember($SCHEMA_SHORT_MEMBER, member, value));
                case 2 -> intMember((int) SchemaUtils.validateSameMember($SCHEMA_INT_MEMBER, member, value));
                case 3 -> longMember((long) SchemaUtils.validateSameMember($SCHEMA_LONG_MEMBER, member, value));
                case 4 -> floatMember((float) SchemaUtils.validateSameMember($SCHEMA_FLOAT_MEMBER, member, value));
                case 5 -> doubleMember((double) SchemaUtils.validateSameMember($SCHEMA_DOUBLE_MEMBER, member, value));
                case 6 -> booleanMember((boolean) SchemaUtils.validateSameMember($SCHEMA_BOOLEAN_MEMBER, member, value));
                default -> ShapeBuilder.super.setMemberValue(member, value);
            }
        }

        @Override
        public ShapeBuilder<PrimitivesNotNullable> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember($SCHEMA_BYTE_MEMBER)) {
                tracker.setMember($SCHEMA_BYTE_MEMBER);
            }
            if (!tracker.checkMember($SCHEMA_SHORT_MEMBER)) {
                tracker.setMember($SCHEMA_SHORT_MEMBER);
            }
            if (!tracker.checkMember($SCHEMA_INT_MEMBER)) {
                tracker.setMember($SCHEMA_INT_MEMBER);
            }
            if (!tracker.checkMember($SCHEMA_LONG_MEMBER)) {
                tracker.setMember($SCHEMA_LONG_MEMBER);
            }
            if (!tracker.checkMember($SCHEMA_FLOAT_MEMBER)) {
                tracker.setMember($SCHEMA_FLOAT_MEMBER);
            }
            if (!tracker.checkMember($SCHEMA_DOUBLE_MEMBER)) {
                tracker.setMember($SCHEMA_DOUBLE_MEMBER);
            }
            if (!tracker.checkMember($SCHEMA_BOOLEAN_MEMBER)) {
                tracker.setMember($SCHEMA_BOOLEAN_MEMBER);
            }
            return this;
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

