/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.function.BiConsumer;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.ListSerializer;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * Provides a basic serializer implementation that only sends values to a blackhole to avoid dead code elimination
 * from using a null serializer. This class should resemble the same basic layout and operations of a real serializer.
 */
public final class BlackholeSerializer implements ShapeSerializer {

    private final Blackhole bh;

    public BlackholeSerializer(Blackhole bh) {
        this.bh = bh;
    }

    @Override
    public void close() {
        bh.consume(this);
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        struct.serializeMembers(new BhStructureWriter(this));
    }

    private static final class BhStructureWriter extends InterceptingSerializer {
        private final BlackholeSerializer bs;

        private BhStructureWriter(BlackholeSerializer bs) {
            this.bs = bs;
        }

        @Override
        protected ShapeSerializer before(Schema schema) {
            return bs;
        }
    }

    @Override
    public <T> void writeList(Schema schema, T state, int size, BiConsumer<T, ShapeSerializer> consumer) {
        consumer.accept(state, new ListSerializer(this, this::doNothingBetweenValues));
    }

    private void doNothingBetweenValues(int position) {
        bh.consume(position);
    }

    @Override
    public <T> void writeMap(Schema schema, T state, int size, BiConsumer<T, MapSerializer> consumer) {
        consumer.accept(state, new BhMapSerializer(this));
    }

    private record BhMapSerializer(BlackholeSerializer bs) implements MapSerializer {
        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            valueSerializer.accept(state, bs);
        }
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        bh.consume(value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        bh.consume(value);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        bh.consume(value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        bh.consume(value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        bh.consume(value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        bh.consume(value);
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        bh.consume(value);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        bh.consume(value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        bh.consume(value);
    }

    @Override
    public void writeString(Schema schema, String value) {
        bh.consume(value);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        bh.consume(value);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        bh.consume(value);
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        value.serializeContents(this);
    }

    // --- Specialized non-sparse list methods ---

    @Override
    public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeStruct(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeStruct(memberSchema, v);
            }
        }
    }

    @Override
    public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeString(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeString(memberSchema, v);
            }
        }
    }

