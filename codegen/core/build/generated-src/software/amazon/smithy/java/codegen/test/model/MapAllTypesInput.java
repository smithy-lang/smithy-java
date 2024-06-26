

package software.amazon.smithy.java.codegen.test.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class MapAllTypesInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.maps#MapAllTypesInput");

    private static final Schema SCHEMA_STRING_BOOLEAN_MAP = Schema.memberBuilder("stringBooleanMap", SharedSchemas.STRING_BOOLEAN_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_BIG_DECIMAL_MAP = Schema.memberBuilder("stringBigDecimalMap", SharedSchemas.STRING_BIG_DECIMAL_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_BIG_INTEGER_MAP = Schema.memberBuilder("stringBigIntegerMap", SharedSchemas.STRING_BIG_INTEGER_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_BYTE_MAP = Schema.memberBuilder("stringByteMap", SharedSchemas.STRING_BYTE_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_DOUBLE_MAP = Schema.memberBuilder("stringDoubleMap", SharedSchemas.STRING_DOUBLE_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_FLOAT_MAP = Schema.memberBuilder("stringFloatMap", SharedSchemas.STRING_FLOAT_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_INTEGER_MAP = Schema.memberBuilder("stringIntegerMap", SharedSchemas.STRING_INTEGER_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_LONG_MAP = Schema.memberBuilder("stringLongMap", SharedSchemas.STRING_LONG_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_SHORT_MAP = Schema.memberBuilder("stringShortMap", SharedSchemas.STRING_SHORT_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_STRING_MAP = Schema.memberBuilder("stringStringMap", SharedSchemas.STRING_STRING_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_BLOB_MAP = Schema.memberBuilder("stringBlobMap", SharedSchemas.STRING_BLOB_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_TIMESTAMP_MAP = Schema.memberBuilder("stringTimestampMap", SharedSchemas.STRING_TIMESTAMP_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_UNION_MAP = Schema.memberBuilder("stringUnionMap", SharedSchemas.STRING_UNION_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_ENUM_MAP = Schema.memberBuilder("stringEnumMap", SharedSchemas.STRING_ENUM_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_INT_ENUM_MAP = Schema.memberBuilder("stringIntEnumMap", SharedSchemas.STRING_INT_ENUM_MAP)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STRING_STRUCT_MAP = Schema.memberBuilder("stringStructMap", SharedSchemas.STRING_STRUCT_MAP)
        .id(ID)
        .build();

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_STRING_BOOLEAN_MAP,
            SCHEMA_STRING_BIG_DECIMAL_MAP,
            SCHEMA_STRING_BIG_INTEGER_MAP,
            SCHEMA_STRING_BYTE_MAP,
            SCHEMA_STRING_DOUBLE_MAP,
            SCHEMA_STRING_FLOAT_MAP,
            SCHEMA_STRING_INTEGER_MAP,
            SCHEMA_STRING_LONG_MAP,
            SCHEMA_STRING_SHORT_MAP,
            SCHEMA_STRING_STRING_MAP,
            SCHEMA_STRING_BLOB_MAP,
            SCHEMA_STRING_TIMESTAMP_MAP,
            SCHEMA_STRING_UNION_MAP,
            SCHEMA_STRING_ENUM_MAP,
            SCHEMA_STRING_INT_ENUM_MAP,
            SCHEMA_STRING_STRUCT_MAP
        )
        .build();

    private transient final Map<String, Boolean> stringBooleanMap;
    private transient final Map<String, BigDecimal> stringBigDecimalMap;
    private transient final Map<String, BigInteger> stringBigIntegerMap;
    private transient final Map<String, Byte> stringByteMap;
    private transient final Map<String, Double> stringDoubleMap;
    private transient final Map<String, Float> stringFloatMap;
    private transient final Map<String, Integer> stringIntegerMap;
    private transient final Map<String, Long> stringLongMap;
    private transient final Map<String, Short> stringShortMap;
    private transient final Map<String, String> stringStringMap;
    private transient final Map<String, ByteBuffer> stringBlobMap;
    private transient final Map<String, Instant> stringTimestampMap;
    private transient final Map<String, NestedUnion> stringUnionMap;
    private transient final Map<String, NestedEnum> stringEnumMap;
    private transient final Map<String, NestedIntEnum> stringIntEnumMap;
    private transient final Map<String, NestedStruct> stringStructMap;

    private MapAllTypesInput(Builder builder) {
        this.stringBooleanMap = builder.stringBooleanMap == null ? null : Collections.unmodifiableMap(builder.stringBooleanMap);
        this.stringBigDecimalMap = builder.stringBigDecimalMap == null ? null : Collections.unmodifiableMap(builder.stringBigDecimalMap);
        this.stringBigIntegerMap = builder.stringBigIntegerMap == null ? null : Collections.unmodifiableMap(builder.stringBigIntegerMap);
        this.stringByteMap = builder.stringByteMap == null ? null : Collections.unmodifiableMap(builder.stringByteMap);
        this.stringDoubleMap = builder.stringDoubleMap == null ? null : Collections.unmodifiableMap(builder.stringDoubleMap);
        this.stringFloatMap = builder.stringFloatMap == null ? null : Collections.unmodifiableMap(builder.stringFloatMap);
        this.stringIntegerMap = builder.stringIntegerMap == null ? null : Collections.unmodifiableMap(builder.stringIntegerMap);
        this.stringLongMap = builder.stringLongMap == null ? null : Collections.unmodifiableMap(builder.stringLongMap);
        this.stringShortMap = builder.stringShortMap == null ? null : Collections.unmodifiableMap(builder.stringShortMap);
        this.stringStringMap = builder.stringStringMap == null ? null : Collections.unmodifiableMap(builder.stringStringMap);
        this.stringBlobMap = builder.stringBlobMap == null ? null : Collections.unmodifiableMap(builder.stringBlobMap);
        this.stringTimestampMap = builder.stringTimestampMap == null ? null : Collections.unmodifiableMap(builder.stringTimestampMap);
        this.stringUnionMap = builder.stringUnionMap == null ? null : Collections.unmodifiableMap(builder.stringUnionMap);
        this.stringEnumMap = builder.stringEnumMap == null ? null : Collections.unmodifiableMap(builder.stringEnumMap);
        this.stringIntEnumMap = builder.stringIntEnumMap == null ? null : Collections.unmodifiableMap(builder.stringIntEnumMap);
        this.stringStructMap = builder.stringStructMap == null ? null : Collections.unmodifiableMap(builder.stringStructMap);
    }

    public Map<String, Boolean> stringBooleanMap() {
        if (stringBooleanMap == null) {
            return Collections.emptyMap();
        }
        return stringBooleanMap;
    }

    public boolean hasStringBooleanMap() {
        return stringBooleanMap != null;
    }

    public Map<String, BigDecimal> stringBigDecimalMap() {
        if (stringBigDecimalMap == null) {
            return Collections.emptyMap();
        }
        return stringBigDecimalMap;
    }

    public boolean hasStringBigDecimalMap() {
        return stringBigDecimalMap != null;
    }

    public Map<String, BigInteger> stringBigIntegerMap() {
        if (stringBigIntegerMap == null) {
            return Collections.emptyMap();
        }
        return stringBigIntegerMap;
    }

    public boolean hasStringBigIntegerMap() {
        return stringBigIntegerMap != null;
    }

    public Map<String, Byte> stringByteMap() {
        if (stringByteMap == null) {
            return Collections.emptyMap();
        }
        return stringByteMap;
    }

    public boolean hasStringByteMap() {
        return stringByteMap != null;
    }

    public Map<String, Double> stringDoubleMap() {
        if (stringDoubleMap == null) {
            return Collections.emptyMap();
        }
        return stringDoubleMap;
    }

    public boolean hasStringDoubleMap() {
        return stringDoubleMap != null;
    }

    public Map<String, Float> stringFloatMap() {
        if (stringFloatMap == null) {
            return Collections.emptyMap();
        }
        return stringFloatMap;
    }

    public boolean hasStringFloatMap() {
        return stringFloatMap != null;
    }

    public Map<String, Integer> stringIntegerMap() {
        if (stringIntegerMap == null) {
            return Collections.emptyMap();
        }
        return stringIntegerMap;
    }

    public boolean hasStringIntegerMap() {
        return stringIntegerMap != null;
    }

    public Map<String, Long> stringLongMap() {
        if (stringLongMap == null) {
            return Collections.emptyMap();
        }
        return stringLongMap;
    }

    public boolean hasStringLongMap() {
        return stringLongMap != null;
    }

    public Map<String, Short> stringShortMap() {
        if (stringShortMap == null) {
            return Collections.emptyMap();
        }
        return stringShortMap;
    }

    public boolean hasStringShortMap() {
        return stringShortMap != null;
    }

    public Map<String, String> stringStringMap() {
        if (stringStringMap == null) {
            return Collections.emptyMap();
        }
        return stringStringMap;
    }

    public boolean hasStringStringMap() {
        return stringStringMap != null;
    }

    public Map<String, ByteBuffer> stringBlobMap() {
        if (stringBlobMap == null) {
            return Collections.emptyMap();
        }
        return stringBlobMap;
    }

    public boolean hasStringBlobMap() {
        return stringBlobMap != null;
    }

    public Map<String, Instant> stringTimestampMap() {
        if (stringTimestampMap == null) {
            return Collections.emptyMap();
        }
        return stringTimestampMap;
    }

    public boolean hasStringTimestampMap() {
        return stringTimestampMap != null;
    }

    public Map<String, NestedUnion> stringUnionMap() {
        if (stringUnionMap == null) {
            return Collections.emptyMap();
        }
        return stringUnionMap;
    }

    public boolean hasStringUnionMap() {
        return stringUnionMap != null;
    }

    public Map<String, NestedEnum> stringEnumMap() {
        if (stringEnumMap == null) {
            return Collections.emptyMap();
        }
        return stringEnumMap;
    }

    public boolean hasStringEnumMap() {
        return stringEnumMap != null;
    }

    public Map<String, NestedIntEnum> stringIntEnumMap() {
        if (stringIntEnumMap == null) {
            return Collections.emptyMap();
        }
        return stringIntEnumMap;
    }

    public boolean hasStringIntEnumMap() {
        return stringIntEnumMap != null;
    }

    public Map<String, NestedStruct> stringStructMap() {
        if (stringStructMap == null) {
            return Collections.emptyMap();
        }
        return stringStructMap;
    }

    public boolean hasStringStructMap() {
        return stringStructMap != null;
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
        MapAllTypesInput that = (MapAllTypesInput) other;
        return Objects.equals(this.stringBooleanMap, that.stringBooleanMap)
               && Objects.equals(this.stringBigDecimalMap, that.stringBigDecimalMap)
               && Objects.equals(this.stringBigIntegerMap, that.stringBigIntegerMap)
               && Objects.equals(this.stringByteMap, that.stringByteMap)
               && Objects.equals(this.stringDoubleMap, that.stringDoubleMap)
               && Objects.equals(this.stringFloatMap, that.stringFloatMap)
               && Objects.equals(this.stringIntegerMap, that.stringIntegerMap)
               && Objects.equals(this.stringLongMap, that.stringLongMap)
               && Objects.equals(this.stringShortMap, that.stringShortMap)
               && Objects.equals(this.stringStringMap, that.stringStringMap)
               && Objects.equals(this.stringBlobMap, that.stringBlobMap)
               && Objects.equals(this.stringTimestampMap, that.stringTimestampMap)
               && Objects.equals(this.stringUnionMap, that.stringUnionMap)
               && Objects.equals(this.stringEnumMap, that.stringEnumMap)
               && Objects.equals(this.stringIntEnumMap, that.stringIntEnumMap)
               && Objects.equals(this.stringStructMap, that.stringStructMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stringBooleanMap, stringBigDecimalMap, stringBigIntegerMap, stringByteMap, stringDoubleMap, stringFloatMap, stringIntegerMap, stringLongMap, stringShortMap, stringStringMap, stringBlobMap, stringTimestampMap, stringUnionMap, stringEnumMap, stringIntEnumMap, stringStructMap);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (stringBooleanMap != null) {
            serializer.writeMap(SCHEMA_STRING_BOOLEAN_MAP, stringBooleanMap, SharedSerde.StringBooleanMapSerializer.INSTANCE);
        }

        if (stringBigDecimalMap != null) {
            serializer.writeMap(SCHEMA_STRING_BIG_DECIMAL_MAP, stringBigDecimalMap, SharedSerde.StringBigDecimalMapSerializer.INSTANCE);
        }

        if (stringBigIntegerMap != null) {
            serializer.writeMap(SCHEMA_STRING_BIG_INTEGER_MAP, stringBigIntegerMap, SharedSerde.StringBigIntegerMapSerializer.INSTANCE);
        }

        if (stringByteMap != null) {
            serializer.writeMap(SCHEMA_STRING_BYTE_MAP, stringByteMap, SharedSerde.StringByteMapSerializer.INSTANCE);
        }

        if (stringDoubleMap != null) {
            serializer.writeMap(SCHEMA_STRING_DOUBLE_MAP, stringDoubleMap, SharedSerde.StringDoubleMapSerializer.INSTANCE);
        }

        if (stringFloatMap != null) {
            serializer.writeMap(SCHEMA_STRING_FLOAT_MAP, stringFloatMap, SharedSerde.StringFloatMapSerializer.INSTANCE);
        }

        if (stringIntegerMap != null) {
            serializer.writeMap(SCHEMA_STRING_INTEGER_MAP, stringIntegerMap, SharedSerde.StringIntegerMapSerializer.INSTANCE);
        }

        if (stringLongMap != null) {
            serializer.writeMap(SCHEMA_STRING_LONG_MAP, stringLongMap, SharedSerde.StringLongMapSerializer.INSTANCE);
        }

        if (stringShortMap != null) {
            serializer.writeMap(SCHEMA_STRING_SHORT_MAP, stringShortMap, SharedSerde.StringShortMapSerializer.INSTANCE);
        }

        if (stringStringMap != null) {
            serializer.writeMap(SCHEMA_STRING_STRING_MAP, stringStringMap, SharedSerde.StringStringMapSerializer.INSTANCE);
        }

        if (stringBlobMap != null) {
            serializer.writeMap(SCHEMA_STRING_BLOB_MAP, stringBlobMap, SharedSerde.StringBlobMapSerializer.INSTANCE);
        }

        if (stringTimestampMap != null) {
            serializer.writeMap(SCHEMA_STRING_TIMESTAMP_MAP, stringTimestampMap, SharedSerde.StringTimestampMapSerializer.INSTANCE);
        }

        if (stringUnionMap != null) {
            serializer.writeMap(SCHEMA_STRING_UNION_MAP, stringUnionMap, SharedSerde.StringUnionMapSerializer.INSTANCE);
        }

        if (stringEnumMap != null) {
            serializer.writeMap(SCHEMA_STRING_ENUM_MAP, stringEnumMap, SharedSerde.StringEnumMapSerializer.INSTANCE);
        }

        if (stringIntEnumMap != null) {
            serializer.writeMap(SCHEMA_STRING_INT_ENUM_MAP, stringIntEnumMap, SharedSerde.StringIntEnumMapSerializer.INSTANCE);
        }

        if (stringStructMap != null) {
            serializer.writeMap(SCHEMA_STRING_STRUCT_MAP, stringStructMap, SharedSerde.StringStructMapSerializer.INSTANCE);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link MapAllTypesInput}.
     */
    public static final class Builder implements ShapeBuilder<MapAllTypesInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private Map<String, Boolean> stringBooleanMap;
        private Map<String, BigDecimal> stringBigDecimalMap;
        private Map<String, BigInteger> stringBigIntegerMap;
        private Map<String, Byte> stringByteMap;
        private Map<String, Double> stringDoubleMap;
        private Map<String, Float> stringFloatMap;
        private Map<String, Integer> stringIntegerMap;
        private Map<String, Long> stringLongMap;
        private Map<String, Short> stringShortMap;
        private Map<String, String> stringStringMap;
        private Map<String, ByteBuffer> stringBlobMap;
        private Map<String, Instant> stringTimestampMap;
        private Map<String, NestedUnion> stringUnionMap;
        private Map<String, NestedEnum> stringEnumMap;
        private Map<String, NestedIntEnum> stringIntEnumMap;
        private Map<String, NestedStruct> stringStructMap;

        private Builder() {}

        public Builder stringBooleanMap(Map<String, Boolean> stringBooleanMap) {
            this.stringBooleanMap = stringBooleanMap;
            return this;
        }

        public Builder stringBigDecimalMap(Map<String, BigDecimal> stringBigDecimalMap) {
            this.stringBigDecimalMap = stringBigDecimalMap;
            return this;
        }

        public Builder stringBigIntegerMap(Map<String, BigInteger> stringBigIntegerMap) {
            this.stringBigIntegerMap = stringBigIntegerMap;
            return this;
        }

        public Builder stringByteMap(Map<String, Byte> stringByteMap) {
            this.stringByteMap = stringByteMap;
            return this;
        }

        public Builder stringDoubleMap(Map<String, Double> stringDoubleMap) {
            this.stringDoubleMap = stringDoubleMap;
            return this;
        }

        public Builder stringFloatMap(Map<String, Float> stringFloatMap) {
            this.stringFloatMap = stringFloatMap;
            return this;
        }

        public Builder stringIntegerMap(Map<String, Integer> stringIntegerMap) {
            this.stringIntegerMap = stringIntegerMap;
            return this;
        }

        public Builder stringLongMap(Map<String, Long> stringLongMap) {
            this.stringLongMap = stringLongMap;
            return this;
        }

        public Builder stringShortMap(Map<String, Short> stringShortMap) {
            this.stringShortMap = stringShortMap;
            return this;
        }

        public Builder stringStringMap(Map<String, String> stringStringMap) {
            this.stringStringMap = stringStringMap;
            return this;
        }

        public Builder stringBlobMap(Map<String, ByteBuffer> stringBlobMap) {
            this.stringBlobMap = stringBlobMap;
            return this;
        }

        public Builder stringTimestampMap(Map<String, Instant> stringTimestampMap) {
            this.stringTimestampMap = stringTimestampMap;
            return this;
        }

        public Builder stringUnionMap(Map<String, NestedUnion> stringUnionMap) {
            this.stringUnionMap = stringUnionMap;
            return this;
        }

        public Builder stringEnumMap(Map<String, NestedEnum> stringEnumMap) {
            this.stringEnumMap = stringEnumMap;
            return this;
        }

        public Builder stringIntEnumMap(Map<String, NestedIntEnum> stringIntEnumMap) {
            this.stringIntEnumMap = stringIntEnumMap;
            return this;
        }

        public Builder stringStructMap(Map<String, NestedStruct> stringStructMap) {
            this.stringStructMap = stringStructMap;
            return this;
        }

        @Override
        public MapAllTypesInput build() {
            return new MapAllTypesInput(this);
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
                    case 0 -> builder.stringBooleanMap(SharedSerde.deserializeStringBooleanMap(member, de));
                    case 1 -> builder.stringBigDecimalMap(SharedSerde.deserializeStringBigDecimalMap(member, de));
                    case 2 -> builder.stringBigIntegerMap(SharedSerde.deserializeStringBigIntegerMap(member, de));
                    case 3 -> builder.stringByteMap(SharedSerde.deserializeStringByteMap(member, de));
                    case 4 -> builder.stringDoubleMap(SharedSerde.deserializeStringDoubleMap(member, de));
                    case 5 -> builder.stringFloatMap(SharedSerde.deserializeStringFloatMap(member, de));
                    case 6 -> builder.stringIntegerMap(SharedSerde.deserializeStringIntegerMap(member, de));
                    case 7 -> builder.stringLongMap(SharedSerde.deserializeStringLongMap(member, de));
                    case 8 -> builder.stringShortMap(SharedSerde.deserializeStringShortMap(member, de));
                    case 9 -> builder.stringStringMap(SharedSerde.deserializeStringStringMap(member, de));
                    case 10 -> builder.stringBlobMap(SharedSerde.deserializeStringBlobMap(member, de));
                    case 11 -> builder.stringTimestampMap(SharedSerde.deserializeStringTimestampMap(member, de));
                    case 12 -> builder.stringUnionMap(SharedSerde.deserializeStringUnionMap(member, de));
                    case 13 -> builder.stringEnumMap(SharedSerde.deserializeStringEnumMap(member, de));
                    case 14 -> builder.stringIntEnumMap(SharedSerde.deserializeStringIntEnumMap(member, de));
                    case 15 -> builder.stringStructMap(SharedSerde.deserializeStringStructMap(member, de));
                }
            }

        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.stringBooleanMap(this.stringBooleanMap);
        builder.stringBigDecimalMap(this.stringBigDecimalMap);
        builder.stringBigIntegerMap(this.stringBigIntegerMap);
        builder.stringByteMap(this.stringByteMap);
        builder.stringDoubleMap(this.stringDoubleMap);
        builder.stringFloatMap(this.stringFloatMap);
        builder.stringIntegerMap(this.stringIntegerMap);
        builder.stringLongMap(this.stringLongMap);
        builder.stringShortMap(this.stringShortMap);
        builder.stringStringMap(this.stringStringMap);
        builder.stringBlobMap(this.stringBlobMap);
        builder.stringTimestampMap(this.stringTimestampMap);
        builder.stringUnionMap(this.stringUnionMap);
        builder.stringEnumMap(this.stringEnumMap);
        builder.stringIntEnumMap(this.stringIntEnumMap);
        builder.stringStructMap(this.stringStructMap);
        return builder;
    }

}

