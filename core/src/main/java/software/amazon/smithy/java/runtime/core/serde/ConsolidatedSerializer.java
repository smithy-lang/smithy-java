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
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

final class ConsolidatedSerializer implements ShapeSerializer {

    private final BiConsumer<Schema, Consumer<ShapeSerializer>> delegate;

    ConsolidatedSerializer(BiConsumer<Schema, Consumer<ShapeSerializer>> delegate) {
        this.delegate = delegate;
    }

    private void write(Schema schema, Consumer<ShapeSerializer> consumer) {
        delegate.accept(schema, consumer);
    }

    @Override
    public void writeStruct(Schema schema, Consumer<ShapeSerializer> consumer) {
        write(schema, ser -> ser.writeStruct(schema, consumer));
    }

    @Override
    public void writeList(Schema schema, Consumer<ShapeSerializer> consumer) {
        write(schema, ser -> ser.writeList(schema, consumer));
    }

    @Override
    public void writeMap(Schema schema, Consumer<MapSerializer> consumer) {
        write(schema, ser -> ser.writeMap(schema, consumer));
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        write(schema, ser -> ser.writeBoolean(schema, value));
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        write(schema, ser -> ser.writeByte(schema, value));
    }

    @Override
    public void writeShort(Schema schema, short value) {
        write(schema, ser -> ser.writeShort(schema, value));
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        write(schema, ser -> ser.writeInteger(schema, value));
    }

    @Override
    public void writeLong(Schema schema, long value) {
        write(schema, ser -> ser.writeLong(schema, value));
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        write(schema, ser -> ser.writeFloat(schema, value));
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        write(schema, ser -> ser.writeDouble(schema, value));
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        write(schema, ser -> ser.writeBigInteger(schema, value));
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        write(schema, ser -> ser.writeBigDecimal(schema, value));
    }

    @Override
    public void writeString(Schema schema, String value) {
        write(schema, ser -> ser.writeString(schema, value));
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        write(schema, ser -> ser.writeBlob(schema, value));
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        write(schema, ser -> ser.writeTimestamp(schema, value));
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        write(schema, ser -> ser.writeDocument(schema, value));
    }

    @Override
    public void writeNull(Schema schema) {
        write(schema, ser -> ser.writeNull(schema));
    }
}
