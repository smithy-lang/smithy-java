/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

/**
 * Intercepts serialization before and after each write.
 *
 * <p>{@link #before(Schema)} is responsible for returning the {@link ShapeSerializer} used to actually perform a
 * write, making the method act as a router between serializers, predicated on the given schema.
 */
public abstract class InterceptingSerializer implements ShapeSerializer {

    /**
     * Called before writing and returns the writer to delegate to.
     *
     * @param schema Schema of the shape about to be written.
     * @return the serializer that is to be used to write this schema.
     */
    protected abstract ShapeSerializer before(Schema schema);

    /**
     * Called after the delegated serializer is called.
     *
     * @param schema Schema that was serialized.
     */
    protected void after(Schema schema) {}

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        before(schema).writeStruct(schema, struct);
        after(schema);
    }

    @Override
    public final <T> void writeList(Schema schema, T listState, BiConsumer<T, ShapeSerializer> consumer) {
        before(schema).writeList(schema, listState, consumer);
        after(schema);
    }

    @Override
    public final <T> void writeMap(Schema schema, T mapState, BiConsumer<T, MapSerializer> consumer) {
        before(schema).writeMap(schema, mapState, consumer);
        after(schema);
    }

    @Override
    public final void writeBoolean(Schema schema, boolean value) {
        before(schema).writeBoolean(schema, value);
        after(schema);
    }

    @Override
    public final void writeShort(Schema schema, short value) {
        before(schema).writeShort(schema, value);
        after(schema);
    }

    @Override
    public final void writeByte(Schema schema, byte value) {
        before(schema).writeByte(schema, value);
        after(schema);
    }

    @Override
    public final void writeInteger(Schema schema, int value) {
        before(schema).writeInteger(schema, value);
        after(schema);
    }

    @Override
    public final void writeLong(Schema schema, long value) {
        before(schema).writeLong(schema, value);
        after(schema);
    }

    @Override
    public final void writeFloat(Schema schema, float value) {
        before(schema).writeFloat(schema, value);
        after(schema);
    }

    @Override
    public final void writeDouble(Schema schema, double value) {
        before(schema).writeDouble(schema, value);
        after(schema);
    }

    @Override
    public final void writeBigInteger(Schema schema, BigInteger value) {
        before(schema).writeBigInteger(schema, value);
        after(schema);
    }

    @Override
    public final void writeBigDecimal(Schema schema, BigDecimal value) {
        before(schema).writeBigDecimal(schema, value);
        after(schema);
    }

    @Override
    public final void writeString(Schema schema, String value) {
        before(schema).writeString(schema, value);
        after(schema);
    }

    @Override
    public final void writeBlob(Schema schema, ByteBuffer value) {
        before(schema).writeBlob(schema, value);
        after(schema);
    }

    @Override
    public final void writeTimestamp(Schema schema, Instant value) {
        before(schema).writeTimestamp(schema, value);
        after(schema);
    }

    @Override
    public final void writeDocument(Schema schema, Document value) {
        before(schema).writeDocument(schema, value);
        after(schema);
    }

    @Override
    public final void writeNull(Schema schema) {
        before(schema).writeNull(schema);
        after(schema);
    }
}
