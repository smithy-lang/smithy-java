/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.io.Flushable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

/**
 * Serializes a shape by receiving the Smithy data model and writing output to a receiver owned by the serializer.
 *
 * <p>Note: null values should only ever be written using {@link #writeNull(SdkSchema)}. Every other method expects
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
    default void close() {}

    /**
     * Writes a structure or union using the given member schema.
     *
     * @param schema A member schema that targets the given struct.
     * @param struct Structure to serialize.
     */
    void writeStruct(SdkSchema schema, SerializableStruct struct);

    /**
     * Writes a structure or union.
     *
     * @param struct Structure to serialize.
     */
    default void writeStruct(SerializableStruct struct) {
        writeStruct(struct.schema(), struct);
    }

    /**
     * Begin a list and write zero or more values into it using the provided serializer.
     *
     * @param schema    List schema.
     * @param listState State to pass into the consumer.
     * @param consumer  Received in the context of the list and writes zero or more values.
     */
    <T> void writeList(SdkSchema schema, T listState, BiConsumer<T, ShapeSerializer> consumer);

    /**
     * Begin a map and write zero or more entries into it using the provided serializer.
     *
     * @param schema   List schema.
     * @param mapState State to pass into the consumer.
     * @param consumer Received in the context of the map and writes zero or more entries.
     */
    <T> void writeMap(SdkSchema schema, T mapState, BiConsumer<T, MapSerializer> consumer);

    /**
     * Serialize a boolean.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeBoolean(SdkSchema schema, boolean value);

    /**
     * Serialize a byte.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeByte(SdkSchema schema, byte value);

    /**
     * Serialize a short.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeShort(SdkSchema schema, short value);

    /**
     * Serialize an integer.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeInteger(SdkSchema schema, int value);

    /**
     * Serialize a long.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeLong(SdkSchema schema, long value);

    /**
     * Serialize a float.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeFloat(SdkSchema schema, float value);

    /**
     * Serialize a double.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeDouble(SdkSchema schema, double value);

    /**
     * Serialize a big integer.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeBigInteger(SdkSchema schema, BigInteger value);

    /**
     * Serialize a big decimal.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeBigDecimal(SdkSchema schema, BigDecimal value);

    /**
     * Serialize a string.
     *
     * @param schema Schema of the shape.
     * @param value  String value.
     */
    void writeString(SdkSchema schema, String value);

    /**
     * Serialize a blob.
     *
     * @param schema Schema of the shape.
     * @param value  Blob value.
     */
    void writeBlob(SdkSchema schema, byte[] value);

    /**
     * Serialize a timestamp.
     *
     * @param schema Schema of the shape.
     * @param value  Timestamp value.
     */
    void writeTimestamp(SdkSchema schema, Instant value);

    /**
     * Serialize a document shape.
     *
     * <p>The underlying contents of the document can be serialized using {@link Document#serializeContents}.
     *
     * @param schema Schema of the shape. Generally this is {@link PreludeSchemas#DOCUMENT} unless the document
     *               wraps a modeled shape.
     * @param value  Value to serialize.
     */
    void writeDocument(SdkSchema schema, Document value);

    /**
     * Serialize a document shape using the schema {@link PreludeSchemas#DOCUMENT}.
     *
     * <p>This method is simply a shorter way to call:
     *
     * <pre>
     * serializer.writeDocument(PreludeSchemas.DOCUMENT, value);
     * </pre>
     *
     * <p>This method should not be used when writing structures because member names are used when writing
     * structures, and the prelude schema for documents has no member name.
     *
     * @param value  Value to serialize.
     */
    default void writeDocument(Document value) {
        writeDocument(PreludeSchemas.DOCUMENT, value);
    }

    /**
     * Writes a null value.
     *
     * @param schema Schema of the null value.
     */
    void writeNull(SdkSchema schema);
}
