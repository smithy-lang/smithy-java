/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

/**
 * Ensures that a value is written to a serializer when required (e.g., when writing structure members).
 */
public final class RequiredWriteSerializer implements ShapeSerializer {

    private final ShapeSerializer delegate;
    private boolean wroteSomething;

    private RequiredWriteSerializer(ShapeSerializer delegate) {
        this.delegate = delegate;
    }

    public static void assertWrite(
        ShapeSerializer delegate,
        Supplier<RuntimeException> errorSupplier,
        Consumer<ShapeSerializer> consumer
    ) {
        RequiredWriteSerializer serializer = new RequiredWriteSerializer(delegate);
        consumer.accept(serializer);
        if (!serializer.wroteSomething) {
            throw errorSupplier.get();
        }
    }

    @Override
    public void writeStruct(Schema schema, Consumer<ShapeSerializer> consumer) {
        delegate.writeStruct(schema, consumer);
        wroteSomething = true;
    }

    @Override
    public void writeList(Schema schema, Consumer<ShapeSerializer> consumer) {
        delegate.writeList(schema, consumer);
        wroteSomething = true;
    }

    @Override
    public void writeMap(Schema schema, Consumer<MapSerializer> consumer) {
        delegate.writeMap(schema, consumer);
        wroteSomething = true;
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        delegate.writeBoolean(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        delegate.writeByte(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeShort(Schema schema, short value) {
        delegate.writeShort(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        delegate.writeInteger(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeLong(Schema schema, long value) {
        delegate.writeLong(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        delegate.writeFloat(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        delegate.writeDouble(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        delegate.writeBigInteger(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        delegate.writeBigDecimal(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeString(Schema schema, String value) {
        delegate.writeString(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        delegate.writeBlob(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        delegate.writeTimestamp(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        delegate.writeDocument(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeNull(Schema schema) {
        delegate.writeNull(schema);
        wroteSomething = true;
    }
}
