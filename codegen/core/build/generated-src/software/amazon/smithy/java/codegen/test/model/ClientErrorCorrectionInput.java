

package software.amazon.smithy.java.codegen.test.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class ClientErrorCorrectionInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#ClientErrorCorrectionInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("boolean", PreludeSchemas.BOOLEAN,
            new RequiredTrait())
        .putMember("bigDecimal", PreludeSchemas.BIG_DECIMAL,
            new RequiredTrait())
        .putMember("bigInteger", PreludeSchemas.BIG_INTEGER,
            new RequiredTrait())
        .putMember("byte", PreludeSchemas.BYTE,
            new RequiredTrait())
        .putMember("double", PreludeSchemas.DOUBLE,
            new RequiredTrait())
        .putMember("float", PreludeSchemas.FLOAT,
            new RequiredTrait())
        .putMember("integer", PreludeSchemas.INTEGER,
            new RequiredTrait())
        .putMember("long", PreludeSchemas.LONG,
            new RequiredTrait())
        .putMember("short", PreludeSchemas.SHORT,
            new RequiredTrait())
        .putMember("string", PreludeSchemas.STRING,
            new RequiredTrait())
        .putMember("blob", PreludeSchemas.BLOB,
            new RequiredTrait())
        .putMember("streamingBlob", SharedSchemas.STREAMING_BLOB,
            new RequiredTrait())
        .putMember("document", PreludeSchemas.DOCUMENT,
            new RequiredTrait())
        .putMember("list", SharedSchemas.LIST_OF_STRING,
            new RequiredTrait())
        .putMember("map", SharedSchemas.STRING_STRING_MAP,
            new RequiredTrait())
        .putMember("timestamp", PreludeSchemas.TIMESTAMP,
            new RequiredTrait())
        .putMember("enum", NestedEnum.SCHEMA,
            new RequiredTrait())
        .putMember("intEnum", NestedIntEnum.SCHEMA,
            new RequiredTrait())
        .build();

    private static final Schema SCHEMA_BOOLEAN_MEMBER = SCHEMA.member("boolean");
    private static final Schema SCHEMA_BIG_DECIMAL = SCHEMA.member("bigDecimal");
    private static final Schema SCHEMA_BIG_INTEGER = SCHEMA.member("bigInteger");
    private static final Schema SCHEMA_BYTE_MEMBER = SCHEMA.member("byte");
    private static final Schema SCHEMA_DOUBLE_MEMBER = SCHEMA.member("double");
    private static final Schema SCHEMA_FLOAT_MEMBER = SCHEMA.member("float");
    private static final Schema SCHEMA_INTEGER = SCHEMA.member("integer");
    private static final Schema SCHEMA_LONG_MEMBER = SCHEMA.member("long");
    private static final Schema SCHEMA_SHORT_MEMBER = SCHEMA.member("short");
    private static final Schema SCHEMA_STRING = SCHEMA.member("string");
    private static final Schema SCHEMA_BLOB = SCHEMA.member("blob");
    private static final Schema SCHEMA_STREAMING_BLOB = SCHEMA.member("streamingBlob");
    private static final Schema SCHEMA_DOCUMENT = SCHEMA.member("document");
    private static final Schema SCHEMA_LIST = SCHEMA.member("list");
    private static final Schema SCHEMA_MAP = SCHEMA.member("map");
    private static final Schema SCHEMA_TIMESTAMP = SCHEMA.member("timestamp");
    private static final Schema SCHEMA_ENUM_MEMBER = SCHEMA.member("enum");
    private static final Schema SCHEMA_INT_ENUM = SCHEMA.member("intEnum");

    private transient final boolean booleanMember;
    private transient final BigDecimal bigDecimal;
    private transient final BigInteger bigInteger;
    private transient final byte byteMember;
    private transient final double doubleMember;
    private transient final float floatMember;
    private transient final int integer;
    private transient final long longMember;
    private transient final short shortMember;
    private transient final String string;
    private transient final ByteBuffer blob;
    private transient final DataStream streamingBlob;
    private transient final Document document;
    private transient final List<String> list;
    private transient final Map<String, String> map;
    private transient final Instant timestamp;
    private transient final NestedEnum enumMember;
    private transient final NestedIntEnum intEnum;

    private ClientErrorCorrectionInput(Builder builder) {
        this.booleanMember = builder.booleanMember;
        this.bigDecimal = builder.bigDecimal;
        this.bigInteger = builder.bigInteger;
        this.byteMember = builder.byteMember;
        this.doubleMember = builder.doubleMember;
        this.floatMember = builder.floatMember;
        this.integer = builder.integer;
        this.longMember = builder.longMember;
        this.shortMember = builder.shortMember;
        this.string = builder.string;
        this.blob = builder.blob.asReadOnlyBuffer();
        this.streamingBlob = builder.streamingBlob;
        this.document = builder.document;
        this.list = Collections.unmodifiableList(builder.list);
        this.map = Collections.unmodifiableMap(builder.map);
        this.timestamp = builder.timestamp;
        this.enumMember = builder.enumMember;
        this.intEnum = builder.intEnum;
    }

    public boolean booleanMember() {
        return booleanMember;
    }

    public BigDecimal bigDecimal() {
        return bigDecimal;
    }

    public BigInteger bigInteger() {
        return bigInteger;
    }

    public byte byteMember() {
        return byteMember;
    }

    public double doubleMember() {
        return doubleMember;
    }

    public float floatMember() {
        return floatMember;
    }

    public int integer() {
        return integer;
    }

    public long longMember() {
        return longMember;
    }

    public short shortMember() {
        return shortMember;
    }

    public String string() {
        return string;
    }

    public ByteBuffer blob() {
        return blob;
    }

    public DataStream streamingBlob() {
        return streamingBlob;
    }

    public Document document() {
        return document;
    }

    public List<String> list() {
        return list;
    }

    public boolean hasList() {
        return true;
    }

    public Map<String, String> map() {
        return map;
    }

    public boolean hasMap() {
        return true;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public NestedEnum enumMember() {
        return enumMember;
    }

    public NestedIntEnum intEnum() {
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
        ClientErrorCorrectionInput that = (ClientErrorCorrectionInput) other;
        return this.booleanMember == that.booleanMember
               && Objects.equals(this.bigDecimal, that.bigDecimal)
               && Objects.equals(this.bigInteger, that.bigInteger)
               && this.byteMember == that.byteMember
               && this.doubleMember == that.doubleMember
               && this.floatMember == that.floatMember
               && this.integer == that.integer
               && this.longMember == that.longMember
               && this.shortMember == that.shortMember
               && Objects.equals(this.string, that.string)
               && Objects.equals(this.blob, that.blob)
               && Objects.equals(this.streamingBlob, that.streamingBlob)
               && Objects.equals(this.document, that.document)
               && Objects.equals(this.list, that.list)
               && Objects.equals(this.map, that.map)
               && Objects.equals(this.timestamp, that.timestamp)
               && Objects.equals(this.enumMember, that.enumMember)
               && Objects.equals(this.intEnum, that.intEnum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(booleanMember, bigDecimal, bigInteger, byteMember, doubleMember, floatMember, integer, longMember, shortMember, string, blob, streamingBlob, document, list, map, timestamp, enumMember, intEnum);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeBoolean(SCHEMA_BOOLEAN_MEMBER, booleanMember);
        serializer.writeBigDecimal(SCHEMA_BIG_DECIMAL, bigDecimal);
        serializer.writeBigInteger(SCHEMA_BIG_INTEGER, bigInteger);
        serializer.writeByte(SCHEMA_BYTE_MEMBER, byteMember);
        serializer.writeDouble(SCHEMA_DOUBLE_MEMBER, doubleMember);
        serializer.writeFloat(SCHEMA_FLOAT_MEMBER, floatMember);
        serializer.writeInteger(SCHEMA_INTEGER, integer);
        serializer.writeLong(SCHEMA_LONG_MEMBER, longMember);
        serializer.writeShort(SCHEMA_SHORT_MEMBER, shortMember);
        serializer.writeString(SCHEMA_STRING, string);
        serializer.writeBlob(SCHEMA_BLOB, blob);
        serializer.writeDocument(SCHEMA_DOCUMENT, document);
        serializer.writeList(SCHEMA_LIST, list, SharedSerde.ListOfStringSerializer.INSTANCE);
        serializer.writeMap(SCHEMA_MAP, map, SharedSerde.StringStringMapSerializer.INSTANCE);
        serializer.writeTimestamp(SCHEMA_TIMESTAMP, timestamp);
        serializer.writeString(SCHEMA_ENUM_MEMBER, enumMember.value());
        serializer.writeInteger(SCHEMA_INT_ENUM, intEnum.value());
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ClientErrorCorrectionInput}.
     */
    public static final class Builder implements ShapeBuilder<ClientErrorCorrectionInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private boolean booleanMember;
        private BigDecimal bigDecimal;
        private BigInteger bigInteger;
        private byte byteMember;
        private double doubleMember;
        private float floatMember;
        private int integer;
        private long longMember;
        private short shortMember;
        private String string;
        private ByteBuffer blob;
        private DataStream streamingBlob;
        private Document document;
        private List<String> list;
        private Map<String, String> map;
        private Instant timestamp;
        private NestedEnum enumMember;
        private NestedIntEnum intEnum;

        private Builder() {}

        public Builder booleanMember(boolean booleanMember) {
            this.booleanMember = booleanMember;
            tracker.setMember(SCHEMA_BOOLEAN_MEMBER);
            return this;
        }

        public Builder bigDecimal(BigDecimal bigDecimal) {
            this.bigDecimal = Objects.requireNonNull(bigDecimal, "bigDecimal cannot be null");
            tracker.setMember(SCHEMA_BIG_DECIMAL);
            return this;
        }

        public Builder bigInteger(BigInteger bigInteger) {
            this.bigInteger = Objects.requireNonNull(bigInteger, "bigInteger cannot be null");
            tracker.setMember(SCHEMA_BIG_INTEGER);
            return this;
        }

        public Builder byteMember(byte byteMember) {
            this.byteMember = byteMember;
            tracker.setMember(SCHEMA_BYTE_MEMBER);
            return this;
        }

        public Builder doubleMember(double doubleMember) {
            this.doubleMember = doubleMember;
            tracker.setMember(SCHEMA_DOUBLE_MEMBER);
            return this;
        }

        public Builder floatMember(float floatMember) {
            this.floatMember = floatMember;
            tracker.setMember(SCHEMA_FLOAT_MEMBER);
            return this;
        }

        public Builder integer(int integer) {
            this.integer = integer;
            tracker.setMember(SCHEMA_INTEGER);
            return this;
        }

        public Builder longMember(long longMember) {
            this.longMember = longMember;
            tracker.setMember(SCHEMA_LONG_MEMBER);
            return this;
        }

        public Builder shortMember(short shortMember) {
            this.shortMember = shortMember;
            tracker.setMember(SCHEMA_SHORT_MEMBER);
            return this;
        }

        public Builder string(String string) {
            this.string = Objects.requireNonNull(string, "string cannot be null");
            tracker.setMember(SCHEMA_STRING);
            return this;
        }

        public Builder blob(ByteBuffer blob) {
            this.blob = Objects.requireNonNull(blob, "blob cannot be null");
            tracker.setMember(SCHEMA_BLOB);
            return this;
        }

        @Override
        public void setDataStream(DataStream stream) {
            streamingBlob(stream);
        }

        public Builder streamingBlob(DataStream streamingBlob) {
            this.streamingBlob = Objects.requireNonNull(streamingBlob, "streamingBlob cannot be null");
            tracker.setMember(SCHEMA_STREAMING_BLOB);
            return this;
        }

        public Builder document(Document document) {
            this.document = Objects.requireNonNull(document, "document cannot be null");
            tracker.setMember(SCHEMA_DOCUMENT);
            return this;
        }

        public Builder list(List<String> list) {
            this.list = Objects.requireNonNull(list, "list cannot be null");
            tracker.setMember(SCHEMA_LIST);
            return this;
        }

        public Builder map(Map<String, String> map) {
            this.map = Objects.requireNonNull(map, "map cannot be null");
            tracker.setMember(SCHEMA_MAP);
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
            tracker.setMember(SCHEMA_TIMESTAMP);
            return this;
        }

        public Builder enumMember(NestedEnum enumMember) {
            this.enumMember = Objects.requireNonNull(enumMember, "enumMember cannot be null");
            tracker.setMember(SCHEMA_ENUM_MEMBER);
            return this;
        }

        public Builder intEnum(NestedIntEnum intEnum) {
            this.intEnum = Objects.requireNonNull(intEnum, "intEnum cannot be null");
            tracker.setMember(SCHEMA_INT_ENUM);
            return this;
        }

        @Override
        public ClientErrorCorrectionInput build() {
            tracker.validate();
            return new ClientErrorCorrectionInput(this);
        }

        @Override
        public ShapeBuilder<ClientErrorCorrectionInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_BOOLEAN_MEMBER)) {
                tracker.setMember(SCHEMA_BOOLEAN_MEMBER);
            }
            if (!tracker.checkMember(SCHEMA_BIG_DECIMAL)) {
                bigDecimal(BigDecimal.ZERO);
            }
            if (!tracker.checkMember(SCHEMA_BIG_INTEGER)) {
                bigInteger(BigInteger.ZERO);
            }
            if (!tracker.checkMember(SCHEMA_BYTE_MEMBER)) {
                tracker.setMember(SCHEMA_BYTE_MEMBER);
            }
            if (!tracker.checkMember(SCHEMA_DOUBLE_MEMBER)) {
                tracker.setMember(SCHEMA_DOUBLE_MEMBER);
            }
            if (!tracker.checkMember(SCHEMA_FLOAT_MEMBER)) {
                tracker.setMember(SCHEMA_FLOAT_MEMBER);
            }
            if (!tracker.checkMember(SCHEMA_INTEGER)) {
                tracker.setMember(SCHEMA_INTEGER);
            }
            if (!tracker.checkMember(SCHEMA_LONG_MEMBER)) {
                tracker.setMember(SCHEMA_LONG_MEMBER);
            }
            if (!tracker.checkMember(SCHEMA_SHORT_MEMBER)) {
                tracker.setMember(SCHEMA_SHORT_MEMBER);
            }
            if (!tracker.checkMember(SCHEMA_STRING)) {
                string("");
            }
            if (!tracker.checkMember(SCHEMA_BLOB)) {
                blob(ByteBuffer.allocate(0));
            }
            if (!tracker.checkMember(SCHEMA_STREAMING_BLOB)) {
                streamingBlob(DataStream.ofEmpty());
            }
            if (!tracker.checkMember(SCHEMA_DOCUMENT)) {
                tracker.setMember(SCHEMA_DOCUMENT);
            }
            if (!tracker.checkMember(SCHEMA_LIST)) {
                list(Collections.emptyList());
            }
            if (!tracker.checkMember(SCHEMA_MAP)) {
                map(Collections.emptyMap());
            }
            if (!tracker.checkMember(SCHEMA_TIMESTAMP)) {
                timestamp(Instant.EPOCH);
            }
            if (!tracker.checkMember(SCHEMA_ENUM_MEMBER)) {
                enumMember(NestedEnum.unknown(""));
            }
            if (!tracker.checkMember(SCHEMA_INT_ENUM)) {
                intEnum(NestedIntEnum.unknown(0));
            }
            return this;
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
                    case 0 -> builder.booleanMember(de.readBoolean(member));
                    case 1 -> builder.bigDecimal(de.readBigDecimal(member));
                    case 2 -> builder.bigInteger(de.readBigInteger(member));
                    case 3 -> builder.byteMember(de.readByte(member));
                    case 4 -> builder.doubleMember(de.readDouble(member));
                    case 5 -> builder.floatMember(de.readFloat(member));
                    case 6 -> builder.integer(de.readInteger(member));
                    case 7 -> builder.longMember(de.readLong(member));
                    case 8 -> builder.shortMember(de.readShort(member));
                    case 9 -> builder.string(de.readString(member));
                    case 10 -> builder.blob(de.readBlob(member));
                    case 12 -> builder.document(de.readDocument());
                    case 13 -> builder.list(SharedSerde.deserializeListOfString(member, de));
                    case 14 -> builder.map(SharedSerde.deserializeStringStringMap(member, de));
                    case 15 -> builder.timestamp(de.readTimestamp(member));
                    case 16 -> builder.enumMember(NestedEnum.builder().deserialize(de).build());
                    case 17 -> builder.intEnum(NestedIntEnum.builder().deserialize(de).build());
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.booleanMember(this.booleanMember);
        builder.bigDecimal(this.bigDecimal);
        builder.bigInteger(this.bigInteger);
        builder.byteMember(this.byteMember);
        builder.doubleMember(this.doubleMember);
        builder.floatMember(this.floatMember);
        builder.integer(this.integer);
        builder.longMember(this.longMember);
        builder.shortMember(this.shortMember);
        builder.string(this.string);
        builder.blob(this.blob);
        builder.streamingBlob(this.streamingBlob);
        builder.document(this.document);
        builder.list(this.list);
        builder.map(this.map);
        builder.timestamp(this.timestamp);
        builder.enumMember(this.enumMember);
        builder.intEnum(this.intEnum);
        return builder;
    }

}

