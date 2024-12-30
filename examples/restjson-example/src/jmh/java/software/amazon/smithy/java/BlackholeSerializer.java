/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.ListSerializer;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * Provides a basic serializer implementation that only sends values to a blackhole to avoid dead code elimination
 * from using a null serializer. This class should resemble the same basic layout and operations of a real serializer.
 */
public final class BlackholeSerializer implements ShapeSerializer {

    private final Blackhole bh;

    public BlackholeSerializer(Blackhole bh) {
        this.bh = bh;
    }

    @Override
    public void close() {
        bh.consume(this);
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        struct.serializeMembers(new BhStructureWriter(this));
    }

    private static final class BhStructureWriter extends InterceptingSerializer {
        private final BlackholeSerializer bs;

        private BhStructureWriter(BlackholeSerializer bs) {
            this.bs = bs;
        }

        @Override
        protected ShapeSerializer before(Schema schema) {
            return bs;
        }
    }

    @Override
    public <T> void writeList(Schema schema, T state, int size, BiConsumer<T, ShapeSerializer> consumer) {
        consumer.accept(state, new ListSerializer(this, this::doNothingBetweenValues));
    }

    private void doNothingBetweenValues(int position) {
        bh.consume(position);
    }

    @Override
    public <T> void writeMap(Schema schema, T state, int size, BiConsumer<T, MapSerializer> consumer) {
        consumer.accept(state, new BhMapSerializer(this));
    }

    private record BhMapSerializer(BlackholeSerializer bs) implements MapSerializer {
        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            valueSerializer.accept(state, bs);
        }
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        bh.consume(value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        bh.consume(value);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        bh.consume(value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        bh.consume(value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        bh.consume(value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        bh.consume(value);
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        bh.consume(value);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        bh.consume(value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        bh.consume(value);
    }

    @Override
    public void writeString(Schema schema, String value) {
        bh.consume(value);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        bh.consume(value);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        bh.consume(value);
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        value.serializeContents(this);
    }

    @Override
    public void writeNull(Schema schema) {
        bh.consume(this);
    }
}
