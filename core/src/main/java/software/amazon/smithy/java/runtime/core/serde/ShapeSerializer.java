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
import software.amazon.smithy.java.runtime.core.serde.document.Document;

/**
 * Serializes a shape by receiving the Smithy data model and writing output to a receiver owned by the serializer.
 */
public interface ShapeSerializer extends Flushable {
    /**
     * Writes a structure or union.
     *
     * @param schema   Schema to serialize.
     * @param consumer Receives the struct serializer and writes members.
     */
    void writeStruct(SdkSchema schema, Consumer<StructSerializer> consumer);

    /**
     * Begin a list and write zero or more values into it using the provided serializer.
     *
     * @param schema   List schema.
     * @param consumer Received in the context of the list and writes zero or more values.
     */
    void writeList(SdkSchema schema, Consumer<ShapeSerializer> consumer);

    /**
     * Begin a map and write zero or more entries into it using the provided serializer.
     *
     * @param schema   List schema.
     * @param consumer Received in the context of the map and writes zero or more entries.
     */
    void writeMap(SdkSchema schema, Consumer<MapSerializer> consumer);

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
     * Serialize a shape, document, or any other kind of value that emits the Smithy data model into this serializer.
     *
     * @param value Shape to serialize.
     */
    default void writeShape(SerializableShape value) {
        value.serialize(this);
    }

    /**
     * Serialize a document shape.
     *
     * <p>The underlying contents of the document can be serialized using {@link Document#serializeContents}.
     *
     * @param value  Value to serialize.
     */
    void writeDocument(Document value);
}
