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
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

/**
 * Serializes a shape by receiving the Smithy data model and writing output to a receiver owned by the serializer.
 *
 * <p>Note: null values should only ever be written using {@link #writeNull(Schema)}. Every other method expects
 * a non-null value or a value type.
 */
public interface ShapeSerializer extends Flushable {

    /**
     * Create a ShapeSerializer that sends all write calls to a singular consumer that can delegate and replay writes.
     *
     * @param delegatingConsumer Consumer that receives each schema and a consumer that will write if invoked.
     * @return the created ShapeSerializer.
     */
    static ShapeSerializer ofDelegatingConsumer(BiConsumer<Schema, Consumer<ShapeSerializer>> delegatingConsumer) {
        return new ConsolidatedSerializer(delegatingConsumer);
    }

    @Override
    default void flush() {}

    /**
     * Writes a structure or union.
     *
     * <p>Each shape written to the given consumer <em>must</em> use a schema that has a member name.
     * The member name is used to write field names.
     *
     * @param schema   Schema to serialize.
     * @param consumer Receives the struct serializer and writes members.
     */
    void writeStruct(Schema schema, Consumer<ShapeSerializer> consumer);

    /**
     * Begin a list and write zero or more values into it using the provided serializer.
     *
     * @param schema   List schema.
     * @param consumer Received in the context of the list and writes zero or more values.
     */
    void writeList(Schema schema, Consumer<ShapeSerializer> consumer);

    /**
     * Begin a map and write zero or more entries into it using the provided serializer.
     *
     * @param schema   List schema.
     * @param consumer Received in the context of the map and writes zero or more entries.
     */
    void writeMap(Schema schema, Consumer<MapSerializer> consumer);

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
    void writeBlob(Schema schema, byte[] value);

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
     * @param schema Schema of the shape. Generally this is {@link PreludeSchemas#DOCUMENT} unless the document
     *               wraps a modeled shape.
     * @param value  Value to serialize.
     */
    void writeDocument(Schema schema, Document value);

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
    void writeNull(Schema schema);

    /**
     * Write to the serializer if the given value is not null.
     *
     * @param serializer Serializer to conditionally write to.
     * @param schema     Schema to write if not null.
     * @param value      Value to write if not null.
     */
    static void writeIfNotNull(ShapeSerializer serializer, Schema schema, Boolean value) {
        if (value != null) {
            serializer.writeBoolean(schema, value);
        }
    }

    /**
     * Write to the serializer if the given value is not null.
     *
     * @param serializer Serializer to conditionally write to.
     * @param schema     Schema to write if not null.
     * @param value      Value to write if not null.
     */
    static void writeIfNotNull(ShapeSerializer serializer, Schema schema, Byte value) {
        if (value != null) {
            serializer.writeByte(schema, value);
        }
    }

    /**
     * Write to the serializer if the given value is not null.
     *
     * @param serializer Serializer to conditionally write to.
     * @param schema     Schema to write if not null.
     * @param value      Value to write if not null.
     */
    static void writeIfNotNull(ShapeSerializer serializer, Schema schema, Short value) {
        if (value != null) {
            serializer.writeShort(schema, value);
        }
    }

    /**
     * Write to the serializer if the given value is not null.
     *
     * @param serializer Serializer to conditionally write to.
     * @param schema     Schema to write if not null.
     * @param value      Value to write if not null.
     */
    static void writeIfNotNull(ShapeSerializer serializer, Schema schema, Integer value) {
        if (value != null) {
            serializer.writeInteger(schema, value);
        }
    }

    /**
     * Write to the serializer if the given value is not null.
     *
     * @param serializer Serializer to conditionally write to.
     * @param schema     Schema to write if not null.
     * @param value      Value to write if not null.
     */
    static void writeIfNotNull(ShapeSerializer serializer, Schema schema, Long value) {
        if (value != null) {
            serializer.writeLong(schema, value);
        }
    }

    /**
     * Write to the serializer if the given value is not null.
     *
     * @param serializer Serializer to conditionally write to.
     * @param schema     Schema to write if not null.
     * @param value      Value to write if not null.
     */
    static void writeIfNotNull(ShapeSerializer serializer, Schema schema, Float value) {
        if (value != null) {
            serializer.writeFloat(schema, value);
        }
    }

    /**
     * Write to the serializer if the given value is not null.
     *
     * @param serializer Serializer to conditionally write to.
     * @param schema     Schema to write if not null.
     * @param value      Value to write if not null.
     */
    static void writeIfNotNull(ShapeSerializer serializer, Schema schema, Double value) {
        if (value != null) {
            serializer.writeDouble(schema, value);
        }
    }

    /**
     * Write to the serializer if the given value is not null.
     *
     * @param serializer Serializer to conditionally write to.
     * @param schema     Schema to write if not null.
     * @param value      Value to write if not null.
     */
    static void writeIfNotNull(ShapeSerializer serializer, Schema schema, BigInteger value) {
        if (value != null) {
            serializer.writeBigInteger(schema, value);
        }
    }

    /**
     * Write to the serializer if the given value is not null.
     *
     * @param serializer Serializer to conditionally write to.
     * @param schema     Schema to write if not null.
     * @param value      Value to write if not null.
     */
    static void writeIfNotNull(ShapeSerializer serializer, Schema schema, BigDecimal value) {
        if (value != null) {
            serializer.writeBigDecimal(schema, value);
        }
    }

    /**
     * Write to the serializer if the given value is not null.
     *
     * @param serializer Serializer to conditionally write to.
     * @param schema     Schema to write if not null.
     * @param value      Value to write if not null.
     */
    static void writeIfNotNull(ShapeSerializer serializer, Schema schema, byte[] value) {
        if (value != null) {
            serializer.writeBlob(schema, value);
        }
    }

    /**
     * Write to the serializer if the given value is not null.
     *
     * @param serializer Serializer to conditionally write to.
     * @param schema     Schema to write if not null.
     * @param value      Value to write if not null.
     */
    static void writeIfNotNull(ShapeSerializer serializer, Schema schema, String value) {
        if (value != null) {
            serializer.writeString(schema, value);
        }
    }

    /**
     * Write to the serializer if the given value is not null.
     *
     * @param serializer Serializer to conditionally write to.
     * @param schema     Schema to write if not null.
     * @param value      Value to write if not null.
     */
    static void writeIfNotNull(ShapeSerializer serializer, Schema schema, Instant value) {
        if (value != null) {
            serializer.writeTimestamp(schema, value);
        }
    }

    /**
     * Write to the serializer if the given value is not null.
     *
     * @param serializer Serializer to conditionally write to.
     * @param schema     Schema to write if not null.
     * @param value      Value to write if not null.
     */
    static void writeIfNotNull(ShapeSerializer serializer, Schema schema, Document value) {
        if (value != null) {
            serializer.writeDocument(schema, value);
        }
    }
}
