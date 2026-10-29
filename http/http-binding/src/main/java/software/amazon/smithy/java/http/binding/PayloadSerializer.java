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
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.MemberSubsetCodec;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.io.ByteBufferOutputStream;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Buffers the single {@code @httpPayload}-bound member into a {@link ByteBuffer}.
 *
 * <p>Because a payload binds exactly one member, this serializer receives exactly one top-level
 * write call.
 *
 * <ul>
 *   <li>blob / data stream / event stream payloads hand the body off by reference via
 *       {@link HttpBindingSerializer#setHttpPayload};</li>
 *   <li>scalar payloads (string, number, boolean, timestamp) write their bytes directly into a
 *       lazily-allocated {@link ByteBufferOutputStream};</li>
 *   <li>structured payloads (struct, document, list, map) are serialized through the pooled
 *       {@link Codec#serialize(SerializableShape)} path, which for JSON borrows a serializer from a
 *       striped pool instead of allocating one.</li>
 * </ul>
 */
final class PayloadSerializer implements ShapeSerializer {
    private static final byte[] NULL_BYTES = "null".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TRUE_BYTES = "true".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FALSE_BYTES = "false".getBytes(StandardCharsets.UTF_8);
    private final HttpBindingSerializer serializer;
    private final Codec codec;
    private boolean payloadWritten = false;
    private ByteBuffer codecResult;
    private ByteBufferOutputStream scalarStream;

    PayloadSerializer(HttpBindingSerializer serializer, Codec codec) {
        this.serializer = serializer;
        this.codec = codec;
    }

    @Override
    public void writeDataStream(Schema schema, DataStream value) {
        payloadWritten = true;
        serializer.setHttpPayload(schema, value);
    }

    @Override
    public void writeEventStream(Schema schema, EventStream<? extends SerializableStruct> value) {
        payloadWritten = true;
        serializer.setEventStream(value.asWriter());
    }

    private ByteBufferOutputStream scalarStream() {
        if (scalarStream == null) {
            scalarStream = new ByteBufferOutputStream();
        }
        return scalarStream;
    }

    private void write(byte[] bytes) {
        try {
            scalarStream().write(bytes);
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
        codecResult = codec.serialize(ser -> ser.writeDocument(schema, value));
    }

    @Override
    public void writeNull(Schema schema) {
        write(NULL_BYTES);
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        serializer.writePayloadContentType();
        // The fast path serializes with struct.schema(), so only take it when that matches the passed schema.
        var target = schema.isMember() ? schema.memberTarget() : schema;
        codecResult = codec instanceof MemberSubsetCodec && struct.schema() == target
                ? codec.serialize(struct)
                : codec.serialize(ser -> ser.writeStruct(schema, struct));
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeList(schema, listState, size, consumer));
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeMap(schema, mapState, size, consumer));
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        write(value ? TRUE_BYTES : FALSE_BYTES);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        scalarStream().write(value);
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

    public boolean isPayloadWritten() {
        return payloadWritten;
    }

    ByteBuffer toByteBuffer() {
        if (codecResult != null) {
            return codecResult;
        } else if (scalarStream != null) {
            return scalarStream.toByteBuffer();
        } else {
            return ByteBuffer.allocate(0);
        }
    }

    @Override
    public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeBooleanList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseBooleanList(schema, values, memberSchema));
    }

    @Override
    public void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeBooleanMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseBooleanMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeByteList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseByteList(schema, values, memberSchema));
    }

    @Override
    public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeByteMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseByteMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeShortList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseShortList(schema, values, memberSchema));
    }

    @Override
    public void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeShortMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseShortMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeIntegerList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseIntegerList(schema, values, memberSchema));
    }

    @Override
    public void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeIntegerMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseIntegerMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeLongList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseLongList(schema, values, memberSchema));
    }

    @Override
    public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeLongMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseLongMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeFloatList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseFloatList(schema, values, memberSchema));
    }

    @Override
    public void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeFloatMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseFloatMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeDoubleList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseDoubleList(schema, values, memberSchema));
    }

    @Override
    public void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeDoubleMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseDoubleMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeBigIntegerList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseBigIntegerList(schema, values, memberSchema));
    }

    @Override
    public void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeBigIntegerMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseBigIntegerMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeBigDecimalList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseBigDecimalList(schema, values, memberSchema));
    }

    @Override
    public void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeBigDecimalMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseBigDecimalMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeStringList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseStringList(schema, values, memberSchema));
    }

    @Override
    public void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeStringMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseStringMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeBlobList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseBlobList(schema, values, memberSchema));
    }

    @Override
    public void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeBlobMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseBlobMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeTimestampList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseTimestampList(schema, values, memberSchema));
    }

    @Override
    public void writeTimestampMap(Schema schema, Map<String, Instant> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeTimestampMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseTimestampMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeDocumentList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseDocumentList(schema, values, memberSchema));
    }

    @Override
    public void writeDocumentMap(Schema schema, Map<String, Document> values, Schema keySchema, Schema valueSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeDocumentMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseDocumentMap(
            Schema schema,
            Map<String, Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseDocumentMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeEnumList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseEnumList(schema, values, memberSchema));
    }

    @Override
    public void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeEnumMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseEnumMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeIntEnumList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseIntEnumList(schema, values, memberSchema));
    }

    @Override
    public void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeIntEnumMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseIntEnumMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeStructList(schema, values, memberSchema));
    }

    @Override
    public void writeSparseStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseStructList(schema, values, memberSchema));
    }

    @Override
    public void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeStructMap(schema, values, keySchema, valueSchema));
    }

    @Override
    public void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeSparseStructMap(schema, values, keySchema, valueSchema));
    }
}
