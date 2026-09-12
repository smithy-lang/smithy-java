/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * A {@link ShapeDeserializer} for Sparrowhawk payloads: a flat single-pass reader over a byte array with
 * absolute positions.
 *
 * <p>Structures are decoded presence-first: each type-section's bitset drives dispatch through the schema's
 * precomputed {@code reverse[section][kIdx]} member tables, and fields unknown to the schema are skipped by
 * their wire type, satisfying the format's schema-evolution requirements.
 */
final class SparrowhawkDeserializer implements ShapeDeserializer {

    private static final VarHandle LONG_LE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT_LE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final int MAX_SKIP_DEPTH = 128;

    private final byte[] b;
    private int pos;
    private final int end;

    /** Set before dispatching a null sparse element; cleared by {@link #readNull}. */
    private boolean nullFlag;

    SparrowhawkDeserializer(byte[] source) {
        this(source, 0, source.length);
    }

    SparrowhawkDeserializer(byte[] source, int offset, int length) {
        this.b = source;
        this.pos = offset;
        this.end = offset + length;
    }

    SparrowhawkDeserializer(ByteBuffer source) {
        if (source.hasArray()) {
            this.b = source.array();
            this.pos = source.arrayOffset() + source.position();
            this.end = pos + source.remaining();
        } else {
            byte[] copy = new byte[source.remaining()];
            source.duplicate().get(copy);
            this.b = copy;
            this.pos = 0;
            this.end = copy.length;
        }
    }

    // ===== varint / primitive decoding =====

    private long uvarint() {
        if (pos >= end) {
            throw new SerializationException("Unexpected end of Sparrowhawk payload");
        }
        int f = b[pos++] & 0xFF;
        if ((f & 1) != 0) {
            return f >>> 1;
        }
        int extra = Sparrowhawk.uvarintExtraBytes(f);
        if (pos + extra > end) {
            throw new SerializationException("Truncated Sparrowhawk varint");
        }
        if (extra == 8) {
            long v = (long) LONG_LE.get(b, pos);
            pos += 8;
            return v;
        }
        if (pos + 8 <= b.length) {
            // Read the whole varint (first byte included) as one little-endian long.
            long raw = (long) LONG_LE.get(b, pos - 1);
            pos += extra;
            int totalBits = 8 * (extra + 1);
            long masked = totalBits == 64 ? raw : raw & ((1L << totalBits) - 1);
            return masked >>> (extra + 1);
        }
        long acc = (long) f >>> (extra + 1);
        for (int i = 1; i <= extra; i++) {
            acc |= (long) (b[pos++] & 0xFF) << (8 * i - (extra + 1));
        }
        return acc;
    }

    private int intBits() {
        if (pos + 4 > end) {
            throw new SerializationException("Truncated Sparrowhawk payload");
        }
        int v = (int) INT_LE.get(b, pos);
        pos += 4;
        return v;
    }

    private long longBits() {
        if (pos + 8 > end) {
            throw new SerializationException("Truncated Sparrowhawk payload");
        }
        long v = (long) LONG_LE.get(b, pos);
        pos += 8;
        return v;
    }

    private int byteListLength() {
        long h = uvarint();
        if ((h & 1) != 0) {
            throw new SerializationException("Expected a Sparrowhawk byte list");
        }
        long len = h >>> 1;
        if (len > end - pos) {
            throw new SerializationException("Sparrowhawk byte list length exceeds payload size");
        }
        return (int) len;
    }

    // ===== scalars =====

    @Override
    public boolean readBoolean(Schema schema) {
        return uvarint() != 0;
    }

    @Override
    public byte readByte(Schema schema) {
        return (byte) readInteger(schema);
    }

    @Override
    public short readShort(Schema schema) {
        return (short) readInteger(schema);
    }

    @Override
    public int readInteger(Schema schema) {
        return Sparrowhawk.unzigzag((int) uvarint());
    }

    @Override
    public long readLong(Schema schema) {
        return Sparrowhawk.unzigzag(uvarint());
    }

    @Override
    public float readFloat(Schema schema) {
        return Float.intBitsToFloat(intBits());
    }

    @Override
    public double readDouble(Schema schema) {
        return Double.longBitsToDouble(longBits());
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        return Instant.ofEpochMilli(Math.round(Double.longBitsToDouble(longBits()) * 1000d));
    }

    @Override
    public String readString(Schema schema) {
        int len = byteListLength();
        String s = new String(b, pos, len, StandardCharsets.UTF_8);
        pos += len;
        return s;
    }

