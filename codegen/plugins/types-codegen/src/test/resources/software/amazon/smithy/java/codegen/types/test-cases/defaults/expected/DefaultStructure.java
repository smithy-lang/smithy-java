
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
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class DefaultStructure implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.DEFAULT_STRUCTURE;
    private static final Schema $SCHEMA_BOOLEAN_MEMBER = $SCHEMA.member("boolean");
    private static final Schema $SCHEMA_BIG_DECIMAL = $SCHEMA.member("bigDecimal");
    private static final Schema $SCHEMA_BIG_DECIMAL_WITH_DOUBLE_DEFAULT = $SCHEMA.member("bigDecimalWithDoubleDefault");
    private static final Schema $SCHEMA_BIG_DECIMAL_WITH_LONG_DEFAULT = $SCHEMA.member("bigDecimalWithLongDefault");
    private static final Schema $SCHEMA_BIG_INTEGER = $SCHEMA.member("bigInteger");
    private static final Schema $SCHEMA_BIG_INTEGER_WITH_LONG_DEFAULT = $SCHEMA.member("bigIntegerWithLongDefault");
    private static final Schema $SCHEMA_BYTE_MEMBER = $SCHEMA.member("byte");
    private static final Schema $SCHEMA_DOUBLE_MEMBER = $SCHEMA.member("double");
    private static final Schema $SCHEMA_FLOAT_MEMBER = $SCHEMA.member("float");
    private static final Schema $SCHEMA_INTEGER = $SCHEMA.member("integer");
    private static final Schema $SCHEMA_LONG_MEMBER = $SCHEMA.member("long");
    private static final Schema $SCHEMA_SHORT_MEMBER = $SCHEMA.member("short");
    private static final Schema $SCHEMA_STRING = $SCHEMA.member("string");
    private static final Schema $SCHEMA_BLOB = $SCHEMA.member("blob");
    private static final Schema $SCHEMA_STREAMING_BLOB = $SCHEMA.member("streamingBlob");
    private static final Schema $SCHEMA_BOOL_DOC = $SCHEMA.member("boolDoc");
    private static final Schema $SCHEMA_STRING_DOC = $SCHEMA.member("stringDoc");
    private static final Schema $SCHEMA_NUMBER_DOC = $SCHEMA.member("numberDoc");
    private static final Schema $SCHEMA_FLOATING_POINTNUMBER_DOC = $SCHEMA.member("floatingPointnumberDoc");
    private static final Schema $SCHEMA_LIST_DOC = $SCHEMA.member("listDoc");
    private static final Schema $SCHEMA_MAP_DOC = $SCHEMA.member("mapDoc");
    private static final Schema $SCHEMA_LIST = $SCHEMA.member("list");
    private static final Schema $SCHEMA_MAP = $SCHEMA.member("map");
    private static final Schema $SCHEMA_TIMESTAMP = $SCHEMA.member("timestamp");
    private static final Schema $SCHEMA_ENUM_MEMBER = $SCHEMA.member("enum");
    private static final Schema $SCHEMA_INT_ENUM = $SCHEMA.member("intEnum");

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
        DefaultStructure that = (DefaultStructure) other;
        return this.booleanMember == that.booleanMember
               && Objects.equals(this.bigDecimal, that.bigDecimal)
               && Objects.equals(this.bigDecimalWithDoubleDefault, that.bigDecimalWithDoubleDefault)
               && Objects.equals(this.bigDecimalWithLongDefault, that.bigDecimalWithLongDefault)
               && Objects.equals(this.bigInteger, that.bigInteger)
               && Objects.equals(this.bigIntegerWithLongDefault, that.bigIntegerWithLongDefault)
               && this.byteMember == that.byteMember
               && this.doubleMember == that.doubleMember
               && this.floatMember == that.floatMember
               && this.integer == that.integer
               && this.longMember == that.longMember
               && this.shortMember == that.shortMember
               && Objects.equals(this.string, that.string)
               && Objects.equals(this.blob, that.blob)
               && Objects.equals(this.streamingBlob, that.streamingBlob)
               && Objects.equals(this.boolDoc, that.boolDoc)
               && Objects.equals(this.stringDoc, that.stringDoc)
               && Objects.equals(this.numberDoc, that.numberDoc)
               && Objects.equals(this.floatingPointnumberDoc, that.floatingPointnumberDoc)
               && Objects.equals(this.listDoc, that.listDoc)
               && Objects.equals(this.mapDoc, that.mapDoc)
               && Objects.equals(this.list, that.list)
               && Objects.equals(this.map, that.map)
               && Objects.equals(this.timestamp, that.timestamp)
               && Objects.equals(this.enumMember, that.enumMember)
               && Objects.equals(this.intEnum, that.intEnum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(booleanMember, bigDecimal, bigDecimalWithDoubleDefault, bigDecimalWithLongDefault, bigInteger, bigIntegerWithLongDefault, byteMember, doubleMember, floatMember, integer, longMember, shortMember, string, blob, streamingBlob, boolDoc, stringDoc, numberDoc, floatingPointnumberDoc, listDoc, mapDoc, list, map, timestamp, enumMember, intEnum);
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeBoolean($SCHEMA_BOOLEAN_MEMBER, booleanMember);
        serializer.writeBigDecimal($SCHEMA_BIG_DECIMAL, bigDecimal);
        serializer.writeBigDecimal($SCHEMA_BIG_DECIMAL_WITH_DOUBLE_DEFAULT, bigDecimalWithDoubleDefault);
        serializer.writeBigDecimal($SCHEMA_BIG_DECIMAL_WITH_LONG_DEFAULT, bigDecimalWithLongDefault);
        serializer.writeBigInteger($SCHEMA_BIG_INTEGER, bigInteger);
        serializer.writeBigInteger($SCHEMA_BIG_INTEGER_WITH_LONG_DEFAULT, bigIntegerWithLongDefault);
        serializer.writeByte($SCHEMA_BYTE_MEMBER, byteMember);
        serializer.writeDouble($SCHEMA_DOUBLE_MEMBER, doubleMember);
        serializer.writeFloat($SCHEMA_FLOAT_MEMBER, floatMember);
        serializer.writeInteger($SCHEMA_INTEGER, integer);
        serializer.writeLong($SCHEMA_LONG_MEMBER, longMember);
        serializer.writeShort($SCHEMA_SHORT_MEMBER, shortMember);
        serializer.writeString($SCHEMA_STRING, string);
        serializer.writeBlob($SCHEMA_BLOB, blob);
        serializer.writeDataStream($SCHEMA_STREAMING_BLOB, streamingBlob);
        serializer.writeDocument($SCHEMA_BOOL_DOC, boolDoc);
        serializer.writeDocument($SCHEMA_STRING_DOC, stringDoc);
        serializer.writeDocument($SCHEMA_NUMBER_DOC, numberDoc);
        serializer.writeDocument($SCHEMA_FLOATING_POINTNUMBER_DOC, floatingPointnumberDoc);
        serializer.writeDocument($SCHEMA_LIST_DOC, listDoc);
        serializer.writeDocument($SCHEMA_MAP_DOC, mapDoc);
        serializer.writeList($SCHEMA_LIST, list, list.size(), SharedSerde.ListOfStringSerializer.INSTANCE);
        serializer.writeMap($SCHEMA_MAP, map, map.size(), SharedSerde.StringStringMapSerializer.INSTANCE);
        serializer.writeTimestamp($SCHEMA_TIMESTAMP, timestamp);
        serializer.writeString($SCHEMA_ENUM_MEMBER, enumMember.getValue());
        serializer.writeInteger($SCHEMA_INT_ENUM, intEnum.getValue());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember($SCHEMA_BOOLEAN_MEMBER, member, booleanMember);
            case 1 -> (T) SchemaUtils.validateSameMember($SCHEMA_BIG_DECIMAL, member, bigDecimal);
            case 2 -> (T) SchemaUtils.validateSameMember($SCHEMA_BIG_DECIMAL_WITH_DOUBLE_DEFAULT, member, bigDecimalWithDoubleDefault);
            case 3 -> (T) SchemaUtils.validateSameMember($SCHEMA_BIG_DECIMAL_WITH_LONG_DEFAULT, member, bigDecimalWithLongDefault);
            case 4 -> (T) SchemaUtils.validateSameMember($SCHEMA_BIG_INTEGER, member, bigInteger);
            case 5 -> (T) SchemaUtils.validateSameMember($SCHEMA_BIG_INTEGER_WITH_LONG_DEFAULT, member, bigIntegerWithLongDefault);
            case 6 -> (T) SchemaUtils.validateSameMember($SCHEMA_BYTE_MEMBER, member, byteMember);
            case 7 -> (T) SchemaUtils.validateSameMember($SCHEMA_DOUBLE_MEMBER, member, doubleMember);
            case 8 -> (T) SchemaUtils.validateSameMember($SCHEMA_FLOAT_MEMBER, member, floatMember);
            case 9 -> (T) SchemaUtils.validateSameMember($SCHEMA_INTEGER, member, integer);
            case 10 -> (T) SchemaUtils.validateSameMember($SCHEMA_LONG_MEMBER, member, longMember);
            case 11 -> (T) SchemaUtils.validateSameMember($SCHEMA_SHORT_MEMBER, member, shortMember);
            case 12 -> (T) SchemaUtils.validateSameMember($SCHEMA_STRING, member, string);
            case 13 -> (T) SchemaUtils.validateSameMember($SCHEMA_BLOB, member, blob);
            case 14 -> (T) SchemaUtils.validateSameMember($SCHEMA_STREAMING_BLOB, member, streamingBlob);
            case 15 -> (T) SchemaUtils.validateSameMember($SCHEMA_BOOL_DOC, member, boolDoc);
            case 16 -> (T) SchemaUtils.validateSameMember($SCHEMA_STRING_DOC, member, stringDoc);
            case 17 -> (T) SchemaUtils.validateSameMember($SCHEMA_NUMBER_DOC, member, numberDoc);
            case 18 -> (T) SchemaUtils.validateSameMember($SCHEMA_FLOATING_POINTNUMBER_DOC, member, floatingPointnumberDoc);
            case 19 -> (T) SchemaUtils.validateSameMember($SCHEMA_LIST_DOC, member, listDoc);
            case 20 -> (T) SchemaUtils.validateSameMember($SCHEMA_MAP_DOC, member, mapDoc);
            case 21 -> (T) SchemaUtils.validateSameMember($SCHEMA_LIST, member, list);
            case 22 -> (T) SchemaUtils.validateSameMember($SCHEMA_MAP, member, map);
            case 23 -> (T) SchemaUtils.validateSameMember($SCHEMA_TIMESTAMP, member, timestamp);
            case 24 -> (T) SchemaUtils.validateSameMember($SCHEMA_ENUM_MEMBER, member, enumMember);
            case 25 -> (T) SchemaUtils.validateSameMember($SCHEMA_INT_ENUM, member, intEnum);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
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
            this.bigDecimal = Objects.requireNonNull(bigDecimal, "bigDecimal cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder bigDecimalWithDoubleDefault(BigDecimal bigDecimalWithDoubleDefault) {
            this.bigDecimalWithDoubleDefault = Objects.requireNonNull(bigDecimalWithDoubleDefault, "bigDecimalWithDoubleDefault cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder bigDecimalWithLongDefault(BigDecimal bigDecimalWithLongDefault) {
            this.bigDecimalWithLongDefault = Objects.requireNonNull(bigDecimalWithLongDefault, "bigDecimalWithLongDefault cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder bigInteger(BigInteger bigInteger) {
            this.bigInteger = Objects.requireNonNull(bigInteger, "bigInteger cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder bigIntegerWithLongDefault(BigInteger bigIntegerWithLongDefault) {
            this.bigIntegerWithLongDefault = Objects.requireNonNull(bigIntegerWithLongDefault, "bigIntegerWithLongDefault cannot be null");
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
            this.string = Objects.requireNonNull(string, "string cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder blob(ByteBuffer blob) {
            this.blob = Objects.requireNonNull(blob, "blob cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder streamingBlob(DataStream streamingBlob) {
            this.streamingBlob = Objects.requireNonNull(streamingBlob, "streamingBlob cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder boolDoc(Document boolDoc) {
            this.boolDoc = Objects.requireNonNull(boolDoc, "boolDoc cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder stringDoc(Document stringDoc) {
            this.stringDoc = Objects.requireNonNull(stringDoc, "stringDoc cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder numberDoc(Document numberDoc) {
            this.numberDoc = Objects.requireNonNull(numberDoc, "numberDoc cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder floatingPointnumberDoc(Document floatingPointnumberDoc) {
            this.floatingPointnumberDoc = Objects.requireNonNull(floatingPointnumberDoc, "floatingPointnumberDoc cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder listDoc(Document listDoc) {
            this.listDoc = Objects.requireNonNull(listDoc, "listDoc cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder mapDoc(Document mapDoc) {
            this.mapDoc = Objects.requireNonNull(mapDoc, "mapDoc cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder list(List<String> list) {
            this.list = Objects.requireNonNull(list, "list cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder map(Map<String, String> map) {
            this.map = Objects.requireNonNull(map, "map cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder enumMember(NestedEnum enumMember) {
            this.enumMember = Objects.requireNonNull(enumMember, "enumMember cannot be null");
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder intEnum(NestedIntEnum intEnum) {
            this.intEnum = Objects.requireNonNull(intEnum, "intEnum cannot be null");
            return this;
        }

        @Override
        public DefaultStructure build() {
            return new DefaultStructure(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> booleanMember((boolean) SchemaUtils.validateSameMember($SCHEMA_BOOLEAN_MEMBER, member, value));
                case 1 -> bigDecimal((BigDecimal) SchemaUtils.validateSameMember($SCHEMA_BIG_DECIMAL, member, value));
                case 2 -> bigDecimalWithDoubleDefault((BigDecimal) SchemaUtils.validateSameMember($SCHEMA_BIG_DECIMAL_WITH_DOUBLE_DEFAULT, member, value));
                case 3 -> bigDecimalWithLongDefault((BigDecimal) SchemaUtils.validateSameMember($SCHEMA_BIG_DECIMAL_WITH_LONG_DEFAULT, member, value));
                case 4 -> bigInteger((BigInteger) SchemaUtils.validateSameMember($SCHEMA_BIG_INTEGER, member, value));
                case 5 -> bigIntegerWithLongDefault((BigInteger) SchemaUtils.validateSameMember($SCHEMA_BIG_INTEGER_WITH_LONG_DEFAULT, member, value));
                case 6 -> byteMember((byte) SchemaUtils.validateSameMember($SCHEMA_BYTE_MEMBER, member, value));
                case 7 -> doubleMember((double) SchemaUtils.validateSameMember($SCHEMA_DOUBLE_MEMBER, member, value));
                case 8 -> floatMember((float) SchemaUtils.validateSameMember($SCHEMA_FLOAT_MEMBER, member, value));
                case 9 -> integer((int) SchemaUtils.validateSameMember($SCHEMA_INTEGER, member, value));
                case 10 -> longMember((long) SchemaUtils.validateSameMember($SCHEMA_LONG_MEMBER, member, value));
                case 11 -> shortMember((short) SchemaUtils.validateSameMember($SCHEMA_SHORT_MEMBER, member, value));
                case 12 -> string((String) SchemaUtils.validateSameMember($SCHEMA_STRING, member, value));
                case 13 -> blob((ByteBuffer) SchemaUtils.validateSameMember($SCHEMA_BLOB, member, value));
                case 14 -> streamingBlob((DataStream) SchemaUtils.validateSameMember($SCHEMA_STREAMING_BLOB, member, value));
                case 15 -> boolDoc((Document) SchemaUtils.validateSameMember($SCHEMA_BOOL_DOC, member, value));
                case 16 -> stringDoc((Document) SchemaUtils.validateSameMember($SCHEMA_STRING_DOC, member, value));
                case 17 -> numberDoc((Document) SchemaUtils.validateSameMember($SCHEMA_NUMBER_DOC, member, value));
                case 18 -> floatingPointnumberDoc((Document) SchemaUtils.validateSameMember($SCHEMA_FLOATING_POINTNUMBER_DOC, member, value));
                case 19 -> listDoc((Document) SchemaUtils.validateSameMember($SCHEMA_LIST_DOC, member, value));
                case 20 -> mapDoc((Document) SchemaUtils.validateSameMember($SCHEMA_MAP_DOC, member, value));
                case 21 -> list((List<String>) SchemaUtils.validateSameMember($SCHEMA_LIST, member, value));
                case 22 -> map((Map<String, String>) SchemaUtils.validateSameMember($SCHEMA_MAP, member, value));
                case 23 -> timestamp((Instant) SchemaUtils.validateSameMember($SCHEMA_TIMESTAMP, member, value));
                case 24 -> enumMember((NestedEnum) SchemaUtils.validateSameMember($SCHEMA_ENUM_MEMBER, member, value));
                case 25 -> intEnum((NestedIntEnum) SchemaUtils.validateSameMember($SCHEMA_INT_ENUM, member, value));
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
                    case 24 -> builder.enumMember(NestedEnum.builder().deserializeMember(de, member).build());
                    case 25 -> builder.intEnum(NestedIntEnum.builder().deserializeMember(de, member).build());
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }
        }
    }
}

