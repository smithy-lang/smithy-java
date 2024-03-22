/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.serde.any.Any;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;

/**
 * Expects to serialize a specific kind of shape and fails if other shapes are serialized.
 */
public abstract class SpecificShapeSerializer implements ShapeSerializer {

    /**
     * Invoked when an unexpected shape is encountered.
     *
     * @param schema Unexpected encountered schema.
     * @return Returns an exception to throw.
     */
    protected abstract RuntimeException throwForInvalidState(SdkSchema schema);

    @Override
    public void flush() {}

    @Override
    public StructSerializer beginStruct(SdkSchema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void beginList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void beginMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeBoolean(SdkSchema schema, boolean value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeShort(SdkSchema schema, short value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeByte(SdkSchema schema, byte value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeLong(SdkSchema schema, long value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeFloat(SdkSchema schema, float value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeDouble(SdkSchema schema, double value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeString(SdkSchema schema, String value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeBlob(SdkSchema schema, byte[] value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeShape(SdkSchema schema, SerializableShape value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeDocument(SdkSchema schema, Any value) {
        throw throwForInvalidState(schema);
    }
}
