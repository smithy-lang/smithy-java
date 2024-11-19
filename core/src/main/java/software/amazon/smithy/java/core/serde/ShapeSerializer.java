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
import java.util.concurrent.Flow;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.document.Document;
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
    default void writeEventStream(Schema schema, Flow.Publisher<? extends SerializableStruct> value) {
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
