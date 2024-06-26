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
 * Serializes nothing.
 *
 * @see ShapeSerializer#nullSerializer()
 */
final class NullSerializer implements ShapeSerializer {

    static final NullSerializer INSTANCE = new NullSerializer();

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {}

    @Override
    public <T> void writeList(Schema schema, T listState, BiConsumer<T, ShapeSerializer> consumer) {}

    @Override
    public <T> void writeMap(Schema schema, T mapState, BiConsumer<T, MapSerializer> consumer) {}

    @Override
    public void writeBoolean(Schema schema, boolean value) {}

    @Override
    public void writeByte(Schema schema, byte value) {}

    @Override
    public void writeShort(Schema schema, short value) {}

    @Override
    public void writeInteger(Schema schema, int value) {}

    @Override
    public void writeLong(Schema schema, long value) {}

    @Override
    public void writeFloat(Schema schema, float value) {}

    @Override
    public void writeDouble(Schema schema, double value) {}

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {}

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {}

    @Override
    public void writeString(Schema schema, String value) {}

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {}

    @Override
    public void writeTimestamp(Schema schema, Instant value) {}

    @Override
    public void writeDocument(Schema schema, Document value) {}

    @Override
    public void writeNull(Schema schema) {}
}
