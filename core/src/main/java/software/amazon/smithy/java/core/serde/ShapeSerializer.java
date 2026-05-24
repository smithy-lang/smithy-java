/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.io.Flushable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Serializes a shape by receiving the Smithy data model and writing output to a receiver owned by the serializer.
 *
 * <p>Note: null values should only ever be written using {@link #writeNull(Schema)}. Every other method expects
 * a non-null value or a value type.
 */
public interface ShapeSerializer extends Flushable, AutoCloseable {

    /**
     * Create a serializer that serializes nothing.
     *
     * @return the null serializer.
     */
    static ShapeSerializer nullSerializer() {
        return NullSerializer.INSTANCE;
    }

    @Override
    default void flush() {}

    @Override
    default void close() {
        flush();
    }

    /**
     * Writes a structure or union using the given member schema.
     *
     * @param schema A member schema that targets the given struct.
     * @param struct Structure to serialize.
     */
    void writeStruct(Schema schema, SerializableStruct struct);

    /**
     * Begin a list and write zero or more values into it using the provided serializer.
     *
     * @param schema    List schema.
     * @param listState State to pass into the consumer.
     * @param size      Number of elements in the list, or -1 if unknown.
     * @param consumer  Received in the context of the list and writes zero or more values.
     */
    <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer);

    /**
     * Begin a map and write zero or more entries into it using the provided serializer.
     *
     * @param schema   List schema.
     * @param mapState State to pass into the consumer.
     * @param size     Number of entries in the map, or -1 if unknown.
     * @param consumer Received in the context of the map and writes zero or more entries.
     */
    <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer);

    /**
     * Writes a list of structs directly, avoiding per-element BiConsumer dispatch.
     */
    default void writeStructList(
            Schema schema,
            List<? extends SerializableStruct> values,
            Schema memberSchema
    ) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeStruct(memberSchema, list.get(i));
                }
            } else {
                for (var v : list) {
                    s.writeStruct(memberSchema, v);
                }
            }
        });
    }

    default void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeString(memberSchema, list.get(i));
                }
            } else {
                for (var v : list) {
                    s.writeString(memberSchema, v);
                }
            }
        });
    }

    default void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeBoolean(memberSchema, list.get(i));
                }
            } else {
                for (var v : list) {
                    s.writeBoolean(memberSchema, v);
                }
            }
        });
    }

    default void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeByte(memberSchema, list.get(i));
                }
            } else {
                for (var v : list) {
                    s.writeByte(memberSchema, v);
                }
            }
        });
    }

    default void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeShort(memberSchema, list.get(i));
                }
            } else {
                for (var v : list) {
                    s.writeShort(memberSchema, v);
                }
            }
        });
    }

    default void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeInteger(memberSchema, list.get(i));
                }
            } else {
                for (var v : list) {
                    s.writeInteger(memberSchema, v);
                }
            }
        });
    }

    default void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeLong(memberSchema, list.get(i));
                }
            } else {
                for (var v : list) {
                    s.writeLong(memberSchema, v);
                }
            }
        });
    }

    default void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeFloat(memberSchema, list.get(i));
                }
            } else {
                for (var v : list) {
                    s.writeFloat(memberSchema, v);
                }
            }
        });
    }

    default void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeDouble(memberSchema, list.get(i));
                }
            } else {
                for (var v : list) {
                    s.writeDouble(memberSchema, v);
                }
            }
        });
    }

    default void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeBigInteger(memberSchema, list.get(i));
                }
            } else {
                for (var v : list) {
                    s.writeBigInteger(memberSchema, v);
                }
            }
        });
    }

    default void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeBigDecimal(memberSchema, list.get(i));
                }
            } else {
                for (var v : list) {
                    s.writeBigDecimal(memberSchema, v);
                }
            }
        });
    }

    default void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeBlob(memberSchema, list.get(i));
                }
            } else {
                for (var v : list) {
                    s.writeBlob(memberSchema, v);
                }
            }
        });
    }

    default void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeTimestamp(memberSchema, list.get(i));
                }
            } else {
                for (var v : list) {
                    s.writeTimestamp(memberSchema, v);
                }
            }
        });
    }

    default void writeDocumentList(
            Schema schema,
            List<? extends Document> values,
            Schema memberSchema
    ) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeDocument(memberSchema, list.get(i));
                }
            } else {
                for (var v : list) {
                    s.writeDocument(memberSchema, v);
                }
            }
        });
    }

    default void writeEnumList(
            Schema schema,
            List<? extends SmithyEnum> values,
            Schema memberSchema
    ) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeString(memberSchema, list.get(i).getValue());
                }
            } else {
                for (var v : list) {
                    s.writeString(memberSchema, v.getValue());
                }
            }
        });
    }

    default void writeIntEnumList(
            Schema schema,
            List<? extends SmithyIntEnum> values,
            Schema memberSchema
    ) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    s.writeInteger(memberSchema, list.get(i).getValue());
                }
            } else {
                for (var v : list) {
                    s.writeInteger(memberSchema, v.getValue());
                }
            }
        });
    }

    // --- Specialized map methods: iterate entries with typed values, no per-entry BiConsumer ---

    default void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeStructEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        });
    }

    default void writeStringMap(
            Schema schema,
            Map<String, String> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeStringEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        });
    }

    default void writeBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeBooleanEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        });
    }

    default void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeByteEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        });
    }

    default void writeShortMap(
            Schema schema,
            Map<String, Short> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeShortEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        });
    }

    default void writeIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeIntegerEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        });
    }

    default void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeLongEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        });
    }

    default void writeFloatMap(
            Schema schema,
            Map<String, Float> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeFloatEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        });
    }

    default void writeDoubleMap(
            Schema schema,
            Map<String, Double> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeDoubleEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        });
    }

    default void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeBigIntegerEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        });
    }

    default void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeBigDecimalEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        });
    }

    default void writeBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeBlobEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        });
    }

    default void writeTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeTimestampEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        });
    }

    default void writeDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeDocumentEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        });
    }

    default void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeStringEntry(keySchema, entry.getKey(), valueSchema, entry.getValue().getValue());
            }
        });
    }

    default void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                ms.writeIntEnumEntry(keySchema, entry.getKey(), valueSchema, entry.getValue().getValue());
            }
        });
    }

    // --- Sparse map methods: same as above but with null check per entry ---

    default void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeStructEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
                }
            }
        });
    }

    default void writeSparseStringMap(
            Schema schema,
            Map<String, String> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeStringEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
                }
            }
        });
    }

    default void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeIntegerEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
                }
            }
        });
    }

    default void writeSparseLongMap(
            Schema schema,
            Map<String, Long> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeLongEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
                }
            }
        });
    }

    default void writeSparseDoubleMap(
            Schema schema,
            Map<String, Double> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeDoubleEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
                }
            }
        });
    }

    default void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeBlobEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
                }
            }
        });
    }

    default void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeTimestampEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
                }
            }
        });
    }

    default void writeSparseDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeDocumentEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
                }
            }
        });
    }

    default void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeBooleanEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
                }
            }
        });
    }

    default void writeSparseByteMap(
            Schema schema,
            Map<String, Byte> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeByteEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
                }
            }
        });
    }

    default void writeSparseShortMap(
            Schema schema,
            Map<String, Short> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeShortEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
                }
            }
        });
    }

    default void writeSparseFloatMap(
            Schema schema,
            Map<String, Float> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeFloatEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
                }
            }
        });
    }

    default void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeBigIntegerEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
                }
            }
        });
    }

    default void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeBigDecimalEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
                }
            }
        });
    }

    default void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeStringEntry(keySchema, entry.getKey(), valueSchema, entry.getValue().getValue());
                }
            }
        });
    }

    default void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> {
            for (var entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    ms.writeNullEntry(keySchema, entry.getKey(), valueSchema);
                } else {
                    ms.writeIntEnumEntry(keySchema, entry.getKey(), valueSchema, entry.getValue().getValue());
                }
            }
        });
    }

    // --- Sparse list methods: same as non-sparse but with null check per element ---

    default void writeSparseStructList(
            Schema schema,
            List<? extends SerializableStruct> values,
            Schema memberSchema
    ) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeStruct(memberSchema, v);
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeStruct(memberSchema, v);
                    }
                }
            }
        });
    }

    default void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeString(memberSchema, v);
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeString(memberSchema, v);
                    }
                }
            }
        });
    }

    default void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeInteger(memberSchema, v);
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeInteger(memberSchema, v);
                    }
                }
            }
        });
    }

    default void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeLong(memberSchema, v);
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeLong(memberSchema, v);
                    }
                }
            }
        });
    }

    default void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeDouble(memberSchema, v);
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeDouble(memberSchema, v);
                    }
                }
            }
        });
    }

    default void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeBlob(memberSchema, v);
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeBlob(memberSchema, v);
                    }
                }
            }
        });
    }

    default void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeTimestamp(memberSchema, v);
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeTimestamp(memberSchema, v);
                    }
                }
            }
        });
    }

    default void writeSparseDocumentList(
            Schema schema,
            List<? extends Document> values,
            Schema memberSchema
    ) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeDocument(memberSchema, v);
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeDocument(memberSchema, v);
                    }
                }
            }
        });
    }

    default void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeBoolean(memberSchema, v);
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeBoolean(memberSchema, v);
                    }
                }
            }
        });
    }

    default void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeByte(memberSchema, v);
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeByte(memberSchema, v);
                    }
                }
            }
        });
    }

    default void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeShort(memberSchema, v);
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeShort(memberSchema, v);
                    }
                }
            }
        });
    }

    default void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeFloat(memberSchema, v);
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeFloat(memberSchema, v);
                    }
                }
            }
        });
    }

    default void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeBigInteger(memberSchema, v);
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeBigInteger(memberSchema, v);
                    }
                }
            }
        });
    }

    default void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeBigDecimal(memberSchema, v);
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeBigDecimal(memberSchema, v);
                    }
                }
            }
        });
    }

    default void writeSparseEnumList(
            Schema schema,
            List<? extends SmithyEnum> values,
            Schema memberSchema
    ) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeString(memberSchema, v.getValue());
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeString(memberSchema, v.getValue());
                    }
                }
            }
        });
    }

    default void writeSparseIntEnumList(
            Schema schema,
            List<? extends SmithyIntEnum> values,
            Schema memberSchema
    ) {
        writeList(schema, values, values.size(), (list, s) -> {
            if (list instanceof RandomAccess) {
                for (int i = 0, sz = list.size(); i < sz; i++) {
                    var v = list.get(i);
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeInteger(memberSchema, v.getValue());
                    }
                }
            } else {
                for (var v : list) {
                    if (v == null) {
                        s.writeNull(memberSchema);
                    } else {
                        s.writeInteger(memberSchema, v.getValue());
                    }
                }
            }
        });
    }

    /**
     * Serialize a boolean.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeBoolean(Schema schema, boolean value);

    /**
     * Serialize a byte.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeByte(Schema schema, byte value);

    /**
     * Serialize a short.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeShort(Schema schema, short value);

    /**
     * Serialize an integer.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeInteger(Schema schema, int value);

    /**
     * Serialize a long.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeLong(Schema schema, long value);

    /**
     * Serialize a float.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeFloat(Schema schema, float value);

    /**
     * Serialize a double.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeDouble(Schema schema, double value);

    /**
     * Serialize a big integer.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeBigInteger(Schema schema, BigInteger value);

    /**
     * Serialize a big decimal.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeBigDecimal(Schema schema, BigDecimal value);

    /**
     * Serialize a string.
     *
     * @param schema Schema of the shape.
     * @param value  String value.
     */
    void writeString(Schema schema, String value);

    /**
     * Serialize a blob.
     *
     * @param schema Schema of the shape.
     * @param value  Blob value.
     */
    void writeBlob(Schema schema, ByteBuffer value);

    default void writeBlob(Schema schema, byte[] value) {
        writeBlob(schema, ByteBuffer.wrap(value));
    }

    /**
     * Serialize a data stream.
     *
     * @param schema Schema of the shape.
     * @param value  Streaming value.
     */
    default void writeDataStream(Schema schema, DataStream value) {
        // by default, do nothing
    }

    /**
     * Serialize an event stream.
     *
     * @param schema Schema of the shape.
     * @param value  Event Stream value.
     */
    default void writeEventStream(Schema schema, EventStream<? extends SerializableStruct> value) {
        // by default, do nothing
    }

    /**
     * Serialize a timestamp.
     *
     * @param schema Schema of the shape.
     * @param value  Timestamp value.
     */
    void writeTimestamp(Schema schema, Instant value);

    /**
     * Serialize a document shape.
     *
     * <p>The underlying contents of the document can be serialized using {@link Document#serializeContents}.
     *
     * @param schema Schema of the shape. Generally, this should be set to {@link PreludeSchemas#DOCUMENT} unless the
     *               document wraps a modeled shape.
     * @param value  Value to serialize.
     */
    void writeDocument(Schema schema, Document value);

    /**
     * Writes a null value.
     *
     * @param schema Schema of the null value.
     */
    void writeNull(Schema schema);
}
