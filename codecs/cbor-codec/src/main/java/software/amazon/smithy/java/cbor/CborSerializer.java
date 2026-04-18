/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static software.amazon.smithy.java.cbor.CborConstants.EIGHT_BYTES;
import static software.amazon.smithy.java.cbor.CborConstants.FOUR_BYTES;
import static software.amazon.smithy.java.cbor.CborConstants.INDEFINITE;
import static software.amazon.smithy.java.cbor.CborConstants.ONE_BYTE;
import static software.amazon.smithy.java.cbor.CborConstants.TAG_DECIMAL;
import static software.amazon.smithy.java.cbor.CborConstants.TAG_NEG_BIG_INT;
import static software.amazon.smithy.java.cbor.CborConstants.TAG_POS_BIG_INT;
import static software.amazon.smithy.java.cbor.CborConstants.TAG_TIME_EPOCH;
import static software.amazon.smithy.java.cbor.CborConstants.TWO_BYTES;
import static software.amazon.smithy.java.cbor.CborConstants.TYPE_ARRAY;
import static software.amazon.smithy.java.cbor.CborConstants.TYPE_BYTESTRING;
import static software.amazon.smithy.java.cbor.CborConstants.TYPE_MAP;
import static software.amazon.smithy.java.cbor.CborConstants.TYPE_NEGINT;
import static software.amazon.smithy.java.cbor.CborConstants.TYPE_POSINT;
import static software.amazon.smithy.java.cbor.CborConstants.TYPE_SIMPLE_BREAK_STREAM;
import static software.amazon.smithy.java.cbor.CborConstants.TYPE_SIMPLE_DOUBLE;
import static software.amazon.smithy.java.cbor.CborConstants.TYPE_SIMPLE_FALSE;
import static software.amazon.smithy.java.cbor.CborConstants.TYPE_SIMPLE_FLOAT;
import static software.amazon.smithy.java.cbor.CborConstants.TYPE_SIMPLE_NULL;
import static software.amazon.smithy.java.cbor.CborConstants.TYPE_SIMPLE_TRUE;
import static software.amazon.smithy.java.cbor.CborConstants.TYPE_TAG;
import static software.amazon.smithy.java.cbor.CborConstants.TYPE_TEXTSTRING;
import static software.amazon.smithy.java.cbor.CborReadUtil.flipBytes;

import java.io.OutputStream;
import java.lang.invoke.VarHandle;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

final class CborSerializer implements ShapeSerializer {

    private static final VarHandle BE_SHORT = CborReadUtil.BE_SHORT;
    private static final VarHandle BE_INT = CborReadUtil.BE_INT;
    private static final VarHandle BE_LONG = CborReadUtil.BE_LONG;

    private static final int MAP_STREAM = TYPE_MAP | INDEFINITE;
    private static final int ARRAY_STREAM = TYPE_ARRAY | INDEFINITE;

    private static final int DEFAULT_BUF_SIZE = 4096;
    private static final int MAX_CACHEABLE_BUF = DEFAULT_BUF_SIZE * 4;

    // Striped serializer pool.
    private static final int POOL_SLOTS;
    private static final int POOL_MASK;
    private static final AtomicReferenceArray<CborSerializer> POOL;
    private static final int MAX_PROBE = 3;

    static {
        int processors = Runtime.getRuntime().availableProcessors();
        int raw = processors * 4;
        POOL_SLOTS = Integer.highestOneBit(raw - 1) << 1;
        POOL_MASK = POOL_SLOTS - 1;
        POOL = new AtomicReferenceArray<>(POOL_SLOTS);
    }

    // Direct byte buffer — all writes go here.
    byte[] buf;
    int pos;

    // Nullable: non-null only for streaming (OutputStream) mode.
    private final OutputStream sink;

    // Collection nesting state (for indefinite-length tracking).
    // Bit i of collectionMask records whether level i was opened as indefinite-length.
    // Overflow storage is lazily allocated for depths >= 64.
    private long collectionMask = 0L;
    private int collectionDepth = 0;
    private long[] collectionOverflow;

    // Pre-resolved field name table for the current struct being serialized.
    private byte[][] currentFieldNameTable;

    // Reusable inner serializers.
    private final CborStructSerializer structSerializer = new CborStructSerializer();
    private final CborMapSerializer mapSerializer = new CborMapSerializer();
    private SerializeDocumentContents serializeDocumentContents;

