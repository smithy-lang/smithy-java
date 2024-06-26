

package io.smithy.codegen.test.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public abstract class UnionType implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.unions#UnionType");

    private static final Schema SCHEMA_BLOB_VALUE = Schema.memberBuilder("blobValue", PreludeSchemas.BLOB)
        .id(ID)
        .build();

    private static final Schema SCHEMA_BOOLEAN_VALUE = Schema.memberBuilder("booleanValue", PreludeSchemas.BOOLEAN)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_VALUE = Schema.memberBuilder("listValue", SharedSchemas.LIST_OF_STRING)
        .id(ID)
        .build();

    private static final Schema SCHEMA_MAP_VALUE = Schema.memberBuilder("mapValue", SharedSchemas.STRING_STRING_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_BIG_DECIMAL_VALUE = Schema.memberBuilder("bigDecimalValue", PreludeSchemas.BIG_DECIMAL)
        .id(ID)
        .build();

    private static final Schema SCHEMA_BIG_INTEGER_VALUE = Schema.memberBuilder("bigIntegerValue", PreludeSchemas.BIG_INTEGER)
        .id(ID)
        .build();

    private static final Schema SCHEMA_BYTE_VALUE = Schema.memberBuilder("byteValue", PreludeSchemas.BYTE)
        .id(ID)
        .build();

    private static final Schema SCHEMA_DOUBLE_VALUE = Schema.memberBuilder("doubleValue", PreludeSchemas.DOUBLE)
        .id(ID)
        .build();

    private static final Schema SCHEMA_FLOAT_VALUE = Schema.memberBuilder("floatValue", PreludeSchemas.FLOAT)
        .id(ID)
        .build();

    private static final Schema SCHEMA_INTEGER_VALUE = Schema.memberBuilder("integerValue", PreludeSchemas.INTEGER)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LONG_VALUE = Schema.memberBuilder("longValue", PreludeSchemas.LONG)
        .id(ID)
        .build();

    private static final Schema SCHEMA_SHORT_VALUE = Schema.memberBuilder("shortValue", PreludeSchemas.SHORT)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_VALUE = Schema.memberBuilder("stringValue", PreludeSchemas.STRING)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRUCTURE_VALUE = Schema.memberBuilder("structureValue", NestedStruct.SCHEMA)
        .id(ID)
        .build();

    private static final Schema SCHEMA_TIMESTAMP_VALUE = Schema.memberBuilder("timestampValue", PreludeSchemas.TIMESTAMP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_UNION_VALUE = Schema.memberBuilder("unionValue", NestedUnion.SCHEMA)
        .id(ID)
        .build();

    private static final Schema SCHEMA_ENUM_VALUE = Schema.memberBuilder("enumValue", NestedEnum.SCHEMA)
        .id(ID)
        .build();

    private static final Schema SCHEMA_INT_ENUM_VALUE = Schema.memberBuilder("intEnumValue", NestedIntEnum.SCHEMA)
        .id(ID)
        .build();

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.UNION)
        .members(
            SCHEMA_BLOB_VALUE,
            SCHEMA_BOOLEAN_VALUE,
            SCHEMA_LIST_VALUE,
            SCHEMA_MAP_VALUE,
            SCHEMA_BIG_DECIMAL_VALUE,
            SCHEMA_BIG_INTEGER_VALUE,
            SCHEMA_BYTE_VALUE,
            SCHEMA_DOUBLE_VALUE,
            SCHEMA_FLOAT_VALUE,
            SCHEMA_INTEGER_VALUE,
            SCHEMA_LONG_VALUE,
            SCHEMA_SHORT_VALUE,
            SCHEMA_STRING_VALUE,
            SCHEMA_STRUCTURE_VALUE,
            SCHEMA_TIMESTAMP_VALUE,
            SCHEMA_UNION_VALUE,
            SCHEMA_ENUM_VALUE,
            SCHEMA_INT_ENUM_VALUE
        )
        .build();

    private final Type type;

    private UnionType(Type type) {
        this.type = type;
    }

    public Type type() {
        return type;
    }

    public enum Type {
        $UNKNOWN,
        BLOB_VALUE,
        BOOLEAN_VALUE,
        LIST_VALUE,
        MAP_VALUE,
        BIG_DECIMAL_VALUE,
        BIG_INTEGER_VALUE,
        BYTE_VALUE,
        DOUBLE_VALUE,
        FLOAT_VALUE,
        INTEGER_VALUE,
        LONG_VALUE,
        SHORT_VALUE,
        STRING_VALUE,
        STRUCTURE_VALUE,
        TIMESTAMP_VALUE,
        UNION_VALUE,
        ENUM_VALUE,
        INT_ENUM_VALUE
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    public byte[] blobValue() {
        return null;
    }

    public Boolean booleanValue() {
        return null;
    }

    public List<String> listValue() {
        return null;
    }

    public Map<String, String> mapValue() {
        return null;
    }

    public BigDecimal bigDecimalValue() {
        return null;
    }

    public BigInteger bigIntegerValue() {
        return null;
    }

    public Byte byteValue() {
        return null;
    }

    public Double doubleValue() {
        return null;
    }

    public Float floatValue() {
        return null;
    }

    public Integer integerValue() {
        return null;
    }

    public Long longValue() {
        return null;
    }

    public Short shortValue() {
        return null;
    }

    public String stringValue() {
        return null;
    }

    public NestedStruct structureValue() {
        return null;
    }

    public Instant timestampValue() {
        return null;
    }

    public NestedUnion unionValue() {
        return null;
    }

    public NestedEnum enumValue() {
        return null;
    }

    public NestedIntEnum intEnumValue() {
        return null;
    }

    @SmithyGenerated
    public static final class BlobValueMember extends UnionType {
        private final transient byte[] value;

        public BlobValueMember(byte[] value) {
            super(Type.BLOB_VALUE);
            this.value = value;
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBlob(SCHEMA_BLOB_VALUE, value);
        }

        @Override
        public byte[] blobValue() {
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
            BlobValueMember that = (BlobValueMember) other;
            return Arrays.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }
    }

    @SmithyGenerated
    public static final class BooleanValueMember extends UnionType {
        private final transient boolean value;

        public BooleanValueMember(boolean value) {
            super(Type.BOOLEAN_VALUE);
            this.value = value;
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBoolean(SCHEMA_BOOLEAN_VALUE, value);
        }

        @Override
        public Boolean booleanValue() {
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
            BooleanValueMember that = (BooleanValueMember) other;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class ListValueMember extends UnionType {
        private final transient List<String> value;

        public ListValueMember(List<String> value) {
            super(Type.LIST_VALUE);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeList(SCHEMA_LIST_VALUE, value, SharedSerde.ListOfStringSerializer.INSTANCE);
        }

        @Override
        public List<String> listValue() {
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
            ListValueMember that = (ListValueMember) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class MapValueMember extends UnionType {
        private final transient Map<String, String> value;

        public MapValueMember(Map<String, String> value) {
            super(Type.MAP_VALUE);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeMap(SCHEMA_MAP_VALUE, value, SharedSerde.StringStringMapSerializer.INSTANCE);
        }

        @Override
        public Map<String, String> mapValue() {
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
            MapValueMember that = (MapValueMember) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class BigDecimalValueMember extends UnionType {
        private final transient BigDecimal value;

        public BigDecimalValueMember(BigDecimal value) {
            super(Type.BIG_DECIMAL_VALUE);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBigDecimal(SCHEMA_BIG_DECIMAL_VALUE, value);
        }

        @Override
        public BigDecimal bigDecimalValue() {
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
            BigDecimalValueMember that = (BigDecimalValueMember) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class BigIntegerValueMember extends UnionType {
        private final transient BigInteger value;

        public BigIntegerValueMember(BigInteger value) {
            super(Type.BIG_INTEGER_VALUE);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBigInteger(SCHEMA_BIG_INTEGER_VALUE, value);
        }

        @Override
        public BigInteger bigIntegerValue() {
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
            BigIntegerValueMember that = (BigIntegerValueMember) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class ByteValueMember extends UnionType {
        private final transient byte value;

        public ByteValueMember(byte value) {
            super(Type.BYTE_VALUE);
            this.value = value;
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeByte(SCHEMA_BYTE_VALUE, value);
        }

        @Override
        public Byte byteValue() {
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
            ByteValueMember that = (ByteValueMember) other;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class DoubleValueMember extends UnionType {
        private final transient double value;

        public DoubleValueMember(double value) {
            super(Type.DOUBLE_VALUE);
            this.value = value;
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeDouble(SCHEMA_DOUBLE_VALUE, value);
        }

        @Override
        public Double doubleValue() {
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
            DoubleValueMember that = (DoubleValueMember) other;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class FloatValueMember extends UnionType {
        private final transient float value;

        public FloatValueMember(float value) {
            super(Type.FLOAT_VALUE);
            this.value = value;
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeFloat(SCHEMA_FLOAT_VALUE, value);
        }

        @Override
        public Float floatValue() {
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
            FloatValueMember that = (FloatValueMember) other;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class IntegerValueMember extends UnionType {
        private final transient int value;

        public IntegerValueMember(int value) {
            super(Type.INTEGER_VALUE);
            this.value = value;
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger(SCHEMA_INTEGER_VALUE, value);
        }

        @Override
        public Integer integerValue() {
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
            IntegerValueMember that = (IntegerValueMember) other;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class LongValueMember extends UnionType {
        private final transient long value;

        public LongValueMember(long value) {
            super(Type.LONG_VALUE);
            this.value = value;
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeLong(SCHEMA_LONG_VALUE, value);
        }

        @Override
        public Long longValue() {
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
            LongValueMember that = (LongValueMember) other;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class ShortValueMember extends UnionType {
        private final transient short value;

        public ShortValueMember(short value) {
            super(Type.SHORT_VALUE);
            this.value = value;
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeShort(SCHEMA_SHORT_VALUE, value);
        }

        @Override
        public Short shortValue() {
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
            ShortValueMember that = (ShortValueMember) other;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class StringValueMember extends UnionType {
        private final transient String value;

        public StringValueMember(String value) {
            super(Type.STRING_VALUE);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SCHEMA_STRING_VALUE, value);
        }

        @Override
        public String stringValue() {
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
            StringValueMember that = (StringValueMember) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class StructureValueMember extends UnionType {
        private final transient NestedStruct value;

        public StructureValueMember(NestedStruct value) {
            super(Type.STRUCTURE_VALUE);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA_STRUCTURE_VALUE, value);
        }

        @Override
        public NestedStruct structureValue() {
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
            StructureValueMember that = (StructureValueMember) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class TimestampValueMember extends UnionType {
        private final transient Instant value;

        public TimestampValueMember(Instant value) {
            super(Type.TIMESTAMP_VALUE);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeTimestamp(SCHEMA_TIMESTAMP_VALUE, value);
        }

        @Override
        public Instant timestampValue() {
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
            TimestampValueMember that = (TimestampValueMember) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class UnionValueMember extends UnionType {
        private final transient NestedUnion value;

        public UnionValueMember(NestedUnion value) {
            super(Type.UNION_VALUE);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA_UNION_VALUE, value);
        }

        @Override
        public NestedUnion unionValue() {
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
            UnionValueMember that = (UnionValueMember) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class EnumValueMember extends UnionType {
        private final transient NestedEnum value;

        public EnumValueMember(NestedEnum value) {
            super(Type.ENUM_VALUE);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SCHEMA_ENUM_VALUE, value.value());
        }

        @Override
        public NestedEnum enumValue() {
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
            EnumValueMember that = (EnumValueMember) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class IntEnumValueMember extends UnionType {
        private final transient NestedIntEnum value;

        public IntEnumValueMember(NestedIntEnum value) {
            super(Type.INT_ENUM_VALUE);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger(SCHEMA_INT_ENUM_VALUE, value.value());
        }

        @Override
        public NestedIntEnum intEnumValue() {
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
            IntEnumValueMember that = (IntEnumValueMember) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    public interface BuildStage {
        UnionType build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link UnionType}.
     */
    public static final class Builder implements ShapeBuilder<UnionType>, BuildStage {
        private UnionType value;

        private Builder() {}

        public BuildStage blobValue(byte[] value) {
            checkForExistingValue();
            this.value = new BlobValueMember(value);
            return this;
        }

        public BuildStage booleanValue(boolean value) {
            checkForExistingValue();
            this.value = new BooleanValueMember(value);
            return this;
        }

        public BuildStage listValue(List<String> value) {
            checkForExistingValue();
            this.value = new ListValueMember(value);
            return this;
        }

        public BuildStage mapValue(Map<String, String> value) {
            checkForExistingValue();
            this.value = new MapValueMember(value);
            return this;
        }

        public BuildStage bigDecimalValue(BigDecimal value) {
            checkForExistingValue();
            this.value = new BigDecimalValueMember(value);
            return this;
        }

        public BuildStage bigIntegerValue(BigInteger value) {
            checkForExistingValue();
            this.value = new BigIntegerValueMember(value);
            return this;
        }

        public BuildStage byteValue(byte value) {
            checkForExistingValue();
            this.value = new ByteValueMember(value);
            return this;
        }

        public BuildStage doubleValue(double value) {
            checkForExistingValue();
            this.value = new DoubleValueMember(value);
            return this;
        }

        public BuildStage floatValue(float value) {
            checkForExistingValue();
            this.value = new FloatValueMember(value);
            return this;
        }

        public BuildStage integerValue(int value) {
            checkForExistingValue();
            this.value = new IntegerValueMember(value);
            return this;
        }

        public BuildStage longValue(long value) {
            checkForExistingValue();
            this.value = new LongValueMember(value);
            return this;
        }

        public BuildStage shortValue(short value) {
            checkForExistingValue();
            this.value = new ShortValueMember(value);
            return this;
        }

        public BuildStage stringValue(String value) {
            checkForExistingValue();
            this.value = new StringValueMember(value);
            return this;
        }

        public BuildStage structureValue(NestedStruct value) {
            checkForExistingValue();
            this.value = new StructureValueMember(value);
            return this;
        }

        public BuildStage timestampValue(Instant value) {
            checkForExistingValue();
            this.value = new TimestampValueMember(value);
            return this;
        }

        public BuildStage unionValue(NestedUnion value) {
            checkForExistingValue();
            this.value = new UnionValueMember(value);
            return this;
        }

        public BuildStage enumValue(NestedEnum value) {
            checkForExistingValue();
            this.value = new EnumValueMember(value);
            return this;
        }

        public BuildStage intEnumValue(NestedIntEnum value) {
            checkForExistingValue();
            this.value = new IntEnumValueMember(value);
            return this;
        }

        private void checkForExistingValue() {
            if (this.value != null) {
                throw new SerializationException("Only one value may be set for unions");
            }
        }

        @Override
        public UnionType build() {
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
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.blobValue(de.readBlob(member));
                    case 1 -> builder.booleanValue(de.readBoolean(member));
                    case 2 -> builder.listValue(SharedSerde.deserializeListOfString(member, de));
                    case 3 -> builder.mapValue(SharedSerde.deserializeStringStringMap(member, de));
                    case 4 -> builder.bigDecimalValue(de.readBigDecimal(member));
                    case 5 -> builder.bigIntegerValue(de.readBigInteger(member));
                    case 6 -> builder.byteValue(de.readByte(member));
                    case 7 -> builder.doubleValue(de.readDouble(member));
                    case 8 -> builder.floatValue(de.readFloat(member));
                    case 9 -> builder.integerValue(de.readInteger(member));
                    case 10 -> builder.longValue(de.readLong(member));
                    case 11 -> builder.shortValue(de.readShort(member));
                    case 12 -> builder.stringValue(de.readString(member));
                    case 13 -> builder.structureValue(NestedStruct.builder().deserialize(de).build());
                    case 14 -> builder.timestampValue(de.readTimestamp(member));
                    case 15 -> builder.unionValue(NestedUnion.builder().deserialize(de).build());
                    case 16 -> builder.enumValue(NestedEnum.builder().deserialize(de).build());
                    case 17 -> builder.intEnumValue(NestedIntEnum.builder().deserialize(de).build());
                }
            }
        }

    }
}

