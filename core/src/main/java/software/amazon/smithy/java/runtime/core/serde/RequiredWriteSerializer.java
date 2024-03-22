/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;
import software.amazon.smithy.java.runtime.core.serde.any.Any;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;

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
    public void beginStruct(SdkSchema schema, Consumer<StructSerializer> consumer) {
        delegate.beginStruct(schema, consumer);
        wroteSomething = true;
    }

    @Override
    public StructSerializer beginStruct(SdkSchema schema) {
        wroteSomething = true;
        return delegate.beginStruct(schema);
    }

    @Override
    public void beginList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        delegate.beginList(schema, consumer);
        wroteSomething = true;
    }

    @Override
    public void beginMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
        delegate.beginMap(schema, consumer);
        wroteSomething = true;
    }

    @Override
    public void writeBoolean(SdkSchema schema, boolean value) {
        delegate.writeBoolean(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeByte(SdkSchema schema, byte value) {
        delegate.writeByte(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeShort(SdkSchema schema, short value) {
        delegate.writeShort(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        delegate.writeInteger(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeLong(SdkSchema schema, long value) {
        delegate.writeLong(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeFloat(SdkSchema schema, float value) {
        delegate.writeFloat(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeDouble(SdkSchema schema, double value) {
        delegate.writeDouble(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {
        delegate.writeBigInteger(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        delegate.writeBigDecimal(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeString(SdkSchema schema, String value) {
        delegate.writeString(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeBlob(SdkSchema schema, byte[] value) {
        delegate.writeBlob(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {
        delegate.writeTimestamp(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeShape(SdkSchema schema, SerializableShape value) {
        delegate.writeShape(schema, value);
        wroteSomething = true;
    }

    @Override
    public void writeDocument(SdkSchema schema, Any value) {
        delegate.writeDocument(schema, value);
        wroteSomething = true;
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }
}