    /** Constructor for pooled / direct-buffer mode (no OutputStream). */
    CborSerializer() {
        this.sink = null;
        this.buf = new byte[DEFAULT_BUF_SIZE];
        this.pos = 0;
    }

    /** Constructor for streaming mode (OutputStream). */
    CborSerializer(OutputStream sink) {
        this.sink = sink;
        this.buf = new byte[DEFAULT_BUF_SIZE];
        this.pos = 0;
    }

    // ---- Pool management ----

    static CborSerializer acquire() {
        if (!Thread.currentThread().isVirtual()) {
            int base = poolProbe();
            for (int i = 0; i < MAX_PROBE; i++) {
                int idx = (base + i) & POOL_MASK;
                CborSerializer s = POOL.getPlain(idx);
                if (s != null && POOL.compareAndExchangeAcquire(idx, s, null) == s) {
                    s.pos = 0;
                    s.collectionMask = 0L;
                    s.collectionDepth = 0;
                    s.currentFieldNameTable = null;
                    return s;
                }
            }
        }
        return new CborSerializer();
    }

    static void release(CborSerializer serializer, boolean exception) {
        if (serializer.buf == null || serializer.sink != null || Thread.currentThread().isVirtual()) {
            return;
        }
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

    ByteBuffer extractResult() {
        return ByteBuffer.wrap(Arrays.copyOf(buf, pos));
    }

    private static int poolProbe() {
        long id = Thread.currentThread().threadId();
        return (int) (id ^ (id >>> 16)) & POOL_MASK;
    }

    // ---- Buffer management ----

    private void ensureCapacity(int needed) {
        if (pos + needed > buf.length) {
            grow(needed);
        }
    }

    // Separate cold method helps JIT inline ensureCapacity's fast path.
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
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    // ---- Low-level CBOR encoding ----

    private void tagAndLength(int type, int len) {
        ensureCapacity(5); // max: 1 type byte + 4 length bytes (int)
        tagAndLengthUnchecked(type, len);
    }

    /** Write tag+length without ensureCapacity — caller must have reserved space. */
    private void tagAndLengthUnchecked(int type, int len) {
        if (len < ONE_BYTE) {
            buf[pos++] = (byte) (type | len);
        } else if (len <= 0xFF) {
            buf[pos++] = (byte) (type | ONE_BYTE);
            buf[pos++] = (byte) len;
        } else if (len <= 0xFFFF) {
            buf[pos++] = (byte) (type | TWO_BYTES);
            BE_SHORT.set(buf, pos, (short) len);
            pos += 2;
        } else {
            buf[pos++] = (byte) (type | FOUR_BYTES);
            BE_INT.set(buf, pos, len);
            pos += 4;
        }
    }

    private void writeLong(long l) {
        ensureCapacity(9); // max: 1 type byte + 8 data bytes
        writeLongUnchecked(l);
    }

    /** Write a CBOR integer without ensureCapacity — caller must have reserved 9 bytes. */
    private void writeLongUnchecked(long l) {
        byte type;
        if (l < 0) {
            l = -l - 1;
            type = TYPE_NEGINT;
        } else {
            type = TYPE_POSINT;
        }

        if (l < ONE_BYTE) {
            buf[pos++] = (byte) (type | (int) l);
        } else if (l <= 0xFFL) {
            buf[pos++] = (byte) (type | ONE_BYTE);
            buf[pos++] = (byte) l;
        } else if (l <= 0xFFFFL) {
            buf[pos++] = (byte) (type | TWO_BYTES);
            BE_SHORT.set(buf, pos, (short) l);
            pos += 2;
        } else if (l <= 0xFFFF_FFFFL) {
            buf[pos++] = (byte) (type | FOUR_BYTES);
            BE_INT.set(buf, pos, (int) l);
            pos += 4;
        } else {
            buf[pos++] = (byte) (type | EIGHT_BYTES);
            BE_LONG.set(buf, pos, l);
            pos += 8;
        }
    }

    private void writeDoubleUnchecked(long bits) {
        buf[pos++] = (byte) TYPE_SIMPLE_DOUBLE;
        BE_LONG.set(buf, pos, bits);
        pos += 8;
    }

    private void writeBytes0(int type, byte[] b, int off, int len) {
        tagAndLength(type, len);
        ensureCapacity(len);
        System.arraycopy(b, off, buf, pos, len);
        pos += len;
    }

    // ---- Collection management ----

    private void startMap(int size) {
        boolean indefinite = size < 0;
        if (indefinite) {
            ensureCapacity(1);
            buf[pos++] = (byte) MAP_STREAM;
        } else {
            tagAndLength(TYPE_MAP, size);
        }
        startCollection(indefinite);
    }

    private void startArray(int size) {
        boolean indefinite = size < 0;
        if (indefinite) {
            ensureCapacity(1);
            buf[pos++] = (byte) ARRAY_STREAM;
        } else {
            tagAndLength(TYPE_ARRAY, size);
        }
        startCollection(indefinite);
    }

    private void startCollection(boolean indefinite) {
        int d = collectionDepth;
        if (d < 64) {
            if (indefinite) {
                collectionMask |= 1L << d;
            } else {
                collectionMask &= ~(1L << d);
            }
        } else {
            pushOverflow(d, indefinite);
        }
        collectionDepth = d + 1;
    }

    private void pushOverflow(int d, boolean indefinite) {
        int overflowIdx = d - 64;
        long[] stack = collectionOverflow;
        if (stack == null) {
            stack = collectionOverflow = new long[Math.max(4, (overflowIdx >> 6) + 1)];
        } else if ((overflowIdx >> 6) >= stack.length) {
            stack = collectionOverflow = Arrays.copyOf(stack, stack.length * 2);
        }
        long bit = 1L << (overflowIdx & 63);
        int slot = overflowIdx >>> 6;
        if (indefinite) {
            stack[slot] |= bit;
        } else {
            stack[slot] &= ~bit;
        }
    }

    private boolean popIndefinite() {
        int d = --collectionDepth;
        if (d < 64) {
            return ((collectionMask >>> d) & 1L) != 0L;
        }
        int overflowIdx = d - 64;
        return ((collectionOverflow[overflowIdx >>> 6] >>> (overflowIdx & 63)) & 1L) != 0L;
    }

    private void endMap() {
        if (popIndefinite()) {
            ensureCapacity(1);
            buf[pos++] = (byte) TYPE_SIMPLE_BREAK_STREAM;
        }
    }

    private void endArray() {
        if (popIndefinite()) {
            ensureCapacity(1);
            buf[pos++] = (byte) TYPE_SIMPLE_BREAK_STREAM;
        }
    }

    // ---- ShapeSerializer implementation ----

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        ensureCapacity(1);
        buf[pos++] = (byte) MAP_STREAM;
        startCollection(true);

        // Resolve field name table once per struct for all member writes.
        byte[][] savedTable = currentFieldNameTable;
        Schema structSchema = schema.isMember() ? schema.memberTarget() : schema;
        var ext = structSchema.getExtension(CborSchemaExtensions.KEY);
        currentFieldNameTable = ext != null ? ext.fieldNameTable() : null;

        struct.serializeMembers(structSerializer);

        currentFieldNameTable = savedTable; // restore for nested struct returns
        endMap();
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        startArray(size);
        consumer.accept(listState, this);
        endArray();
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        startMap(size);
        consumer.accept(mapState, mapSerializer);
        endMap();
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        ensureCapacity(1);
        buf[pos++] = (byte) (value ? TYPE_SIMPLE_TRUE : TYPE_SIMPLE_FALSE);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        writeLong(value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        writeLong(value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        writeLong(value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        writeLong(value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        ensureCapacity(5);
        buf[pos++] = (byte) TYPE_SIMPLE_FLOAT;
        BE_INT.set(buf, pos, Float.floatToRawIntBits(value));
        pos += 4;
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        ensureCapacity(9);
        writeDoubleUnchecked(Double.doubleToRawLongBits(value));
    }

    @Override
    public void writeString(Schema schema, String value) {
        writeStringValue(value);
    }

    @SuppressWarnings("deprecation")
    private void writeStringValue(String value) {
        int charLen = value.length();
        if (charLen == 0) {
            ensureCapacity(1);
            buf[pos++] = (byte) TYPE_TEXTSTRING;
            return;
        }
        // Optimistic ASCII path: copy with deprecated getBytes (zero alloc, correct for ASCII).
        // Use branchless OR accumulator to detect any non-ASCII byte.
        ensureCapacity(5 + charLen * 3); // worst case for UTF-8
        int headerStart = pos;
        tagAndLengthUnchecked(TYPE_TEXTSTRING, charLen);
        int dataStart = pos;
        value.getBytes(0, charLen, buf, pos);
        int orAccum = 0;
        for (int i = 0; i < charLen; i++) {
            orAccum |= buf[pos + i];
        }
        if (orAccum >= 0) {
            // All bytes have high bit clear — pure ASCII, encoding is correct
            pos += charLen;
            return;
        }
        // Non-ASCII: rewind and use proper UTF-8 encoding
        pos = headerStart;
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        tagAndLengthUnchecked(TYPE_TEXTSTRING, utf8.length);
        System.arraycopy(utf8, 0, buf, pos, utf8.length);
        pos += utf8.length;
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        int len = value.remaining();
        tagAndLength(TYPE_BYTESTRING, len);
        ensureCapacity(len);
        if (value.hasArray()) {
            System.arraycopy(value.array(), value.arrayOffset() + value.position(), buf, pos, len);
        } else {
            value.duplicate().get(buf, pos, len);
        }
        pos += len;
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        writeBytes0(TYPE_BYTESTRING, value, 0, value.length);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        long millis = value.toEpochMilli();
        if (millis % 1000 == 0) {
            // Whole-second timestamp — compact integer encoding per RPCv2 spec
            ensureCapacity(10); // 1 tag + max 9 for integer
            buf[pos++] = (byte) (TYPE_TAG | TAG_TIME_EPOCH);
            writeLongUnchecked(millis / 1000);
        } else {
            // Fractional timestamp — double with millisecond precision per RPCv2 spec
            double epochSeconds = millis / 1000D;
            ensureCapacity(10); // 1 tag + 1 type + 8 double
            buf[pos++] = (byte) (TYPE_TAG | TAG_TIME_EPOCH);
            writeDoubleUnchecked(Double.doubleToRawLongBits(epochSeconds));
        }
    }

    @Override
    public void writeNull(Schema schema) {
        ensureCapacity(1);
        buf[pos++] = (byte) TYPE_SIMPLE_NULL;
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        writeBigInteger(value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        ensureCapacity(2);
        buf[pos++] = (byte) (TYPE_TAG | TAG_DECIMAL);
        tagAndLengthUnchecked(TYPE_ARRAY, 2);
        writeLong(-value.scale());
        writeBigInteger(value.unscaledValue());
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

    private void writeBigInteger(BigInteger value) {
        int bits = value.bitLength();
        if (bits < 64) {
            writeLong(value.longValue());
        } else {
            int signum = value.signum() >> 1;
            if (bits == 64) {
                byte type;
                if (signum < 0) {
                    type = TYPE_NEGINT;
                } else {
                    type = TYPE_POSINT;
                }
                ensureCapacity(9);
                buf[pos++] = (byte) (type | EIGHT_BYTES);
                long v = value.longValue() ^ signum;
                BE_LONG.set(buf, pos, v);
                pos += 8;
            } else {
                byte[] bytes = value.toByteArray();
                byte tag;
                if (signum < 0) {
                    tag = TAG_NEG_BIG_INT;
                    flipBytes(bytes);
                } else {
                    tag = TAG_POS_BIG_INT;
                }
                ensureCapacity(1);
                buf[pos++] = (byte) (TYPE_TAG | tag);
                writeBytes0(TYPE_BYTESTRING, bytes, 0, bytes.length);
            }
        }
    }

    // ---- Field name resolution ----

    private byte[] resolveFieldNameBytes(Schema schema) {
        byte[][] table = currentFieldNameTable;
        int idx = schema.memberIndex();
        if (table != null && idx >= 0 && idx < table.length && table[idx] != null) {
            return table[idx];
        }
        // Fallback: compute on the fly from the member schema extension
        var ext = schema.getExtension(CborSchemaExtensions.KEY);
        if (ext != null && ext.memberNameBytes() != null) {
            return ext.memberNameBytes();
        }
        // Last resort: encode from string
        return encodeMemberName(schema.memberName());
    }

    @SuppressWarnings("deprecation")
    static byte[] encodeMemberName(String name) {
        int len = name.length();
        // Compute the CBOR text string header size
        int headerSize;
        if (len < ONE_BYTE) {
            headerSize = 1;
        } else if (len <= 0xFF) {
            headerSize = 2;
        } else if (len <= 0xFFFF) {
            headerSize = 3;
        } else {
            headerSize = 5;
        }
        byte[] result = new byte[headerSize + len];
        int p = 0;
        if (len < ONE_BYTE) {
            result[p++] = (byte) (TYPE_TEXTSTRING | len);
        } else if (len <= 0xFF) {
            result[p++] = (byte) (TYPE_TEXTSTRING | ONE_BYTE);
            result[p++] = (byte) len;
        } else if (len <= 0xFFFF) {
            result[p++] = (byte) (TYPE_TEXTSTRING | TWO_BYTES);
            result[p++] = (byte) (len >> 8);
            result[p++] = (byte) len;
        } else {
            result[p++] = (byte) (TYPE_TEXTSTRING | FOUR_BYTES);
            result[p++] = (byte) (len >> 24);
            result[p++] = (byte) (len >> 16);
            result[p++] = (byte) (len >> 8);
            result[p++] = (byte) len;
        }
        // Smithy member names are always ASCII
        name.getBytes(0, len, result, p);
        return result;
    }

    // ---- Flat struct serializer: writes field name + value in one shot ----

    private final class CborStructSerializer implements ShapeSerializer {

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 1);
            System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
            pos += nameBytes.length;
            buf[pos++] = (byte) (value ? TYPE_SIMPLE_TRUE : TYPE_SIMPLE_FALSE);
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 9);
            System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
            pos += nameBytes.length;
            writeLongUnchecked(value);
        }

        @Override
        public void writeShort(Schema schema, short value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 9);
            System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
            pos += nameBytes.length;
            writeLongUnchecked(value);
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 9);
            System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
            pos += nameBytes.length;
            writeLongUnchecked(value);
        }

        @Override
        public void writeLong(Schema schema, long value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 9);
            System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
            pos += nameBytes.length;
            writeLongUnchecked(value);
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 5);
            System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
            pos += nameBytes.length;
            buf[pos++] = (byte) TYPE_SIMPLE_FLOAT;
            BE_INT.set(buf, pos, Float.floatToRawIntBits(value));
            pos += 4;
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 9);
            System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
            pos += nameBytes.length;
            writeDoubleUnchecked(Double.doubleToRawLongBits(value));
        }

        @Override
        public void writeNull(Schema schema) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length + 1);
            System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
            pos += nameBytes.length;
            buf[pos++] = (byte) TYPE_SIMPLE_NULL;
        }

        // Variable-size and recursive types: write field name then delegate to outer.

        @Override
        public void writeString(Schema schema, String value) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeString(schema, value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeBlob(schema, value);
        }

        @Override
        public void writeBlob(Schema schema, byte[] value) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeBlob(schema, value);
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeTimestamp(schema, value);
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeBigInteger(schema, value);
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeBigDecimal(schema, value);
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeStruct(schema, struct);
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeList(schema, listState, size, consumer);
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeMap(schema, mapState, size, consumer);
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeDocument(schema, value);
        }

        private void writeFieldNameBytes(Schema schema) {
            byte[] nameBytes = resolveFieldNameBytes(schema);
            ensureCapacity(nameBytes.length);
            System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
            pos += nameBytes.length;
        }
    }

    // ---- Map serializer ----

    private final class CborMapSerializer implements MapSerializer {
        @Override
        @SuppressWarnings("deprecation")
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            int keyLen = key.length();
            ensureCapacity(5 + keyLen * 3);
            int headerStart = pos;
            tagAndLengthUnchecked(TYPE_TEXTSTRING, keyLen);
            key.getBytes(0, keyLen, buf, pos);
            int orAccum = 0;
            for (int i = 0; i < keyLen; i++) {
                orAccum |= buf[pos + i];
            }
            if (orAccum >= 0) {
                pos += keyLen;
            } else {
                pos = headerStart;
                byte[] utf8 = key.getBytes(StandardCharsets.UTF_8);
                tagAndLengthUnchecked(TYPE_TEXTSTRING, utf8.length);
                System.arraycopy(utf8, 0, buf, pos, utf8.length);
                pos += utf8.length;
            }
            valueSerializer.accept(state, CborSerializer.this);
        }
    }

    // ---- Document struct serializer (writes __type) ----

    private static final byte[] TYPE_FIELD_BYTES = encodeMemberName("__type");

    private static final class SerializeDocumentContents extends SpecificShapeSerializer {
        private final CborSerializer parent;

        SerializeDocumentContents(CborSerializer parent) {
            this.parent = parent;
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            parent.ensureCapacity(1);
            parent.buf[parent.pos++] = (byte) MAP_STREAM;
            parent.startCollection(true);
            // Write __type field
            parent.ensureCapacity(TYPE_FIELD_BYTES.length);
            System.arraycopy(TYPE_FIELD_BYTES, 0, parent.buf, parent.pos, TYPE_FIELD_BYTES.length);
            parent.pos += TYPE_FIELD_BYTES.length;
            parent.writeString(null, schema.id().toString());
            struct.serializeMembers(parent.structSerializer);
            parent.endMap();
        }
    }
}
