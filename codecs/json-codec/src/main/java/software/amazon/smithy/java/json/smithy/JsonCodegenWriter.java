/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import software.amazon.smithy.java.codecs.commons.CompactStringAccess;
import software.amazon.smithy.java.codecs.commons.NumberCodec;
import software.amazon.smithy.java.codecs.commons.StripedPool;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.JsonSettings;

final class JsonCodegenWriter {
    private static final int DEFAULT_CAPACITY = 8192;
    private static final int MAX_CACHEABLE_CAPACITY = DEFAULT_CAPACITY * 16;
    private static final int MAX_DEPTH = 64;
    private static final StripedPool<JsonCodegenWriter, JsonSettings> POOL = new WriterPool();

    private byte[] bytes = new byte[DEFAULT_CAPACITY];
    private int position;
    private int depth;
    private final boolean[] needsComma = new boolean[MAX_DEPTH];
    private JsonSettings settings;
    private OutputStream sink;

    private JsonCodegenWriter(JsonSettings settings) {
        this.settings = settings;
    }

    static JsonCodegenWriter acquire(JsonSettings settings) {
        return POOL.acquire(settings);
    }

    static void release(JsonCodegenWriter writer) {
        POOL.release(writer);
    }

    void sink(OutputStream sink) {
        this.sink = sink;
    }

    ByteBuffer detach() {
        return ByteBuffer.wrap(Arrays.copyOf(bytes, position));
    }

    int size() {
        return position;
    }

