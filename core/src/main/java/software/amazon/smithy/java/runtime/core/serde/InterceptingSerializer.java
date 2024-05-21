/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

/**
 * Intercepts serialization before and after each write.
 *
 * <p>{@link #before(SdkSchema)} is responsible for returning the {@link ShapeSerializer} used to actually perform a
 * write, making the method act as a router between serializers, predicated on the given schema.
 */
public abstract class InterceptingSerializer implements ShapeSerializer {

    /**
     * Called before writing and returns the writer to delegate to.
     *
     * @param schema Schema of the shape about to be written.
     * @return the serializer that is to be used to write this schema.
     */
    protected abstract ShapeSerializer before(SdkSchema schema);

    /**
     * Called after the delegated serializer is called.
     *
     * @param schema Schema that was serialized.
     */
    protected void after(SdkSchema schema) {}

    @Override
    public void writeStruct(SdkSchema schema, SerializableStruct struct) {
        before(schema).writeStruct(schema, struct);
        after(schema);
    }

    @Override
    public final <T> void writeList(SdkSchema schema, T listState, BiConsumer<T, ShapeSerializer> consumer) {
        before(schema).writeList(schema, listState, consumer);
        after(schema);
    }

    @Override
    public final <T> void writeMap(SdkSchema schema, T mapState, BiConsumer<T, MapSerializer> consumer) {
        before(schema).writeMap(schema, mapState, consumer);
        after(schema);
    }

    @Override
    public final void writeBoolean(SdkSchema schema, boolean value) {
        before(schema).writeBoolean(schema, value);
        after(schema);
    }

    @Override
    public final void writeShort(SdkSchema schema, short value) {
        before(schema).writeShort(schema, value);
        after(schema);
    }

    @Override
    public final void writeByte(SdkSchema schema, byte value) {
        before(schema).writeByte(schema, value);
        after(schema);
    }

    @Override
    public final void writeInteger(SdkSchema schema, int value) {
        before(schema).writeInteger(schema, value);
        after(schema);
    }

    @Override
    public final void writeLong(SdkSchema schema, long value) {
        before(schema).writeLong(schema, value);
        after(schema);
    }

    @Override
    public final void writeFloat(SdkSchema schema, float value) {
        before(schema).writeFloat(schema, value);
        after(schema);
    }

    @Override
    public final void writeDouble(SdkSchema schema, double value) {
        before(schema).writeDouble(schema, value);
        after(schema);
    }

    @Override
    public final void writeBigInteger(SdkSchema schema, BigInteger value) {
        before(schema).writeBigInteger(schema, value);
        after(schema);
    }

    @Override
    public final void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        before(schema).writeBigDecimal(schema, value);
        after(schema);
    }

    @Override
    public final void writeString(SdkSchema schema, String value) {
        before(schema).writeString(schema, value);
        after(schema);
    }

    @Override
    public final void writeBlob(SdkSchema schema, byte[] value) {
        before(schema).writeBlob(schema, value);
        after(schema);
    }

    @Override
    public final void writeTimestamp(SdkSchema schema, Instant value) {
        before(schema).writeTimestamp(schema, value);
        after(schema);
    }

    @Override
    public final void writeDocument(SdkSchema schema, Document value) {
        before(schema).writeDocument(schema, value);
        after(schema);
    }

    @Override
    public final void writeDocument(Document value) {
        ShapeSerializer.super.writeDocument(value);
    }

    @Override
    public final void writeNull(SdkSchema schema) {
        before(schema).writeNull(schema);
        after(schema);
    }
}
