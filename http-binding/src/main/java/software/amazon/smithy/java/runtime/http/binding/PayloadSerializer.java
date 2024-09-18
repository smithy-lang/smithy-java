/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.Flow;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.common.datastream.DataStream;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class PayloadSerializer implements ShapeSerializer {
    private static final byte[] NULL_BYTES = "null".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TRUE_BYTES = "true".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FALSE_BYTES = "false".getBytes(StandardCharsets.UTF_8);
    private final HttpBindingSerializer serializer;
    private final ShapeSerializer structSerializer;
    private final ByteArrayOutputStream outputStream;
    private boolean payloadWritten = false;

    PayloadSerializer(HttpBindingSerializer serializer, Codec codec) {
        this.serializer = serializer;
        this.outputStream = new ByteArrayOutputStream();
        this.structSerializer = codec.createSerializer(outputStream);
    }

    @Override
    public void writeDataStream(Schema schema, DataStream value) {
        payloadWritten = true;
        serializer.setHttpPayload(schema, value);
    }

    @Override
    public void writeEventStream(
        Schema schema,
        Flow.Publisher<? extends SerializableStruct> value
    ) {
        payloadWritten = true;
        serializer.setEventStream(value);
    }

    private void write(byte[] bytes) {
        try {
            outputStream.write(bytes);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        TimestampFormatter formatter;
        if (schema.hasTrait(TimestampFormatTrait.class)) {
            formatter = TimestampFormatter.of(schema.getTrait(TimestampFormatTrait.class));
        } else {
            formatter = TimestampFormatter.Prelude.EPOCH_SECONDS;
        }
        write(formatter.writeString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        serializer.writePayloadContentType();
        structSerializer.writeDocument(schema, value);
    }

    @Override
    public void writeNull(Schema schema) {
        write(NULL_BYTES);
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        serializer.writePayloadContentType();
        structSerializer.writeStruct(schema, struct);
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        structSerializer.writeList(schema, listState, size, consumer);
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        structSerializer.writeMap(schema, mapState, size, consumer);
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        write(value ? TRUE_BYTES : FALSE_BYTES);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        outputStream.write(value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        write(Short.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        write(Integer.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeLong(Schema schema, long value) {
        write(Long.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        write(Float.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        write(Double.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        write(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        write(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeString(Schema schema, String value) {
        write(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        payloadWritten = true;
        serializer.setHttpPayload(schema, DataStream.ofBytes(value));
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        payloadWritten = true;
        serializer.setHttpPayload(schema, DataStream.ofByteBuffer(value));
    }

    @Override
    public void flush() {
        structSerializer.flush();
        try {
            outputStream.flush();
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void close() {
        structSerializer.close();
        try {
            outputStream.close();
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    public boolean isPayloadWritten() {
        return payloadWritten;
    }

    byte[] toByteArray() {
        return outputStream.toByteArray();
    }
}
