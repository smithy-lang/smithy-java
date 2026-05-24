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
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiConsumer;
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
    private static final int MAX_CACHEABLE_BUF = DEFAULT_BUF_SIZE * 4;

    // Striped serializer pool.
    private static final int POOL_SLOTS;
    private static final int POOL_MASK;
    private static final AtomicReferenceArray<SmithyJsonSerializer> POOL;
    private static final int MAX_PROBE = 3;

    static {
        int processors = Runtime.getRuntime().availableProcessors();
        int raw = processors * 4;
        POOL_SLOTS = Integer.highestOneBit(raw - 1) << 1;
        POOL_MASK = POOL_SLOTS - 1;
        POOL = new AtomicReferenceArray<>(POOL_SLOTS);
    }

    private byte[] buf;
    private int pos;
    private final OutputStream sink;
    private final JsonSettings settings;
    private final boolean useJsonName;

    // Nesting state for comma insertion
    private int depth;
    private final boolean[] needsComma = new boolean[MAX_DEPTH];

    // Cached field name table for the current struct being serialized.
    // Resolved once per writeStruct call, then used for all member writes.
    private byte[][] currentFieldNameTable;

    // Reusable Schubfach instances for double/float write
    private final Schubfach.DoubleToDecimal doubleToDecimal = Schubfach.createDoubleToDecimal();
    private final Schubfach.FloatToDecimal floatToDecimal = Schubfach.createFloatToDecimal();

    private final ShapeSerializer structSerializer = new StructSerializer();
    private final ShapeSerializer listElementSerializer = new ListElementSerializer();
    private final MapSerializer mapSerializer = new SmithyMapSerializer();
    private SerializeDocumentContents serializeDocumentContents;

    SmithyJsonSerializer(OutputStream sink, JsonSettings settings) {
        this.sink = sink;
        this.settings = settings;
        this.useJsonName = settings.fieldMapper() instanceof JsonFieldMapper.UseJsonNameTrait;
        this.buf = new byte[DEFAULT_BUF_SIZE];
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
        this.buf = new byte[DEFAULT_BUF_SIZE];
        this.pos = 0;
        this.depth = 0;
    }

    /**
     * Acquires a serializer from the pool, or creates a new one.
     * The returned serializer is ready for use with a fresh buffer.
     *
     * <p>Uses getPlain to peek at slots cheaply (plain read, no ordering), then
     * compareAndExchangeAcquire to atomically claim a non-null entry (acquire
     * semantics ensure we see the serializer's fully-written state). This pays
     * the atomic price only once per acquire - empty slots are skipped with a
     * plain read instead of a full getAndSet.
     */
    static SmithyJsonSerializer acquire(JsonSettings settings) {
        //TODO Have a different strat for VTs,
        // we still some sort of pooling for VTs but the current strategy won't work.
        if (!Thread.currentThread().isVirtual()) {
            int base = poolProbe();
            for (int i = 0; i < MAX_PROBE; i++) {
                int idx = (base + i) & POOL_MASK;
                SmithyJsonSerializer s = POOL.getPlain(idx);
                if (s != null && POOL.compareAndExchangeAcquire(idx, s, null) == s) {
                    if (s.settings.equals(settings)) {
                        s.pos = 0;
                        s.depth = 0;
                        s.currentFieldNameTable = null;
                        return s;
                    }
                    POOL.setRelease(idx, s); // wrong settings, put back
                }
            }
        }
        return new SmithyJsonSerializer(settings);
    }

    /**
     * Returns a serializer to the pool for reuse. If the pool is full, the
     * buffer is oversized, or we're on a virtual thread, the serializer is discarded.
     *
     * <p>Uses getPlain to peek for empty slots, then compareAndExchangeRelease to
     * store the serializer with release semantics (ensures all serializer state is
     * visible to the thread that later acquires it).
     */
    static void release(SmithyJsonSerializer serializer, boolean exception) {
        if (serializer.buf == null || Thread.currentThread().isVirtual()) {
            return;
        }
        // If an exception occurred, the needsComma array may be in an inconsistent state.
        // Clear it before pooling so the next acquire gets a clean serializer.
        if (exception) {
            Arrays.fill(serializer.needsComma, false);
        }
        // Downsize oversized buffers before pooling to bound memory
        if (serializer.buf.length > MAX_CACHEABLE_BUF) {
            serializer.buf = new byte[DEFAULT_BUF_SIZE];
        }
        int base = poolProbe();
        for (int i = 0; i < MAX_PROBE; i++) {
            int idx = (base + i) & POOL_MASK;
            if (POOL.getPlain(idx) == null
                    && POOL.compareAndExchangeRelease(idx, null, serializer) == null) {
                return;
            }
        }
        // Pool full — let GC collect
    }

    /**
     * Extracts the serialized JSON as a ByteBuffer without releasing the internal
     * buffer. Used with {@link #acquire}/{@link #release} for pooled serializers.
     */
    ByteBuffer extractResult() {
        return ByteBuffer.wrap(Arrays.copyOf(buf, pos));
    }

    private static int poolProbe() {
        long id = Thread.currentThread().threadId();
        return (int) (id ^ (id >>> 16)) & POOL_MASK;
    }

    private void ensureCapacity(int needed) {
        if (pos + needed > buf.length) {
            grow(needed);
        }
    }

    // Separate cold grow method helps JIT inline ensureCapacity's fast path
    private void grow(int needed) {
        buf = Arrays.copyOf(buf, Math.max(buf.length * 2, pos + needed));
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
            buf = null;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    // ---- Value writers (no field name prefix) ----

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        byte[] bytes = value ? JsonWriteUtils.TRUE_BYTES : JsonWriteUtils.FALSE_BYTES;
        ensureCapacity(bytes.length);
        System.arraycopy(bytes, 0, buf, pos, bytes.length);
        pos += bytes.length;
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
        if (Float.isFinite(value)) {
            ensureCapacity(24);
            pos = JsonWriteUtils.writeFloat(buf, pos, value, floatToDecimal);
        } else if (Float.isNaN(value)) {
            ensureCapacity(JsonWriteUtils.NAN_BYTES.length);
            System.arraycopy(JsonWriteUtils.NAN_BYTES, 0, buf, pos, JsonWriteUtils.NAN_BYTES.length);
            pos += JsonWriteUtils.NAN_BYTES.length;
        } else {
            byte[] bytes = value > 0 ? JsonWriteUtils.INF_BYTES : JsonWriteUtils.NEG_INF_BYTES;
            ensureCapacity(bytes.length);
            System.arraycopy(bytes, 0, buf, pos, bytes.length);
            pos += bytes.length;
        }
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        if (Double.isFinite(value)) {
            ensureCapacity(24);
            pos = JsonWriteUtils.writeDouble(buf, pos, value, doubleToDecimal);
        } else if (Double.isNaN(value)) {
            ensureCapacity(JsonWriteUtils.NAN_BYTES.length);
            System.arraycopy(JsonWriteUtils.NAN_BYTES, 0, buf, pos, JsonWriteUtils.NAN_BYTES.length);
            pos += JsonWriteUtils.NAN_BYTES.length;
        } else {
            byte[] bytes = value > 0 ? JsonWriteUtils.INF_BYTES : JsonWriteUtils.NEG_INF_BYTES;
            ensureCapacity(bytes.length);
            System.arraycopy(bytes, 0, buf, pos, bytes.length);
            pos += bytes.length;
        }
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        if (settings.useStringForArbitraryPrecision()) {
            String s = value.toString();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(s));
            pos = JsonWriteUtils.writeQuotedString(buf, pos, s);
            return;
        }
        if (value.bitLength() < 64) {
            ensureCapacity(20);
            pos = JsonWriteUtils.writeLong(buf, pos, value.longValue());
            return;
        }
        ensureCapacity(value.bitLength() / 3 + 2);
        pos = JsonWriteUtils.writeBigInteger(buf, pos, value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        int scale = value.scale();
        if (value.unscaledValue().bitLength() < 64) {
            if (scale == 0) {
                ensureCapacity(20);
                pos = JsonWriteUtils.writeLong(buf, pos, value.longValueExact());
                return;
            }
            if (scale > 0) {
                // Fast path: write "integerPart.fractionalPart" directly.
                // E.g., BigDecimal("99999.99999") -> unscaled=9999999999, scale=5
                long unscaled = value.unscaledValue().longValue();
                ensureCapacity(22 + scale); // sign + 20 digits + dot + scale digits
                pos = JsonWriteUtils.writeBigDecimalFromLong(buf, pos, unscaled, scale);
                return;
            }
        }
        String s = value.toString();
        // Preempt the quotes wrapping, as a BigDecimal write will almost
        // always have at least 1 additional character after it.
        ensureCapacity(s.length() + 2);
        if (settings.useStringForArbitraryPrecision()) {
            pos = JsonWriteUtils.writeQuotedString(buf, pos, s);
        } else {
            pos = JsonWriteUtils.writeAsciiString(buf, pos, s);
        }
    }

    @Override
    public void writeString(Schema schema, String value) {
        ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(value));
        pos = JsonWriteUtils.writeQuotedString(buf, pos, value);
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        ensureCapacity(JsonWriteUtils.maxBase64Bytes(value.length));
        pos = JsonWriteUtils.writeBase64String(buf, pos, value, 0, value.length);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        int len = value.remaining();
        byte[] data;
        int off;
        if (value.hasArray()) {
            data = value.array();
            off = value.arrayOffset() + value.position();
        } else {
            data = ByteBufferUtils.getBytes(value.duplicate());
            off = 0;
        }
        ensureCapacity(JsonWriteUtils.maxBase64Bytes(len));
        pos = JsonWriteUtils.writeBase64String(buf, pos, data, off, len);
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
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        consumer.accept(listState, listElementSerializer);
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeStructList(
            Schema schema,
            List<? extends SerializableStruct> values,
            Schema memberSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeStruct(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeStruct(memberSchema, v);
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(values.get(i)));
                pos = JsonWriteUtils.writeQuotedString(buf, pos, values.get(i));
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(v));
                pos = JsonWriteUtils.writeQuotedString(buf, pos, v);
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeBoolean(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeBoolean(memberSchema, v);
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeByte(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeByte(memberSchema, v);
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeShort(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeShort(memberSchema, v);
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeInteger(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeInteger(memberSchema, v);
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeLong(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeLong(memberSchema, v);
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeFloat(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeFloat(memberSchema, v);
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeDouble(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeDouble(memberSchema, v);
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeBigInteger(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeBigInteger(memberSchema, v);
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeBigDecimal(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeBigDecimal(memberSchema, v);
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeBlob(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeBlob(memberSchema, v);
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeTimestamp(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeTimestamp(memberSchema, v);
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeDocumentList(
            Schema schema,
            List<? extends Document> values,
            Schema memberSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeDocument(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeDocument(memberSchema, v);
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeEnumList(
            Schema schema,
            List<? extends SmithyEnum> values,
            Schema memberSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeString(memberSchema, values.get(i).getValue());
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeString(memberSchema, v.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeIntEnumList(
            Schema schema,
            List<? extends SmithyIntEnum> values,
            Schema memberSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                writeInteger(memberSchema, values.get(i).getValue());
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                writeInteger(memberSchema, v.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseStructList(
            Schema schema,
            List<? extends SerializableStruct> values,
            Schema memberSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeStruct(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeStruct(memberSchema, v);
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeString(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeString(memberSchema, v);
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeInteger(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeInteger(memberSchema, v);
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeLong(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeLong(memberSchema, v);
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeDouble(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeDouble(memberSchema, v);
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBlob(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBlob(memberSchema, v);
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeTimestamp(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeTimestamp(memberSchema, v);
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseDocumentList(
            Schema schema,
            List<? extends Document> values,
            Schema memberSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeDocument(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeDocument(memberSchema, v);
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBoolean(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBoolean(memberSchema, v);
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeByte(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeByte(memberSchema, v);
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeShort(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeShort(memberSchema, v);
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeFloat(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeFloat(memberSchema, v);
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBigInteger(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBigInteger(memberSchema, v);
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBigDecimal(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBigDecimal(memberSchema, v);
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseEnumList(
            Schema schema,
            List<? extends SmithyEnum> values,
            Schema memberSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeString(memberSchema, v.getValue());
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeString(memberSchema, v.getValue());
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeSparseIntEnumList(
            Schema schema,
            List<? extends SmithyIntEnum> values,
            Schema memberSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeCommaIfNeeded();
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeInteger(memberSchema, v.getValue());
                }
            }
        } else {
            for (var v : values) {
                writeCommaIfNeeded();
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeInteger(memberSchema, v.getValue());
                }
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeStruct(valueSchema, entry.getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeStringMap(
            Schema schema,
            Map<String, String> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(
                    JsonWriteUtils.maxQuotedStringBytes(entry.getKey())
                            + 1
                            + JsonWriteUtils.maxQuotedStringBytes(entry.getValue()));
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeBoolean(valueSchema, entry.getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeByte(valueSchema, entry.getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeShortMap(
            Schema schema,
            Map<String, Short> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeShort(valueSchema, entry.getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeInteger(valueSchema, entry.getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeLong(valueSchema, entry.getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeFloatMap(
            Schema schema,
            Map<String, Float> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeFloat(valueSchema, entry.getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeDoubleMap(
            Schema schema,
            Map<String, Double> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeDouble(valueSchema, entry.getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeBigInteger(valueSchema, entry.getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeBigDecimal(valueSchema, entry.getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeBlob(valueSchema, entry.getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeTimestamp(valueSchema, entry.getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeDocument(valueSchema, entry.getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeString(valueSchema, entry.getValue().getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            writeInteger(valueSchema, entry.getValue().getValue());
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeStruct(valueSchema, entry.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseStringMap(
            Schema schema,
            Map<String, String> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeString(valueSchema, entry.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeInteger(valueSchema, entry.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseLongMap(
            Schema schema,
            Map<String, Long> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeLong(valueSchema, entry.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseDoubleMap(
            Schema schema,
            Map<String, Double> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeDouble(valueSchema, entry.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeBlob(valueSchema, entry.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeTimestamp(valueSchema, entry.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeDocument(valueSchema, entry.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeBoolean(valueSchema, entry.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseByteMap(
            Schema schema,
            Map<String, Byte> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeByte(valueSchema, entry.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseShortMap(
            Schema schema,
            Map<String, Short> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeShort(valueSchema, entry.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseFloatMap(
            Schema schema,
            Map<String, Float> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeFloat(valueSchema, entry.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeBigInteger(valueSchema, entry.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeBigDecimal(valueSchema, entry.getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeString(valueSchema, entry.getValue().getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        for (var entry : values.entrySet()) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(entry.getKey()) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, entry.getKey());
            buf[pos++] = ':';
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeInteger(valueSchema, entry.getValue().getValue());
            }
        }
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        consumer.accept(mapState, mapSerializer);
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
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

    // ---- Comma management ----

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

    // ---- Field name writing ----

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

    // ---- Inner struct serializer: writes field name + value ----

    private final class StructSerializer implements ShapeSerializer {

        // Fused capacity check: resolve field name + ensure capacity for name + comma + max value
        // in a single check, then write both without separate capacity checks.

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 1 + 5); // +5 for "false"
            writeFieldNameBytesUnchecked(nameBytes);
            byte[] bytes = value ? JsonWriteUtils.TRUE_BYTES : JsonWriteUtils.FALSE_BYTES;
            System.arraycopy(bytes, 0, buf, pos, bytes.length);
            pos += bytes.length;
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
            ensureCapacity(nameBytes.length + 1 + 24);
            writeFieldNameBytesUnchecked(nameBytes);
            if (Float.isFinite(value)) {
                pos = JsonWriteUtils.writeFloat(buf, pos, value, floatToDecimal);
            } else if (Float.isNaN(value)) {
                System.arraycopy(JsonWriteUtils.NAN_BYTES, 0, buf, pos, JsonWriteUtils.NAN_BYTES.length);
                pos += JsonWriteUtils.NAN_BYTES.length;
            } else {
                byte[] bytes = value > 0 ? JsonWriteUtils.INF_BYTES : JsonWriteUtils.NEG_INF_BYTES;
                System.arraycopy(bytes, 0, buf, pos, bytes.length);
                pos += bytes.length;
            }
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 1 + 24);
            writeFieldNameBytesUnchecked(nameBytes);
            if (Double.isFinite(value)) {
                pos = JsonWriteUtils.writeDouble(buf, pos, value, doubleToDecimal);
            } else if (Double.isNaN(value)) {
                System.arraycopy(JsonWriteUtils.NAN_BYTES, 0, buf, pos, JsonWriteUtils.NAN_BYTES.length);
                pos += JsonWriteUtils.NAN_BYTES.length;
            } else {
                byte[] bytes = value > 0 ? JsonWriteUtils.INF_BYTES : JsonWriteUtils.NEG_INF_BYTES;
                System.arraycopy(bytes, 0, buf, pos, bytes.length);
                pos += bytes.length;
            }
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
            ensureCapacity(nameBytes.length + 1 + JsonWriteUtils.maxQuotedStringBytes(value));
            writeFieldNameBytesUnchecked(nameBytes);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, value);
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
        public void writeStructList(
                Schema schema,
                List<? extends SerializableStruct> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeStructList(schema, values, memberSchema);
        }

        @Override
        public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeStringList(schema, values, memberSchema);
        }

        @Override
        public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBooleanList(schema, values, memberSchema);
        }

        @Override
        public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeByteList(schema, values, memberSchema);
        }

        @Override
        public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeShortList(schema, values, memberSchema);
        }

        @Override
        public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeLongList(schema, values, memberSchema);
        }

        @Override
        public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeFloatList(schema, values, memberSchema);
        }

        @Override
        public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeDoubleList(schema, values, memberSchema);
        }

        @Override
        public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBigIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBigDecimalList(schema, values, memberSchema);
        }

        @Override
        public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBlobList(schema, values, memberSchema);
        }

        @Override
        public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeTimestampList(schema, values, memberSchema);
        }

        @Override
        public void writeDocumentList(
                Schema schema,
                List<? extends Document> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeDocumentList(schema, values, memberSchema);
        }

        @Override
        public void writeEnumList(
                Schema schema,
                List<? extends SmithyEnum> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeIntEnumList(
                Schema schema,
                List<? extends SmithyIntEnum> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeIntEnumList(schema, values, memberSchema);
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
        public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseStringList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseLongList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseDoubleList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseBlobList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseTimestampList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseDocumentList(
                Schema schema,
                List<? extends Document> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseDocumentList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseBooleanList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseByteList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseShortList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseFloatList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseBigIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseBigDecimalList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseEnumList(
                Schema schema,
                List<? extends SmithyEnum> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseIntEnumList(
                Schema schema,
                List<? extends SmithyIntEnum> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseIntEnumList(schema, values, memberSchema);
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
        public void writeStringMap(
                Schema schema,
                Map<String, String> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeStringMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBooleanMap(
                Schema schema,
                Map<String, Boolean> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBooleanMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeByteMap(
                Schema schema,
                Map<String, Byte> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeByteMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeShortMap(
                Schema schema,
                Map<String, Short> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeShortMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeIntegerMap(
                Schema schema,
                Map<String, Integer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeLongMap(
                Schema schema,
                Map<String, Long> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeLongMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeFloatMap(
                Schema schema,
                Map<String, Float> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeFloatMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeDoubleMap(
                Schema schema,
                Map<String, Double> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeDoubleMap(schema, values, keySchema, valueSchema);
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
        public void writeBlobMap(
                Schema schema,
                Map<String, ByteBuffer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBlobMap(schema, values, keySchema, valueSchema);
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
        public void writeDocumentMap(
                Schema schema,
                Map<String, ? extends Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeDocumentMap(schema, values, keySchema, valueSchema);
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
        public void writeSparseStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseStructMap(schema, values, keySchema, valueSchema);
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
        public void writeSparseLongMap(
                Schema schema,
                Map<String, Long> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseLongMap(schema, values, keySchema, valueSchema);
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
        public void writeSparseDocumentMap(
                Schema schema,
                Map<String, ? extends Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseDocumentMap(schema, values, keySchema, valueSchema);
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
        public void writeSparseByteMap(
                Schema schema,
                Map<String, Byte> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeSparseByteMap(schema, values, keySchema, valueSchema);
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
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeMap(schema, mapState, size, consumer);
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeDocument(schema, value);
        }
    }

    // ---- List element serializer: handles comma separation between elements ----

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
    }

    // ---- Map serializer ----

    private final class SmithyMapSerializer implements MapSerializer {
        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            valueSerializer.accept(state, SmithyJsonSerializer.this);
        }

        @Override
        public void writeStructEntry(Schema keySchema, String key, Schema valueSchema, SerializableStruct value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeStruct(valueSchema, value);
        }

        @Override
        public void writeStringEntry(Schema keySchema, String key, Schema valueSchema, String value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + JsonWriteUtils.maxQuotedStringBytes(value) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            pos = JsonWriteUtils.writeQuotedString(buf, pos, value);
        }

        @Override
        public void writeBooleanEntry(Schema keySchema, String key, Schema valueSchema, Boolean value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeBoolean(valueSchema, value);
        }

        @Override
        public void writeByteEntry(Schema keySchema, String key, Schema valueSchema, Byte value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeByte(valueSchema, value);
        }

        @Override
        public void writeShortEntry(Schema keySchema, String key, Schema valueSchema, Short value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeShort(valueSchema, value);
        }

        @Override
        public void writeIntegerEntry(Schema keySchema, String key, Schema valueSchema, Integer value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeInteger(valueSchema, value);
        }

        @Override
        public void writeLongEntry(Schema keySchema, String key, Schema valueSchema, Long value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeLong(valueSchema, value);
        }

        @Override
        public void writeFloatEntry(Schema keySchema, String key, Schema valueSchema, Float value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeFloat(valueSchema, value);
        }

        @Override
        public void writeDoubleEntry(Schema keySchema, String key, Schema valueSchema, Double value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeDouble(valueSchema, value);
        }

        @Override
        public void writeBigIntegerEntry(Schema keySchema, String key, Schema valueSchema, BigInteger value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeBigInteger(valueSchema, value);
        }

        @Override
        public void writeBigDecimalEntry(Schema keySchema, String key, Schema valueSchema, BigDecimal value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeBigDecimal(valueSchema, value);
        }

        @Override
        public void writeBlobEntry(Schema keySchema, String key, Schema valueSchema, ByteBuffer value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeBlob(valueSchema, value);
        }

        @Override
        public void writeTimestampEntry(Schema keySchema, String key, Schema valueSchema, Instant value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeTimestamp(valueSchema, value);
        }

        @Override
        public void writeDocumentEntry(Schema keySchema, String key, Schema valueSchema, Document value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeDocument(valueSchema, value);
        }

        @Override
        public void writeNullEntry(Schema keySchema, String key, Schema valueSchema) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeNull(valueSchema);
        }

        @Override
        public void writeIntEnumEntry(Schema keySchema, String key, Schema valueSchema, int value) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            SmithyJsonSerializer.this.writeInteger(valueSchema, value);
        }
    }

    // ---- Document struct serializer (writes __type) ----

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
            if (parent.settings.serializeTypeInDocuments()) {
                parent.needsComma[parent.depth] = true;
                String typeValue = schema.id().toString();
                parent.ensureCapacity(JsonWriteUtils.maxQuotedStringBytes("__type")
                        + 1
                        + JsonWriteUtils.maxQuotedStringBytes(typeValue));
                parent.pos = JsonWriteUtils.writeQuotedString(parent.buf, parent.pos, "__type");
                parent.buf[parent.pos++] = ':';
                parent.pos = JsonWriteUtils.writeQuotedString(parent.buf, parent.pos, typeValue);
            }
            struct.serializeMembers(parent.structSerializer);
            parent.depth--;
            parent.ensureCapacity(1);
            parent.buf[parent.pos++] = '}';
        }
    }
}