    @Override
    public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeBoolean(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeBoolean(memberSchema, v);
            }
        }
    }

    @Override
    public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeByte(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeByte(memberSchema, v);
            }
        }
    }

    @Override
    public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeShort(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeShort(memberSchema, v);
            }
        }
    }

    @Override
    public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeInteger(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeInteger(memberSchema, v);
            }
        }
    }

    @Override
    public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeLong(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeLong(memberSchema, v);
            }
        }
    }

    @Override
    public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeFloat(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeFloat(memberSchema, v);
            }
        }
    }

    @Override
    public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeDouble(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeDouble(memberSchema, v);
            }
        }
    }

    @Override
    public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeBigInteger(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeBigInteger(memberSchema, v);
            }
        }
    }

    @Override
    public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeBigDecimal(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeBigDecimal(memberSchema, v);
            }
        }
    }

    @Override
    public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeBlob(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeBlob(memberSchema, v);
            }
        }
    }

    @Override
    public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeTimestamp(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeTimestamp(memberSchema, v);
            }
        }
    }

    @Override
    public void writeDocumentList(Schema schema, List<? extends Document> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeDocument(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeDocument(memberSchema, v);
            }
        }
    }

    @Override
    public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeString(memberSchema, values.get(i).getValue());
            }
        } else {
            for (var v : values) {
                writeString(memberSchema, v.getValue());
            }
        }
    }

    @Override
    public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeInteger(memberSchema, values.get(i).getValue());
            }
        } else {
            for (var v : values) {
                writeInteger(memberSchema, v.getValue());
            }
        }
    }

    // --- Specialized sparse list methods ---

    @Override
    public void writeSparseStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeStruct(memberSchema, v); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeStruct(memberSchema, v); }
            }
        }
    }

    @Override
    public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeString(memberSchema, v); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeString(memberSchema, v); }
            }
        }
    }

    @Override
    public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeInteger(memberSchema, v); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeInteger(memberSchema, v); }
            }
        }
    }

    @Override
    public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeLong(memberSchema, v); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeLong(memberSchema, v); }
            }
        }
    }

    @Override
    public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeDouble(memberSchema, v); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeDouble(memberSchema, v); }
            }
        }
    }

    @Override
    public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeBlob(memberSchema, v); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeBlob(memberSchema, v); }
            }
        }
    }

    @Override
    public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeTimestamp(memberSchema, v); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeTimestamp(memberSchema, v); }
            }
        }
    }

    @Override
    public void writeSparseDocumentList(Schema schema, List<? extends Document> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeDocument(memberSchema, v); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeDocument(memberSchema, v); }
            }
        }
    }

    @Override
    public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeBoolean(memberSchema, v); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeBoolean(memberSchema, v); }
            }
        }
    }

    @Override
    public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeByte(memberSchema, v); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeByte(memberSchema, v); }
            }
        }
    }

    @Override
    public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeShort(memberSchema, v); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeShort(memberSchema, v); }
            }
        }
    }

    @Override
    public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeFloat(memberSchema, v); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeFloat(memberSchema, v); }
            }
        }
    }

    @Override
    public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeBigInteger(memberSchema, v); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeBigInteger(memberSchema, v); }
            }
        }
    }

    @Override
    public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeBigDecimal(memberSchema, v); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeBigDecimal(memberSchema, v); }
            }
        }
    }

    @Override
    public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeString(memberSchema, v.getValue()); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeString(memberSchema, v.getValue()); }
            }
        }
    }

    @Override
    public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) { writeNull(memberSchema); } else { writeInteger(memberSchema, v.getValue()); }
            }
        } else {
            for (var v : values) {
                if (v == null) { writeNull(memberSchema); } else { writeInteger(memberSchema, v.getValue()); }
            }
        }
    }

    // --- Specialized non-sparse map methods ---

    @Override
    public void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeStruct(valueSchema, entry.getValue());
        }
    }

    @Override
    public void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeString(valueSchema, entry.getValue());
        }
    }

    @Override
    public void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeBoolean(valueSchema, entry.getValue());
        }
    }

    @Override
    public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeByte(valueSchema, entry.getValue());
        }
    }

    @Override
    public void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeShort(valueSchema, entry.getValue());
        }
    }

    @Override
    public void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeInteger(valueSchema, entry.getValue());
        }
    }

    @Override
    public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeLong(valueSchema, entry.getValue());
        }
    }

    @Override
    public void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeFloat(valueSchema, entry.getValue());
        }
    }

    @Override
    public void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeDouble(valueSchema, entry.getValue());
        }
    }

    @Override
    public void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeBigInteger(valueSchema, entry.getValue());
        }
    }

    @Override
    public void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeBigDecimal(valueSchema, entry.getValue());
        }
    }

    @Override
    public void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeBlob(valueSchema, entry.getValue());
        }
    }

    @Override
    public void writeTimestampMap(Schema schema, Map<String, Instant> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeTimestamp(valueSchema, entry.getValue());
        }
    }

    @Override
    public void writeDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeDocument(valueSchema, entry.getValue());
        }
    }

    @Override
    public void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeString(valueSchema, entry.getValue().getValue());
        }
    }

    @Override
    public void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            writeInteger(valueSchema, entry.getValue().getValue());
        }
    }

    // --- Specialized sparse map methods ---

    @Override
    public void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeStruct(valueSchema, entry.getValue());
            }
        }
    }

    @Override
    public void writeSparseStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeString(valueSchema, entry.getValue());
            }
        }
    }

    @Override
    public void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeInteger(valueSchema, entry.getValue());
            }
        }
    }

    @Override
    public void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeLong(valueSchema, entry.getValue());
            }
        }
    }

    @Override
    public void writeSparseDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeDouble(valueSchema, entry.getValue());
            }
        }
    }

    @Override
    public void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeBlob(valueSchema, entry.getValue());
            }
        }
    }

    @Override
    public void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeTimestamp(valueSchema, entry.getValue());
            }
        }
    }

    @Override
    public void writeSparseDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeDocument(valueSchema, entry.getValue());
            }
        }
    }

    @Override
    public void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeBoolean(valueSchema, entry.getValue());
            }
        }
    }

    @Override
    public void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeByte(valueSchema, entry.getValue());
            }
        }
    }

    @Override
    public void writeSparseShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeShort(valueSchema, entry.getValue());
            }
        }
    }

    @Override
    public void writeSparseFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeFloat(valueSchema, entry.getValue());
            }
        }
    }

    @Override
    public void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeBigInteger(valueSchema, entry.getValue());
            }
        }
    }

    @Override
    public void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeBigDecimal(valueSchema, entry.getValue());
            }
        }
    }

    @Override
    public void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeString(valueSchema, entry.getValue().getValue());
            }
        }
    }

    @Override
    public void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        for (var entry : values.entrySet()) {
            bh.consume(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeInteger(valueSchema, entry.getValue().getValue());
            }
        }
    }

    @Override
    public void writeNull(Schema schema) {
        bh.consume(this);
    }
}
