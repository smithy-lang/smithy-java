/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * Helper class that can be used to serialize content between values.
 *
 * <p>This class allows for easily injecting a separator between list values.
 */
public final class ListSerializer implements ShapeSerializer {

    private final ShapeSerializer delegate;
    private final IntConsumer beforeEachValue;
    private int position = 0;

    /**
     * @param delegate        Delegate that does the actual value serialization.
     * @param beforeEachValue Invoked before each value and given the current position in the list.
     */
    public ListSerializer(ShapeSerializer delegate, IntConsumer beforeEachValue) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is null");
        this.beforeEachValue = Objects.requireNonNull(beforeEachValue, "beforeEachValue is null");
    }

    private void beforeWrite() {
        beforeEachValue.accept(position++);
    }

    /**
     * Get the current index of the serializer where the next element would be written.
     *
     * @return the current index.
     */
    public int position() {
        return position;
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        beforeWrite();
        delegate.writeStruct(schema, struct);
    }

    @Override
    public <T> void writeList(Schema schema, T state, int size, BiConsumer<T, ShapeSerializer> consumer) {
        beforeWrite();
        delegate.writeList(schema, state, size, consumer);
    }

    @Override
    public <T> void writeMap(Schema schema, T state, int size, BiConsumer<T, MapSerializer> consumer) {
        beforeWrite();
        delegate.writeMap(schema, state, size, consumer);
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        beforeWrite();
        delegate.writeBoolean(schema, value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        beforeWrite();
        delegate.writeShort(schema, value);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        beforeWrite();
        delegate.writeByte(schema, value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        beforeWrite();
        delegate.writeInteger(schema, value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        beforeWrite();
        delegate.writeLong(schema, value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        beforeWrite();
        delegate.writeFloat(schema, value);
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        beforeWrite();
        delegate.writeDouble(schema, value);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        beforeWrite();
        delegate.writeBigInteger(schema, value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        beforeWrite();
        delegate.writeBigDecimal(schema, value);
    }

    @Override
    public void writeString(Schema schema, String value) {
        beforeWrite();
        delegate.writeString(schema, value);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        beforeWrite();
        delegate.writeBlob(schema, value);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        beforeWrite();
        delegate.writeTimestamp(schema, value);
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        beforeWrite();
        delegate.writeDocument(schema, value);
    }

    @Override
    public void writeNull(Schema schema) {
        beforeWrite();
        delegate.writeNull(schema);
    }
}
