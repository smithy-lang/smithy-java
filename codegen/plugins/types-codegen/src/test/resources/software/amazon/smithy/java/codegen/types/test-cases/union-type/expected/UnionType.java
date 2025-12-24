
package software.amazon.smithy.java.example.standalone.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.Unit;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public sealed interface UnionType extends SerializableStruct {
    Schema $SCHEMA = Schemas.UNION_TYPE;

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
    record BlobValueMember(ByteBuffer blobValue) implements UnionType {
        private static final Schema $SCHEMA_BLOB_VALUE = $SCHEMA.member("blobValue");
        public BlobValueMember {
            Objects.requireNonNull(blobValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBlob($SCHEMA_BLOB_VALUE, blobValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public ByteBuffer getValue() {
            return blobValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record BooleanValueMember(boolean booleanValue) implements UnionType {
        private static final Schema $SCHEMA_BOOLEAN_VALUE = $SCHEMA.member("booleanValue");
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBoolean($SCHEMA_BOOLEAN_VALUE, booleanValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Boolean getValue() {
            return booleanValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record ListValueMember(List<String> listValue) implements UnionType {
        private static final Schema $SCHEMA_LIST_VALUE = $SCHEMA.member("listValue");
        public ListValueMember {
            listValue = Collections.unmodifiableList(Objects.requireNonNull(listValue, "Union value cannot be null"));
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeList($SCHEMA_LIST_VALUE, listValue, listValue.size(), SharedSerde.ListOfStringSerializer.INSTANCE);
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<String> getValue() {
            return listValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record MapValueMember(Map<String, String> mapValue) implements UnionType {
        private static final Schema $SCHEMA_MAP_VALUE = $SCHEMA.member("mapValue");
        public MapValueMember {
            mapValue = Collections.unmodifiableMap(Objects.requireNonNull(mapValue, "Union value cannot be null"));
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeMap($SCHEMA_MAP_VALUE, mapValue, mapValue.size(), SharedSerde.StringStringMapSerializer.INSTANCE);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, String> getValue() {
            return mapValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record BigDecimalValueMember(BigDecimal bigDecimalValue) implements UnionType {
        private static final Schema $SCHEMA_BIG_DECIMAL_VALUE = $SCHEMA.member("bigDecimalValue");
        public BigDecimalValueMember {
            Objects.requireNonNull(bigDecimalValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBigDecimal($SCHEMA_BIG_DECIMAL_VALUE, bigDecimalValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public BigDecimal getValue() {
            return bigDecimalValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record BigIntegerValueMember(BigInteger bigIntegerValue) implements UnionType {
        private static final Schema $SCHEMA_BIG_INTEGER_VALUE = $SCHEMA.member("bigIntegerValue");
        public BigIntegerValueMember {
            Objects.requireNonNull(bigIntegerValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBigInteger($SCHEMA_BIG_INTEGER_VALUE, bigIntegerValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public BigInteger getValue() {
            return bigIntegerValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record ByteValueMember(byte byteValue) implements UnionType {
        private static final Schema $SCHEMA_BYTE_VALUE = $SCHEMA.member("byteValue");
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeByte($SCHEMA_BYTE_VALUE, byteValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Byte getValue() {
            return byteValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record DoubleValueMember(double doubleValue) implements UnionType {
        private static final Schema $SCHEMA_DOUBLE_VALUE = $SCHEMA.member("doubleValue");
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeDouble($SCHEMA_DOUBLE_VALUE, doubleValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Double getValue() {
            return doubleValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record FloatValueMember(float floatValue) implements UnionType {
        private static final Schema $SCHEMA_FLOAT_VALUE = $SCHEMA.member("floatValue");
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeFloat($SCHEMA_FLOAT_VALUE, floatValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Float getValue() {
            return floatValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record IntegerValueMember(int integerValue) implements UnionType {
        private static final Schema $SCHEMA_INTEGER_VALUE = $SCHEMA.member("integerValue");
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger($SCHEMA_INTEGER_VALUE, integerValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Integer getValue() {
            return integerValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record LongValueMember(long longValue) implements UnionType {
        private static final Schema $SCHEMA_LONG_VALUE = $SCHEMA.member("longValue");
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeLong($SCHEMA_LONG_VALUE, longValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Long getValue() {
            return longValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record ShortValueMember(short shortValue) implements UnionType {
        private static final Schema $SCHEMA_SHORT_VALUE = $SCHEMA.member("shortValue");
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeShort($SCHEMA_SHORT_VALUE, shortValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Short getValue() {
            return shortValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record StringValueMember(String stringValue) implements UnionType {
        private static final Schema $SCHEMA_STRING_VALUE = $SCHEMA.member("stringValue");
        public StringValueMember {
            Objects.requireNonNull(stringValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString($SCHEMA_STRING_VALUE, stringValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public String getValue() {
            return stringValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record StructureValueMember(NestedStruct structureValue) implements UnionType {
        private static final Schema $SCHEMA_STRUCTURE_VALUE = $SCHEMA.member("structureValue");
        public StructureValueMember {
            Objects.requireNonNull(structureValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct($SCHEMA_STRUCTURE_VALUE, structureValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public NestedStruct getValue() {
            return structureValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record TimestampValueMember(Instant timestampValue) implements UnionType {
        private static final Schema $SCHEMA_TIMESTAMP_VALUE = $SCHEMA.member("timestampValue");
        public TimestampValueMember {
            Objects.requireNonNull(timestampValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeTimestamp($SCHEMA_TIMESTAMP_VALUE, timestampValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Instant getValue() {
            return timestampValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record UnionValueMember(NestedUnion unionValue) implements UnionType {
        private static final Schema $SCHEMA_UNION_VALUE = $SCHEMA.member("unionValue");
        public UnionValueMember {
            Objects.requireNonNull(unionValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct($SCHEMA_UNION_VALUE, unionValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public NestedUnion getValue() {
            return unionValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record EnumValueMember(NestedEnum enumValue) implements UnionType {
        private static final Schema $SCHEMA_ENUM_VALUE = $SCHEMA.member("enumValue");
        public EnumValueMember {
            Objects.requireNonNull(enumValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString($SCHEMA_ENUM_VALUE, enumValue.getValue());
        }

        @Override
        @SuppressWarnings("unchecked")
        public NestedEnum getValue() {
            return enumValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record IntEnumValueMember(NestedIntEnum intEnumValue) implements UnionType {
        private static final Schema $SCHEMA_INT_ENUM_VALUE = $SCHEMA.member("intEnumValue");
        public IntEnumValueMember {
            Objects.requireNonNull(intEnumValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger($SCHEMA_INT_ENUM_VALUE, intEnumValue.getValue());
        }

        @Override
        @SuppressWarnings("unchecked")
        public NestedIntEnum getValue() {
            return intEnumValue;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    @SmithyGenerated
    record UnitValueMember() implements UnionType {
        private static final Schema $SCHEMA_UNIT_VALUE = $SCHEMA.member("unitValue");
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct($SCHEMA_UNIT_VALUE, Unit.getInstance());
        }

        @Override
        @SuppressWarnings("unchecked")
        public Unit getValue() {
            return null;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    record $Unknown(String memberName) implements UnionType {
        @Override
        public void serialize(ShapeSerializer serializer) {
            throw new UnsupportedOperationException("Cannot serialize union with unknown member " + this.memberName);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        @SuppressWarnings("unchecked")
        public String getValue() {
            return memberName;
        }

        private record $Hidden() implements UnionType {
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
        UnionType build();
    }

    /**
     * @return returns a new Builder.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link UnionType}.
     */
    final class Builder implements ShapeBuilder<UnionType>, BuildStage {
        private UnionType value;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        public BuildStage blobValue(ByteBuffer value) {
            return setValue(new BlobValueMember(value));
        }

        public BuildStage booleanValue(boolean value) {
            return setValue(new BooleanValueMember(value));
        }

        public BuildStage listValue(List<String> value) {
            return setValue(new ListValueMember(value));
        }

        public BuildStage mapValue(Map<String, String> value) {
            return setValue(new MapValueMember(value));
        }

        public BuildStage bigDecimalValue(BigDecimal value) {
            return setValue(new BigDecimalValueMember(value));
        }

        public BuildStage bigIntegerValue(BigInteger value) {
            return setValue(new BigIntegerValueMember(value));
        }

        public BuildStage byteValue(byte value) {
            return setValue(new ByteValueMember(value));
        }

        public BuildStage doubleValue(double value) {
            return setValue(new DoubleValueMember(value));
        }

        public BuildStage floatValue(float value) {
            return setValue(new FloatValueMember(value));
        }

        public BuildStage integerValue(int value) {
            return setValue(new IntegerValueMember(value));
        }

        public BuildStage longValue(long value) {
            return setValue(new LongValueMember(value));
        }

        public BuildStage shortValue(short value) {
            return setValue(new ShortValueMember(value));
        }

        public BuildStage stringValue(String value) {
            return setValue(new StringValueMember(value));
        }

        public BuildStage structureValue(NestedStruct value) {
            return setValue(new StructureValueMember(value));
        }

        public BuildStage timestampValue(Instant value) {
            return setValue(new TimestampValueMember(value));
        }

        public BuildStage unionValue(NestedUnion value) {
            return setValue(new UnionValueMember(value));
        }

        public BuildStage enumValue(NestedEnum value) {
            return setValue(new EnumValueMember(value));
        }

        public BuildStage intEnumValue(NestedIntEnum value) {
            return setValue(new IntEnumValueMember(value));
        }

        public BuildStage unitValue(Unit value) {
            return setValue(new UnitValueMember());
        }

        public BuildStage $unknownMember(String memberName) {
            return setValue(new $Unknown(memberName));
        }

        private BuildStage setValue(UnionType value) {
            if (this.value != null) {
                throw new IllegalArgumentException("Only one value may be set for unions");
            }
            this.value = value;
            return this;
        }

        @Override
        public UnionType build() {
            return Objects.requireNonNull(value, "no union value set");
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> blobValue((ByteBuffer) SchemaUtils.validateSameMember(BlobValueMember.$SCHEMA_BLOB_VALUE, member, value));
                case 1 -> booleanValue((boolean) SchemaUtils.validateSameMember(BooleanValueMember.$SCHEMA_BOOLEAN_VALUE, member, value));
                case 2 -> listValue((List<String>) SchemaUtils.validateSameMember(ListValueMember.$SCHEMA_LIST_VALUE, member, value));
                case 3 -> mapValue((Map<String, String>) SchemaUtils.validateSameMember(MapValueMember.$SCHEMA_MAP_VALUE, member, value));
                case 4 -> bigDecimalValue((BigDecimal) SchemaUtils.validateSameMember(BigDecimalValueMember.$SCHEMA_BIG_DECIMAL_VALUE, member, value));
                case 5 -> bigIntegerValue((BigInteger) SchemaUtils.validateSameMember(BigIntegerValueMember.$SCHEMA_BIG_INTEGER_VALUE, member, value));
                case 6 -> byteValue((byte) SchemaUtils.validateSameMember(ByteValueMember.$SCHEMA_BYTE_VALUE, member, value));
                case 7 -> doubleValue((double) SchemaUtils.validateSameMember(DoubleValueMember.$SCHEMA_DOUBLE_VALUE, member, value));
                case 8 -> floatValue((float) SchemaUtils.validateSameMember(FloatValueMember.$SCHEMA_FLOAT_VALUE, member, value));
                case 9 -> integerValue((int) SchemaUtils.validateSameMember(IntegerValueMember.$SCHEMA_INTEGER_VALUE, member, value));
                case 10 -> longValue((long) SchemaUtils.validateSameMember(LongValueMember.$SCHEMA_LONG_VALUE, member, value));
                case 11 -> shortValue((short) SchemaUtils.validateSameMember(ShortValueMember.$SCHEMA_SHORT_VALUE, member, value));
                case 12 -> stringValue((String) SchemaUtils.validateSameMember(StringValueMember.$SCHEMA_STRING_VALUE, member, value));
                case 13 -> structureValue((NestedStruct) SchemaUtils.validateSameMember(StructureValueMember.$SCHEMA_STRUCTURE_VALUE, member, value));
                case 14 -> timestampValue((Instant) SchemaUtils.validateSameMember(TimestampValueMember.$SCHEMA_TIMESTAMP_VALUE, member, value));
                case 15 -> unionValue((NestedUnion) SchemaUtils.validateSameMember(UnionValueMember.$SCHEMA_UNION_VALUE, member, value));
                case 16 -> enumValue((NestedEnum) SchemaUtils.validateSameMember(EnumValueMember.$SCHEMA_ENUM_VALUE, member, value));
                case 17 -> intEnumValue((NestedIntEnum) SchemaUtils.validateSameMember(IntEnumValueMember.$SCHEMA_INT_ENUM_VALUE, member, value));
                case 18 -> unitValue((Unit) SchemaUtils.validateSameMember(UnitValueMember.$SCHEMA_UNIT_VALUE, member, value));
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
                    case 13 -> builder.structureValue(NestedStruct.builder().deserializeMember(de, member).build());
                    case 14 -> builder.timestampValue(de.readTimestamp(member));
                    case 15 -> builder.unionValue(NestedUnion.builder().deserializeMember(de, member).build());
                    case 16 -> builder.enumValue(NestedEnum.builder().deserializeMember(de, member).build());
                    case 17 -> builder.intEnumValue(NestedIntEnum.builder().deserializeMember(de, member).build());
                    case 18 -> builder.unitValue(Unit.builder().deserializeMember(de, member).build());
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

