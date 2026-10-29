/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.io.OutputStream;
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
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.json.smithy.SmithyGeneratedJsonSerde;

final class CodegenShapeSerializer implements ShapeSerializer {
    private final OutputStream sink;
    private final JsonSerdeProvider delegateProvider;
    private final SmithyGeneratedJsonSerde generated;
    private final JsonSettings settings;
    private ShapeSerializer delegate;

    CodegenShapeSerializer(
            OutputStream sink,
            JsonSerdeProvider delegateProvider,
            SmithyGeneratedJsonSerde generated,
            JsonSettings settings
    ) {
        this.sink = sink;
        this.delegateProvider = delegateProvider;
        this.generated = generated;
        this.settings = settings;
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        if (!generated.serializeTo(struct, sink, settings)) {
            delegate().writeStruct(schema, struct);
        }
    }

    @Override
    public <T> void writeList(Schema schema, T state, int size, BiConsumer<T, ShapeSerializer> consumer) {
        delegate().writeList(schema, state, size, consumer);
    }

    @Override
    public <T> void writeMap(Schema schema, T state, int size, BiConsumer<T, MapSerializer> consumer) {
        delegate().writeMap(schema, state, size, consumer);
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        delegate().writeBoolean(schema, value);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        delegate().writeByte(schema, value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        delegate().writeShort(schema, value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        delegate().writeInteger(schema, value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        delegate().writeLong(schema, value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        delegate().writeFloat(schema, value);
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        delegate().writeDouble(schema, value);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        delegate().writeBigInteger(schema, value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        delegate().writeBigDecimal(schema, value);
    }

    @Override
    public void writeString(Schema schema, String value) {
        delegate().writeString(schema, value);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        delegate().writeBlob(schema, value);
    }

    @Override
    public void writeDataStream(Schema schema, DataStream value) {
        delegate().writeDataStream(schema, value);
    }

    @Override
    public void writeEventStream(Schema schema, EventStream<? extends SerializableStruct> value) {
        delegate().writeEventStream(schema, value);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        delegate().writeTimestamp(schema, value);
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        delegate().writeDocument(schema, value);
    }

    @Override
    public void writeNull(Schema schema) {
        delegate().writeNull(schema);
    }

    @Override
    public void flush() {
        if (delegate != null) {
            delegate.flush();
        }
    }

    @Override
    public void close() {
        if (delegate != null) {
            delegate.close();
        }
    }

    private ShapeSerializer delegate() {
        if (settings.strictRuntimeCodegen()) {
            throw new IllegalStateException(
                    "Strict JSON runtime codegen cannot fall back to the streaming dispatch serializer");
        }
        if (delegate == null) {
            delegate = delegateProvider.newSerializer(sink, settings);
        }
        return delegate;
    }

    @Override
    public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        delegate().writeBooleanList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        delegate().writeSparseBooleanList(schema, values, memberSchema);
    }

    @Override
    public void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
        delegate().writeBooleanMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeSparseBooleanMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        delegate().writeByteList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        delegate().writeSparseByteList(schema, values, memberSchema);
    }

    @Override
    public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        delegate().writeByteMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        delegate().writeSparseByteMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        delegate().writeShortList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        delegate().writeSparseShortList(schema, values, memberSchema);
    }

    @Override
    public void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        delegate().writeShortMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        delegate().writeSparseShortMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        delegate().writeIntegerList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        delegate().writeSparseIntegerList(schema, values, memberSchema);
    }

    @Override
    public void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
        delegate().writeIntegerMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeSparseIntegerMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        delegate().writeLongList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        delegate().writeSparseLongList(schema, values, memberSchema);
    }

    @Override
    public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        delegate().writeLongMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        delegate().writeSparseLongMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        delegate().writeFloatList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        delegate().writeSparseFloatList(schema, values, memberSchema);
    }

    @Override
    public void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        delegate().writeFloatMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        delegate().writeSparseFloatMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        delegate().writeDoubleList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        delegate().writeSparseDoubleList(schema, values, memberSchema);
    }

    @Override
    public void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        delegate().writeDoubleMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        delegate().writeSparseDoubleMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        delegate().writeBigIntegerList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        delegate().writeSparseBigIntegerList(schema, values, memberSchema);
    }

    @Override
    public void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeBigIntegerMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeSparseBigIntegerMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        delegate().writeBigDecimalList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        delegate().writeSparseBigDecimalList(schema, values, memberSchema);
    }

    @Override
    public void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeBigDecimalMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeSparseBigDecimalMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        delegate().writeStringList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        delegate().writeSparseStringList(schema, values, memberSchema);
    }

    @Override
    public void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        delegate().writeStringMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        delegate().writeSparseStringMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        delegate().writeBlobList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        delegate().writeSparseBlobList(schema, values, memberSchema);
    }

    @Override
    public void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
        delegate().writeBlobMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeSparseBlobMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        delegate().writeTimestampList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        delegate().writeSparseTimestampList(schema, values, memberSchema);
    }

    @Override
    public void writeTimestampMap(Schema schema, Map<String, Instant> values, Schema keySchema, Schema valueSchema) {
        delegate().writeTimestampMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeSparseTimestampMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
        delegate().writeDocumentList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
        delegate().writeSparseDocumentList(schema, values, memberSchema);
    }

    @Override
    public void writeDocumentMap(Schema schema, Map<String, Document> values, Schema keySchema, Schema valueSchema) {
        delegate().writeDocumentMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseDocumentMap(
            Schema schema,
            Map<String, Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeSparseDocumentMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        delegate().writeEnumList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        delegate().writeSparseEnumList(schema, values, memberSchema);
    }

    @Override
    public void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeEnumMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeSparseEnumMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        delegate().writeIntEnumList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        delegate().writeSparseIntEnumList(schema, values, memberSchema);
    }

    @Override
    public void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeIntEnumMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeSparseIntEnumMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        delegate().writeStructList(schema, values, memberSchema);
    }

    @Override
    public void writeSparseStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        delegate().writeSparseStructList(schema, values, memberSchema);
    }

    @Override
    public void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeStructMap(schema, values, keySchema, valueSchema);
    }

    @Override
    public void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        delegate().writeSparseStructMap(schema, values, keySchema, valueSchema);
    }
}
