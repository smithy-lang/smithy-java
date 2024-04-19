/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

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
    protected RuntimeException throwForInvalidState(String message, SdkSchema schema) {
        throw new SdkSerdeException(message);
    }

    private RuntimeException throwForInvalidState(SdkSchema schema) {
        return throwForInvalidState("Unexpected schema type: " + schema, schema);
    }

    @Override
    public void writeStruct(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeEnum(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
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
    public void writeDocument(SdkSchema schema, Document value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeNull(SdkSchema schema) {
        throw throwForInvalidState("Unexpected null value written for " + schema, schema);
    }

    // Delegates to writing with a schema. Only override writing a document with a schema.
    @Override
    public final void writeDocument(Document value) {
        ShapeSerializer.super.writeDocument(value);
    }
}
