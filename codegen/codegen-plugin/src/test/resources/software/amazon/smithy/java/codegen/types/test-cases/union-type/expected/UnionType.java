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
        private static final Schema $0 = $SCHEMA.member(0); // blobValue
        public BlobValueMember {
            Objects.requireNonNull(blobValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBlob($0, blobValue);
        }

        @Override
        public ByteBuffer getValue() {
            return blobValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record BooleanValueMember(boolean booleanValue) implements UnionType {
        private static final Schema $1 = $SCHEMA.member(1); // booleanValue
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBoolean($1, booleanValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Boolean getValue() {
            return booleanValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record ListValueMember(List<String> listValue) implements UnionType {
        private static final Schema $2 = $SCHEMA.member(2); // listValue
        public ListValueMember {
            listValue = Collections.unmodifiableList(Objects.requireNonNull(listValue, "Union value cannot be null"));
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStringList($2, listValue, $2.listMember());
        }

        @Override
        public List<String> getValue() {
            return listValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record MapValueMember(Map<String, String> mapValue) implements UnionType {
        private static final Schema $3 = $SCHEMA.member(3); // mapValue
        public MapValueMember {
            mapValue = Collections.unmodifiableMap(Objects.requireNonNull(mapValue, "Union value cannot be null"));
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStringMap($3, mapValue, $3.mapKeyMember(), $3.mapValueMember());
        }

        @Override
        public Map<String, String> getValue() {
            return mapValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record BigDecimalValueMember(BigDecimal bigDecimalValue) implements UnionType {
        private static final Schema $4 = $SCHEMA.member(4); // bigDecimalValue
        public BigDecimalValueMember {
            Objects.requireNonNull(bigDecimalValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBigDecimal($4, bigDecimalValue);
        }

        @Override
        public BigDecimal getValue() {
            return bigDecimalValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record BigIntegerValueMember(BigInteger bigIntegerValue) implements UnionType {
        private static final Schema $5 = $SCHEMA.member(5); // bigIntegerValue
        public BigIntegerValueMember {
            Objects.requireNonNull(bigIntegerValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBigInteger($5, bigIntegerValue);
        }

        @Override
        public BigInteger getValue() {
            return bigIntegerValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record ByteValueMember(byte byteValue) implements UnionType {
        private static final Schema $6 = $SCHEMA.member(6); // byteValue
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeByte($6, byteValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Byte getValue() {
            return byteValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record DoubleValueMember(double doubleValue) implements UnionType {
        private static final Schema $7 = $SCHEMA.member(7); // doubleValue
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeDouble($7, doubleValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Double getValue() {
            return doubleValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record FloatValueMember(float floatValue) implements UnionType {
        private static final Schema $8 = $SCHEMA.member(8); // floatValue
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeFloat($8, floatValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Float getValue() {
            return floatValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record IntegerValueMember(int integerValue) implements UnionType {
        private static final Schema $9 = $SCHEMA.member(9); // integerValue
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger($9, integerValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Integer getValue() {
            return integerValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record LongValueMember(long longValue) implements UnionType {
        private static final Schema $10 = $SCHEMA.member(10); // longValue
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeLong($10, longValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Long getValue() {
            return longValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record ShortValueMember(short shortValue) implements UnionType {
        private static final Schema $11 = $SCHEMA.member(11); // shortValue
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeShort($11, shortValue);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Short getValue() {
            return shortValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record StringValueMember(String stringValue) implements UnionType {
        private static final Schema $12 = $SCHEMA.member(12); // stringValue
        public StringValueMember {
            Objects.requireNonNull(stringValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString($12, stringValue);
        }

        @Override
        public String getValue() {
            return stringValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record StructureValueMember(NestedStruct structureValue) implements UnionType {
        private static final Schema $13 = $SCHEMA.member(13); // structureValue
        public StructureValueMember {
            Objects.requireNonNull(structureValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct($13, structureValue);
        }

        @Override
        public NestedStruct getValue() {
            return structureValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record TimestampValueMember(Instant timestampValue) implements UnionType {
        private static final Schema $14 = $SCHEMA.member(14); // timestampValue
        public TimestampValueMember {
            Objects.requireNonNull(timestampValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeTimestamp($14, timestampValue);
        }

        @Override
        public Instant getValue() {
            return timestampValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record UnionValueMember(NestedUnion unionValue) implements UnionType {
        private static final Schema $15 = $SCHEMA.member(15); // unionValue
        public UnionValueMember {
            Objects.requireNonNull(unionValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct($15, unionValue);
        }

        @Override
        public NestedUnion getValue() {
            return unionValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record EnumValueMember(NestedEnum enumValue) implements UnionType {
        private static final Schema $16 = $SCHEMA.member(16); // enumValue
        public EnumValueMember {
            Objects.requireNonNull(enumValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString($16, enumValue.getValue());
        }

        @Override
        public NestedEnum getValue() {
            return enumValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record IntEnumValueMember(NestedIntEnum intEnumValue) implements UnionType {
        private static final Schema $17 = $SCHEMA.member(17); // intEnumValue
        public IntEnumValueMember {
            Objects.requireNonNull(intEnumValue, "Union value cannot be null");
        }
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger($17, intEnumValue.getValue());
        }

        @Override
        public NestedIntEnum getValue() {
            return intEnumValue;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    @SmithyGenerated
    record UnitValueMember() implements UnionType {
        private static final Schema $18 = $SCHEMA.member(18); // unitValue
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct($18, Unit.getInstance());
        }

        @Override
        public Unit getValue() {
            return null;
        }

        @Override
        public String toString() {
            return SchemaUtils.toString(this);
        }

    }

    record $Unknown(String memberName) implements UnionType {
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
            SchemaUtils.validateMemberOf($SCHEMA, member);
            switch (member.memberIndex()) {
                case 0 -> blobValue((ByteBuffer) value);
                case 1 -> booleanValue((boolean) value);
                case 2 -> listValue((List<String>) value);
                case 3 -> mapValue((Map<String, String>) value);
                case 4 -> bigDecimalValue((BigDecimal) value);
                case 5 -> bigIntegerValue((BigInteger) value);
                case 6 -> byteValue((byte) value);
                case 7 -> doubleValue((double) value);
                case 8 -> floatValue((float) value);
                case 9 -> integerValue((int) value);
                case 10 -> longValue((long) value);
                case 11 -> shortValue((short) value);
                case 12 -> stringValue((String) value);
                case 13 -> structureValue((NestedStruct) value);
                case 14 -> timestampValue((Instant) value);
                case 15 -> unionValue((NestedUnion) value);
                case 16 -> enumValue((NestedEnum) value);
                case 17 -> intEnumValue((NestedIntEnum) value);
                case 18 -> unitValue((Unit) value);
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
                case 16 -> builder.enumValue(NestedEnum.fromValue(de.readString(member)));
                case 17 -> builder.intEnumValue(NestedIntEnum.fromValue(de.readInteger(member)));
                case 18 -> builder.unitValue(Unit.builder().deserializeMember(de, member).build());
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
