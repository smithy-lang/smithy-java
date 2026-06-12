/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.io.ByteBufferOutputStream;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Buffers the single {@code @httpPayload}-bound member into a {@link ByteBuffer}.
 *
 * <p>Because a payload binds exactly one member, this serializer receives exactly one top-level
 * write call.
 *
 * <ul>
 *   <li>blob / data stream / event stream payloads hand the body off by reference via
 *       {@link HttpBindingSerializer#setHttpPayload};</li>
 *   <li>scalar payloads (string, number, boolean, timestamp) write their bytes directly into a
 *       lazily-allocated {@link ByteBufferOutputStream};</li>
 *   <li>structured payloads (struct, document, list, map) are serialized through the pooled
 *       {@link Codec#serialize(SerializableShape)} path, which for JSON borrows a serializer from a
 *       striped pool instead of allocating one.</li>
 * </ul>
 */
final class PayloadSerializer implements ShapeSerializer {
    private static final byte[] NULL_BYTES = "null".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TRUE_BYTES = "true".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FALSE_BYTES = "false".getBytes(StandardCharsets.UTF_8);
    private final HttpBindingSerializer serializer;
    private final Codec codec;
    private boolean payloadWritten = false;
    private ByteBuffer codecResult;
    private ByteBufferOutputStream scalarStream;

    PayloadSerializer(HttpBindingSerializer serializer, Codec codec) {
        this.serializer = serializer;
        this.codec = codec;
    }

    @Override
    public void writeDataStream(Schema schema, DataStream value) {
        payloadWritten = true;
        serializer.setHttpPayload(schema, value);
    }

    @Override
    public void writeEventStream(Schema schema, EventStream<? extends SerializableStruct> value) {
        payloadWritten = true;
        serializer.setEventStream(value.asWriter());
    }

    private ByteBufferOutputStream scalarStream() {
        if (scalarStream == null) {
            scalarStream = new ByteBufferOutputStream();
        }
        return scalarStream;
    }

    private void write(byte[] bytes) {
        try {
            scalarStream().write(bytes);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        TimestampFormatter formatter;
        var trait = schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
        if (trait != null) {
            formatter = TimestampFormatter.of(trait);
        } else {
            formatter = TimestampFormatter.Prelude.EPOCH_SECONDS;
        }
        write(formatter.writeString(value).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeDocument(schema, value));
    }

    @Override
    public void writeNull(Schema schema) {
        write(NULL_BYTES);
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeStruct(schema, struct));
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        serializer.writePayloadContentType();
        codecResult = codec.serialize(ser -> ser.writeList(schema, listState, size, consumer));
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        codecResult = codec.serialize(ser -> ser.writeMap(schema, mapState, size, consumer));
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        write(value ? TRUE_BYTES : FALSE_BYTES);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        scalarStream().write(value);
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

    public boolean isPayloadWritten() {
        return payloadWritten;
    }

    ByteBuffer toByteBuffer() {
        if (codecResult != null) {
            return codecResult;
        } else if (scalarStream != null) {
            return scalarStream.toByteBuffer();
        } else {
            return ByteBuffer.allocate(0);
        }
    }
}