    @Override
    public ByteBuffer readBlob(Schema schema) {
        // Zero-copy view over the payload, matching the CBOR codec's readBlob behavior.
        int len = byteListLength();
        ByteBuffer result = ByteBuffer.wrap(b, pos, len).slice();
        pos += len;
        return result;
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        int len = byteListLength();
        var result = new BigInteger(b, pos, len);
        pos += len;
        return result;
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        int len = byteListLength();
        int structEnd = pos + len;
        int exponent = 0;
        byte[] mantissa = null;
        while (pos < structEnd) {
            long fs = uvarint();
            int type = (int) (fs & 3);
            int group = (fs & Sparrowhawk.CONTINUATION) != 0 ? (int) uvarint() + 1 : 0;
            long bits = fs >>> 3;
            while (bits != 0) {
                int k = group * Sparrowhawk.FIELDS_PER_GROUP + Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                if (type == Sparrowhawk.T_VARINT && k == 0) {
                    exponent = Sparrowhawk.unzigzag((int) uvarint());
                } else if (type == Sparrowhawk.T_LIST && k == 0) {
                    int mLen = byteListLength();
                    mantissa = Arrays.copyOfRange(b, pos, pos + mLen);
                    pos += mLen;
                } else {
                    skipValue(type, 0);
                }
            }
        }
        if (mantissa == null) {
            throw new SerializationException("Sparrowhawk bigDecimal is missing its mantissa");
        }
        return new BigDecimal(new BigInteger(mantissa), -exponent);
    }

    @Override
    public Document readDocument() {
        throw new SerializationException("Sparrowhawk does not support document types");
    }

    @Override
    public boolean isNull() {
        return nullFlag;
    }

    @Override
    public <T> T readNull() {
        nullFlag = false;
        return null;
    }

