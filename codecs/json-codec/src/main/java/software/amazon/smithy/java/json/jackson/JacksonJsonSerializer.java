/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.jackson;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.JsonFieldMapper;
import software.amazon.smithy.java.json.JsonSchemaExtensions;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.model.shapes.ShapeType;
import tools.jackson.core.JsonGenerator;

final class JacksonJsonSerializer implements ShapeSerializer {

    private JsonGenerator generator;
    private final JsonSettings settings;
    private final boolean useJsonName;
    private SerializeDocumentContents serializeDocumentContents;
    private final ShapeSerializer structSerializer = new JsonStructSerializer();
    private final MapSerializer mapSerializer = new JsonMapSerializer();

    JacksonJsonSerializer(
            JsonGenerator generator,
            JsonSettings settings
    ) {
        this.generator = generator;
        this.settings = settings;
        this.useJsonName = settings.fieldMapper() instanceof JsonFieldMapper.UseJsonNameTrait;
    }

    @Override
    public void flush() {
        try {
            generator.flush();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void close() {
        try {
            generator.close();
            generator = null;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        try {
            generator.writeBoolean(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        try {
            generator.writeNumber(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeShort(Schema schema, short value) {
        try {
            generator.writeNumber(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        try {
            generator.writeBinary(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        try {
            int len = value.remaining();
            if (value.hasArray()) {
                generator.writeBinary(value.array(), value.arrayOffset() + value.position(), len);
            } else {
                // don't disturb the mark on the existing buffer
                generator.writeBinary(ByteBufferUtils.byteBufferInputStream(value.duplicate()), len);
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        try {
            generator.writeNumber(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeLong(Schema schema, long value) {
        try {
            generator.writeNumber(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        try {
            if (Float.isFinite(value)) {
                int intValue = (int) value;
                if (value == (float) intValue) {
                    generator.writeNumber(intValue);
                } else {
                    generator.writeNumber(value);
                }
            } else if (Float.isNaN(value)) {
                generator.writeString("NaN");
            } else {
                generator.writeString(value > 0 ? "Infinity" : "-Infinity");
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        try {
            if (Double.isFinite(value)) {
                // Avoid writing 1.0 and instead write 1.
                long longValue = (long) value;
                if (value == (double) longValue) {
                    generator.writeNumber(longValue);
                } else {
                    generator.writeNumber(value);
                }
            } else if (Double.isNaN(value)) {
                generator.writeString("NaN");
            } else {
                generator.writeString(value > 0 ? "Infinity" : "-Infinity");
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        try {
            if (settings.useStringForArbitraryPrecision()) {
                generator.writeString(value.toString());
            } else {
                generator.writeNumber(value);
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        try {
            if (settings.useStringForArbitraryPrecision()) {
                generator.writeString(value.toString());
            } else {
                generator.writeNumber(value);
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeString(Schema schema, String value) {
        try {
            generator.writeString(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        settings.timestampResolver().resolve(schema).writeToSerializer(schema, value, this);
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        try {
            generator.writeStartObject();
            struct.serializeMembers(structSerializer);
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private void writeFieldName(Schema schema) throws Exception {
        var ext = schema.getExtension(JsonSchemaExtensions.KEY);
        generator.writeName(useJsonName ? ext.jsonFieldName() : ext.memberFieldName());
    }

    private final class JsonStructSerializer implements ShapeSerializer {
        @Override
        public void writeBoolean(Schema schema, boolean value) {
            try {
                writeFieldName(schema);
                generator.writeBoolean(value);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            try {
                writeFieldName(schema);
                generator.writeNumber(value);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void writeShort(Schema schema, short value) {
            try {
                writeFieldName(schema);
                generator.writeNumber(value);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            try {
                writeFieldName(schema);
                generator.writeNumber(value);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void writeLong(Schema schema, long value) {
            try {
                writeFieldName(schema);
                generator.writeNumber(value);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            try {
                writeFieldName(schema);
                JacksonJsonSerializer.this.writeFloat(schema, value);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            try {
                writeFieldName(schema);
                JacksonJsonSerializer.this.writeDouble(schema, value);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            try {
                writeFieldName(schema);
                JacksonJsonSerializer.this.writeBigInteger(schema, value);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            try {
                writeFieldName(schema);
                JacksonJsonSerializer.this.writeBigDecimal(schema, value);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void writeString(Schema schema, String value) {
            try {
                writeFieldName(schema);
                generator.writeString(value);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            try {
                writeFieldName(schema);
                JacksonJsonSerializer.this.writeBlob(schema, value);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            settings.timestampResolver().resolve(schema).writeToSerializer(schema, value, JacksonJsonSerializer.this);
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeStruct(schema, struct);
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeList(schema, listState, size, consumer);
        }

        @Override
        public void writeStructList(
                Schema schema,
                List<? extends SerializableStruct> values,
                Schema memberSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeStructList(schema, values, memberSchema);
        }

        @Override
        public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeStringList(schema, values, memberSchema);
        }

        @Override
        public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeBooleanList(schema, values, memberSchema);
        }

        @Override
        public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeByteList(schema, values, memberSchema);
        }

        @Override
        public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeShortList(schema, values, memberSchema);
        }

        @Override
        public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeLongList(schema, values, memberSchema);
        }

        @Override
        public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeFloatList(schema, values, memberSchema);
        }

        @Override
        public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeDoubleList(schema, values, memberSchema);
        }

        @Override
        public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeBigIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeBigDecimalList(schema, values, memberSchema);
        }

        @Override
        public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeBlobList(schema, values, memberSchema);
        }

        @Override
        public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeTimestampList(schema, values, memberSchema);
        }

        @Override
        public void writeDocumentList(
                Schema schema,
                List<? extends Document> values,
                Schema memberSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeDocumentList(schema, values, memberSchema);
        }

        @Override
        public void writeEnumList(
                Schema schema,
                List<? extends SmithyEnum> values,
                Schema memberSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeIntEnumList(
                Schema schema,
                List<? extends SmithyIntEnum> values,
                Schema memberSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeIntEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseStructList(
                Schema schema,
                List<? extends SerializableStruct> values,
                Schema memberSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseStructList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseStringList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseLongList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseDoubleList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseBlobList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseTimestampList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseDocumentList(
                Schema schema,
                List<? extends Document> values,
                Schema memberSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseDocumentList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseBooleanList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseByteList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseShortList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseFloatList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseBigIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseBigDecimalList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseEnumList(
                Schema schema,
                List<? extends SmithyEnum> values,
                Schema memberSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseIntEnumList(
                Schema schema,
                List<? extends SmithyIntEnum> values,
                Schema memberSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseIntEnumList(schema, values, memberSchema);
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeMap(schema, mapState, size, consumer);
        }

        @Override
        public void writeStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeStructMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeStringMap(
                Schema schema,
                Map<String, String> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeStringMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBooleanMap(
                Schema schema,
                Map<String, Boolean> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeBooleanMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeByteMap(
                Schema schema,
                Map<String, Byte> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeByteMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeShortMap(
                Schema schema,
                Map<String, Short> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeShortMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeIntegerMap(
                Schema schema,
                Map<String, Integer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeLongMap(
                Schema schema,
                Map<String, Long> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeLongMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeFloatMap(
                Schema schema,
                Map<String, Float> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeFloatMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeDoubleMap(
                Schema schema,
                Map<String, Double> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeDoubleMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeBigIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeBigDecimalMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBlobMap(
                Schema schema,
                Map<String, ByteBuffer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeBlobMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeTimestampMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeDocumentMap(
                Schema schema,
                Map<String, ? extends Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeDocumentMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeIntEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseStructMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseStringMap(
                Schema schema,
                Map<String, String> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseStringMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseIntegerMap(
                Schema schema,
                Map<String, Integer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseLongMap(
                Schema schema,
                Map<String, Long> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseLongMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseDoubleMap(
                Schema schema,
                Map<String, Double> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseDoubleMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBlobMap(
                Schema schema,
                Map<String, ByteBuffer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseBlobMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseTimestampMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseDocumentMap(
                Schema schema,
                Map<String, ? extends Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseDocumentMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBooleanMap(
                Schema schema,
                Map<String, Boolean> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseBooleanMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseByteMap(
                Schema schema,
                Map<String, Byte> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseByteMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseShortMap(
                Schema schema,
                Map<String, Short> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseShortMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseFloatMap(
                Schema schema,
                Map<String, Float> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseFloatMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseBigIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseBigDecimalMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeSparseIntEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeDocument(schema, value);
        }

        @Override
        public void writeNull(Schema schema) {
            try {
                writeFieldName(schema);
                generator.writeNull();
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        try {
            generator.writeStartArray();
            consumer.accept(listState, this);
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeStructList(
            Schema schema,
            List<? extends SerializableStruct> values,
            Schema memberSchema
    ) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeStruct(memberSchema, values.get(i));
                }
            } else {
                for (var v : values) {
                    writeStruct(memberSchema, v);
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeString(memberSchema, values.get(i));
                }
            } else {
                for (var v : values) {
                    writeString(memberSchema, v);
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeBoolean(memberSchema, values.get(i));
                }
            } else {
                for (var v : values) {
                    writeBoolean(memberSchema, v);
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeByte(memberSchema, values.get(i));
                }
            } else {
                for (var v : values) {
                    writeByte(memberSchema, v);
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeShort(memberSchema, values.get(i));
                }
            } else {
                for (var v : values) {
                    writeShort(memberSchema, v);
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeInteger(memberSchema, values.get(i));
                }
            } else {
                for (var v : values) {
                    writeInteger(memberSchema, v);
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeLong(memberSchema, values.get(i));
                }
            } else {
                for (var v : values) {
                    writeLong(memberSchema, v);
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeFloat(memberSchema, values.get(i));
                }
            } else {
                for (var v : values) {
                    writeFloat(memberSchema, v);
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeDouble(memberSchema, values.get(i));
                }
            } else {
                for (var v : values) {
                    writeDouble(memberSchema, v);
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeBigInteger(memberSchema, values.get(i));
                }
            } else {
                for (var v : values) {
                    writeBigInteger(memberSchema, v);
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeBigDecimal(memberSchema, values.get(i));
                }
            } else {
                for (var v : values) {
                    writeBigDecimal(memberSchema, v);
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeBlob(memberSchema, values.get(i));
                }
            } else {
                for (var v : values) {
                    writeBlob(memberSchema, v);
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeTimestamp(memberSchema, values.get(i));
                }
            } else {
                for (var v : values) {
                    writeTimestamp(memberSchema, v);
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeDocumentList(
            Schema schema,
            List<? extends Document> values,
            Schema memberSchema
    ) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeDocument(memberSchema, values.get(i));
                }
            } else {
                for (var v : values) {
                    writeDocument(memberSchema, v);
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeEnumList(
            Schema schema,
            List<? extends SmithyEnum> values,
            Schema memberSchema
    ) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeString(memberSchema, values.get(i).getValue());
                }
            } else {
                for (var v : values) {
                    writeString(memberSchema, v.getValue());
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeIntEnumList(
            Schema schema,
            List<? extends SmithyIntEnum> values,
            Schema memberSchema
    ) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    writeInteger(memberSchema, values.get(i).getValue());
                }
            } else {
                for (var v : values) {
                    writeInteger(memberSchema, v.getValue());
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    // --- Sparse list methods ---

    @Override
    public void writeSparseStructList(
            Schema schema,
            List<? extends SerializableStruct> values,
            Schema memberSchema
    ) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeStruct(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeStruct(memberSchema, v);
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeString(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeString(memberSchema, v);
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeInteger(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeInteger(memberSchema, v);
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeLong(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeLong(memberSchema, v);
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeDouble(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeDouble(memberSchema, v);
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeBlob(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeBlob(memberSchema, v);
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeTimestamp(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeTimestamp(memberSchema, v);
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseDocumentList(
            Schema schema,
            List<? extends Document> values,
            Schema memberSchema
    ) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeDocument(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeDocument(memberSchema, v);
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeBoolean(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeBoolean(memberSchema, v);
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeByte(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeByte(memberSchema, v);
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeShort(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeShort(memberSchema, v);
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeFloat(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeFloat(memberSchema, v);
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeBigInteger(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeBigInteger(memberSchema, v);
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeBigDecimal(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeBigDecimal(memberSchema, v);
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseEnumList(
            Schema schema,
            List<? extends SmithyEnum> values,
            Schema memberSchema
    ) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeString(memberSchema, v.getValue());
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeString(memberSchema, v.getValue());
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseIntEnumList(
            Schema schema,
            List<? extends SmithyIntEnum> values,
            Schema memberSchema
    ) {
        try {
            generator.writeStartArray();
            if (values instanceof RandomAccess) {
                for (int i = 0, sz = values.size(); i < sz; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeInteger(memberSchema, v.getValue());
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        writeNull(memberSchema);
                    } else {
                        writeInteger(memberSchema, v.getValue());
                    }
                }
            }
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        try {
            generator.writeStartObject();
            consumer.accept(mapState, mapSerializer);
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private final class JsonMapSerializer implements MapSerializer {
        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            try {
                generator.writeName(key);
                valueSerializer.accept(state, JacksonJsonSerializer.this);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }
    }

    // --- Specialized map methods ---

    @Override
    public void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeStruct(valueSchema, entry.getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeStringMap(
            Schema schema,
            Map<String, String> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeString(valueSchema, entry.getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeBoolean(valueSchema, entry.getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeByte(valueSchema, entry.getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeShortMap(
            Schema schema,
            Map<String, Short> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeShort(valueSchema, entry.getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeInteger(valueSchema, entry.getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeLong(valueSchema, entry.getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeFloatMap(
            Schema schema,
            Map<String, Float> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeFloat(valueSchema, entry.getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeDoubleMap(
            Schema schema,
            Map<String, Double> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeDouble(valueSchema, entry.getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeBigInteger(valueSchema, entry.getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeBigDecimal(valueSchema, entry.getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeBlob(valueSchema, entry.getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeTimestamp(valueSchema, entry.getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeDocument(valueSchema, entry.getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeString(valueSchema, entry.getValue().getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                writeInteger(valueSchema, entry.getValue().getValue());
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    // --- Sparse map methods ---

    @Override
    public void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeStruct(valueSchema, entry.getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseStringMap(
            Schema schema,
            Map<String, String> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeString(valueSchema, entry.getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeInteger(valueSchema, entry.getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseLongMap(
            Schema schema,
            Map<String, Long> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeLong(valueSchema, entry.getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseDoubleMap(
            Schema schema,
            Map<String, Double> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeDouble(valueSchema, entry.getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeBlob(valueSchema, entry.getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeTimestamp(valueSchema, entry.getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeDocument(valueSchema, entry.getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeBoolean(valueSchema, entry.getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseByteMap(
            Schema schema,
            Map<String, Byte> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeByte(valueSchema, entry.getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseShortMap(
            Schema schema,
            Map<String, Short> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeShort(valueSchema, entry.getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseFloatMap(
            Schema schema,
            Map<String, Float> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeFloat(valueSchema, entry.getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeBigInteger(valueSchema, entry.getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeBigDecimal(valueSchema, entry.getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeString(valueSchema, entry.getValue().getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        try {
            generator.writeStartObject();
            for (var entry : values.entrySet()) {
                generator.writeName(entry.getKey());
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeInteger(valueSchema, entry.getValue().getValue());
                }
            }
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        // Document values in JSON are serialized inline by receiving the data model contents of the document.
        if (value.type() != ShapeType.STRUCTURE) {
            value.serializeContents(this);
        } else {
            if (serializeDocumentContents == null) {
                serializeDocumentContents = new SerializeDocumentContents(this);
            }
            value.serializeContents(serializeDocumentContents);
        }
    }

    private static final class SerializeDocumentContents extends SpecificShapeSerializer {
        private final JacksonJsonSerializer parent;

        SerializeDocumentContents(JacksonJsonSerializer parent) {
            this.parent = parent;
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            try {
                parent.generator.writeStartObject();
                if (parent.settings.serializeTypeInDocuments()) {
                    parent.generator.writeStringProperty("__type", schema.id().toString());
                }
                struct.serializeMembers(parent.structSerializer);
                parent.generator.writeEndObject();
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }
    }

    @Override
    public void writeNull(Schema schema) {
        try {
            generator.writeNull();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }
}
