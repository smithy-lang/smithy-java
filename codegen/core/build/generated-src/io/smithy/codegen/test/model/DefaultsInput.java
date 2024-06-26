

package io.smithy.codegen.test.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
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
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class DefaultsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#DefaultsInput");

    private static final Schema SCHEMA_BOOLEAN_MEMBER = Schema.memberBuilder("boolean", PreludeSchemas.BOOLEAN)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(true)
            )
        )
        .build();

    private static final Schema SCHEMA_BIG_DECIMAL = Schema.memberBuilder("bigDecimal", PreludeSchemas.BIG_DECIMAL)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1)
            )
        )
        .build();

    private static final Schema SCHEMA_BIG_INTEGER = Schema.memberBuilder("bigInteger", PreludeSchemas.BIG_INTEGER)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1)
            )
        )
        .build();

    private static final Schema SCHEMA_BYTE_MEMBER = Schema.memberBuilder("byte", PreludeSchemas.BYTE)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1)
            )
        )
        .build();

    private static final Schema SCHEMA_DOUBLE_MEMBER = Schema.memberBuilder("double", PreludeSchemas.DOUBLE)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1.0)
            )
        )
        .build();

    private static final Schema SCHEMA_FLOAT_MEMBER = Schema.memberBuilder("float", PreludeSchemas.FLOAT)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1.0)
            )
        )
        .build();

    private static final Schema SCHEMA_INTEGER = Schema.memberBuilder("integer", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1)
            )
        )
        .build();

    private static final Schema SCHEMA_LONG_MEMBER = Schema.memberBuilder("long", PreludeSchemas.LONG)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1)
            )
        )
        .build();

    private static final Schema SCHEMA_SHORT_MEMBER = Schema.memberBuilder("short", PreludeSchemas.SHORT)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1)
            )
        )
        .build();

    private static final Schema SCHEMA_STRING = Schema.memberBuilder("string", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from("default")
            )
        )
        .build();

    private static final Schema SCHEMA_BLOB = Schema.memberBuilder("blob", PreludeSchemas.BLOB)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from("YmxvYg==")
            )
        )
        .build();

    private static final Schema SCHEMA_STREAMING_BLOB = Schema.memberBuilder("streamingBlob", SharedSchemas.STREAMING_BLOB)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from("c3RyZWFtaW5n")
            )
        )
        .build();

    private static final Schema SCHEMA_BOOL_DOC = Schema.memberBuilder("boolDoc", PreludeSchemas.DOCUMENT)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(true)
            )
        )
        .build();

    private static final Schema SCHEMA_STRING_DOC = Schema.memberBuilder("stringDoc", PreludeSchemas.DOCUMENT)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from("string")
            )
        )
        .build();

    private static final Schema SCHEMA_NUMBER_DOC = Schema.memberBuilder("numberDoc", PreludeSchemas.DOCUMENT)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1)
            )
        )
        .build();

    private static final Schema SCHEMA_FLOATING_POINTNUMBER_DOC = Schema.memberBuilder("floatingPointnumberDoc", PreludeSchemas.DOCUMENT)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1.2)
            )
        )
        .build();

    private static final Schema SCHEMA_LIST_DOC = Schema.memberBuilder("listDoc", PreludeSchemas.DOCUMENT)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                ArrayNode.builder()
                    .build()
            )
        )
        .build();

    private static final Schema SCHEMA_MAP_DOC = Schema.memberBuilder("mapDoc", PreludeSchemas.DOCUMENT)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.objectNodeBuilder()
                    .build()
            )
        )
        .build();

    private static final Schema SCHEMA_LIST = Schema.memberBuilder("list", SharedSchemas.LIST_OF_STRING)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                ArrayNode.builder()
                    .build()
            )
        )
        .build();

    private static final Schema SCHEMA_MAP = Schema.memberBuilder("map", SharedSchemas.STRING_STRING_MAP)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.objectNodeBuilder()
                    .build()
            )
        )
        .build();

    private static final Schema SCHEMA_TIMESTAMP = Schema.memberBuilder("timestamp", PreludeSchemas.TIMESTAMP)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from("1985-04-12T23:20:50.52Z")
            )
        )
        .build();

    private static final Schema SCHEMA_ENUM_MEMBER = Schema.memberBuilder("enum", NestedEnum.SCHEMA)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from("A")
            )
        )
        .build();

    private static final Schema SCHEMA_INT_ENUM = Schema.memberBuilder("intEnum", NestedIntEnum.SCHEMA)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1)
            )
        )
        .build();

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_BOOLEAN_MEMBER,
            SCHEMA_BIG_DECIMAL,
            SCHEMA_BIG_INTEGER,
            SCHEMA_BYTE_MEMBER,
            SCHEMA_DOUBLE_MEMBER,
            SCHEMA_FLOAT_MEMBER,
            SCHEMA_INTEGER,
            SCHEMA_LONG_MEMBER,
            SCHEMA_SHORT_MEMBER,
            SCHEMA_STRING,
            SCHEMA_BLOB,
            SCHEMA_STREAMING_BLOB,
            SCHEMA_BOOL_DOC,
            SCHEMA_STRING_DOC,
            SCHEMA_NUMBER_DOC,
            SCHEMA_FLOATING_POINTNUMBER_DOC,
            SCHEMA_LIST_DOC,
            SCHEMA_MAP_DOC,
            SCHEMA_LIST,
            SCHEMA_MAP,
            SCHEMA_TIMESTAMP,
            SCHEMA_ENUM_MEMBER,
            SCHEMA_INT_ENUM
        )
        .build();

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
    private transient final byte[] blob;
    private transient final DataStream streamingBlob;
    private transient final Document boolDoc;
    private transient final Document stringDoc;
    private transient final Document numberDoc;
    private transient final Document floatingPointnumberDoc;
    private transient final Document listDoc;
    private transient final Document mapDoc;
    private transient final List<String> list;
    private transient final Map<String, String> map;
    private transient final Instant timestamp;
    private transient final NestedEnum enumMember;
    private transient final NestedIntEnum intEnum;

    private DefaultsInput(Builder builder) {
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
        this.blob = builder.blob;
        this.streamingBlob = builder.streamingBlob;
        this.boolDoc = builder.boolDoc;
        this.stringDoc = builder.stringDoc;
        this.numberDoc = builder.numberDoc;
        this.floatingPointnumberDoc = builder.floatingPointnumberDoc;
        this.listDoc = builder.listDoc;
        this.mapDoc = builder.mapDoc;
        this.list = builder.list;
        this.map = builder.map;
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

    public byte[] blob() {
        return blob;
    }

    public DataStream streamingBlob() {
        return streamingBlob;
    }

    public Document boolDoc() {
        return boolDoc;
    }

    public Document stringDoc() {
        return stringDoc;
    }

    public Document numberDoc() {
        return numberDoc;
    }

    public Document floatingPointnumberDoc() {
        return floatingPointnumberDoc;
    }

    public Document listDoc() {
        return listDoc;
    }

    public Document mapDoc() {
        return mapDoc;
    }

    public List<String> list() {
        return Collections.unmodifiableList(list);
    }

    public boolean hasList() {
        return true;
    }

    public Map<String, String> map() {
        return Collections.unmodifiableMap(map);
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
        DefaultsInput that = (DefaultsInput) other;
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
               && this.blob == that.blob
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
        int result = Objects.hash(booleanMember, bigDecimal, bigInteger, byteMember, doubleMember, floatMember, integer, longMember, shortMember, string, streamingBlob, boolDoc, stringDoc, numberDoc, floatingPointnumberDoc, listDoc, mapDoc, list, map, timestamp, enumMember, intEnum);
        result = 31 * result + Arrays.hashCode(blob);
        return result;

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

        serializer.writeDocument(SCHEMA_BOOL_DOC, boolDoc);

        serializer.writeDocument(SCHEMA_STRING_DOC, stringDoc);

        serializer.writeDocument(SCHEMA_NUMBER_DOC, numberDoc);

        serializer.writeDocument(SCHEMA_FLOATING_POINTNUMBER_DOC, floatingPointnumberDoc);

        serializer.writeDocument(SCHEMA_LIST_DOC, listDoc);

        serializer.writeDocument(SCHEMA_MAP_DOC, mapDoc);

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
     * Builder for {@link DefaultsInput}.
     */
    public static final class Builder implements ShapeBuilder<DefaultsInput> {
        private static final BigDecimal BIG_DECIMAL_DEFAULT = BigDecimal.valueOf(1.0);
        private static final BigInteger BIG_INTEGER_DEFAULT = BigInteger.valueOf(1);
        private static final String STRING_DEFAULT = "default";
        private static final DataStream STREAMING_BLOB_DEFAULT = DataStream.ofBytes(Base64.getDecoder().decode("c3RyZWFtaW5n"));
        private static final Document BOOL_DOC_DEFAULT = Document.createBoolean(true);
        private static final Document STRING_DOC_DEFAULT = Document.createString("string");
        private static final Document NUMBER_DOC_DEFAULT = Document.createInteger(1);
        private static final Document FLOATING_POINTNUMBER_DOC_DEFAULT = Document.createDouble(1.2);
        private static final Document LIST_DOC_DEFAULT = Document.createList(Collections.emptyList());
        private static final Document MAP_DOC_DEFAULT = Document.createStringMap(Collections.emptyMap());
        private static final Instant TIMESTAMP_DEFAULT = Instant.ofEpochMilli(482196050520L);
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private boolean booleanMember = true;
        private BigDecimal bigDecimal = BIG_DECIMAL_DEFAULT;
        private BigInteger bigInteger = BIG_INTEGER_DEFAULT;
        private byte byteMember = 1;
        private double doubleMember = 1.0;
        private float floatMember = 1.0f;
        private int integer = 1;
        private long longMember = 1L;
        private short shortMember = 1;
        private String string = STRING_DEFAULT;
        private byte[] blob = Base64.getDecoder().decode("YmxvYg==");
        private DataStream streamingBlob = DataStream.ofEmpty();
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

        public Builder booleanMember(boolean booleanMember) {
            this.booleanMember = booleanMember;
            return this;
        }

        public Builder bigDecimal(BigDecimal bigDecimal) {
            this.bigDecimal = Objects.requireNonNull(bigDecimal, "bigDecimal cannot be null");
            return this;
        }

        public Builder bigInteger(BigInteger bigInteger) {
            this.bigInteger = Objects.requireNonNull(bigInteger, "bigInteger cannot be null");
            return this;
        }

        public Builder byteMember(byte byteMember) {
            this.byteMember = byteMember;
            return this;
        }

        public Builder doubleMember(double doubleMember) {
            this.doubleMember = doubleMember;
            return this;
        }

        public Builder floatMember(float floatMember) {
            this.floatMember = floatMember;
            return this;
        }

        public Builder integer(int integer) {
            this.integer = integer;
            return this;
        }

        public Builder longMember(long longMember) {
            this.longMember = longMember;
            return this;
        }

        public Builder shortMember(short shortMember) {
            this.shortMember = shortMember;
            return this;
        }

        public Builder string(String string) {
            this.string = Objects.requireNonNull(string, "string cannot be null");
            return this;
        }

        public Builder blob(byte[] blob) {
            this.blob = blob;
            return this;
        }

        @Override
        public void setDataStream(DataStream stream) {
            streamingBlob(stream);
        }

        public Builder streamingBlob(DataStream streamingBlob) {
            this.streamingBlob = Objects.requireNonNull(streamingBlob, "streamingBlob cannot be null");
            return this;
        }

        public Builder boolDoc(Document boolDoc) {
            this.boolDoc = Objects.requireNonNull(boolDoc, "boolDoc cannot be null");
            return this;
        }

        public Builder stringDoc(Document stringDoc) {
            this.stringDoc = Objects.requireNonNull(stringDoc, "stringDoc cannot be null");
            return this;
        }

        public Builder numberDoc(Document numberDoc) {
            this.numberDoc = Objects.requireNonNull(numberDoc, "numberDoc cannot be null");
            return this;
        }

        public Builder floatingPointnumberDoc(Document floatingPointnumberDoc) {
            this.floatingPointnumberDoc = Objects.requireNonNull(floatingPointnumberDoc, "floatingPointnumberDoc cannot be null");
            return this;
        }

        public Builder listDoc(Document listDoc) {
            this.listDoc = Objects.requireNonNull(listDoc, "listDoc cannot be null");
            return this;
        }

        public Builder mapDoc(Document mapDoc) {
            this.mapDoc = Objects.requireNonNull(mapDoc, "mapDoc cannot be null");
            return this;
        }

        public Builder list(List<String> list) {
            this.list = Objects.requireNonNull(list, "list cannot be null");
            return this;
        }

        public Builder map(Map<String, String> map) {
            this.map = Objects.requireNonNull(map, "map cannot be null");
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
            return this;
        }

        public Builder enumMember(NestedEnum enumMember) {
            this.enumMember = Objects.requireNonNull(enumMember, "enumMember cannot be null");
            return this;
        }

        public Builder intEnum(NestedIntEnum intEnum) {
            this.intEnum = Objects.requireNonNull(intEnum, "intEnum cannot be null");
            return this;
        }

        @Override
        public DefaultsInput build() {
            return new DefaultsInput(this);
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
                    case 12 -> builder.boolDoc(de.readDocument());
                    case 13 -> builder.stringDoc(de.readDocument());
                    case 14 -> builder.numberDoc(de.readDocument());
                    case 15 -> builder.floatingPointnumberDoc(de.readDocument());
                    case 16 -> builder.listDoc(de.readDocument());
                    case 17 -> builder.mapDoc(de.readDocument());
                    case 18 -> builder.list(SharedSerde.deserializeListOfString(member, de));
                    case 19 -> builder.map(SharedSerde.deserializeStringStringMap(member, de));
                    case 20 -> builder.timestamp(de.readTimestamp(member));
                    case 21 -> builder.enumMember(NestedEnum.builder().deserialize(de).build());
                    case 22 -> builder.intEnum(NestedIntEnum.builder().deserialize(de).build());
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

}

