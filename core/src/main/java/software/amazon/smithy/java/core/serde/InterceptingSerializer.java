/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Intercepts serialization before and after each write.
 *
 * <p>{@link #before(Schema)} is responsible for returning the {@link ShapeSerializer} used to actually perform a
 * write, making the method act as a router between serializers, predicated on the given schema.
 */
public abstract class InterceptingSerializer implements ShapeSerializer {

    /**
     * Called before writing and returns the writer to delegate to.
     *
     * @param schema Schema of the shape about to be written.
     * @return the serializer that is to be used to write this schema.
     */
    protected abstract ShapeSerializer before(Schema schema);

    /**
     * Called after the delegated serializer is called.
     *
     * @param schema Schema that was serialized.
     */
    protected void after(Schema schema) {}

    @Override
    public final void writeStruct(Schema schema, SerializableStruct struct) {
        before(schema).writeStruct(schema, struct);
        after(schema);
    }

    @Override
    public final <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        before(schema).writeList(schema, listState, size, consumer);
        after(schema);
    }

    @Override
    public final void writeStructList(
            Schema schema,
            List<? extends SerializableStruct> values,
            Schema memberSchema
    ) {
        before(schema).writeStructList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        before(schema).writeStringList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        before(schema).writeBooleanList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        before(schema).writeByteList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        before(schema).writeShortList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        before(schema).writeIntegerList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        before(schema).writeLongList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        before(schema).writeFloatList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        before(schema).writeDoubleList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeBigIntegerList(
            Schema schema,
            List<BigInteger> values,
            Schema memberSchema
    ) {
        before(schema).writeBigIntegerList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeBigDecimalList(
            Schema schema,
            List<BigDecimal> values,
            Schema memberSchema
    ) {
        before(schema).writeBigDecimalList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        before(schema).writeBlobList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeTimestampList(
            Schema schema,
            List<Instant> values,
            Schema memberSchema
    ) {
        before(schema).writeTimestampList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeDocumentList(
            Schema schema,
            List<? extends Document> values,
            Schema memberSchema
    ) {
        before(schema).writeDocumentList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeEnumList(
            Schema schema,
            List<? extends SmithyEnum> values,
            Schema memberSchema
    ) {
        before(schema).writeEnumList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeIntEnumList(
            Schema schema,
            List<? extends SmithyIntEnum> values,
            Schema memberSchema
    ) {
        before(schema).writeIntEnumList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseStructList(
            Schema schema,
            List<? extends SerializableStruct> values,
            Schema memberSchema
    ) {
        before(schema).writeSparseStructList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        before(schema).writeSparseStringList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        before(schema).writeSparseBooleanList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        before(schema).writeSparseByteList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        before(schema).writeSparseShortList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        before(schema).writeSparseIntegerList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        before(schema).writeSparseLongList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        before(schema).writeSparseFloatList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        before(schema).writeSparseDoubleList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        before(schema).writeSparseBigIntegerList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        before(schema).writeSparseBigDecimalList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        before(schema).writeSparseBlobList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        before(schema).writeSparseTimestampList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseDocumentList(
            Schema schema,
            List<? extends Document> values,
            Schema memberSchema
    ) {
        before(schema).writeSparseDocumentList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseEnumList(
            Schema schema,
            List<? extends SmithyEnum> values,
            Schema memberSchema
    ) {
        before(schema).writeSparseEnumList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeSparseIntEnumList(
            Schema schema,
            List<? extends SmithyIntEnum> values,
            Schema memberSchema
    ) {
        before(schema).writeSparseIntEnumList(schema, values, memberSchema);
        after(schema);
    }

    @Override
    public final void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeStructMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        before(schema).writeStringMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeBooleanMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        before(schema).writeByteMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        before(schema).writeShortMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeIntegerMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        before(schema).writeLongMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        before(schema).writeFloatMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        before(schema).writeDoubleMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeBigIntegerMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeBigDecimalMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeBlobMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeTimestampMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeDocumentMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeEnumMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeIntEnumMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseStructMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseStringMap(
            Schema schema,
            Map<String, String> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseStringMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseIntegerMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseLongMap(
            Schema schema,
            Map<String, Long> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseLongMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseDoubleMap(
            Schema schema,
            Map<String, Double> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseDoubleMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseBlobMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseTimestampMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseDocumentMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseBooleanMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseByteMap(
            Schema schema,
            Map<String, Byte> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseByteMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseShortMap(
            Schema schema,
            Map<String, Short> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseShortMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseFloatMap(
            Schema schema,
            Map<String, Float> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseFloatMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseBigIntegerMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseBigDecimalMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseEnumMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        before(schema).writeSparseIntEnumMap(schema, values, keySchema, valueSchema);
        after(schema);
    }

    @Override
    public final <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        before(schema).writeMap(schema, mapState, size, consumer);
        after(schema);
    }

    @Override
    public final void writeBoolean(Schema schema, boolean value) {
        before(schema).writeBoolean(schema, value);
        after(schema);
    }

    @Override
    public final void writeShort(Schema schema, short value) {
        before(schema).writeShort(schema, value);
        after(schema);
    }

    @Override
    public final void writeByte(Schema schema, byte value) {
        before(schema).writeByte(schema, value);
        after(schema);
    }

    @Override
    public final void writeInteger(Schema schema, int value) {
        before(schema).writeInteger(schema, value);
        after(schema);
    }

    @Override
    public final void writeLong(Schema schema, long value) {
        before(schema).writeLong(schema, value);
        after(schema);
    }

    @Override
    public final void writeFloat(Schema schema, float value) {
        before(schema).writeFloat(schema, value);
        after(schema);
    }

    @Override
    public final void writeDouble(Schema schema, double value) {
        before(schema).writeDouble(schema, value);
        after(schema);
    }

    @Override
    public final void writeBigInteger(Schema schema, BigInteger value) {
        before(schema).writeBigInteger(schema, value);
        after(schema);
    }

    @Override
    public final void writeBigDecimal(Schema schema, BigDecimal value) {
        before(schema).writeBigDecimal(schema, value);
        after(schema);
    }

    @Override
    public final void writeString(Schema schema, String value) {
        before(schema).writeString(schema, value);
        after(schema);
    }

    @Override
    public final void writeBlob(Schema schema, ByteBuffer value) {
        before(schema).writeBlob(schema, value);
        after(schema);
    }

    @Override
    public void writeDataStream(Schema schema, DataStream value) {
        before(schema).writeDataStream(schema, value);
        after(schema);
    }

    @Override
    public void writeEventStream(Schema schema, EventStream<? extends SerializableStruct> value) {
        before(schema).writeEventStream(schema, value);
        after(schema);
    }

    @Override
    public final void writeTimestamp(Schema schema, Instant value) {
        before(schema).writeTimestamp(schema, value);
        after(schema);
    }

    @Override
    public final void writeDocument(Schema schema, Document value) {
        before(schema).writeDocument(schema, value);
        after(schema);
    }

    @Override
    public final void writeNull(Schema schema) {
        before(schema).writeNull(schema);
        after(schema);
    }
}