    // ===== containers =====

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
        SparrowhawkSchemaExtensions.Layout layout = schema.getExtension(SparrowhawkSchemaExtensions.KEY);
        if (layout == null || (layout.memberInfo == null && layout.memberCount != 0)) {
            throw new SerializationException("Not a Sparrowhawk structure schema: " + targetId(schema));
        }
        int len = byteListLength();
        int structEnd = pos + len;
        while (pos < structEnd) {
            long fs = uvarint();
            int type = (int) (fs & 3);
            int group = (fs & Sparrowhawk.CONTINUATION) != 0 ? (int) uvarint() + 1 : 0;
            long bits = fs >>> 3;
            Schema[] reverse = layout.reverse == null ? null : layout.reverse[type];
            int base = group * Sparrowhawk.FIELDS_PER_GROUP;
            while (bits != 0) {
                int k = base + Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                Schema member = reverse != null && k < reverse.length ? reverse[k] : null;
                if (member == null) {
                    consumer.unknownMember(state, String.valueOf(k));
                    skipValue(type, 0);
                } else {
                    consumer.accept(state, member, this);
                }
            }
        }
        if (pos != structEnd) {
            throw new SerializationException("Malformed Sparrowhawk structure: content overran its length");
        }
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
        SparrowhawkSchemaExtensions.Layout layout = schema.getExtension(SparrowhawkSchemaExtensions.KEY);
        if (layout == null || layout.memberInfo != null) {
            throw new SerializationException("Not a Sparrowhawk list schema: " + targetId(schema));
        }
        long h = uvarint();
        if ((h & 1) == 0) {
            throw new SerializationException("Expected a typed Sparrowhawk list");
        }
        int count = checkedCount(h);
        if (layout.sparse) {
            for (int i = 0; i < count; i++) {
                readSparseElement(state, consumer);
            }
        } else {
            for (int i = 0; i < count; i++) {
                consumer.accept(state, this);
            }
        }
    }

    private <T> void readSparseElement(T state, ListMemberConsumer<T> consumer) {
        long eh = uvarint();
        if (eh == 0) {
            nullFlag = true;
            consumer.accept(state, this);
            nullFlag = false;
        } else {
            if ((eh & 1) != 0) {
                throw new SerializationException("Malformed Sparrowhawk sparse element");
            }
            if (pos >= end || b[pos++] != Sparrowhawk.SPARSE_PRESENT_MARKER) {
                throw new SerializationException("Malformed Sparrowhawk sparse element wrapper");
            }
            consumer.accept(state, this);
        }
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
        SparrowhawkSchemaExtensions.Layout layout = schema.getExtension(SparrowhawkSchemaExtensions.KEY);
        if (layout == null || layout.memberInfo != null) {
            throw new SerializationException("Not a Sparrowhawk map schema: " + targetId(schema));
        }
        int len = byteListLength();
        if (len == 0) {
            return;
        }
        int mapEnd = pos + len;
        long fs = uvarint();
        if ((fs & 3) != Sparrowhawk.T_LIST || (fs >>> 3 & 0b11) != 0b11) {
            throw new SerializationException("Malformed Sparrowhawk map: expected key and value list fields");
        }
        long kh = uvarint();
        if ((kh & 7) != Sparrowhawk.LIST_LEN_DELIMITED) {
            throw new SerializationException("Malformed Sparrowhawk map: keys must be length-delimited");
        }
        int count = checkedCount(kh);
        String[] keys = new String[count];
        for (int i = 0; i < count; i++) {
            int kLen = byteListLength();
            keys[i] = new String(b, pos, kLen, StandardCharsets.UTF_8);
            pos += kLen;
        }
        long vh = uvarint();
        if ((vh & 1) == 0 || checkedCount(vh) != count) {
            throw new SerializationException("Malformed Sparrowhawk map: key and value counts differ");
        }
        if (layout.sparse) {
            for (int i = 0; i < count; i++) {
                readSparseMapValue(state, keys[i], consumer);
            }
        } else {
            for (int i = 0; i < count; i++) {
                consumer.accept(state, keys[i], this);
            }
        }
        if (pos != mapEnd) {
            throw new SerializationException("Malformed Sparrowhawk map: content overran its length");
        }
    }

    private <T> void readSparseMapValue(T state, String key, MapMemberConsumer<String, T> consumer) {
        long eh = uvarint();
        if (eh == 0) {
            nullFlag = true;
            consumer.accept(state, key, this);
            nullFlag = false;
        } else {
            if ((eh & 1) != 0 || pos >= end || b[pos++] != Sparrowhawk.SPARSE_PRESENT_MARKER) {
                throw new SerializationException("Malformed Sparrowhawk sparse map value");
            }
            consumer.accept(state, key, this);
        }
    }

    @Override
    public int containerSize() {
        int saved = pos;
        try {
            long h = uvarint();
            if ((h & 1) != 0) {
                // A typed list: the header carries the element count.
                return checkedCount(h);
            }
            // A byte list: a map (or struct/string, for which the answer is unused). Peek the key count.
            long len = h >>> 1;
            if (len == 0) {
                return 0;
            }
            if (len > end - pos) {
                return -1;
            }
            long fs = uvarint();
            if ((fs & 3) != Sparrowhawk.T_LIST || (fs >>> 3 & 0b11) != 0b11) {
                return -1;
            }
            long kh = uvarint();
            if ((kh & 7) != Sparrowhawk.LIST_LEN_DELIMITED) {
                return -1;
            }
            return checkedCount(kh);
        } catch (RuntimeException e) {
            return -1;
        } finally {
            pos = saved;
        }
    }

    private int checkedCount(long header) {
        long count = header >>> 3;
        // Every element occupies at least one byte; reject counts the remaining payload cannot hold.
        if (count > end - pos) {
            throw new SerializationException("Sparrowhawk list count exceeds payload size");
        }
        return (int) count;
    }

    // ===== unknown-field skipping =====

    private void skipValue(int type, int depth) {
        switch (type) {
            case Sparrowhawk.T_VARINT -> skipVarint();
            case Sparrowhawk.T_FOUR -> skipBytes(4);
            case Sparrowhawk.T_EIGHT -> skipBytes(8);
            default -> skipList(depth);
        }
    }

    private void skipVarint() {
        if (pos >= end) {
            throw new SerializationException("Truncated Sparrowhawk payload");
        }
        int f = b[pos++] & 0xFF;
        skipBytes(Sparrowhawk.uvarintExtraBytes(f));
    }

    private void skipBytes(int n) {
        if (n < 0 || n > end - pos) {
            throw new SerializationException("Truncated Sparrowhawk payload");
        }
        pos += n;
    }

    private void skipList(int depth) {
        if (depth > MAX_SKIP_DEPTH) {
            throw new SerializationException("Maximum Sparrowhawk nesting depth exceeded while skipping");
        }
        long h = uvarint();
        if ((h & 1) == 0) {
            skipBytes((int) Math.min(h >>> 1, Integer.MAX_VALUE));
            return;
        }
        int count = checkedCount(h);
        switch ((int) (h & 7)) {
            case Sparrowhawk.LIST_VARINTS -> {
                for (int i = 0; i < count; i++) {
                    skipVarint();
                }
            }
            case Sparrowhawk.LIST_FOUR -> skipBytes(4 * count);
            case Sparrowhawk.LIST_EIGHT -> skipBytes(8 * count);
            default -> {
                for (int i = 0; i < count; i++) {
                    skipList(depth + 1);
                }
            }
        }
    }

    private static String targetId(Schema schema) {
        return (schema.isMember() ? schema.memberTarget() : schema).id().toString();
    }
}
