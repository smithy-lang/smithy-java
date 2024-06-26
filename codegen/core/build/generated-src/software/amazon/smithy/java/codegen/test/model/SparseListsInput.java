

package software.amazon.smithy.java.codegen.test.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class SparseListsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.lists#SparseListsInput");

    private static final Schema SCHEMA_LIST_OF_BOOLEANS = Schema.memberBuilder("listOfBooleans", SharedSchemas.SPARSE_BOOLEANS)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_BIG_DECIMAL = Schema.memberBuilder("listOfBigDecimal", SharedSchemas.SPARSE_BIG_DECIMALS)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_BIG_INTEGER = Schema.memberBuilder("listOfBigInteger", SharedSchemas.SPARSE_BIG_INTEGERS)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_BYTE = Schema.memberBuilder("listOfByte", SharedSchemas.SPARSE_BYTES)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_DOUBLE = Schema.memberBuilder("listOfDouble", SharedSchemas.SPARSE_DOUBLES)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_FLOAT = Schema.memberBuilder("listOfFloat", SharedSchemas.SPARSE_FLOATS)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_INTEGER = Schema.memberBuilder("listOfInteger", SharedSchemas.SPARSE_INTEGERS)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_LONG = Schema.memberBuilder("listOfLong", SharedSchemas.SPARSE_LONGS)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_SHORT = Schema.memberBuilder("listOfShort", SharedSchemas.SPARSE_SHORTS)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_STRING = Schema.memberBuilder("listOfString", SharedSchemas.SPARSE_STRINGS)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_BLOBS = Schema.memberBuilder("listOfBlobs", SharedSchemas.SPARSE_BLOBS)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_TIMESTAMPS = Schema.memberBuilder("listOfTimestamps", SharedSchemas.SPARSE_TIMESTAMPS)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_UNION = Schema.memberBuilder("listOfUnion", SharedSchemas.SPARSE_UNIONS)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_ENUM = Schema.memberBuilder("listOfEnum", SharedSchemas.SPARSE_ENUMS)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_INT_ENUM = Schema.memberBuilder("listOfIntEnum", SharedSchemas.SPARSE_INT_ENUMS)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_STRUCT = Schema.memberBuilder("listOfStruct", SharedSchemas.SPARSE_STRUCTS)
        .id(ID)
        .build();

    private static final Schema SCHEMA_LIST_OF_DOCUMENTS = Schema.memberBuilder("listOfDocuments", SharedSchemas.SPARSE_DOCS)
        .id(ID)
        .build();

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_LIST_OF_BOOLEANS,
            SCHEMA_LIST_OF_BIG_DECIMAL,
            SCHEMA_LIST_OF_BIG_INTEGER,
            SCHEMA_LIST_OF_BYTE,
            SCHEMA_LIST_OF_DOUBLE,
            SCHEMA_LIST_OF_FLOAT,
            SCHEMA_LIST_OF_INTEGER,
            SCHEMA_LIST_OF_LONG,
            SCHEMA_LIST_OF_SHORT,
            SCHEMA_LIST_OF_STRING,
            SCHEMA_LIST_OF_BLOBS,
            SCHEMA_LIST_OF_TIMESTAMPS,
            SCHEMA_LIST_OF_UNION,
            SCHEMA_LIST_OF_ENUM,
            SCHEMA_LIST_OF_INT_ENUM,
            SCHEMA_LIST_OF_STRUCT,
            SCHEMA_LIST_OF_DOCUMENTS
        )
        .build();

    private transient final List<Boolean> listOfBooleans;
    private transient final List<BigDecimal> listOfBigDecimal;
    private transient final List<BigInteger> listOfBigInteger;
    private transient final List<Byte> listOfByte;
    private transient final List<Double> listOfDouble;
    private transient final List<Float> listOfFloat;
    private transient final List<Integer> listOfInteger;
    private transient final List<Long> listOfLong;
    private transient final List<Short> listOfShort;
    private transient final List<String> listOfString;
    private transient final List<byte[]> listOfBlobs;
    private transient final List<Instant> listOfTimestamps;
    private transient final List<NestedUnion> listOfUnion;
    private transient final List<NestedEnum> listOfEnum;
    private transient final List<NestedIntEnum> listOfIntEnum;
    private transient final List<NestedStruct> listOfStruct;
    private transient final List<Document> listOfDocuments;

    private SparseListsInput(Builder builder) {
        this.listOfBooleans = builder.listOfBooleans == null ? null : Collections.unmodifiableList(builder.listOfBooleans);
        this.listOfBigDecimal = builder.listOfBigDecimal == null ? null : Collections.unmodifiableList(builder.listOfBigDecimal);
        this.listOfBigInteger = builder.listOfBigInteger == null ? null : Collections.unmodifiableList(builder.listOfBigInteger);
        this.listOfByte = builder.listOfByte == null ? null : Collections.unmodifiableList(builder.listOfByte);
        this.listOfDouble = builder.listOfDouble == null ? null : Collections.unmodifiableList(builder.listOfDouble);
        this.listOfFloat = builder.listOfFloat == null ? null : Collections.unmodifiableList(builder.listOfFloat);
        this.listOfInteger = builder.listOfInteger == null ? null : Collections.unmodifiableList(builder.listOfInteger);
        this.listOfLong = builder.listOfLong == null ? null : Collections.unmodifiableList(builder.listOfLong);
        this.listOfShort = builder.listOfShort == null ? null : Collections.unmodifiableList(builder.listOfShort);
        this.listOfString = builder.listOfString == null ? null : Collections.unmodifiableList(builder.listOfString);
        this.listOfBlobs = builder.listOfBlobs == null ? null : Collections.unmodifiableList(builder.listOfBlobs);
        this.listOfTimestamps = builder.listOfTimestamps == null ? null : Collections.unmodifiableList(builder.listOfTimestamps);
        this.listOfUnion = builder.listOfUnion == null ? null : Collections.unmodifiableList(builder.listOfUnion);
        this.listOfEnum = builder.listOfEnum == null ? null : Collections.unmodifiableList(builder.listOfEnum);
        this.listOfIntEnum = builder.listOfIntEnum == null ? null : Collections.unmodifiableList(builder.listOfIntEnum);
        this.listOfStruct = builder.listOfStruct == null ? null : Collections.unmodifiableList(builder.listOfStruct);
        this.listOfDocuments = builder.listOfDocuments == null ? null : Collections.unmodifiableList(builder.listOfDocuments);
    }

    public List<Boolean> listOfBooleans() {
        if (listOfBooleans == null) {
            return Collections.emptyList();
        }
        return listOfBooleans;
    }

    public boolean hasListOfBooleans() {
        return listOfBooleans != null;
    }

    public List<BigDecimal> listOfBigDecimal() {
        if (listOfBigDecimal == null) {
            return Collections.emptyList();
        }
        return listOfBigDecimal;
    }

    public boolean hasListOfBigDecimal() {
        return listOfBigDecimal != null;
    }

    public List<BigInteger> listOfBigInteger() {
        if (listOfBigInteger == null) {
            return Collections.emptyList();
        }
        return listOfBigInteger;
    }

    public boolean hasListOfBigInteger() {
        return listOfBigInteger != null;
    }

    public List<Byte> listOfByte() {
        if (listOfByte == null) {
            return Collections.emptyList();
        }
        return listOfByte;
    }

    public boolean hasListOfByte() {
        return listOfByte != null;
    }

    public List<Double> listOfDouble() {
        if (listOfDouble == null) {
            return Collections.emptyList();
        }
        return listOfDouble;
    }

    public boolean hasListOfDouble() {
        return listOfDouble != null;
    }

    public List<Float> listOfFloat() {
        if (listOfFloat == null) {
            return Collections.emptyList();
        }
        return listOfFloat;
    }

    public boolean hasListOfFloat() {
        return listOfFloat != null;
    }

    public List<Integer> listOfInteger() {
        if (listOfInteger == null) {
            return Collections.emptyList();
        }
        return listOfInteger;
    }

    public boolean hasListOfInteger() {
        return listOfInteger != null;
    }

    public List<Long> listOfLong() {
        if (listOfLong == null) {
            return Collections.emptyList();
        }
        return listOfLong;
    }

    public boolean hasListOfLong() {
        return listOfLong != null;
    }

    public List<Short> listOfShort() {
        if (listOfShort == null) {
            return Collections.emptyList();
        }
        return listOfShort;
    }

    public boolean hasListOfShort() {
        return listOfShort != null;
    }

    public List<String> listOfString() {
        if (listOfString == null) {
            return Collections.emptyList();
        }
        return listOfString;
    }

    public boolean hasListOfString() {
        return listOfString != null;
    }

    public List<byte[]> listOfBlobs() {
        if (listOfBlobs == null) {
            return Collections.emptyList();
        }
        return listOfBlobs;
    }

    public boolean hasListOfBlobs() {
        return listOfBlobs != null;
    }

    public List<Instant> listOfTimestamps() {
        if (listOfTimestamps == null) {
            return Collections.emptyList();
        }
        return listOfTimestamps;
    }

    public boolean hasListOfTimestamps() {
        return listOfTimestamps != null;
    }

    public List<NestedUnion> listOfUnion() {
        if (listOfUnion == null) {
            return Collections.emptyList();
        }
        return listOfUnion;
    }

    public boolean hasListOfUnion() {
        return listOfUnion != null;
    }

    public List<NestedEnum> listOfEnum() {
        if (listOfEnum == null) {
            return Collections.emptyList();
        }
        return listOfEnum;
    }

    public boolean hasListOfEnum() {
        return listOfEnum != null;
    }

    public List<NestedIntEnum> listOfIntEnum() {
        if (listOfIntEnum == null) {
            return Collections.emptyList();
        }
        return listOfIntEnum;
    }

    public boolean hasListOfIntEnum() {
        return listOfIntEnum != null;
    }

    public List<NestedStruct> listOfStruct() {
        if (listOfStruct == null) {
            return Collections.emptyList();
        }
        return listOfStruct;
    }

    public boolean hasListOfStruct() {
        return listOfStruct != null;
    }

    public List<Document> listOfDocuments() {
        if (listOfDocuments == null) {
            return Collections.emptyList();
        }
        return listOfDocuments;
    }

    public boolean hasListOfDocuments() {
        return listOfDocuments != null;
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
        SparseListsInput that = (SparseListsInput) other;
        return Objects.equals(this.listOfBooleans, that.listOfBooleans)
               && Objects.equals(this.listOfBigDecimal, that.listOfBigDecimal)
               && Objects.equals(this.listOfBigInteger, that.listOfBigInteger)
               && Objects.equals(this.listOfByte, that.listOfByte)
               && Objects.equals(this.listOfDouble, that.listOfDouble)
               && Objects.equals(this.listOfFloat, that.listOfFloat)
               && Objects.equals(this.listOfInteger, that.listOfInteger)
               && Objects.equals(this.listOfLong, that.listOfLong)
               && Objects.equals(this.listOfShort, that.listOfShort)
               && Objects.equals(this.listOfString, that.listOfString)
               && Objects.equals(this.listOfBlobs, that.listOfBlobs)
               && Objects.equals(this.listOfTimestamps, that.listOfTimestamps)
               && Objects.equals(this.listOfUnion, that.listOfUnion)
               && Objects.equals(this.listOfEnum, that.listOfEnum)
               && Objects.equals(this.listOfIntEnum, that.listOfIntEnum)
               && Objects.equals(this.listOfStruct, that.listOfStruct)
               && Objects.equals(this.listOfDocuments, that.listOfDocuments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listOfBooleans, listOfBigDecimal, listOfBigInteger, listOfByte, listOfDouble, listOfFloat, listOfInteger, listOfLong, listOfShort, listOfString, listOfBlobs, listOfTimestamps, listOfUnion, listOfEnum, listOfIntEnum, listOfStruct, listOfDocuments);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (listOfBooleans != null) {
            serializer.writeList(SCHEMA_LIST_OF_BOOLEANS, listOfBooleans, SharedSerde.SparseBooleansSerializer.INSTANCE);
        }

        if (listOfBigDecimal != null) {
            serializer.writeList(SCHEMA_LIST_OF_BIG_DECIMAL, listOfBigDecimal, SharedSerde.SparseBigDecimalsSerializer.INSTANCE);
        }

        if (listOfBigInteger != null) {
            serializer.writeList(SCHEMA_LIST_OF_BIG_INTEGER, listOfBigInteger, SharedSerde.SparseBigIntegersSerializer.INSTANCE);
        }

        if (listOfByte != null) {
            serializer.writeList(SCHEMA_LIST_OF_BYTE, listOfByte, SharedSerde.SparseBytesSerializer.INSTANCE);
        }

        if (listOfDouble != null) {
            serializer.writeList(SCHEMA_LIST_OF_DOUBLE, listOfDouble, SharedSerde.SparseDoublesSerializer.INSTANCE);
        }

        if (listOfFloat != null) {
            serializer.writeList(SCHEMA_LIST_OF_FLOAT, listOfFloat, SharedSerde.SparseFloatsSerializer.INSTANCE);
        }

        if (listOfInteger != null) {
            serializer.writeList(SCHEMA_LIST_OF_INTEGER, listOfInteger, SharedSerde.SparseIntegersSerializer.INSTANCE);
        }

        if (listOfLong != null) {
            serializer.writeList(SCHEMA_LIST_OF_LONG, listOfLong, SharedSerde.SparseLongsSerializer.INSTANCE);
        }

        if (listOfShort != null) {
            serializer.writeList(SCHEMA_LIST_OF_SHORT, listOfShort, SharedSerde.SparseShortsSerializer.INSTANCE);
        }

        if (listOfString != null) {
            serializer.writeList(SCHEMA_LIST_OF_STRING, listOfString, SharedSerde.SparseStringsSerializer.INSTANCE);
        }

        if (listOfBlobs != null) {
            serializer.writeList(SCHEMA_LIST_OF_BLOBS, listOfBlobs, SharedSerde.SparseBlobsSerializer.INSTANCE);
        }

        if (listOfTimestamps != null) {
            serializer.writeList(SCHEMA_LIST_OF_TIMESTAMPS, listOfTimestamps, SharedSerde.SparseTimestampsSerializer.INSTANCE);
        }

        if (listOfUnion != null) {
            serializer.writeList(SCHEMA_LIST_OF_UNION, listOfUnion, SharedSerde.SparseUnionsSerializer.INSTANCE);
        }

        if (listOfEnum != null) {
            serializer.writeList(SCHEMA_LIST_OF_ENUM, listOfEnum, SharedSerde.SparseEnumsSerializer.INSTANCE);
        }

        if (listOfIntEnum != null) {
            serializer.writeList(SCHEMA_LIST_OF_INT_ENUM, listOfIntEnum, SharedSerde.SparseIntEnumsSerializer.INSTANCE);
        }

        if (listOfStruct != null) {
            serializer.writeList(SCHEMA_LIST_OF_STRUCT, listOfStruct, SharedSerde.SparseStructsSerializer.INSTANCE);
        }

        if (listOfDocuments != null) {
            serializer.writeList(SCHEMA_LIST_OF_DOCUMENTS, listOfDocuments, SharedSerde.SparseDocsSerializer.INSTANCE);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link SparseListsInput}.
     */
    public static final class Builder implements ShapeBuilder<SparseListsInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private List<Boolean> listOfBooleans;
        private List<BigDecimal> listOfBigDecimal;
        private List<BigInteger> listOfBigInteger;
        private List<Byte> listOfByte;
        private List<Double> listOfDouble;
        private List<Float> listOfFloat;
        private List<Integer> listOfInteger;
        private List<Long> listOfLong;
        private List<Short> listOfShort;
        private List<String> listOfString;
        private List<byte[]> listOfBlobs;
        private List<Instant> listOfTimestamps;
        private List<NestedUnion> listOfUnion;
        private List<NestedEnum> listOfEnum;
        private List<NestedIntEnum> listOfIntEnum;
        private List<NestedStruct> listOfStruct;
        private List<Document> listOfDocuments;

        private Builder() {}

        public Builder listOfBooleans(List<Boolean> listOfBooleans) {
            this.listOfBooleans = listOfBooleans;
            return this;
        }

        public Builder listOfBigDecimal(List<BigDecimal> listOfBigDecimal) {
            this.listOfBigDecimal = listOfBigDecimal;
            return this;
        }

        public Builder listOfBigInteger(List<BigInteger> listOfBigInteger) {
            this.listOfBigInteger = listOfBigInteger;
            return this;
        }

        public Builder listOfByte(List<Byte> listOfByte) {
            this.listOfByte = listOfByte;
            return this;
        }

        public Builder listOfDouble(List<Double> listOfDouble) {
            this.listOfDouble = listOfDouble;
            return this;
        }

        public Builder listOfFloat(List<Float> listOfFloat) {
            this.listOfFloat = listOfFloat;
            return this;
        }

        public Builder listOfInteger(List<Integer> listOfInteger) {
            this.listOfInteger = listOfInteger;
            return this;
        }

        public Builder listOfLong(List<Long> listOfLong) {
            this.listOfLong = listOfLong;
            return this;
        }

        public Builder listOfShort(List<Short> listOfShort) {
            this.listOfShort = listOfShort;
            return this;
        }

        public Builder listOfString(List<String> listOfString) {
            this.listOfString = listOfString;
            return this;
        }

        public Builder listOfBlobs(List<byte[]> listOfBlobs) {
            this.listOfBlobs = listOfBlobs;
            return this;
        }

        public Builder listOfTimestamps(List<Instant> listOfTimestamps) {
            this.listOfTimestamps = listOfTimestamps;
            return this;
        }

        public Builder listOfUnion(List<NestedUnion> listOfUnion) {
            this.listOfUnion = listOfUnion;
            return this;
        }

        public Builder listOfEnum(List<NestedEnum> listOfEnum) {
            this.listOfEnum = listOfEnum;
            return this;
        }

        public Builder listOfIntEnum(List<NestedIntEnum> listOfIntEnum) {
            this.listOfIntEnum = listOfIntEnum;
            return this;
        }

        public Builder listOfStruct(List<NestedStruct> listOfStruct) {
            this.listOfStruct = listOfStruct;
            return this;
        }

        public Builder listOfDocuments(List<Document> listOfDocuments) {
            this.listOfDocuments = listOfDocuments;
            return this;
        }

        @Override
        public SparseListsInput build() {
            return new SparseListsInput(this);
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
                    case 0 -> builder.listOfBooleans(SharedSerde.deserializeSparseBooleans(member, de));
                    case 1 -> builder.listOfBigDecimal(SharedSerde.deserializeSparseBigDecimals(member, de));
                    case 2 -> builder.listOfBigInteger(SharedSerde.deserializeSparseBigIntegers(member, de));
                    case 3 -> builder.listOfByte(SharedSerde.deserializeSparseBytes(member, de));
                    case 4 -> builder.listOfDouble(SharedSerde.deserializeSparseDoubles(member, de));
                    case 5 -> builder.listOfFloat(SharedSerde.deserializeSparseFloats(member, de));
                    case 6 -> builder.listOfInteger(SharedSerde.deserializeSparseIntegers(member, de));
                    case 7 -> builder.listOfLong(SharedSerde.deserializeSparseLongs(member, de));
                    case 8 -> builder.listOfShort(SharedSerde.deserializeSparseShorts(member, de));
                    case 9 -> builder.listOfString(SharedSerde.deserializeSparseStrings(member, de));
                    case 10 -> builder.listOfBlobs(SharedSerde.deserializeSparseBlobs(member, de));
                    case 11 -> builder.listOfTimestamps(SharedSerde.deserializeSparseTimestamps(member, de));
                    case 12 -> builder.listOfUnion(SharedSerde.deserializeSparseUnions(member, de));
                    case 13 -> builder.listOfEnum(SharedSerde.deserializeSparseEnums(member, de));
                    case 14 -> builder.listOfIntEnum(SharedSerde.deserializeSparseIntEnums(member, de));
                    case 15 -> builder.listOfStruct(SharedSerde.deserializeSparseStructs(member, de));
                    case 16 -> builder.listOfDocuments(SharedSerde.deserializeSparseDocs(member, de));
                }
            }

        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.listOfBooleans(this.listOfBooleans);
        builder.listOfBigDecimal(this.listOfBigDecimal);
        builder.listOfBigInteger(this.listOfBigInteger);
        builder.listOfByte(this.listOfByte);
        builder.listOfDouble(this.listOfDouble);
        builder.listOfFloat(this.listOfFloat);
        builder.listOfInteger(this.listOfInteger);
        builder.listOfLong(this.listOfLong);
        builder.listOfShort(this.listOfShort);
        builder.listOfString(this.listOfString);
        builder.listOfBlobs(this.listOfBlobs);
        builder.listOfTimestamps(this.listOfTimestamps);
        builder.listOfUnion(this.listOfUnion);
        builder.listOfEnum(this.listOfEnum);
        builder.listOfIntEnum(this.listOfIntEnum);
        builder.listOfStruct(this.listOfStruct);
        builder.listOfDocuments(this.listOfDocuments);
        return builder;
    }

}

