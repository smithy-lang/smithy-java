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

    byte[] buf;
    int pos;

    private final OutputStream sink;

    // Bit i of collectionMask records whether level i was opened as indefinite-length.
    private long collectionMask = 0L;
    private int collectionDepth = 0;
    private long[] collectionOverflow;

    private byte[][] currentFieldNameTable;

    private final CborStructSerializer structSerializer = new CborStructSerializer();
    private final CborMapSerializer mapSerializer = new CborMapSerializer();
    private SerializeDocumentContents serializeDocumentContents;

    CborSerializer() {
        this.sink = null;
        this.buf = new byte[DEFAULT_BUF_SIZE];
        this.pos = 0;
    }

    CborSerializer(OutputStream sink) {
        this.sink = sink;
        this.buf = new byte[DEFAULT_BUF_SIZE];
        this.pos = 0;
    }

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
        // Pool full, let GC collect
    }

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

    private void tagAndLength(int type, int len) {
        ensureCapacity(5); // max: 1 type byte + 4 length bytes (int)
        tagAndLengthUnchecked(type, len);
    }

    /** Write tag+length without ensureCapacity, caller must have reserved space. */
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

    /** Write a CBOR integer without ensureCapacity, caller must have reserved 9 bytes. */
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

    private void writeBytes0(byte[] b, int len) {
        ensureCapacity(5 + len);
        tagAndLengthUnchecked(CborConstants.TYPE_BYTESTRING, len);
        System.arraycopy(b, 0, buf, pos, len);
        pos += len;
    }

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

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        ensureCapacity(1);
        buf[pos++] = (byte) MAP_STREAM;
        startCollection(true);

        byte[][] savedTable = currentFieldNameTable;
        Schema structSchema = schema.isMember() ? schema.memberTarget() : schema;
        var ext = structSchema.getExtension(CborSchemaExtensions.KEY);
        currentFieldNameTable = ext != null ? ext.fieldNameTable() : null;

        struct.serializeMembers(structSerializer);

        currentFieldNameTable = savedTable;
        endMap();
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        startArray(size);
        consumer.accept(listState, this);
        endArray();
    }

    @Override
    public void writeStructList(
            Schema schema,
            List<? extends SerializableStruct> values,
            Schema memberSchema
    ) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeStruct(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeStruct(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeString(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeString(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeBoolean(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeBoolean(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeByte(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeByte(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeShort(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeShort(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeInteger(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeInteger(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeLong(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeLong(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeFloat(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeFloat(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeDouble(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeDouble(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeBigInteger(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeBigInteger(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeBigDecimal(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeBigDecimal(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeBlob(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeBlob(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeTimestamp(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeTimestamp(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeDocumentList(
            Schema schema,
            List<? extends Document> values,
            Schema memberSchema
    ) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeDocument(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                writeDocument(memberSchema, v);
            }
        }
        endArray();
    }

    @Override
    public void writeEnumList(
            Schema schema,
            List<? extends SmithyEnum> values,
            Schema memberSchema
    ) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeString(memberSchema, values.get(i).getValue());
            }
        } else {
            for (var v : values) {
                writeString(memberSchema, v.getValue());
            }
        }
        endArray();
    }

    @Override
    public void writeIntEnumList(
            Schema schema,
            List<? extends SmithyIntEnum> values,
            Schema memberSchema
    ) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                writeInteger(memberSchema, values.get(i).getValue());
            }
        } else {
            for (var v : values) {
                writeInteger(memberSchema, v.getValue());
            }
        }
        endArray();
    }

    @Override
    public void writeSparseStructList(
            Schema schema,
            List<? extends SerializableStruct> values,
            Schema memberSchema
    ) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeStruct(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeStruct(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeString(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeString(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeInteger(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeInteger(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeLong(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeLong(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeDouble(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeDouble(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBlob(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBlob(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeTimestamp(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeTimestamp(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseDocumentList(
            Schema schema,
            List<? extends Document> values,
            Schema memberSchema
    ) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeDocument(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeDocument(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBoolean(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBoolean(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeByte(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeByte(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeShort(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeShort(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeFloat(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeFloat(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBigInteger(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBigInteger(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBigDecimal(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBigDecimal(memberSchema, v);
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseEnumList(
            Schema schema,
            List<? extends SmithyEnum> values,
            Schema memberSchema
    ) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeString(memberSchema, v.getValue());
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeString(memberSchema, v.getValue());
                }
            }
        }
        endArray();
    }

    @Override
    public void writeSparseIntEnumList(
            Schema schema,
            List<? extends SmithyIntEnum> values,
            Schema memberSchema
    ) {
        startArray(values.size());
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeInteger(memberSchema, v.getValue());
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeInteger(memberSchema, v.getValue());
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
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeStruct(valueSchema, entry.getValue());
        }
        endMap();
    }

    @Override
    public void writeStringMap(
            Schema schema,
            Map<String, String> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeString(valueSchema, entry.getValue());
        }
        endMap();
    }

    @Override
    public void writeBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeBoolean(valueSchema, entry.getValue());
        }
        endMap();
    }

    @Override
    public void writeByteMap(
            Schema schema,
            Map<String, Byte> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeByte(valueSchema, entry.getValue());
        }
        endMap();
    }

    @Override
    public void writeShortMap(
            Schema schema,
            Map<String, Short> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeShort(valueSchema, entry.getValue());
        }
        endMap();
    }

    @Override
    public void writeIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeInteger(valueSchema, entry.getValue());
        }
        endMap();
    }

    @Override
    public void writeLongMap(
            Schema schema,
            Map<String, Long> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeLong(valueSchema, entry.getValue());
        }
        endMap();
    }

    @Override
    public void writeFloatMap(
            Schema schema,
            Map<String, Float> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeFloat(valueSchema, entry.getValue());
        }
        endMap();
    }

    @Override
    public void writeDoubleMap(
            Schema schema,
            Map<String, Double> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeDouble(valueSchema, entry.getValue());
        }
        endMap();
    }

    @Override
    public void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeBigInteger(valueSchema, entry.getValue());
        }
        endMap();
    }

    @Override
    public void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeBigDecimal(valueSchema, entry.getValue());
        }
        endMap();
    }

    @Override
    public void writeBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeBlob(valueSchema, entry.getValue());
        }
        endMap();
    }

    @Override
    public void writeTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeTimestamp(valueSchema, entry.getValue());
        }
        endMap();
    }

    @Override
    public void writeDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeDocument(valueSchema, entry.getValue());
        }
        endMap();
    }

    @Override
    public void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeString(valueSchema, entry.getValue().getValue());
        }
        endMap();
    }

    @Override
    public void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            writeInteger(valueSchema, entry.getValue().getValue());
        }
        endMap();
    }

    @Override
    public void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeStruct(valueSchema, entry.getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseStringMap(
            Schema schema,
            Map<String, String> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeString(valueSchema, entry.getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeInteger(valueSchema, entry.getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseLongMap(
            Schema schema,
            Map<String, Long> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeLong(valueSchema, entry.getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseDoubleMap(
            Schema schema,
            Map<String, Double> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeDouble(valueSchema, entry.getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeBlob(valueSchema, entry.getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeTimestamp(valueSchema, entry.getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeDocument(valueSchema, entry.getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeBoolean(valueSchema, entry.getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseByteMap(
            Schema schema,
            Map<String, Byte> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeByte(valueSchema, entry.getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseShortMap(
            Schema schema,
            Map<String, Short> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeShort(valueSchema, entry.getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseFloatMap(
            Schema schema,
            Map<String, Float> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeFloat(valueSchema, entry.getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeBigInteger(valueSchema, entry.getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeBigDecimal(valueSchema, entry.getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeString(valueSchema, entry.getValue().getValue());
            }
        }
        endMap();
    }

    @Override
    public void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        startMap(values.size());
        for (var entry : values.entrySet()) {
            writeStringValue(entry.getKey());
            if (entry.getValue() == null) {
                writeNull(valueSchema);
            } else {
                writeInteger(valueSchema, entry.getValue().getValue());
            }
        }
        endMap();
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
        ensureCapacity(5 + charLen * 3);
        int headerStart = pos;
        //Don't scan if the string is too long.
        if (charLen < 1000) {
            int orAccum = 0;
            for (int i = 0; i < charLen; i++) {
                orAccum |= value.charAt(i);
            }
            if (orAccum < 0x80) {
                tagAndLengthUnchecked(TYPE_TEXTSTRING, charLen);
                value.getBytes(0, charLen, buf, pos);
                pos += charLen;
                return;
            }
        }
        encodeUtf8TextStringRewind(value, charLen, headerStart);
    }

    /**
     * Encodes {@code value} as a CBOR text string starting at {@code headerStart}. Caller must have
     * reserved {@code 5 + charLen * 3} bytes from {@code headerStart}. Writes data into a 5-byte-header
     * scratch area, backpatches the header with the actual UTF-8 byte length, and shifts the payload
     * left if the real header is shorter than 5 bytes. On exit, {@code pos} points past the payload.
     */
    private void encodeUtf8TextStringRewind(String value, int charLen, int headerStart) {
        int writeStart = headerStart + 5;
        int p = writeStart;
        for (int j = 0; j < charLen; j++) {
            int c = value.charAt(j);
            if (c < 0x80) {
                buf[p++] = (byte) c;
            } else if (c < 0x800) {
                buf[p++] = (byte) (0xC0 | (c >> 6));
                buf[p++] = (byte) (0x80 | (c & 0x3F));
            } else if (c >= 0xD800 && c <= 0xDBFF && j + 1 < charLen) {
                char low = value.charAt(j + 1);
                if (low >= 0xDC00 && low <= 0xDFFF) {
                    int cp = Character.toCodePoint((char) c, low);
                    buf[p++] = (byte) (0xF0 | (cp >> 18));
                    buf[p++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
                    buf[p++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
                    buf[p++] = (byte) (0x80 | (cp & 0x3F));
                    j++;
                } else {
                    buf[p++] = (byte) 0xEF;
                    buf[p++] = (byte) 0xBF;
                    buf[p++] = (byte) 0xBD;
                }
            } else if (c >= 0xDC00 && c <= 0xDFFF) {
                // Unpaired low surrogate, replace with U+FFFD to match JDK getBytes(UTF_8)
                buf[p++] = (byte) 0xEF;
                buf[p++] = (byte) 0xBF;
                buf[p++] = (byte) 0xBD;
            } else {
                buf[p++] = (byte) (0xE0 | (c >> 12));
                buf[p++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                buf[p++] = (byte) (0x80 | (c & 0x3F));
            }
        }
        int byteLen = p - writeStart;
        pos = headerStart;
        tagAndLengthUnchecked(TYPE_TEXTSTRING, byteLen);
        int actualHeaderLen = pos - headerStart;
        int shift = 5 - actualHeaderLen;
        if (shift > 0) {
            System.arraycopy(buf, writeStart, buf, writeStart - shift, byteLen);
        }
        pos = headerStart + actualHeaderLen + byteLen;
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        int len = value.remaining();
        ensureCapacity(5 + len);
        tagAndLengthUnchecked(TYPE_BYTESTRING, len);
        if (value.hasArray()) {
            System.arraycopy(value.array(), value.arrayOffset() + value.position(), buf, pos, len);
        } else {
            value.duplicate().get(buf, pos, len);
        }
        pos += len;
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        writeBytes0(value, value.length);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        long millis = value.toEpochMilli();
        if (millis % 1000 == 0) {
            ensureCapacity(10);
            buf[pos++] = (byte) (TYPE_TAG | TAG_TIME_EPOCH);
            writeLongUnchecked(millis / 1000);
        } else {
            double epochSeconds = millis / 1000D;
            ensureCapacity(10);
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
                writeBytes0(bytes, bytes.length);
            }
        }
    }

    private byte[] resolveFieldNameBytes(Schema schema) {
        byte[][] table = currentFieldNameTable;
        int idx = schema.memberIndex();
        if (table != null && idx >= 0 && idx < table.length && table[idx] != null) {
            return table[idx];
        }
        var ext = schema.getExtension(CborSchemaExtensions.KEY);
        if (ext != null && ext.memberNameBytes() != null) {
            return ext.memberNameBytes();
        }
        return encodeMemberName(schema.memberName());
    }

    @SuppressWarnings("deprecation")
    static byte[] encodeMemberName(String name) {
        int len = name.length();
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
        public void writeStructList(
                Schema schema,
                List<? extends SerializableStruct> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeStructList(schema, values, memberSchema);
        }

        @Override
        public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeStringList(schema, values, memberSchema);
        }

        @Override
        public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeBooleanList(schema, values, memberSchema);
        }

        @Override
        public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeByteList(schema, values, memberSchema);
        }

        @Override
        public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeShortList(schema, values, memberSchema);
        }

        @Override
        public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeLongList(schema, values, memberSchema);
        }

        @Override
        public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeFloatList(schema, values, memberSchema);
        }

        @Override
        public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeDoubleList(schema, values, memberSchema);
        }

        @Override
        public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeBigIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeBigDecimalList(schema, values, memberSchema);
        }

        @Override
        public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeBlobList(schema, values, memberSchema);
        }

        @Override
        public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeTimestampList(schema, values, memberSchema);
        }

        @Override
        public void writeDocumentList(
                Schema schema,
                List<? extends Document> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeDocumentList(schema, values, memberSchema);
        }

        @Override
        public void writeEnumList(
                Schema schema,
                List<? extends SmithyEnum> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeIntEnumList(
                Schema schema,
                List<? extends SmithyIntEnum> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeIntEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseStructList(
                Schema schema,
                List<? extends SerializableStruct> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseStructList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseStringList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseLongList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseDoubleList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseBlobList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseTimestampList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseDocumentList(
                Schema schema,
                List<? extends Document> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseDocumentList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseBooleanList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseByteList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseShortList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseFloatList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseBigIntegerList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseBigDecimalList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseEnumList(
                Schema schema,
                List<? extends SmithyEnum> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeSparseIntEnumList(
                Schema schema,
                List<? extends SmithyIntEnum> values,
                Schema memberSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseIntEnumList(schema, values, memberSchema);
        }

        @Override
        public void writeStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeStructMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeStringMap(
                Schema schema,
                Map<String, String> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeStringMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBooleanMap(
                Schema schema,
                Map<String, Boolean> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeBooleanMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeByteMap(
                Schema schema,
                Map<String, Byte> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeByteMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeShortMap(
                Schema schema,
                Map<String, Short> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeShortMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeIntegerMap(
                Schema schema,
                Map<String, Integer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeLongMap(
                Schema schema,
                Map<String, Long> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeLongMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeFloatMap(
                Schema schema,
                Map<String, Float> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeFloatMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeDoubleMap(
                Schema schema,
                Map<String, Double> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeDoubleMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeBigIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeBigDecimalMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeBlobMap(
                Schema schema,
                Map<String, ByteBuffer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeBlobMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeTimestampMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeDocumentMap(
                Schema schema,
                Map<String, ? extends Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeDocumentMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeIntEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseStructMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseStringMap(
                Schema schema,
                Map<String, String> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseStringMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseIntegerMap(
                Schema schema,
                Map<String, Integer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseLongMap(
                Schema schema,
                Map<String, Long> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseLongMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseDoubleMap(
                Schema schema,
                Map<String, Double> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseDoubleMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBlobMap(
                Schema schema,
                Map<String, ByteBuffer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseBlobMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseTimestampMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseDocumentMap(
                Schema schema,
                Map<String, ? extends Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseDocumentMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBooleanMap(
                Schema schema,
                Map<String, Boolean> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseBooleanMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseByteMap(
                Schema schema,
                Map<String, Byte> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseByteMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseShortMap(
                Schema schema,
                Map<String, Short> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseShortMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseFloatMap(
                Schema schema,
                Map<String, Float> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseFloatMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseBigIntegerMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseBigDecimalMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseEnumMap(schema, values, keySchema, valueSchema);
        }

        @Override
        public void writeSparseIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            writeFieldNameBytes(schema);
            CborSerializer.this.writeSparseIntEnumMap(schema, values, keySchema, valueSchema);
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

    private final class CborMapSerializer implements MapSerializer {
        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            writeStringValue(key);
            valueSerializer.accept(state, CborSerializer.this);
        }

        @Override
        public void writeStructEntry(
                Schema keySchema,
                String key,
                Schema valueSchema,
                SerializableStruct value
        ) {
            writeStringValue(key);
            CborSerializer.this.writeStruct(valueSchema, value);
        }

        @Override
        public void writeStringEntry(Schema keySchema, String key, Schema valueSchema, String value) {
            writeStringValue(key);
            CborSerializer.this.writeString(valueSchema, value);
        }

        @Override
        public void writeBooleanEntry(Schema keySchema, String key, Schema valueSchema, Boolean value) {
            writeStringValue(key);
            CborSerializer.this.writeBoolean(valueSchema, value);
        }

        @Override
        public void writeByteEntry(Schema keySchema, String key, Schema valueSchema, Byte value) {
            writeStringValue(key);
            CborSerializer.this.writeByte(valueSchema, value);
        }

        @Override
        public void writeShortEntry(Schema keySchema, String key, Schema valueSchema, Short value) {
            writeStringValue(key);
            CborSerializer.this.writeShort(valueSchema, value);
        }

        @Override
        public void writeIntegerEntry(Schema keySchema, String key, Schema valueSchema, Integer value) {
            writeStringValue(key);
            CborSerializer.this.writeInteger(valueSchema, value);
        }

        @Override
        public void writeLongEntry(Schema keySchema, String key, Schema valueSchema, Long value) {
            writeStringValue(key);
            CborSerializer.this.writeLong(valueSchema, value);
        }

        @Override
        public void writeFloatEntry(Schema keySchema, String key, Schema valueSchema, Float value) {
            writeStringValue(key);
            CborSerializer.this.writeFloat(valueSchema, value);
        }

        @Override
        public void writeDoubleEntry(Schema keySchema, String key, Schema valueSchema, Double value) {
            writeStringValue(key);
            CborSerializer.this.writeDouble(valueSchema, value);
        }

        @Override
        public void writeBigIntegerEntry(Schema keySchema, String key, Schema valueSchema, BigInteger value) {
            writeStringValue(key);
            CborSerializer.this.writeBigInteger(valueSchema, value);
        }

        @Override
        public void writeBigDecimalEntry(Schema keySchema, String key, Schema valueSchema, BigDecimal value) {
            writeStringValue(key);
            CborSerializer.this.writeBigDecimal(valueSchema, value);
        }

        @Override
        public void writeBlobEntry(Schema keySchema, String key, Schema valueSchema, ByteBuffer value) {
            writeStringValue(key);
            CborSerializer.this.writeBlob(valueSchema, value);
        }

        @Override
        public void writeTimestampEntry(Schema keySchema, String key, Schema valueSchema, Instant value) {
            writeStringValue(key);
            CborSerializer.this.writeTimestamp(valueSchema, value);
        }

        @Override
        public void writeDocumentEntry(Schema keySchema, String key, Schema valueSchema, Document value) {
            writeStringValue(key);
            CborSerializer.this.writeDocument(valueSchema, value);
        }

        @Override
        public void writeNullEntry(Schema keySchema, String key, Schema valueSchema) {
            writeStringValue(key);
            CborSerializer.this.writeNull(valueSchema);
        }

        @Override
        public void writeIntEnumEntry(Schema keySchema, String key, Schema valueSchema, int value) {
            writeStringValue(key);
            CborSerializer.this.writeInteger(valueSchema, value);
        }
    }

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
            parent.ensureCapacity(TYPE_FIELD_BYTES.length);
            System.arraycopy(TYPE_FIELD_BYTES, 0, parent.buf, parent.pos, TYPE_FIELD_BYTES.length);
            parent.pos += TYPE_FIELD_BYTES.length;
            parent.writeString(null, schema.id().toString());
            struct.serializeMembers(parent.structSerializer);
            parent.endMap();
        }
    }
}
