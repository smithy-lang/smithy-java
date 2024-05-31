

package io.smithy.codegen.test.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class ClientErrorCorrectionInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#ClientErrorCorrectionInput");

    private static final SdkSchema SCHEMA_BOOLEAN_MEMBER = SdkSchema.memberBuilder("booleanMember", PreludeSchemas.BOOLEAN)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_BIG_DECIMAL = SdkSchema.memberBuilder("bigDecimal", PreludeSchemas.BIG_DECIMAL)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_BIG_INTEGER = SdkSchema.memberBuilder("bigInteger", PreludeSchemas.BIG_INTEGER)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_BYTE_MEMBER = SdkSchema.memberBuilder("byteMember", PreludeSchemas.BYTE)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_DOUBLE_MEMBER = SdkSchema.memberBuilder("doubleMember", PreludeSchemas.DOUBLE)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_FLOAT_MEMBER = SdkSchema.memberBuilder("floatMember", PreludeSchemas.FLOAT)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_INTEGER = SdkSchema.memberBuilder("integer", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_LONG_MEMBER = SdkSchema.memberBuilder("longMember", PreludeSchemas.LONG)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_SHORT_MEMBER = SdkSchema.memberBuilder("shortMember", PreludeSchemas.SHORT)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_STRING = SdkSchema.memberBuilder("string", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_BLOB = SdkSchema.memberBuilder("blob", PreludeSchemas.BLOB)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_STREAMING_BLOB = SdkSchema.memberBuilder("streamingBlob", SharedSchemas.NESTED_STREAMING_BLOB)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_DOCUMENT = SdkSchema.memberBuilder("document", PreludeSchemas.DOCUMENT)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_LIST = SdkSchema.memberBuilder("list", SharedSchemas.CORRECTED_LIST)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_MAP = SdkSchema.memberBuilder("map", SharedSchemas.CORRECTED_MAP)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_STRUCTURE = SdkSchema.memberBuilder("structure", CouldBeEmptyStruct.SCHEMA)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_TIMESTAMP = SdkSchema.memberBuilder("timestamp", PreludeSchemas.TIMESTAMP)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
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
            SCHEMA_DOCUMENT,
            SCHEMA_LIST,
            SCHEMA_MAP,
            SCHEMA_STRUCTURE,
            SCHEMA_TIMESTAMP
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
    private transient final Document document;
    private transient final List<String> list;
    private transient final Map<String, String> map;
    private transient final CouldBeEmptyStruct structure;
    private transient final Instant timestamp;

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
        this.blob = builder.blob;
        this.streamingBlob = builder.streamingBlob;
        this.document = builder.document;
        this.list = builder.list;
        this.map = builder.map;
        this.structure = builder.structure;
        this.timestamp = builder.timestamp;
    }

    public Boolean booleanMember() {
        return booleanMember;
    }

    public BigDecimal bigDecimal() {
        return bigDecimal;
    }

    public BigInteger bigInteger() {
        return bigInteger;
    }

    public Byte byteMember() {
        return byteMember;
    }

    public Double doubleMember() {
        return doubleMember;
    }

    public Float floatMember() {
        return floatMember;
    }

    public Integer integer() {
        return integer;
    }

    public Long longMember() {
        return longMember;
    }

    public Short shortMember() {
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

    public Document document() {
        return document;
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

    public CouldBeEmptyStruct structure() {
        return structure;
    }

    public Instant timestamp() {
        return timestamp;
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
        return booleanMember == that.booleanMember
               && Objects.equals(bigDecimal, that.bigDecimal)
               && Objects.equals(bigInteger, that.bigInteger)
               && byteMember == that.byteMember
               && doubleMember == that.doubleMember
               && floatMember == that.floatMember
               && integer == that.integer
               && longMember == that.longMember
               && shortMember == that.shortMember
               && Objects.equals(string, that.string)
               && blob == that.blob
               && Objects.equals(streamingBlob, that.streamingBlob)
               && Objects.equals(document, that.document)
               && Objects.equals(list, that.list)
               && Objects.equals(map, that.map)
               && Objects.equals(structure, that.structure)
               && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(booleanMember, bigDecimal, bigInteger, byteMember, doubleMember, floatMember, integer, longMember, shortMember, string, streamingBlob, document, list, map, structure, timestamp);
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

        serializer.writeDocument(SCHEMA_DOCUMENT, document);

        serializer.writeList(SCHEMA_LIST, list, SharedSerde.CorrectedListSerializer.INSTANCE);

        serializer.writeMap(SCHEMA_MAP, map, SharedSerde.CorrectedMapSerializer.INSTANCE);

        serializer.writeStruct(SCHEMA_STRUCTURE, structure);

        serializer.writeTimestamp(SCHEMA_TIMESTAMP, timestamp);

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ClientErrorCorrectionInput}.
     */
    public static final class Builder implements SdkShapeBuilder<ClientErrorCorrectionInput> {
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
        private byte[] blob;
        private DataStream streamingBlob = DataStream.ofEmpty();
        private Document document;
        private List<String> list;
        private Map<String, String> map;
        private CouldBeEmptyStruct structure;
        private Instant timestamp;

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

        public Builder blob(byte[] blob) {
            this.blob = blob;
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

        public Builder structure(CouldBeEmptyStruct structure) {
            this.structure = Objects.requireNonNull(structure, "structure cannot be null");
            tracker.setMember(SCHEMA_STRUCTURE);
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
            tracker.setMember(SCHEMA_TIMESTAMP);
            return this;
        }

        @Override
        public ClientErrorCorrectionInput build() {
            tracker.validate();
            return new ClientErrorCorrectionInput(this);
        }

        @Override
        public SdkShapeBuilder<ClientErrorCorrectionInput> errorCorrection() {
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
                blob(new byte[0]);
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
            if (!tracker.checkMember(SCHEMA_STRUCTURE)) {
                structure(CouldBeEmptyStruct.builder().build());
            }
            if (!tracker.checkMember(SCHEMA_TIMESTAMP)) {
                timestamp(Instant.EPOCH);
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
            public void accept(Builder builder, SdkSchema member, ShapeDeserializer de) {
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
                    case 13 -> builder.list(SharedSerde.deserializeCorrectedList(member, de));
                    case 14 -> builder.map(SharedSerde.deserializeCorrectedMap(member, de));
                    case 15 -> builder.structure(CouldBeEmptyStruct.builder().deserialize(de).build());
                    case 16 -> builder.timestamp(de.readTimestamp(member));
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
        builder.structure(this.structure);
        builder.timestamp(this.timestamp);
        return builder;
    }

}

