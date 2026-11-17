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
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * Helper class that can be used to serialize content between values.
 *
 * <p>This class allows for easily injecting a separator between list values.
 */
public final class ListSerializer implements ShapeSerializer {

    private final ShapeSerializer delegate;
    private final IntConsumer beforeEachValue;
    private int position = 0;

    /**
     * @param delegate        Delegate that does the actual value serialization.
     * @param beforeEachValue Invoked before each value and given the current position in the list.
     */
    public ListSerializer(ShapeSerializer delegate, IntConsumer beforeEachValue) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is null");
        this.beforeEachValue = Objects.requireNonNull(beforeEachValue, "beforeEachValue is null");
    }

    private void beforeWrite() {
        beforeEachValue.accept(position++);
    }

    /**
     * Get the current index of the serializer where the next element would be written.
     *
     * @return the current index.
     */
    public int position() {
        return position;
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        beforeWrite();
        delegate.writeStruct(schema, struct);
    }

    @Override
    public <T> void writeList(Schema schema, T state, int size, BiConsumer<T, ShapeSerializer> consumer) {
        beforeWrite();
        delegate.writeList(schema, state, size, consumer);
    }

    @Override
    public <T> void writeMap(Schema schema, T state, int size, BiConsumer<T, MapSerializer> consumer) {
        beforeWrite();
        delegate.writeMap(schema, state, size, consumer);
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        beforeWrite();
        delegate.writeBoolean(schema, value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        beforeWrite();
        delegate.writeShort(schema, value);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        beforeWrite();
        delegate.writeByte(schema, value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        beforeWrite();
        delegate.writeInteger(schema, value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        beforeWrite();
        delegate.writeLong(schema, value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        beforeWrite();
        delegate.writeFloat(schema, value);
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        beforeWrite();
        delegate.writeDouble(schema, value);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        beforeWrite();
        delegate.writeBigInteger(schema, value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        beforeWrite();
        delegate.writeBigDecimal(schema, value);
    }

    @Override
    public void writeString(Schema schema, String value) {
        beforeWrite();
        delegate.writeString(schema, value);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        beforeWrite();
        delegate.writeBlob(schema, value);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        beforeWrite();
        delegate.writeTimestamp(schema, value);
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        beforeWrite();
        delegate.writeDocument(schema, value);
    }

    @Override
    public void writeNull(Schema schema) {
        beforeWrite();
        delegate.writeNull(schema);
    }

    @Override
    public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeBooleanList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseBooleanList(schema, values, memberSchema);
    }

    @Override
    public void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeBooleanMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeSparseBooleanMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeByteList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseByteList(schema, values, memberSchema);
    }

    @Override
    public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeByteMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeSparseByteMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeShortList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseShortList(schema, values, memberSchema);
    }

    @Override
    public void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeShortMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeSparseShortMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeIntegerList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseIntegerList(schema, values, memberSchema);
    }

    @Override
    public void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeIntegerMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeSparseIntegerMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeLongList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseLongList(schema, values, memberSchema);
    }

    @Override
    public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeLongMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeSparseLongMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeFloatList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseFloatList(schema, values, memberSchema);
    }

    @Override
    public void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeFloatMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeSparseFloatMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeDoubleList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseDoubleList(schema, values, memberSchema);
    }

    @Override
    public void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeDoubleMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeSparseDoubleMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeBigIntegerList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseBigIntegerList(schema, values, memberSchema);
    }

    @Override
    public void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeBigIntegerMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeSparseBigIntegerMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeBigDecimalList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseBigDecimalList(schema, values, memberSchema);
    }

    @Override
    public void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeBigDecimalMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeSparseBigDecimalMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeStringList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseStringList(schema, values, memberSchema);
    }

    @Override
    public void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeStringMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeSparseStringMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeBlobList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseBlobList(schema, values, memberSchema);
    }

    @Override
    public void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeBlobMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeSparseBlobMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeTimestampList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseTimestampList(schema, values, memberSchema);
    }

    @Override
    public void writeTimestampMap(Schema schema, Map<String, Instant> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeTimestampMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeSparseTimestampMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeDocumentList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseDocumentList(schema, values, memberSchema);
    }

    @Override
    public void writeDocumentMap(Schema schema, Map<String, Document> values, Schema keySchema, Schema valueSchema) {
        beforeWrite();
        delegate.writeDocumentMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseDocumentMap(
            Schema schema,
            Map<String, Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeSparseDocumentMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeEnumList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseEnumList(schema, values, memberSchema);
    }

    @Override
    public void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeEnumMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeSparseEnumMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeIntEnumList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseIntEnumList(schema, values, memberSchema);
    }

    @Override
    public void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeIntEnumMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeSparseIntEnumMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeStructList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        beforeWrite();
        delegate.writeSparseStructList(schema, values, memberSchema);
    }

    @Override
    public void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeStructMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beforeWrite();
        delegate.writeSparseStructMap(schema, values, keySchema, valueSchema);
    }
}
