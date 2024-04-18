/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

final class ConsolidatedSerializer implements ShapeSerializer {

    private final BiConsumer<SdkSchema, Consumer<ShapeSerializer>> delegate;

    ConsolidatedSerializer(BiConsumer<SdkSchema, Consumer<ShapeSerializer>> delegate) {
        this.delegate = delegate;
    }

    private void write(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        delegate.accept(schema, consumer);
    }

    @Override
    public void writeStruct(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        write(schema, ser -> ser.writeStruct(schema, consumer));
    }

    @Override
    public void writeList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        write(schema, ser -> ser.writeList(schema, consumer));
    }

    @Override
    public void writeMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
        write(schema, ser -> ser.writeMap(schema, consumer));
    }

    @Override
    public void writeBoolean(SdkSchema schema, boolean value) {
        write(schema, ser -> ser.writeBoolean(schema, value));
    }

    @Override
    public void writeByte(SdkSchema schema, byte value) {
        write(schema, ser -> ser.writeByte(schema, value));
    }

    @Override
    public void writeShort(SdkSchema schema, short value) {
        write(schema, ser -> ser.writeShort(schema, value));
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        write(schema, ser -> ser.writeInteger(schema, value));
    }

    @Override
    public void writeLong(SdkSchema schema, long value) {
        write(schema, ser -> ser.writeLong(schema, value));
    }

    @Override
    public void writeFloat(SdkSchema schema, float value) {
        write(schema, ser -> ser.writeFloat(schema, value));
    }

    @Override
    public void writeDouble(SdkSchema schema, double value) {
        write(schema, ser -> ser.writeDouble(schema, value));
    }

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {
        write(schema, ser -> ser.writeBigInteger(schema, value));
    }

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        write(schema, ser -> ser.writeBigDecimal(schema, value));
    }

    @Override
    public void writeString(SdkSchema schema, String value) {
        write(schema, ser -> ser.writeString(schema, value));
    }

    @Override
    public void writeBlob(SdkSchema schema, byte[] value) {
        write(schema, ser -> ser.writeBlob(schema, value));
    }

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {
        write(schema, ser -> ser.writeTimestamp(schema, value));
    }

    @Override
    public void writeDocument(SdkSchema schema, Document value) {
        write(schema, ser -> ser.writeDocument(schema, value));
    }

    @Override
    public void writeNull(SdkSchema schema) {
        write(schema, ser -> ser.writeNull(schema));
    }
}