    void flush() {
        if (sink == null || position == 0) {
            return;
        }
        try {
            sink.write(bytes, 0, position);
            position = 0;
            sink.flush();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    void beginObject() {
        ensure(1);
        bytes[position++] = '{';
        push();
    }

    void endObject() {
        pop();
        ensure(1);
        bytes[position++] = '}';
    }

    void beginArray() {
        ensure(1);
        bytes[position++] = '[';
        push();
    }

    void endArray() {
        pop();
        ensure(1);
        bytes[position++] = ']';
    }

    void field(byte[] token) {
        separator();
        ensure(token.length);
        position = copyToken(token, position);
    }

    void field(byte[] token, int index) {
        ensure(token.length + 1);
        if (index != 0) {
            bytes[position++] = ',';
        }
        position = copyToken(token, position);
    }

    // Overlapping word stores avoid arraycopy overhead without writing past the token.
    private int copyToken(byte[] token, int pos) {
        int length = token.length;
        if (length <= Long.BYTES * 2) {
            if (length >= Long.BYTES) {
                int tail = length - Long.BYTES;
                JsonReadUtils.writeLongLittleEndian(bytes, pos, JsonReadUtils.readLongLittleEndian(token, 0));
                JsonReadUtils.writeLongLittleEndian(
                        bytes,
                        pos + tail,
                        JsonReadUtils.readLongLittleEndian(token, tail));
                return pos + length;
            }
            if (length >= Integer.BYTES) {
                int tail = length - Integer.BYTES;
                JsonReadUtils.writeIntLittleEndian(bytes, pos, JsonReadUtils.readIntLittleEndian(token, 0));
                JsonReadUtils.writeIntLittleEndian(
                        bytes,
                        pos + tail,
                        JsonReadUtils.readIntLittleEndian(token, tail));
                return pos + length;
            }
        }
        System.arraycopy(token, 0, bytes, pos, length);
        return pos + length;
    }

    void fieldString(byte[] token, int index, String value) {
        byte[] latin1 = CompactStringAccess.latin1Bytes(value);
        ensure(token.length + 1
                + (latin1 != null
                        ? latin1.length + 2
                        : JsonWriteUtils.maxQuotedStringBytes(value)));
        if (index != 0) {
            bytes[position++] = ',';
        }
        position = copyToken(token, position);
        if (latin1 != null) {
            int next = JsonWriteUtils.writeQuotedAscii(bytes, position, latin1);
            if (next >= 0) {
                position = next;
                return;
            }
            ensure(JsonWriteUtils.maxQuotedStringBytes(value));
        }
        position = JsonWriteUtils.writeQuotedStringGeneral(bytes, position, value);
    }

    void element() {
        separator();
    }

    void element(int index) {
        if (index != 0) {
            ensure(1);
            bytes[position++] = ',';
        }
    }

    void dynamicField(String name) {
        separator();
        writeString(name);
        ensure(1);
        bytes[position++] = ':';
    }

    void dynamicField(String name, int index) {
        if (index != 0) {
            ensure(1);
            bytes[position++] = ',';
        }
        writeString(name);
        ensure(1);
        bytes[position++] = ':';
    }

    void writeNull() {
        ensure(4);
        bytes[position++] = 'n';
        bytes[position++] = 'u';
        bytes[position++] = 'l';
        bytes[position++] = 'l';
    }

    void writeBoolean(boolean value) {
        ensure(5);
        position = NumberCodec.writeBoolean(bytes, position, value);
    }

    void writeByte(byte value) {
        ensure(4);
        position = JsonWriteUtils.writeInt(bytes, position, value);
    }

    void writeShort(short value) {
        ensure(6);
        position = JsonWriteUtils.writeInt(bytes, position, value);
    }

    void writeInteger(int value) {
        ensure(11);
        position = JsonWriteUtils.writeInt(bytes, position, value);
    }

    void writeLong(long value) {
        ensure(20);
        position = JsonWriteUtils.writeLong(bytes, position, value);
    }

    void writeFloat(float value) {
        ensure(24);
        position = NumberCodec.writeFloatFullQuoted(bytes, position, value);
    }

    void writeDouble(double value) {
        ensure(24);
        position = NumberCodec.writeDoubleFullQuoted(bytes, position, value);
    }

    void writeBigInteger(BigInteger value) {
        ensure(value.bitLength() / 3 + 4);
        if (settings.useStringForArbitraryPrecision()) {
            bytes[position++] = '"';
            position = NumberCodec.writeBigInteger(bytes, position, value);
            bytes[position++] = '"';
        } else {
            position = NumberCodec.writeBigInteger(bytes, position, value);
        }
    }

    void writeBigDecimal(BigDecimal value) {
        ensure(NumberCodec.maxBigDecimalLength(value) + 2);
        if (settings.useStringForArbitraryPrecision()) {
            bytes[position++] = '"';
            position = NumberCodec.writeBigDecimal(bytes, position, value);
            bytes[position++] = '"';
        } else {
            position = NumberCodec.writeBigDecimal(bytes, position, value);
        }
    }

    void writeString(String value) {
        byte[] latin1 = CompactStringAccess.latin1Bytes(value);
        if (latin1 != null) {
            ensure(latin1.length + 2);
            int next = JsonWriteUtils.writeQuotedAscii(bytes, position, latin1);
            if (next >= 0) {
                position = next;
                return;
            }
        }
        ensure(JsonWriteUtils.maxQuotedStringBytes(value));
        position = JsonWriteUtils.writeQuotedStringGeneral(bytes, position, value);
    }

    private byte[] base64Scratch;

    void writeBlob(ByteBuffer value) {
        int remaining = value.remaining();
        ensure(ByteBufferUtils.base64EncodedSize(remaining) + 2);
        if (remaining >= 32) {
            byte[] b = bytes;
            int p = position;
            b[p++] = '"';
            p += ByteBufferUtils.base64EncodeTo(value, b, p, base64Scratch(remaining));
            b[p++] = '"';
            position = p;
            return;
        }
        position = JsonWriteUtils.writeBase64String(bytes, position, value);
    }

    private byte[] base64Scratch(int dataLen) {
        int capacity = ByteBufferUtils.base64EncodedSize(dataLen);
        byte[] scratch = base64Scratch;
        if (scratch == null || scratch.length < capacity) {
            scratch = base64Scratch = new byte[capacity];
        }
        return scratch;
    }

    void writeTimestamp(Instant value, int format) {
        switch (format) {
            case 1 -> {
                ensure(42);
                position = JsonWriteUtils.writeIso8601Timestamp(bytes, position, value);
            }
            case 2 -> {
                ensure(35);
                position = JsonWriteUtils.writeHttpDate(bytes, position, value);
            }
            default -> {
                ensure(30);
                position = JsonWriteUtils.writeEpochSeconds(
                        bytes,
                        position,
                        value.getEpochSecond(),
                        value.getNano());
            }
        }
    }

    void writeDocument(Document document) {
        if (document == null) {
            writeNull();
            return;
        }
        SmithyJsonSerializer serializer = SmithyJsonSerializer.acquire(settings);
        boolean failed = true;
        try {
            serializer.writeDocument(PreludeSchemas.DOCUMENT, document);
            ByteBuffer encoded = serializer.extractResult();
            ensure(encoded.remaining());
            int length = encoded.remaining();
            encoded.get(bytes, position, length);
            position += length;
            failed = false;
        } finally {
            SmithyJsonSerializer.release(serializer, failed);
        }
    }

    private void push() {
        if (++depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
    }

    private void pop() {
        needsComma[depth] = false;
        depth--;
    }

    private void separator() {
        ensure(1);
        if (needsComma[depth]) {
            bytes[position++] = ',';
        } else {
            needsComma[depth] = true;
        }
    }

    private void ensure(int needed) {
        if (position + needed > bytes.length) {
            grow(needed);
        }
    }

    private void grow(int needed) {
        bytes = Arrays.copyOf(bytes, Math.max(bytes.length * 2, position + needed));
    }

    private static final class WriterPool extends StripedPool<JsonCodegenWriter, JsonSettings> {
        @Override
        protected JsonCodegenWriter create(JsonSettings settings) {
            return new JsonCodegenWriter(settings);
        }

        @Override
        protected void cleanup(JsonCodegenWriter writer) {
            writer.sink = null;
            writer.position = 0;
            writer.depth = 0;
        }

        @Override
        protected boolean canPool(JsonCodegenWriter writer) {
            return writer.bytes != null;
        }

        @Override
        protected void prepareForPool(JsonCodegenWriter writer) {
            if (writer.bytes.length > MAX_CACHEABLE_CAPACITY) {
                writer.bytes = new byte[DEFAULT_CAPACITY];
            }
            if (writer.base64Scratch != null && writer.base64Scratch.length > MAX_CACHEABLE_CAPACITY) {
                writer.base64Scratch = null;
            }
        }

        @Override
        protected boolean reset(JsonCodegenWriter writer, JsonSettings settings) {
            writer.settings = settings;
            return true;
        }
    }
}
