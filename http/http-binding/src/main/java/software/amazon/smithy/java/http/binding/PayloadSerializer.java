/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.io.ByteBufferOutputStream;
import software.amazon.smithy.java.io.datastream.DataStream;

final class PayloadSerializer implements ShapeSerializer {
    private static final byte[] NULL_BYTES = "null".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TRUE_BYTES = "true".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FALSE_BYTES = "false".getBytes(StandardCharsets.UTF_8);
    private final HttpBindingSerializer serializer;
    private final ShapeSerializer structSerializer;
    private final ByteBufferOutputStream outputStream;
    private boolean payloadWritten = false;

    PayloadSerializer(HttpBindingSerializer serializer, Codec codec) {
        this.serializer = serializer;
        this.outputStream = new ByteBufferOutputStream();
        this.structSerializer = codec.createSerializer(outputStream);
    }

    @Override
    public void writeDataStream(Schema schema, DataStream value) {
        payloadWritten = true;
        serializer.setHttpPayload(schema, value);
    }

    @Override
    public void writeEventStream(
            Schema schema,
            EventStream<? extends SerializableStruct> value
    ) {
        payloadWritten = true;
        serializer.setEventStream(value.asWriter());
    }

    private void write(byte[] bytes) {
        try {
            outputStream.write(bytes);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        TimestampFormatter formatter;
        var trait = schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
        if (trait != null) {
            formatter = TimestampFormatter.of(trait);
        } else {
            formatter = TimestampFormatter.Prelude.EPOCH_SECONDS;
        }
        write(formatter.writeString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        serializer.writePayloadContentType();
        structSerializer.writeDocument(schema, value);
    }

    @Override
    public void writeNull(Schema schema) {
        write(NULL_BYTES);
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        serializer.writePayloadContentType();
        structSerializer.writeStruct(schema, struct);
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        serializer.writePayloadContentType();
        structSerializer.writeList(schema, listState, size, consumer);
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        structSerializer.writeMap(schema, mapState, size, consumer);
    }

    // --- Specialized list method overrides ---

    @Override
    public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeStructList(schema, values, memberSchema);
    }

    @Override
    public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeStringList(schema, values, memberSchema);
    }

    @Override
    public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeBooleanList(schema, values, memberSchema);
    }

    @Override
    public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeByteList(schema, values, memberSchema);
    }

    @Override
    public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeShortList(schema, values, memberSchema);
    }

    @Override
    public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeIntegerList(schema, values, memberSchema);
    }

    @Override
    public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeLongList(schema, values, memberSchema);
    }

    @Override
    public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeFloatList(schema, values, memberSchema);
    }

    @Override
    public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeDoubleList(schema, values, memberSchema);
    }

    @Override
    public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeBigIntegerList(schema, values, memberSchema);
    }

    @Override
    public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeBigDecimalList(schema, values, memberSchema);
    }

    @Override
    public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeBlobList(schema, values, memberSchema);
    }

    @Override
    public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeTimestampList(schema, values, memberSchema);
    }

    @Override
    public void writeDocumentList(Schema schema, List<? extends Document> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeDocumentList(schema, values, memberSchema);
    }

    @Override
    public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeEnumList(schema, values, memberSchema);
    }

    @Override
    public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeIntEnumList(schema, values, memberSchema);
    }

    // --- Sparse list method overrides ---

    @Override
    public void writeSparseStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseStructList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseStringList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseBooleanList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseByteList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseShortList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseIntegerList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseLongList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseFloatList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseDoubleList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseBigIntegerList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseBigDecimalList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseBlobList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseTimestampList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseDocumentList(Schema schema, List<? extends Document> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseDocumentList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseEnumList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        structSerializer.writeSparseIntEnumList(schema, values, memberSchema);
    }

    @Override
    public void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeStructMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeStringMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeBooleanMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeByteMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeShortMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeIntegerMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeLongMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeFloatMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeDoubleMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeBigIntegerMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeBigDecimalMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeBlobMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeTimestampMap(Schema schema, Map<String, Instant> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeTimestampMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeDocumentMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeEnumMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeIntEnumMap(schema, values, keySchema, valueSchema);
    }

    // --- Sparse map method overrides ---

    @Override
    public void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeSparseStructMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeSparseStringMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeSparseBooleanMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeSparseByteMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeSparseShortMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeSparseIntegerMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeSparseLongMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeSparseFloatMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        structSerializer.writeSparseDoubleMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeSparseBigIntegerMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeSparseBigDecimalMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeSparseBlobMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeSparseTimestampMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeSparseDocumentMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeSparseEnumMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        structSerializer.writeSparseIntEnumMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        write(value ? TRUE_BYTES : FALSE_BYTES);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        outputStream.write(value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        write(Short.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        write(Integer.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeLong(Schema schema, long value) {
        write(Long.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        write(Float.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        write(Double.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        write(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        write(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeString(Schema schema, String value) {
        write(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        payloadWritten = true;
        serializer.setHttpPayload(schema, DataStream.ofBytes(value));
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        payloadWritten = true;
        serializer.setHttpPayload(schema, DataStream.ofByteBuffer(value));
    }

    @Override
    public void flush() {
        structSerializer.flush();
        try {
            outputStream.flush();
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void close() {
        structSerializer.close();
        try {
            outputStream.close();
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    public boolean isPayloadWritten() {
        return payloadWritten;
    }

    ByteBuffer toByteBuffer() {
        return outputStream.toByteBuffer();
    }
}
