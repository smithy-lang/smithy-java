/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

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
    public void writeStruct(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        beforeWrite();
        delegate.writeStruct(schema, consumer);
    }

    @Override
    public void writeList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        beforeWrite();
        delegate.writeList(schema, consumer);
    }

    @Override
    public void writeMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
        beforeWrite();
        delegate.writeMap(schema, consumer);
    }

    @Override
    public void writeBoolean(SdkSchema schema, boolean value) {
        beforeWrite();
        delegate.writeBoolean(schema, value);
    }

    @Override
    public void writeShort(SdkSchema schema, short value) {
        beforeWrite();
        delegate.writeShort(schema, value);
    }

    @Override
    public void writeByte(SdkSchema schema, byte value) {
        beforeWrite();
        delegate.writeByte(schema, value);
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        beforeWrite();
        delegate.writeInteger(schema, value);
    }

    @Override
    public void writeLong(SdkSchema schema, long value) {
        beforeWrite();
        delegate.writeLong(schema, value);
    }

    @Override
    public void writeFloat(SdkSchema schema, float value) {
        beforeWrite();
        delegate.writeFloat(schema, value);
    }

    @Override
    public void writeDouble(SdkSchema schema, double value) {
        beforeWrite();
        delegate.writeDouble(schema, value);
    }

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {
        beforeWrite();
        delegate.writeBigInteger(schema, value);
    }

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        beforeWrite();
        delegate.writeBigDecimal(schema, value);
    }

    @Override
    public void writeString(SdkSchema schema, String value) {
        beforeWrite();
        delegate.writeString(schema, value);
    }

    @Override
    public void writeBlob(SdkSchema schema, byte[] value) {
        beforeWrite();
        delegate.writeBlob(schema, value);
    }

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {
        beforeWrite();
        delegate.writeTimestamp(schema, value);
    }

    @Override
    public void writeDocument(SdkSchema schema, Document value) {
        beforeWrite();
        delegate.writeDocument(schema, value);
    }
}
