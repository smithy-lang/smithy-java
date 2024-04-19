/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
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
    protected RuntimeException throwForInvalidState(String message, Schema schema) {
        throw new SerdeException(message);
    }

    private RuntimeException throwForInvalidState(Schema schema) {
        return throwForInvalidState("Unexpected schema type: " + schema, schema);
    }

    @Override
    public void writeStruct(Schema schema, Consumer<ShapeSerializer> consumer) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeList(Schema schema, Consumer<ShapeSerializer> consumer) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeMap(Schema schema, Consumer<MapSerializer> consumer) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeString(Schema schema, String value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        throw throwForInvalidState(schema);
    }

    @Override
    public void writeNull(Schema schema) {
        throw throwForInvalidState("Unexpected null value written for " + schema, schema);
    }

    // Delegates to writing with a schema. Only override writing a document with a schema.
    @Override
    public final void writeDocument(Document value) {
        ShapeSerializer.super.writeDocument(value);
    }
}
