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
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.codecs.commons.NumberCodec;
import software.amazon.smithy.java.codecs.commons.OutputBuffers;
import software.amazon.smithy.java.codecs.commons.StripedPool;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.json.JsonFieldMapper;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * JSON serializer that writes directly to a byte array buffer.
 *
 * <p>Uses pre-computed field name byte arrays from {@link SmithyJsonSchemaExtensions}
 * for field name emission via {@link System#arraycopy}.
 */
final class SmithyJsonSerializer implements ShapeSerializer {

    private static final int MAX_DEPTH = 64;
    private static final int DEFAULT_BUF_SIZE = 8192;
    private static final int MAX_CACHEABLE_SCRATCH = DEFAULT_BUF_SIZE * 16;

    private static final StripedPool<SmithyJsonSerializer, JsonSettings> POOL = new JsonStripedPool();

    private byte[] buf;
    private byte[] base64Scratch;
    private int pos;
    private int sizeHint = DEFAULT_BUF_SIZE;
    private final OutputStream sink;
    private JsonSettings settings;
    private OutputBuffers pool;
    private boolean useJsonName;

    // Nesting state for comma insertion
    private int depth;
    private final boolean[] needsComma = new boolean[MAX_DEPTH];

    // Cached field name table for the current struct being serialized.
    // Resolved once per writeStruct call, then used for all member writes.
    private byte[][] currentFieldNameTable;

    private final ShapeSerializer structSerializer = new StructSerializer();
    private final ShapeSerializer listElementSerializer = new ListElementSerializer();
    private final MapSerializer mapSerializer = new SmithyMapSerializer();
    private SerializeDocumentContents serializeDocumentContents;

    SmithyJsonSerializer(OutputStream sink, JsonSettings settings) {
        this.sink = sink;
        this.settings = settings;
        this.useJsonName = settings.fieldMapper() instanceof JsonFieldMapper.UseJsonNameTrait;
        this.pool = settings.outputBuffers();
        this.buf = pool.acquire(DEFAULT_BUF_SIZE);
        this.pos = 0;
        this.depth = 0;
    }

    /**
     * Creates a serializer for direct buffer extraction (no OutputStream).
     * Use with {@link #acquire} for the pooled path, or construct directly for one-off use.
     */
    SmithyJsonSerializer(JsonSettings settings) {
        this.sink = null;
        this.settings = settings;
        this.useJsonName = settings.fieldMapper() instanceof JsonFieldMapper.UseJsonNameTrait;
        this.pool = settings.outputBuffers();
        this.buf = pool.acquire(DEFAULT_BUF_SIZE);
        this.pos = 0;
        this.depth = 0;
    }

    static SmithyJsonSerializer acquire(JsonSettings settings) {
        return POOL.acquire(settings);
    }

    static void release(SmithyJsonSerializer serializer, boolean exception) {
        if (exception) {
            Arrays.fill(serializer.needsComma, false);
        }
        POOL.release(serializer);
    }

    ByteBuffer extractResult() {
        sizeHint = OutputBuffers.nextSizeHint(sizeHint, pos);
        return ByteBuffer.wrap(Arrays.copyOf(buf, pos));
    }

    DataStream detach(String contentType) {
        byte[] detached = buf;
        int length = pos;
        sizeHint = OutputBuffers.nextSizeHint(sizeHint, length);
        buf = null;
        pos = 0;
        return DataStream.ofLeasedBytes(detached, 0, length, contentType, pool.recycler());
    }

    private void releaseBuffer() {
        if (buf != null) {
            pool.release(buf);
            buf = null;
        }
    }

    private void ensureCapacity(int needed) {
        if (pos + needed > buf.length) {
            grow(needed);
        }
    }

    // Separate cold grow method helps JIT inline ensureCapacity's fast path
    private void grow(int needed) {
        buf = pool.grow(buf, pos, pos + needed);
    }

    /** Writes with the exact ASCII reservation, widening only when needed. */
    private void writeQuotedStringExact(String value) {
        writeQuotedStringExact(value, 0);
    }

    private void writeQuotedStringExact(String value, int trailingBytes) {
        int next = JsonWriteUtils.tryWriteQuotedAscii(buf, pos, value);
        if (next < 0) {
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(value) + trailingBytes);
            next = JsonWriteUtils.writeQuotedStringGeneral(buf, pos, value);
        }
        pos = next;
    }

    @Override
    public void flush() {
        try {
            if (sink != null && pos > 0) {
                sink.write(buf, 0, pos);
                pos = 0;
                sink.flush();
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void close() {
        try {
            if (sink != null && pos > 0) {
                sink.write(buf, 0, pos);
                pos = 0;
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        } finally {
            releaseBuffer();
        }
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        ensureCapacity(5);
        pos = NumberCodec.writeBoolean(buf, pos, value);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        ensureCapacity(4);
        pos = JsonWriteUtils.writeInt(buf, pos, value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        ensureCapacity(6);
        pos = JsonWriteUtils.writeInt(buf, pos, value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        ensureCapacity(11);
        pos = JsonWriteUtils.writeInt(buf, pos, value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        ensureCapacity(20);
        pos = JsonWriteUtils.writeLong(buf, pos, value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        ensureCapacity(NumberCodec.FLOAT_MAX_BYTES);
        pos = NumberCodec.writeFloatFullQuoted(buf, pos, value);
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        ensureCapacity(NumberCodec.DOUBLE_MAX_BYTES);
        pos = NumberCodec.writeDoubleFullQuoted(buf, pos, value);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        int maxLen = value.bitLength() / 3 + 2;
        ensureCapacity(maxLen + 2);
        if (settings.useStringForArbitraryPrecision()) {
            buf[pos++] = '"';
            pos = NumberCodec.writeBigInteger(buf, pos, value);
            buf[pos++] = '"';
        } else {
            pos = NumberCodec.writeBigInteger(buf, pos, value);
        }
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        ensureCapacity(NumberCodec.maxBigDecimalLength(value) + 2);
        if (settings.useStringForArbitraryPrecision()) {
            buf[pos++] = '"';
            pos = NumberCodec.writeBigDecimal(buf, pos, value);
            buf[pos++] = '"';
        } else {
            pos = NumberCodec.writeBigDecimal(buf, pos, value);
        }
    }

    @Override
    public void writeString(Schema schema, String value) {
        ensureCapacity(value.length() + 2);
        writeQuotedStringExact(value);
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        writeBlobValue(ByteBuffer.wrap(value));
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        writeBlobValue(value);
    }

    private void writeBlobValue(ByteBuffer value) {
        int remaining = value.remaining();
        ensureCapacity(ByteBufferUtils.base64EncodedSize(remaining) + 2);
        if (remaining >= 32) {
            byte[] b = buf;
            int p = pos;
            b[p++] = '"';
            p += ByteBufferUtils.base64EncodeTo(value, b, p, base64Scratch(remaining));
            b[p++] = '"';
            pos = p;
            return;
        }
        pos = JsonWriteUtils.writeBase64String(buf, pos, value);
    }

    private byte[] base64Scratch(int dataLen) {
        int capacity = ByteBufferUtils.base64EncodedSize(dataLen);
        byte[] scratch = base64Scratch;
        if (scratch == null || scratch.length < capacity) {
            scratch = base64Scratch = new byte[capacity];
        }
        return scratch;
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        var format = settings.timestampResolver().resolve(schema);
        if (format == TimestampFormatter.Prelude.EPOCH_SECONDS) {
            // '-' + 19 digits (Long.MIN_VALUE) + '.' + 9 nano digits = 30 bytes
            ensureCapacity(30);
            pos = JsonWriteUtils.writeEpochSeconds(buf, pos, value.getEpochSecond(), value.getNano());
            return;
        }
        if (format == TimestampFormatter.Prelude.DATE_TIME) {
            ensureCapacity(42);
            pos = JsonWriteUtils.writeIso8601Timestamp(buf, pos, value);
            return;
        }
        if (format == TimestampFormatter.Prelude.HTTP_DATE) {
            // "Sat, 01 Jan 2026 00:00:00 GMT" = 31 chars + 2 quotes = 33
            ensureCapacity(35);
            pos = JsonWriteUtils.writeHttpDate(buf, pos, value);
            return;
        }
        format.writeToSerializer(schema, value, this);
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;

        // Resolve field name table once per struct for all member writes.
        byte[][] savedTable = currentFieldNameTable;
        Schema structSchema = schema.isMember() ? schema.memberTarget() : schema;
        var ext = structSchema.getExtension(SmithyJsonSchemaExtensions.KEY);
        if (ext != null) {
            currentFieldNameTable = useJsonName ? ext.jsonFieldNameTable() : ext.memberFieldNameTable();
        } else {
            currentFieldNameTable = null;
        }

        struct.serializeMembers(structSerializer);

        currentFieldNameTable = savedTable; // restore for nested struct returns
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        beginArray();
        consumer.accept(listState, listElementSerializer);
        endArray();
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        beginObject();
        consumer.accept(mapState, mapSerializer);
        endObject();
    }

    private void beginArray() {
        ensureCapacity(2);
        buf[pos++] = '[';
        enterContainer();
    }

    private void endArray() {
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    private void beginObject() {
        ensureCapacity(2);
        buf[pos++] = '{';
        enterContainer();
    }

    private void endObject() {
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    private void enterContainer() {
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
    }

    private void writeEntryKey(String key) {
        writeCommaIfNeeded();
        ensureCapacity(key.length() + 2 + 1);
        writeQuotedStringExact(key, 1);
        buf[pos++] = ':';
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        if (value.type() != ShapeType.STRUCTURE) {
            value.serializeContents(this);
        } else {
            if (serializeDocumentContents == null) {
                serializeDocumentContents = new SerializeDocumentContents(this);
            }
            value.serializeContents(serializeDocumentContents);
        }
    }

    @Override
    public void writeNull(Schema schema) {
        ensureCapacity(JsonWriteUtils.NULL_BYTES.length);
        System.arraycopy(JsonWriteUtils.NULL_BYTES, 0, buf, pos, JsonWriteUtils.NULL_BYTES.length);
        pos += JsonWriteUtils.NULL_BYTES.length;
    }

    private void writeCommaIfNeeded() {
        if (needsComma[depth]) {
            if (pos >= buf.length) {
                grow(1);
            }
            buf[pos++] = ',';
        } else {
            needsComma[depth] = true;
        }
    }

    /**
     * Resolves the pre-computed field name bytes for a schema member.
     */
    private byte[] resolveFieldNameBytes(Schema schema) {
        byte[][] table = currentFieldNameTable;
        int idx = schema.memberIndex();
        if (table != null && idx >= 0 && idx < table.length && table[idx] != null) {
            return table[idx];
        }
        var ext = schema.getExtension(SmithyJsonSchemaExtensions.KEY);
        return useJsonName ? ext.jsonNameBytes() : ext.memberNameBytes();
    }

    /**
     * Writes comma (if needed) + field name bytes. Caller must have already ensured
     * sufficient capacity for nameBytes.length + 1 + value bytes.
     */
    private void writeFieldNameBytesUnchecked(byte[] nameBytes) {
        if (needsComma[depth]) {
            buf[pos++] = ',';
        } else {
            needsComma[depth] = true;
        }
        System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
        pos += nameBytes.length;
    }

    /**
     * Resolves field name, ensures capacity for name + comma, and writes them.
     * Used by write methods that need separate capacity logic for their value.
     */
    private void writeFieldNameBytes(Schema schema) {
        byte[] nameBytes = resolveFieldNameBytes(schema);
        int needed = nameBytes.length + 1;
        if (pos + needed > buf.length) {
            grow(needed);
        }
        writeFieldNameBytesUnchecked(nameBytes);
    }

    private static class JsonStripedPool extends StripedPool<SmithyJsonSerializer, JsonSettings> {
        @Override
        protected SmithyJsonSerializer create(JsonSettings settings) {
            return new SmithyJsonSerializer(settings);
        }

        @Override
        protected void cleanup(SmithyJsonSerializer s) {
            s.releaseBuffer();
            // Clear client settings and pool references because idle serializers remain in a JVM-wide pool.
            s.settings = null;
            s.pool = null;
        }

        @Override
        protected boolean canPool(SmithyJsonSerializer s) {
            return true;
        }

        @Override
        protected void prepareForPool(SmithyJsonSerializer s) {
            if (s.base64Scratch != null && s.base64Scratch.length > MAX_CACHEABLE_SCRATCH) {
                s.base64Scratch = null;
            }
        }

        @Override
        protected boolean reset(SmithyJsonSerializer s, JsonSettings settings) {
            s.settings = settings;
            s.useJsonName = settings.fieldMapper() instanceof JsonFieldMapper.UseJsonNameTrait;
            s.pos = 0;
            s.depth = 0;
            s.currentFieldNameTable = null;
            s.pool = settings.outputBuffers();
            s.buf = s.pool.acquire(s.sizeHint);
            return true;
        }
    }

    private final class StructSerializer implements ShapeSerializer {

        // Fused capacity check: resolve field name + ensure capacity for name + comma + max value
        // in a single check, then write both without separate capacity checks.

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 1 + 5);
            writeFieldNameBytesUnchecked(nameBytes);
            pos = NumberCodec.writeBoolean(buf, pos, value);
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 1 + 4);
            writeFieldNameBytesUnchecked(nameBytes);
            pos = JsonWriteUtils.writeInt(buf, pos, value);
        }

        @Override
        public void writeShort(Schema schema, short value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 1 + 6);
            writeFieldNameBytesUnchecked(nameBytes);
            pos = JsonWriteUtils.writeInt(buf, pos, value);
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 1 + 11);
            writeFieldNameBytesUnchecked(nameBytes);
            pos = JsonWriteUtils.writeInt(buf, pos, value);
        }

        @Override
        public void writeLong(Schema schema, long value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 1 + 20);
            writeFieldNameBytesUnchecked(nameBytes);
            pos = JsonWriteUtils.writeLong(buf, pos, value);
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 1 + NumberCodec.FLOAT_MAX_BYTES);
            writeFieldNameBytesUnchecked(nameBytes);
            pos = NumberCodec.writeFloatFullQuoted(buf, pos, value);
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 1 + NumberCodec.DOUBLE_MAX_BYTES);
            writeFieldNameBytesUnchecked(nameBytes);
            pos = NumberCodec.writeDoubleFullQuoted(buf, pos, value);
        }

        @Override
        public void writeNull(Schema schema) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 1 + 4);
            writeFieldNameBytesUnchecked(nameBytes);
            System.arraycopy(JsonWriteUtils.NULL_BYTES, 0, buf, pos, JsonWriteUtils.NULL_BYTES.length);
            pos += JsonWriteUtils.NULL_BYTES.length;
        }

        // Variable-size and recursive types: delegate to outer class (separate capacity checks)

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBigInteger(schema, value);
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBigDecimal(schema, value);
        }

        @Override
        public void writeString(Schema schema, String value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 1 + value.length() + 2);
            writeFieldNameBytesUnchecked(nameBytes);
            writeQuotedStringExact(value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBlob(schema, value);
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeTimestamp(schema, value);
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeStruct(schema, struct);
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeList(schema, listState, size, consumer);
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeMap(schema, mapState, size, consumer);
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeDocument(schema, value);
        }

        @Override
        public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBooleanList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseBooleanList(schema, values, memberSchema);
        }

        @Override
        public void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBooleanMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBooleanMap(
                Schema schema,
                Map<String, Boolean> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseBooleanMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeByteList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseByteList(schema, values, memberSchema);
        }

        @Override
        public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeByteMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseByteMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeShortList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseShortList(schema, values, memberSchema);
        }

        @Override
        public void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeShortMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseShortMap(
                Schema schema,
                Map<String, Short> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseShortMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseIntegerMap(
                Schema schema,
                Map<String, Integer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeLongList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseLongList(schema, values, memberSchema);
        }

        @Override
        public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeLongMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseLongMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeFloatList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseFloatList(schema, values, memberSchema);
        }

        @Override
        public void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeFloatMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseFloatMap(
                Schema schema,
                Map<String, Float> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseFloatMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeDoubleList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseDoubleList(schema, values, memberSchema);
        }

        @Override
        public void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeDoubleMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseDoubleMap(
                Schema schema,
                Map<String, Double> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseDoubleMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBigIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseBigIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBigIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseBigIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBigDecimalList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseBigDecimalList(schema, values, memberSchema);
        }

        @Override
        public void writeBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBigDecimalMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseBigDecimalMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeStringList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseStringList(schema, values, memberSchema);
        }

        @Override
        public void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeStringMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseStringMap(
                Schema schema,
                Map<String, String> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseStringMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBlobList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseBlobList(schema, values, memberSchema);
        }

        @Override
        public void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBlobMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBlobMap(
                Schema schema,
                Map<String, ByteBuffer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseBlobMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeTimestampList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseTimestampList(schema, values, memberSchema);
        }

        @Override
        public void writeTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeTimestampMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseTimestampMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeDocumentList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseDocumentList(schema, values, memberSchema);
        }

        @Override
        public void writeDocumentMap(
                Schema schema,
                Map<String, Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeDocumentMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseDocumentMap(
                Schema schema,
                Map<String, Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseDocumentMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeIntEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseIntEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeIntEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseIntEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeStructList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseStructList(
                Schema schema,
                List<? extends SerializableStruct> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseStructList(schema, values, memberSchema);
        }

        @Override
        public void writeStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeStructMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseStructMap(schema, values, keySchema, valueSchema);
        }
    }

    private final class ListElementSerializer implements ShapeSerializer {
        private void beforeElement() {
            writeCommaIfNeeded();
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            beforeElement();
            SmithyJsonSerializer.this.writeBoolean(schema, value);
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            beforeElement();
            SmithyJsonSerializer.this.writeByte(schema, value);
        }

        @Override
        public void writeShort(Schema schema, short value) {
            beforeElement();
            SmithyJsonSerializer.this.writeShort(schema, value);
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            beforeElement();
            SmithyJsonSerializer.this.writeInteger(schema, value);
        }

        @Override
        public void writeLong(Schema schema, long value) {
            beforeElement();
            SmithyJsonSerializer.this.writeLong(schema, value);
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            beforeElement();
            SmithyJsonSerializer.this.writeFloat(schema, value);
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            beforeElement();
            SmithyJsonSerializer.this.writeDouble(schema, value);
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            beforeElement();
            SmithyJsonSerializer.this.writeBigInteger(schema, value);
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            beforeElement();
            SmithyJsonSerializer.this.writeBigDecimal(schema, value);
        }

        @Override
        public void writeString(Schema schema, String value) {
            beforeElement();
            SmithyJsonSerializer.this.writeString(schema, value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            beforeElement();
            SmithyJsonSerializer.this.writeBlob(schema, value);
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            beforeElement();
            SmithyJsonSerializer.this.writeTimestamp(schema, value);
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            beforeElement();
            SmithyJsonSerializer.this.writeStruct(schema, struct);
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            beforeElement();
            SmithyJsonSerializer.this.writeList(schema, listState, size, consumer);
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            beforeElement();
            SmithyJsonSerializer.this.writeMap(schema, mapState, size, consumer);
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            beforeElement();
            SmithyJsonSerializer.this.writeDocument(schema, value);
        }

        @Override
        public void writeNull(Schema schema) {
            beforeElement();
            SmithyJsonSerializer.this.writeNull(schema);
        }

        @Override
        public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeBooleanList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseBooleanList(schema, values, memberSchema);
        }

        @Override
        public void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeBooleanMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBooleanMap(
                Schema schema,
                Map<String, Boolean> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseBooleanMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeByteList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseByteList(schema, values, memberSchema);
        }

        @Override
        public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeByteMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseByteMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeShortList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseShortList(schema, values, memberSchema);
        }

        @Override
        public void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeShortMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseShortMap(
                Schema schema,
                Map<String, Short> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseShortMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseIntegerMap(
                Schema schema,
                Map<String, Integer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeLongList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseLongList(schema, values, memberSchema);
        }

        @Override
        public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeLongMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseLongMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeFloatList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseFloatList(schema, values, memberSchema);
        }

        @Override
        public void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeFloatMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseFloatMap(
                Schema schema,
                Map<String, Float> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseFloatMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeDoubleList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseDoubleList(schema, values, memberSchema);
        }

        @Override
        public void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeDoubleMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseDoubleMap(
                Schema schema,
                Map<String, Double> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseDoubleMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeBigIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseBigIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeBigIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseBigIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeBigDecimalList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseBigDecimalList(schema, values, memberSchema);
        }

        @Override
        public void writeBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeBigDecimalMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseBigDecimalMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeStringList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseStringList(schema, values, memberSchema);
        }

        @Override
        public void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeStringMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseStringMap(
                Schema schema,
                Map<String, String> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseStringMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeBlobList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseBlobList(schema, values, memberSchema);
        }

        @Override
        public void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeBlobMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBlobMap(
                Schema schema,
                Map<String, ByteBuffer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseBlobMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeTimestampList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseTimestampList(schema, values, memberSchema);
        }

        @Override
        public void writeTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeTimestampMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseTimestampMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeDocumentList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseDocumentList(schema, values, memberSchema);
        }

        @Override
        public void writeDocumentMap(
                Schema schema,
                Map<String, Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeDocumentMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseDocumentMap(
                Schema schema,
                Map<String, Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseDocumentMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeIntEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseIntEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeIntEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseIntEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
            beforeElement();
            SmithyJsonSerializer.this.writeStructList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseStructList(
                Schema schema,
                List<? extends SerializableStruct> values,
                Schema memberSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseStructList(schema, values, memberSchema);
        }

        @Override
        public void writeStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeStructMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            beforeElement();
            SmithyJsonSerializer.this.writeSparseStructMap(schema, values, keySchema, valueSchema);
        }
    }

    private final class SmithyMapSerializer implements MapSerializer {
        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            writeEntryKey(key);
            valueSerializer.accept(state, SmithyJsonSerializer.this);
        }
    }

    private static final class SerializeDocumentContents extends SpecificShapeSerializer {
        private final SmithyJsonSerializer parent;

        SerializeDocumentContents(SmithyJsonSerializer parent) {
            this.parent = parent;
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            parent.ensureCapacity(2);
            parent.buf[parent.pos++] = '{';
            parent.depth++;
            if (parent.depth >= MAX_DEPTH) {
                throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
            }
            parent.needsComma[parent.depth] = false;

            byte[][] savedTable = parent.currentFieldNameTable;
            Schema structSchema = schema.isMember() ? schema.memberTarget() : schema;
            var ext = structSchema.getExtension(SmithyJsonSchemaExtensions.KEY);
            if (ext != null) {
                parent.currentFieldNameTable =
                        parent.useJsonName ? ext.jsonFieldNameTable() : ext.memberFieldNameTable();
            } else {
                parent.currentFieldNameTable = null;
            }

            if (parent.settings.serializeTypeInDocuments()) {
                parent.needsComma[parent.depth] = true;
                String typeValue = structSchema.id().toString();
                parent.ensureCapacity("__type".length() + 2 + 1 + typeValue.length() + 2);
                parent.writeQuotedStringExact("__type");
                parent.buf[parent.pos++] = ':';
                parent.writeQuotedStringExact(typeValue);
            }
            struct.serializeMembers(parent.structSerializer);
            parent.currentFieldNameTable = savedTable;
            parent.depth--;
            parent.ensureCapacity(1);
            parent.buf[parent.pos++] = '}';
        }
    }

    @Override
    public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeBoolean(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeBoolean(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeBoolean(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeBoolean(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeBoolean(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeBoolean(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeByte(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeByte(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeByte(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeByte(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeByte(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeByte(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeShort(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeShort(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeShort(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeShort(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeShort(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeShort(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeInteger(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeInteger(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeInteger(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeInteger(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeInteger(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeInteger(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeLong(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeLong(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeLong(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeLong(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeLong(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeLong(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeFloat(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeFloat(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeFloat(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeFloat(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeFloat(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeFloat(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeDouble(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeDouble(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeDouble(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeDouble(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeDouble(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeDouble(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeBigInteger(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeBigInteger(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeBigInteger(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeBigInteger(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeBigInteger(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeBigInteger(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeBigDecimal(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeBigDecimal(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeBigDecimal(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeBigDecimal(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeBigDecimal(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeBigDecimal(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeString(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeString(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeString(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeString(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeString(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeString(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeBlob(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeBlob(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeBlob(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeBlob(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeBlob(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeBlob(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeTimestamp(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeTimestamp(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeTimestamp(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeTimestamp(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeTimestampMap(Schema schema, Map<String, Instant> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeTimestamp(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeTimestamp(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeDocument(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeDocument(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeDocument(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeDocument(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeDocumentMap(Schema schema, Map<String, Document> values, Schema keySchema, Schema valueSchema) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeDocument(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseDocumentMap(
            Schema schema,
            Map<String, Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeDocument(valueSchema, v);
            }

        });
        endObject();
    }

    @Override
    public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeString(memberSchema, v.getValue());
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeString(memberSchema, v.getValue());
            }
        }
        endArray();
    }

    @Override
    public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeString(memberSchema, v.getValue());
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeString(memberSchema, v.getValue());
                }
            }
        }
        endArray();
    }

    @Override
    public void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeString(valueSchema, v.getValue());

        });
        endObject();
    }

    @Override
    public void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeString(valueSchema, v.getValue());
            }

        });
        endObject();
    }

    @Override
    public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeInteger(memberSchema, v.getValue());
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeInteger(memberSchema, v.getValue());
            }
        }
        endArray();
    }

    @Override
    public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeInteger(memberSchema, v.getValue());
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeInteger(memberSchema, v.getValue());
                }
            }
        }
        endArray();
    }

    @Override
    public void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeInteger(valueSchema, v.getValue());

        });
        endObject();
    }

    @Override
    public void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeInteger(valueSchema, v.getValue());
            }

        });
        endObject();
    }

    @Override
    public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listElementSerializer.writeStruct(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listElementSerializer.writeStruct(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeSparseStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        beginArray();
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeStruct(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listElementSerializer.writeNull(memberSchema);
                } else {
                    listElementSerializer.writeStruct(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            this.writeStruct(valueSchema, v);

        });
        endObject();
    }

    @Override
    public void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginObject();
        values.forEach((k, v) -> {
            writeEntryKey(k);
            if (v == null) {
                this.writeNull(valueSchema);
            } else {
                this.writeStruct(valueSchema, v);
            }

        });
        endObject();
    }
}
