package software.amazon.smithy.java.example.defaults.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class DefaultStructure implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.DEFAULT_STRUCTURE;
    private static final Schema $0 = $SCHEMA.member(0); // boolean
    private static final Schema $1 = $SCHEMA.member(1); // bigDecimal
    private static final Schema $2 = $SCHEMA.member(2); // bigDecimalWithDoubleDefault
    private static final Schema $3 = $SCHEMA.member(3); // bigDecimalWithLongDefault
    private static final Schema $4 = $SCHEMA.member(4); // bigInteger
    private static final Schema $5 = $SCHEMA.member(5); // bigIntegerWithLongDefault
    private static final Schema $6 = $SCHEMA.member(6); // byte
    private static final Schema $7 = $SCHEMA.member(7); // double
    private static final Schema $8 = $SCHEMA.member(8); // float
    private static final Schema $9 = $SCHEMA.member(9); // integer
    private static final Schema $10 = $SCHEMA.member(10); // long
    private static final Schema $11 = $SCHEMA.member(11); // short
    private static final Schema $12 = $SCHEMA.member(12); // string
    private static final Schema $13 = $SCHEMA.member(13); // blob
    private static final Schema $14 = $SCHEMA.member(14); // streamingBlob
    private static final Schema $15 = $SCHEMA.member(15); // boolDoc
    private static final Schema $16 = $SCHEMA.member(16); // stringDoc
    private static final Schema $17 = $SCHEMA.member(17); // numberDoc
    private static final Schema $18 = $SCHEMA.member(18); // floatingPointnumberDoc
    private static final Schema $19 = $SCHEMA.member(19); // listDoc
    private static final Schema $20 = $SCHEMA.member(20); // mapDoc
    private static final Schema $21 = $SCHEMA.member(21); // list
    private static final Schema $22 = $SCHEMA.member(22); // map
    private static final Schema $23 = $SCHEMA.member(23); // timestamp
    private static final Schema $24 = $SCHEMA.member(24); // enum
    private static final Schema $25 = $SCHEMA.member(25); // intEnum

    public static final ShapeId $ID = $SCHEMA.id();

    private final transient boolean booleanMember;
    private final transient BigDecimal bigDecimal;
    private final transient BigDecimal bigDecimalWithDoubleDefault;
    private final transient BigDecimal bigDecimalWithLongDefault;
    private final transient BigInteger bigInteger;
    private final transient BigInteger bigIntegerWithLongDefault;
    private final transient byte byteMember;
    private final transient double doubleMember;
    private final transient float floatMember;
    private final transient int integer;
    private final transient long longMember;
    private final transient short shortMember;
    private final transient String string;
    private final transient ByteBuffer blob;
    private final transient DataStream streamingBlob;
    private final transient Document boolDoc;
    private final transient Document stringDoc;
    private final transient Document numberDoc;
    private final transient Document floatingPointnumberDoc;
    private final transient Document listDoc;
    private final transient Document mapDoc;
    private final transient List<String> list;
    private final transient Map<String, String> map;
    private final transient Instant timestamp;
    private final transient NestedEnum enumMember;
    private final transient NestedIntEnum intEnum;

    private DefaultStructure(Builder builder) {
        this.booleanMember = builder.booleanMember;
        this.bigDecimal = builder.bigDecimal;
        this.bigDecimalWithDoubleDefault = builder.bigDecimalWithDoubleDefault;
        this.bigDecimalWithLongDefault = builder.bigDecimalWithLongDefault;
        this.bigInteger = builder.bigInteger;
        this.bigIntegerWithLongDefault = builder.bigIntegerWithLongDefault;
        this.byteMember = builder.byteMember;
        this.doubleMember = builder.doubleMember;
        this.floatMember = builder.floatMember;
        this.integer = builder.integer;
        this.longMember = builder.longMember;
        this.shortMember = builder.shortMember;
        this.string = builder.string;
        this.blob = builder.blob.duplicate();
        this.streamingBlob = builder.streamingBlob;
        this.boolDoc = builder.boolDoc;
        this.stringDoc = builder.stringDoc;
        this.numberDoc = builder.numberDoc;
        this.floatingPointnumberDoc = builder.floatingPointnumberDoc;
        this.listDoc = builder.listDoc;
        this.mapDoc = builder.mapDoc;
        this.list = Collections.unmodifiableList(builder.list);
        this.map = Collections.unmodifiableMap(builder.map);
        this.timestamp = builder.timestamp;
        this.enumMember = builder.enumMember;
        this.intEnum = builder.intEnum;
    }

    public boolean isBoolean() {
        return booleanMember;
    }

    public BigDecimal getBigDecimal() {
        return bigDecimal;
    }

    public BigDecimal getBigDecimalWithDoubleDefault() {
        return bigDecimalWithDoubleDefault;
    }

    public BigDecimal getBigDecimalWithLongDefault() {
        return bigDecimalWithLongDefault;
    }

    public BigInteger getBigInteger() {
        return bigInteger;
    }

    public BigInteger getBigIntegerWithLongDefault() {
        return bigIntegerWithLongDefault;
    }

    public byte getByte() {
        return byteMember;
    }

    public double getDouble() {
        return doubleMember;
    }

    public float getFloat() {
        return floatMember;
    }

    public int getInteger() {
        return integer;
    }

    public long getLong() {
        return longMember;
    }

    public short getShort() {
        return shortMember;
    }

    public String getString() {
        return string;
    }

    public ByteBuffer getBlob() {
        return blob;
    }

    public DataStream getStreamingBlob() {
        return streamingBlob;
    }

    public Document getBoolDoc() {
        return boolDoc;
    }

    public Document getStringDoc() {
        return stringDoc;
    }

    public Document getNumberDoc() {
        return numberDoc;
    }

    public Document getFloatingPointnumberDoc() {
        return floatingPointnumberDoc;
    }

    public Document getListDoc() {
        return listDoc;
    }

    public Document getMapDoc() {
        return mapDoc;
    }

    public List<String> getList() {
        return list;
    }

    public boolean hasList() {
        return true;
    }

    public Map<String, String> getMap() {
        return map;
    }

    public boolean hasMap() {
        return true;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public NestedEnum getEnum() {
        return enumMember;
    }

    public NestedIntEnum getIntEnum() {
        return intEnum;
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
        if (!(other instanceof DefaultStructure that)) {
            return false;
        }
        return this.booleanMember == that.booleanMember
               && this.byteMember == that.byteMember
               && this.shortMember == that.shortMember
               && this.integer == that.integer
               && this.longMember == that.longMember
               && Float.compare(this.floatMember, that.floatMember) == 0
               && Double.compare(this.doubleMember, that.doubleMember) == 0
               && Objects.equals(this.intEnum, that.intEnum)
               && Objects.equals(this.string, that.string)
               && Objects.equals(this.enumMember, that.enumMember)
               && Objects.equals(this.timestamp, that.timestamp)
               && Objects.equals(this.bigInteger, that.bigInteger)
               && Objects.equals(this.bigIntegerWithLongDefault, that.bigIntegerWithLongDefault)
               && Objects.equals(this.bigDecimal, that.bigDecimal)
               && Objects.equals(this.bigDecimalWithDoubleDefault, that.bigDecimalWithDoubleDefault)
               && Objects.equals(this.bigDecimalWithLongDefault, that.bigDecimalWithLongDefault)
               && Objects.equals(this.blob, that.blob)
               && Objects.equals(this.streamingBlob, that.streamingBlob)
               && Objects.equals(this.list, that.list)
               && Objects.equals(this.map, that.map)
               && Objects.equals(this.boolDoc, that.boolDoc)
               && Objects.equals(this.stringDoc, that.stringDoc)
               && Objects.equals(this.numberDoc, that.numberDoc)
               && Objects.equals(this.floatingPointnumberDoc, that.floatingPointnumberDoc)
               && Objects.equals(this.listDoc, that.listDoc)
               && Objects.equals(this.mapDoc, that.mapDoc);
    }

    @Override
    public int hashCode() {
        int $hc = Boolean.hashCode(booleanMember);
        $hc = 31 * $hc + Objects.hashCode(bigDecimal);
        $hc = 31 * $hc + Objects.hashCode(bigDecimalWithDoubleDefault);
        $hc = 31 * $hc + Objects.hashCode(bigDecimalWithLongDefault);
        $hc = 31 * $hc + Objects.hashCode(bigInteger);
        $hc = 31 * $hc + Objects.hashCode(bigIntegerWithLongDefault);
        $hc = 31 * $hc + Byte.hashCode(byteMember);
        $hc = 31 * $hc + Double.hashCode(doubleMember);
        $hc = 31 * $hc + Float.hashCode(floatMember);
        $hc = 31 * $hc + Integer.hashCode(integer);
        $hc = 31 * $hc + Long.hashCode(longMember);
        $hc = 31 * $hc + Short.hashCode(shortMember);
        $hc = 31 * $hc + Objects.hashCode(string);
        $hc = 31 * $hc + Objects.hashCode(blob);
        $hc = 31 * $hc + Objects.hashCode(streamingBlob);
        $hc = 31 * $hc + Objects.hashCode(boolDoc);
        $hc = 31 * $hc + Objects.hashCode(stringDoc);
        $hc = 31 * $hc + Objects.hashCode(numberDoc);
        $hc = 31 * $hc + Objects.hashCode(floatingPointnumberDoc);
        $hc = 31 * $hc + Objects.hashCode(listDoc);
        $hc = 31 * $hc + Objects.hashCode(mapDoc);
        $hc = 31 * $hc + Objects.hashCode(list);
        $hc = 31 * $hc + Objects.hashCode(map);
        $hc = 31 * $hc + Objects.hashCode(timestamp);
        $hc = 31 * $hc + Objects.hashCode(enumMember);
        $hc = 31 * $hc + Objects.hashCode(intEnum);
        return $hc;
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeBoolean($0, booleanMember);
        serializer.writeBigDecimal($1, bigDecimal);
        serializer.writeBigDecimal($2, bigDecimalWithDoubleDefault);
        serializer.writeBigDecimal($3, bigDecimalWithLongDefault);
        serializer.writeBigInteger($4, bigInteger);
        serializer.writeBigInteger($5, bigIntegerWithLongDefault);
        serializer.writeByte($6, byteMember);
        serializer.writeDouble($7, doubleMember);
        serializer.writeFloat($8, floatMember);
        serializer.writeInteger($9, integer);
        serializer.writeLong($10, longMember);
        serializer.writeShort($11, shortMember);
        serializer.writeString($12, string);
        serializer.writeBlob($13, blob);
        serializer.writeDataStream($14, streamingBlob);
        serializer.writeDocument($15, boolDoc);
        serializer.writeDocument($16, stringDoc);
        serializer.writeDocument($17, numberDoc);
        serializer.writeDocument($18, floatingPointnumberDoc);
        serializer.writeDocument($19, listDoc);
        serializer.writeDocument($20, mapDoc);
        serializer.writeStringList($21, list, $21.listMember());
        serializer.writeStringMap($22, map, $22.mapKeyMember(), $22.mapValueMember());
        serializer.writeTimestamp($23, timestamp);
        serializer.writeString($24, enumMember.getValue());
        serializer.writeInteger($25, intEnum.getValue());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        SchemaUtils.validateMemberOf($SCHEMA, member);
        return switch (member.memberIndex()) {
            case 0 -> (T) (Object) booleanMember;
            case 1 -> (T) bigDecimal;
            case 2 -> (T) bigDecimalWithDoubleDefault;
            case 3 -> (T) bigDecimalWithLongDefault;
            case 4 -> (T) bigInteger;
            case 5 -> (T) bigIntegerWithLongDefault;
            case 6 -> (T) (Object) byteMember;
            case 7 -> (T) (Object) doubleMember;
            case 8 -> (T) (Object) floatMember;
            case 9 -> (T) (Object) integer;
            case 10 -> (T) (Object) longMember;
            case 11 -> (T) (Object) shortMember;
            case 12 -> (T) string;
            case 13 -> (T) blob;
            case 14 -> (T) streamingBlob;
            case 15 -> (T) boolDoc;
            case 16 -> (T) stringDoc;
            case 17 -> (T) numberDoc;
            case 18 -> (T) floatingPointnumberDoc;
            case 19 -> (T) listDoc;
            case 20 -> (T) mapDoc;
            case 21 -> (T) list;
            case 22 -> (T) map;
            case 23 -> (T) timestamp;
            case 24 -> (T) enumMember;
            case 25 -> (T) intEnum;
            default -> throw SchemaUtils.unknownMember(member);
        };
    }

    /**
     * Create a new builder containing all the current property values of this object.
     *
     * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
     *
     * @return a builder for {@link DefaultStructure}.
     */
    public Builder toBuilder() {
        var builder = new Builder();
        builder.booleanMember(this.booleanMember);
        builder.bigDecimal(this.bigDecimal);
        builder.bigDecimalWithDoubleDefault(this.bigDecimalWithDoubleDefault);
        builder.bigDecimalWithLongDefault(this.bigDecimalWithLongDefault);
        builder.bigInteger(this.bigInteger);
        builder.bigIntegerWithLongDefault(this.bigIntegerWithLongDefault);
        builder.byteMember(this.byteMember);
        builder.doubleMember(this.doubleMember);
        builder.floatMember(this.floatMember);
        builder.integer(this.integer);
        builder.longMember(this.longMember);
        builder.shortMember(this.shortMember);
        builder.string(this.string);
        builder.blob(this.blob);
        builder.streamingBlob(this.streamingBlob);
        builder.boolDoc(this.boolDoc);
        builder.stringDoc(this.stringDoc);
        builder.numberDoc(this.numberDoc);
        builder.floatingPointnumberDoc(this.floatingPointnumberDoc);
        builder.listDoc(this.listDoc);
        builder.mapDoc(this.mapDoc);
        builder.list(this.list);
        builder.map(this.map);
        builder.timestamp(this.timestamp);
        builder.enumMember(this.enumMember);
        builder.intEnum(this.intEnum);
        return builder;
    }

    /**
     * @return returns a new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DefaultStructure}.
     */
    public static final class Builder implements ShapeBuilder<DefaultStructure> {
        private static final BigDecimal BIG_DECIMAL_DEFAULT = new BigDecimal("1E+309");
        private static final BigDecimal BIG_DECIMAL_WITH_DOUBLE_DEFAULT_DEFAULT = BigDecimal.valueOf(1.3);
        private static final BigDecimal BIG_DECIMAL_WITH_LONG_DEFAULT_DEFAULT = BigDecimal.valueOf(5L);
        private static final BigInteger BIG_INTEGER_DEFAULT = new BigInteger("123456789123456789123456789123456789123456789123456789");
        private static final BigInteger BIG_INTEGER_WITH_LONG_DEFAULT_DEFAULT = BigInteger.valueOf(1L);
        private static final String STRING_DEFAULT = "default";
        private static final Document BOOL_DOC_DEFAULT = Document.of(true);
        private static final Document STRING_DOC_DEFAULT = Document.of("string");
        private static final Document NUMBER_DOC_DEFAULT = Document.of(1);
        private static final Document FLOATING_POINTNUMBER_DOC_DEFAULT = Document.of(1.2);
        private static final Document LIST_DOC_DEFAULT = Document.of(Collections.emptyList());
        private static final Document MAP_DOC_DEFAULT = Document.of(Collections.emptyMap());
        private static final Instant TIMESTAMP_DEFAULT = Instant.ofEpochMilli(482196050520L);
        private boolean booleanMember = true;
        private BigDecimal bigDecimal = BIG_DECIMAL_DEFAULT;
        private BigDecimal bigDecimalWithDoubleDefault = BIG_DECIMAL_WITH_DOUBLE_DEFAULT_DEFAULT;
        private BigDecimal bigDecimalWithLongDefault = BIG_DECIMAL_WITH_LONG_DEFAULT_DEFAULT;
        private BigInteger bigInteger = BIG_INTEGER_DEFAULT;
        private BigInteger bigIntegerWithLongDefault = BIG_INTEGER_WITH_LONG_DEFAULT_DEFAULT;
        private byte byteMember = 1;
        private double doubleMember = 1.0;
        private float floatMember = 1.0f;
        private int integer = 1;
        private long longMember = 1L;
        private short shortMember = 1;
        private String string = STRING_DEFAULT;
        private ByteBuffer blob = ByteBuffer.wrap(Base64.getDecoder().decode("YmxvYg==")).duplicate();
        private DataStream streamingBlob = DataStream.ofBytes(Base64.getDecoder().decode("c3RyZWFtaW5n"));
        private Document boolDoc = BOOL_DOC_DEFAULT;
        private Document stringDoc = STRING_DOC_DEFAULT;
        private Document numberDoc = NUMBER_DOC_DEFAULT;
        private Document floatingPointnumberDoc = FLOATING_POINTNUMBER_DOC_DEFAULT;
        private Document listDoc = LIST_DOC_DEFAULT;
        private Document mapDoc = MAP_DOC_DEFAULT;
        private List<String> list = Collections.emptyList();
        private Map<String, String> map = Collections.emptyMap();
        private Instant timestamp = TIMESTAMP_DEFAULT;
        private NestedEnum enumMember = NestedEnum.A;
        private NestedIntEnum intEnum = NestedIntEnum.A;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        /**
         * @return this builder.
         */
        public Builder booleanMember(boolean booleanMember) {
            this.booleanMember = booleanMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder bigDecimal(BigDecimal bigDecimal) {
            this.bigDecimal = SchemaUtils.requireNonNull(bigDecimal, $1);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder bigDecimalWithDoubleDefault(BigDecimal bigDecimalWithDoubleDefault) {
            this.bigDecimalWithDoubleDefault = SchemaUtils.requireNonNull(bigDecimalWithDoubleDefault, $2);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder bigDecimalWithLongDefault(BigDecimal bigDecimalWithLongDefault) {
            this.bigDecimalWithLongDefault = SchemaUtils.requireNonNull(bigDecimalWithLongDefault, $3);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder bigInteger(BigInteger bigInteger) {
            this.bigInteger = SchemaUtils.requireNonNull(bigInteger, $4);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder bigIntegerWithLongDefault(BigInteger bigIntegerWithLongDefault) {
            this.bigIntegerWithLongDefault = SchemaUtils.requireNonNull(bigIntegerWithLongDefault, $5);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder byteMember(byte byteMember) {
            this.byteMember = byteMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder doubleMember(double doubleMember) {
            this.doubleMember = doubleMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder floatMember(float floatMember) {
            this.floatMember = floatMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder integer(int integer) {
            this.integer = integer;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder longMember(long longMember) {
            this.longMember = longMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder shortMember(short shortMember) {
            this.shortMember = shortMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder string(String string) {
            this.string = SchemaUtils.requireNonNull(string, $12);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder blob(ByteBuffer blob) {
            this.blob = SchemaUtils.requireNonNull(blob, $13);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder streamingBlob(DataStream streamingBlob) {
            this.streamingBlob = SchemaUtils.requireNonNull(streamingBlob, $14);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder boolDoc(Document boolDoc) {
            this.boolDoc = SchemaUtils.requireNonNull(boolDoc, $15);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder stringDoc(Document stringDoc) {
            this.stringDoc = SchemaUtils.requireNonNull(stringDoc, $16);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder numberDoc(Document numberDoc) {
            this.numberDoc = SchemaUtils.requireNonNull(numberDoc, $17);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder floatingPointnumberDoc(Document floatingPointnumberDoc) {
            this.floatingPointnumberDoc = SchemaUtils.requireNonNull(floatingPointnumberDoc, $18);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder listDoc(Document listDoc) {
            this.listDoc = SchemaUtils.requireNonNull(listDoc, $19);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder mapDoc(Document mapDoc) {
            this.mapDoc = SchemaUtils.requireNonNull(mapDoc, $20);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder list(List<String> list) {
            this.list = SchemaUtils.requireNonNull(list, $21);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder map(Map<String, String> map) {
            this.map = SchemaUtils.requireNonNull(map, $22);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = SchemaUtils.requireNonNull(timestamp, $23);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder enumMember(NestedEnum enumMember) {
            this.enumMember = SchemaUtils.requireNonNull(enumMember, $24);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder intEnum(NestedIntEnum intEnum) {
            this.intEnum = SchemaUtils.requireNonNull(intEnum, $25);
            return this;
        }

        @Override
        public DefaultStructure build() {
            return new DefaultStructure(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            SchemaUtils.validateMemberOf($SCHEMA, member);
            switch (member.memberIndex()) {
                case 0 -> booleanMember((boolean) value);
                case 1 -> bigDecimal((BigDecimal) value);
                case 2 -> bigDecimalWithDoubleDefault((BigDecimal) value);
                case 3 -> bigDecimalWithLongDefault((BigDecimal) value);
                case 4 -> bigInteger((BigInteger) value);
                case 5 -> bigIntegerWithLongDefault((BigInteger) value);
                case 6 -> byteMember((byte) value);
                case 7 -> doubleMember((double) value);
                case 8 -> floatMember((float) value);
                case 9 -> integer((int) value);
                case 10 -> longMember((long) value);
                case 11 -> shortMember((short) value);
                case 12 -> string((String) value);
                case 13 -> blob((ByteBuffer) value);
                case 14 -> streamingBlob((DataStream) value);
                case 15 -> boolDoc((Document) value);
                case 16 -> stringDoc((Document) value);
                case 17 -> numberDoc((Document) value);
                case 18 -> floatingPointnumberDoc((Document) value);
                case 19 -> listDoc((Document) value);
                case 20 -> mapDoc((Document) value);
                case 21 -> list((List<String>) value);
                case 22 -> map((Map<String, String>) value);
                case 23 -> timestamp((Instant) value);
                case 24 -> enumMember((NestedEnum) value);
                case 25 -> intEnum((NestedIntEnum) value);
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
                case 0 -> builder.booleanMember(de.readBoolean(member));
                case 1 -> builder.bigDecimal(de.readBigDecimal(member));
                case 2 -> builder.bigDecimalWithDoubleDefault(de.readBigDecimal(member));
                case 3 -> builder.bigDecimalWithLongDefault(de.readBigDecimal(member));
                case 4 -> builder.bigInteger(de.readBigInteger(member));
                case 5 -> builder.bigIntegerWithLongDefault(de.readBigInteger(member));
                case 6 -> builder.byteMember(de.readByte(member));
                case 7 -> builder.doubleMember(de.readDouble(member));
                case 8 -> builder.floatMember(de.readFloat(member));
                case 9 -> builder.integer(de.readInteger(member));
                case 10 -> builder.longMember(de.readLong(member));
                case 11 -> builder.shortMember(de.readShort(member));
                case 12 -> builder.string(de.readString(member));
                case 13 -> builder.blob(de.readBlob(member));
                case 14 -> builder.streamingBlob(de.readDataStream(member));
                case 15 -> builder.boolDoc(de.readDocument());
                case 16 -> builder.stringDoc(de.readDocument());
                case 17 -> builder.numberDoc(de.readDocument());
                case 18 -> builder.floatingPointnumberDoc(de.readDocument());
                case 19 -> builder.listDoc(de.readDocument());
                case 20 -> builder.mapDoc(de.readDocument());
                case 21 -> builder.list(SharedSerde.deserializeListOfString(member, de));
                case 22 -> builder.map(SharedSerde.deserializeStringStringMap(member, de));
                case 23 -> builder.timestamp(de.readTimestamp(member));
                case 24 -> builder.enumMember(NestedEnum.fromValue(de.readString(member)));
                case 25 -> builder.intEnum(NestedIntEnum.fromValue(de.readInteger(member)));
                default -> throw SchemaUtils.unknownMember(member);
            }
        }
    }
}
