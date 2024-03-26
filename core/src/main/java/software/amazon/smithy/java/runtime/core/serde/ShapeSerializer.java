/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.io.Flushable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.any.Any;

/**
 * Serializes a shape by receiving the Smithy data model and writing output to a receiver owned by the serializer.
 */
public interface ShapeSerializer extends Flushable {

    /**
     * Writes a structure or union inside the given consumer.
     *
     * <p>Closing the structure is handled automatically; consumers must not call
     * {@link StructSerializer#endStruct()}.
     *
     * @param schema   Schema to write.
     * @param consumer Consumer that accepts the created serializer and writes members.
     */
    default void beginStruct(SdkSchema schema, Consumer<StructSerializer> consumer) {
        StructSerializer serializer = beginStruct(schema);
        consumer.accept(serializer);
        serializer.endStruct();
    }

    /**
     * Creates a structure serializer that is closed externally.
     *
     * <p>{@link StructSerializer#endStruct()} must be called when finished or else the serializer will be in an
     * undefined state.
     *
     * @param schema Schema to serialize.
     * @return Returns the created serializer.
     */
    StructSerializer beginStruct(SdkSchema schema);

    /**
     * Begin a list and write zero or more values into it using the provided serializer.
     *
     * @param schema   List schema.
     * @param consumer Received in the context of the list and writes zero or more values.
     */
    void beginList(SdkSchema schema, Consumer<ShapeSerializer> consumer);

    /**
     * Begin a map and write zero or more entries into it using the provided serializer.
     *
     * @param schema   List schema.
     * @param consumer Received in the context of the map and writes zero or more entries.
     */
    void beginMap(SdkSchema schema, Consumer<MapSerializer> consumer);

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
     * Serialize a shape into this serializer.
     *
     * @param schema Schema of the shape to serialize.
     * @param value  Shape to serialize.
     */
    void writeShape(SdkSchema schema, SerializableShape value);

    /**
     * Serialize a document shape.
     *
     * @param schema Document schema.
     * @param value  Value to serialize.
     */
    void writeDocument(SdkSchema schema, Any value);
}
