/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * Serializes a map shape.
 */
public interface MapSerializer {
    /**
     * Writes a string key for the map, and the valueSerializer is called and required to serialize the value.
     *
     * @param keySchema       Schema of the map key. The same schema should be provided for every map key entry.
     * @param key             Key to write.
     * @param state           State to pass to {@code valueSerializer}.
     * @param valueSerializer Serializer used to serialize the map value. A value must be serialized.
     */
    <T> void writeEntry(Schema keySchema, String key, T state, BiConsumer<T, ShapeSerializer> valueSerializer);

    default void writeStructEntry(Schema keySchema, String key, Schema valueSchema, SerializableStruct value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeStruct(valueSchema, v));
    }

    default void writeStringEntry(Schema keySchema, String key, Schema valueSchema, String value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeString(valueSchema, v));
    }

    default void writeBooleanEntry(Schema keySchema, String key, Schema valueSchema, Boolean value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeBoolean(valueSchema, v));
    }

    default void writeByteEntry(Schema keySchema, String key, Schema valueSchema, Byte value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeByte(valueSchema, v));
    }

    default void writeShortEntry(Schema keySchema, String key, Schema valueSchema, Short value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeShort(valueSchema, v));
    }

    default void writeIntegerEntry(Schema keySchema, String key, Schema valueSchema, Integer value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeInteger(valueSchema, v));
    }

    default void writeLongEntry(Schema keySchema, String key, Schema valueSchema, Long value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeLong(valueSchema, v));
    }

    default void writeFloatEntry(Schema keySchema, String key, Schema valueSchema, Float value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeFloat(valueSchema, v));
    }

    default void writeDoubleEntry(Schema keySchema, String key, Schema valueSchema, Double value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeDouble(valueSchema, v));
    }

    default void writeBigIntegerEntry(Schema keySchema, String key, Schema valueSchema, BigInteger value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeBigInteger(valueSchema, v));
    }

    default void writeBigDecimalEntry(Schema keySchema, String key, Schema valueSchema, BigDecimal value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeBigDecimal(valueSchema, v));
    }

    default void writeBlobEntry(Schema keySchema, String key, Schema valueSchema, ByteBuffer value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeBlob(valueSchema, v));
    }

    default void writeTimestampEntry(Schema keySchema, String key, Schema valueSchema, Instant value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeTimestamp(valueSchema, v));
    }

    default void writeDocumentEntry(Schema keySchema, String key, Schema valueSchema, Document value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeDocument(valueSchema, v));
    }

    default void writeNullEntry(Schema keySchema, String key, Schema valueSchema) {
        writeEntry(keySchema, key, null, (v, s) -> s.writeNull(valueSchema));
    }

    default void writeIntEnumEntry(Schema keySchema, String key, Schema valueSchema, int value) {
        writeEntry(keySchema, key, value, (v, s) -> s.writeInteger(valueSchema, v));
    }
}
