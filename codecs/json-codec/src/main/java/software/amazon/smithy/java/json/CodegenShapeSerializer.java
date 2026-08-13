/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.json.smithy.SmithyGeneratedJsonSerde;

final class CodegenShapeSerializer implements ShapeSerializer {
    private final OutputStream sink;
    private final JsonSerdeProvider delegateProvider;
    private final SmithyGeneratedJsonSerde generated;
    private final JsonSettings settings;
    private ShapeSerializer delegate;

    CodegenShapeSerializer(
            OutputStream sink,
            JsonSerdeProvider delegateProvider,
            SmithyGeneratedJsonSerde generated,
            JsonSettings settings
    ) {
        this.sink = sink;
        this.delegateProvider = delegateProvider;
        this.generated = generated;
        this.settings = settings;
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        if (!generated.serializeTo(struct, sink, settings)) {
            delegate().writeStruct(schema, struct);
        }
    }

    @Override
    public <T> void writeList(Schema schema, T state, int size, BiConsumer<T, ShapeSerializer> consumer) {
        delegate().writeList(schema, state, size, consumer);
    }

    @Override
    public <T> void writeMap(Schema schema, T state, int size, BiConsumer<T, MapSerializer> consumer) {
        delegate().writeMap(schema, state, size, consumer);
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        delegate().writeBoolean(schema, value);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        delegate().writeByte(schema, value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        delegate().writeShort(schema, value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        delegate().writeInteger(schema, value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        delegate().writeLong(schema, value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        delegate().writeFloat(schema, value);
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        delegate().writeDouble(schema, value);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        delegate().writeBigInteger(schema, value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        delegate().writeBigDecimal(schema, value);
    }

    @Override
    public void writeString(Schema schema, String value) {
        delegate().writeString(schema, value);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        delegate().writeBlob(schema, value);
    }

    @Override
    public void writeDataStream(Schema schema, DataStream value) {
        delegate().writeDataStream(schema, value);
    }

    @Override
    public void writeEventStream(Schema schema, EventStream<? extends SerializableStruct> value) {
        delegate().writeEventStream(schema, value);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        delegate().writeTimestamp(schema, value);
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        delegate().writeDocument(schema, value);
    }

    @Override
    public void writeNull(Schema schema) {
        delegate().writeNull(schema);
    }

    @Override
    public void flush() {
        if (delegate != null) {
            delegate.flush();
        }
    }

    @Override
    public void close() {
        if (delegate != null) {
            delegate.close();
        }
    }

    private ShapeSerializer delegate() {
        if (delegate == null) {
            delegate = delegateProvider.newSerializer(sink, settings);
        }
        return delegate;
    }
}
