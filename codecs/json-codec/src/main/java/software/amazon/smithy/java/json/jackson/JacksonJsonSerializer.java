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
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            try {
                writeFieldName(schema);
            } catch (Exception e) {
                throw new SerializationException(e);
            }
            JacksonJsonSerializer.this.writeMap(schema, mapState, size, consumer);
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

        @Override
        public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeBooleanList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseBooleanList(schema, values, memberSchema);
        }

        @Override
        public void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeBooleanMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBooleanMap(
                Schema schema,
                Map<String, Boolean> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseBooleanMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeByteList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseByteList(schema, values, memberSchema);
        }

        @Override
        public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeByteMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseByteMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeShortList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseShortList(schema, values, memberSchema);
        }

        @Override
        public void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeShortMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseShortMap(
                Schema schema,
                Map<String, Short> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseShortMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseIntegerMap(
                Schema schema,
                Map<String, Integer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeLongList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseLongList(schema, values, memberSchema);
        }

        @Override
        public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeLongMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseLongMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeFloatList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseFloatList(schema, values, memberSchema);
        }

        @Override
        public void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeFloatMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseFloatMap(
                Schema schema,
                Map<String, Float> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseFloatMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeDoubleList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseDoubleList(schema, values, memberSchema);
        }

        @Override
        public void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeDoubleMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseDoubleMap(
                Schema schema,
                Map<String, Double> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseDoubleMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeBigIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseBigIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeBigIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseBigIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeBigDecimalList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseBigDecimalList(schema, values, memberSchema);
        }

        @Override
        public void writeBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeBigDecimalMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseBigDecimalMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeStringList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseStringList(schema, values, memberSchema);
        }

        @Override
        public void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeStringMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseStringMap(
                Schema schema,
                Map<String, String> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseStringMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeBlobList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseBlobList(schema, values, memberSchema);
        }

        @Override
        public void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeBlobMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBlobMap(
                Schema schema,
                Map<String, ByteBuffer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseBlobMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeTimestampList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseTimestampList(schema, values, memberSchema);
        }

        @Override
        public void writeTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeTimestampMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseTimestampMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeDocumentList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseDocumentList(schema, values, memberSchema);
        }

        @Override
        public void writeDocumentMap(
                Schema schema,
                Map<String, Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeDocumentMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseDocumentMap(
                Schema schema,
                Map<String, Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseDocumentMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeIntEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseIntEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeIntEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseIntEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeStructList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseStructList(
                Schema schema,
                List<? extends SerializableStruct> values,
                Schema memberSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseStructList(schema, values, memberSchema);
        }

        @Override
        public void writeStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeStructMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameChecked(schema);
            JacksonJsonSerializer.this.writeSparseStructMap(schema, values, keySchema, valueSchema);
        }
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        beginArray();
        consumer.accept(listState, this);
        endArray();
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        beginObject();
        consumer.accept(mapState, mapSerializer);
        endObject();
    }

    private void beginArray() {
        try {
            generator.writeStartArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private void endArray() {
        try {
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private void beginObject() {
        try {
            generator.writeStartObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private void endObject() {
        try {
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private void writeName(String key) {
        try {
            generator.writeName(key);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private void writeFieldNameChecked(Schema schema) {
        try {
            writeFieldName(schema);
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

    @Override
    public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeBoolean(memberSchema, v);
            }
        } else {
            for (var v : values) {
                this.writeBoolean(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeBoolean(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeBoolean(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeBoolean(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeBoolean(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeByte(memberSchema, v);
            }
        } else {
            for (var v : values) {
                this.writeByte(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeByte(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeByte(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeByte(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeByte(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeShort(memberSchema, v);
            }
        } else {
            for (var v : values) {
                this.writeShort(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeShort(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeShort(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeShort(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeShort(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeInteger(memberSchema, v);
            }
        } else {
            for (var v : values) {
                this.writeInteger(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeInteger(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeInteger(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeInteger(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeInteger(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeLong(memberSchema, v);
            }
        } else {
            for (var v : values) {
                this.writeLong(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeLong(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeLong(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeLong(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeLong(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeFloat(memberSchema, v);
            }
        } else {
            for (var v : values) {
                this.writeFloat(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeFloat(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeFloat(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeFloat(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeFloat(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeDouble(memberSchema, v);
            }
        } else {
            for (var v : values) {
                this.writeDouble(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeDouble(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeDouble(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeDouble(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeDouble(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeBigInteger(memberSchema, v);
            }
        } else {
            for (var v : values) {
                this.writeBigInteger(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeBigInteger(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeBigInteger(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeBigInteger(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeBigInteger(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeBigDecimal(memberSchema, v);
            }
        } else {
            for (var v : values) {
                this.writeBigDecimal(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeBigDecimal(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeBigDecimal(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeBigDecimal(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeBigDecimal(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeString(memberSchema, v);
            }
        } else {
            for (var v : values) {
                this.writeString(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeString(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeString(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeString(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeString(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeBlob(memberSchema, v);
            }
        } else {
            for (var v : values) {
                this.writeBlob(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeBlob(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeBlob(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeBlob(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeBlob(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeTimestamp(memberSchema, v);
            }
        } else {
            for (var v : values) {
                this.writeTimestamp(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeTimestamp(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeTimestamp(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeTimestampMap(Schema schema, Map<String, Instant> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeTimestamp(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeTimestamp(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeDocument(memberSchema, v);
            }
        } else {
            for (var v : values) {
                this.writeDocument(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeDocument(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeDocument(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeDocumentMap(Schema schema, Map<String, Document> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeDocument(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseDocumentMap(
            Schema schema,
            Map<String, Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeDocument(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeString(memberSchema, v.getValue());
            }
        } else {
            for (var v : values) {
                this.writeString(memberSchema, v.getValue());
            }
        }
        endArray();
    }

    @Override
    public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeString(memberSchema, v.getValue());
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeString(memberSchema, v.getValue());
                }
            }
        }
        endArray();
    }

    @Override
    public void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeString(valueSchema, v.getValue());

        });
        endObject();
    }

    @Override
    public void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeString(valueSchema, v.getValue());
            }

        });
        endObject();
    }

    @Override
    public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeInteger(memberSchema, v.getValue());
            }
        } else {
            for (var v : values) {
                this.writeInteger(memberSchema, v.getValue());
            }
        }
        endArray();
    }

    @Override
    public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeInteger(memberSchema, v.getValue());
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeInteger(memberSchema, v.getValue());
                }
            }
        }
        endArray();
    }

    @Override
    public void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeInteger(valueSchema, v.getValue());

        });
        endObject();
    }

    @Override
    public void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeInteger(valueSchema, v.getValue());
            }

        });
        endObject();
    }

    @Override
    public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                this.writeStruct(memberSchema, v);
            }
        } else {
            for (var v : values) {
                this.writeStruct(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeStruct(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    this.writeNull(memberSchema);
                } else {
                    this.writeStruct(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            this.writeStruct(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeName(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeStruct(valueSchema, v);
            }

        });
        endObject();
    }
}
