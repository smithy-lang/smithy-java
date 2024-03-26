/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.serde.any.Any;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;

/**
 * Helper class that can be used to serialize content between values.
 *
 * <p>This class allows for easily injecting a separator between list values.
 */
public final class ListSerializer implements ShapeSerializer {

    private final ShapeSerializer delegate;
    private final ThrowableRunnable betweenValues;
    private final ThrowableRunnable afterValues;
    private boolean wroteValue = false;

    /**
     * @param delegate      Delegate that does the actual value serialization.
     * @param betweenValues Method to invoke between each value.
     */
    public ListSerializer(ShapeSerializer delegate, ThrowableRunnable betweenValues) {
        this(delegate, betweenValues, () -> {});
    }

    /**
     * @param delegate      Delegate that does the actual value serialization.
     * @param betweenValues Method to invoke between each value.
     * @param afterValues   Method to invoke after each value.
     */
    public ListSerializer(ShapeSerializer delegate, ThrowableRunnable betweenValues, ThrowableRunnable afterValues) {
        this.delegate = delegate;
        this.betweenValues = betweenValues;
        this.afterValues = afterValues;
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    private void beforeWrite() {
        try {
            if (wroteValue) {
                betweenValues.run();
            } else {
                wroteValue = true;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> T afterWrite(T result) {
        if (afterValues != null) {
            try {
                afterValues.run();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return result;
    }

    @Override
    public StructSerializer beginStruct(SdkSchema schema) {
        beforeWrite();

        if (afterValues == null) {
            return delegate.beginStruct(schema);
        } else {
            // Wrap the structure serializer so that afterWrite callback can be invoked.
            StructSerializer delegateSerializer = delegate.beginStruct(schema);
            return afterWrite(new StructSerializer() {
                @Override
                public void endStruct() {
                    delegateSerializer.endStruct();
                    try {
                        afterValues.run();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                @Override
                public void member(SdkSchema member, Consumer<ShapeSerializer> memberWriter) {
                    delegateSerializer.member(member, memberWriter);
                }
            });
        }
    }

    @Override
    public void beginList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        beforeWrite();
        delegate.beginList(schema, consumer);
        afterWrite(null);
    }

    @Override
    public void beginMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
        beforeWrite();
        delegate.beginMap(schema, consumer);
        afterWrite(null);
    }

    @Override
    public void writeBoolean(SdkSchema schema, boolean value) {
        beforeWrite();
        delegate.writeBoolean(schema, value);
        afterWrite(null);
    }

    @Override
    public void writeShort(SdkSchema schema, short value) {
        beforeWrite();
        delegate.writeShort(schema, value);
        afterWrite(null);
    }

    @Override
    public void writeByte(SdkSchema schema, byte value) {
        beforeWrite();
        delegate.writeByte(schema, value);
        afterWrite(null);
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        beforeWrite();
        delegate.writeInteger(schema, value);
        afterWrite(null);
    }

    @Override
    public void writeLong(SdkSchema schema, long value) {
        beforeWrite();
        delegate.writeLong(schema, value);
        afterWrite(null);
    }

    @Override
    public void writeFloat(SdkSchema schema, float value) {
        beforeWrite();
        delegate.writeFloat(schema, value);
        afterWrite(null);
    }

    @Override
    public void writeDouble(SdkSchema schema, double value) {
        beforeWrite();
        delegate.writeDouble(schema, value);
        afterWrite(null);
    }

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {
        beforeWrite();
        delegate.writeBigInteger(schema, value);
        afterWrite(null);
    }

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        beforeWrite();
        delegate.writeBigDecimal(schema, value);
        afterWrite(null);
    }

    @Override
    public void writeString(SdkSchema schema, String value) {
        beforeWrite();
        delegate.writeString(schema, value);
        afterWrite(null);
    }

    @Override
    public void writeBlob(SdkSchema schema, byte[] value) {
        beforeWrite();
        delegate.writeBlob(schema, value);
        afterWrite(null);
    }

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {
        beforeWrite();
        delegate.writeTimestamp(schema, value);
        afterWrite(null);
    }

    @Override
    public void writeShape(SdkSchema schema, SerializableShape value) {
        beforeWrite();
        delegate.writeShape(schema, value);
        afterWrite(null);
    }

    @Override
    public void writeDocument(SdkSchema schema, Any value) {
        beforeWrite();
        delegate.writeDocument(schema, value);
        afterWrite(null);
    }
}
