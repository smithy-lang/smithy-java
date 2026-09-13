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
import java.util.function.BiConsumer;
import software.amazon.smithy.java.codecs.commons.CompactStringAccess;
import software.amazon.smithy.java.codecs.commons.StripedPool;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * EXPERIMENTAL backward (right-to-left) Sparrowhawk serializer. See perf-opt/backward-writer.md.
 *
 * <p>The buffer is written from its end toward its start: a container writes its values first, measures
 * them, and prepends its exact length prefix and section headers. Nothing is reserved, backfilled, or
 * compacted, and no presence contract is needed; presence bits accumulate from the member callbacks
 * themselves, so structures of any width take the same path.
 *
 * <p>Requires member callbacks in strictly DESCENDING memberIndex order and list consumers that iterate
 * in reverse: only code generated with the {@code reverseMemberSerialization} codegen setting (or
 * hand-written equivalents) is compatible. Ascending dispatch throws. Map entries are emitted in reversed
 * entry order, which is semantically equivalent for Smithy maps but not byte-identical to the forward
 * serializer for map-bearing payloads.
 *
 * <p>Large blobs are recorded as holes (reserved ranges plus a source reference) and copied once,
 * directly into the final output, mirroring the forward serializer's extern gaps.
 */
final class SparrowhawkBackwardSerializer implements ShapeSerializer {

    private static final VarHandle LONG_LE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT_LE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    private static final int DEFAULT_BUF = 4096;
    private static final int DEFAULT_KEYS = 512;
    private static final int DEFAULT_HOLES = 16;
    private static final int MAX_POOLED_BUF = 1 << 17;
    private static final int MAX_POOLED_HOLES = 1 << 12;
    private static final int MAX_DEPTH = 1000;
    private static final int EXTERN_MIN_BYTES = 64;

    private static final int K_TOP = 0;
    private static final int K_STRUCT = 1;
    private static final int K_LIST = 2;
    private static final int K_MAP = 3;
    private static final int K_INLINE = 4;

    private static final long NO_TOKEN = Long.MIN_VALUE;

    private static final StripedPool<SparrowhawkBackwardSerializer, Void> POOL = new Pool();

    /** Written region is [cur, buf.length). */
    private byte[] buf;
    private int cur;

    /** Map keys, also written backward; each map consumes its own contiguous block at close. */
    private byte[] keys;
    private int kCur;

    // Blob holes: reserved ranges filled from the source during the final copy. Distances are from the
    // buffer END so growth never invalidates them. Appended distances increase monotonically, so walking
    // the arrays backward yields ascending output positions.
    private int[] holeDist;
    private long[] holeData;
    private ByteBuffer[] holeRefs;
    private int holeCount;

    // Per-site one-entry layout memos, mirroring the forward serializer's.
    private Schema structSchema;
    private SparrowhawkSchemaExtensions.Layout structLayout;
    private Schema listSchema;
    private SparrowhawkSchemaExtensions.Layout listLayout;
    private Schema mapSchema;
    private SparrowhawkSchemaExtensions.Layout mapLayout;

    // Active container state, suspended/resumed via call-stack locals around nested containers.
    private int cKind = K_TOP;
    private SparrowhawkSchemaExtensions.Layout cLayout;
    private int cSection = -1;
    private int cGroup = -1;
    private long cBits;
    private int cLastMember = Integer.MAX_VALUE;
    private int cCount;
    private boolean cSparse;
    /** K_INLINE: the expected memberIndex, cleared to -1 once written. */
    private int cInlineMember;
    private int depth;

    SparrowhawkBackwardSerializer() {
        this.buf = new byte[DEFAULT_BUF];
        this.cur = buf.length;
        this.keys = new byte[DEFAULT_KEYS];
        this.kCur = keys.length;
        this.holeDist = new int[DEFAULT_HOLES];
        this.holeData = new long[DEFAULT_HOLES];
        this.holeRefs = new ByteBuffer[DEFAULT_HOLES];
    }

    static SparrowhawkBackwardSerializer acquire() {
        return POOL.acquire(null);
    }

    static void release(SparrowhawkBackwardSerializer serializer) {
        POOL.release(serializer);
    }

    private int used() {
        return buf.length - cur;
    }

    ByteBuffer finish() {
        int len = used();
        byte[] out = new byte[len];
        int in = cur;
        int op = 0;
        for (int i = holeCount - 1; i >= 0; i--) {
            int hStart = buf.length - holeDist[i];
            int seg = hStart - in;
            System.arraycopy(buf, in, out, op, seg);
            op += seg;
            long d = holeData[i];
            int hLen = (int) d;
            holeRefs[i].get((int) (d >>> 32), out, op, hLen);
            op += hLen;
            in = hStart + hLen;
        }
        System.arraycopy(buf, in, out, op, buf.length - in);
        resetState();
        return ByteBuffer.wrap(out);
    }

    private void resetState() {
        if (holeCount > 0) {
            Arrays.fill(holeRefs, 0, Math.min(holeCount, holeRefs.length), null);
        }
        cur = buf.length;
        kCur = keys.length;
        holeCount = 0;
        cKind = K_TOP;
        cLayout = null;
        cSection = -1;
        cGroup = -1;
        cBits = 0;
        cLastMember = Integer.MAX_VALUE;
        cCount = 0;
        cSparse = false;
        depth = 0;
    }

    @Override
    public void flush() {
        // In-memory only; the sink path stays on the forward serializer.
    }

    // ===== containers =====

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        SparrowhawkSchemaExtensions.Layout layout;
        if (schema == structSchema) {
            layout = structLayout;
        } else {
            layout = schema.getExtension(SparrowhawkSchemaExtensions.KEY);
            structSchema = schema;
            structLayout = layout;
        }
        if (layout == null || (layout.memberInfo == null && layout.memberCount != 0)) {
            throw new SerializationException("Not a Sparrowhawk structure schema: " + schema.id());
        }
        long token = beforeValue(schema);
        // Single-leaf-member fast path (unions of scalars): presence isn't required backward, but when a
        // generated struct offers it, the whole group state machine can be skipped.
        long presence = layout.memberCount != 0 && layout.memberCount <= 63
                ? struct.presenceBits()
                : SerializableStruct.PRESENCE_UNKNOWN;
        if (presence >= 0
                && (presence & (presence - 1)) == 0
                && (presence & layout.leafMembers) != 0
                && (presence >>> layout.memberCount) == 0) {
            writeStructInline(struct, layout, Long.numberOfTrailingZeros(presence));
            afterValue(token);
            return;
        }
        int mark = used();

        int pKind = cKind;
        var pLayout = cLayout;
        int pSection = cSection;
        int pGroup = cGroup;
        long pBits = cBits;
        int pLastMember = cLastMember;

        if (++depth > MAX_DEPTH) {
            throw new SerializationException("Maximum Sparrowhawk nesting depth exceeded");
        }
        cKind = K_STRUCT;
        cLayout = layout;
        cSection = -1;
        cGroup = -1;
        cBits = 0;
        cLastMember = Integer.MAX_VALUE;

        struct.serializeMembers(this);

        closeGroup();
        prependUVarint(Sparrowhawk.byteListHeader(used() - mark));
        depth--;

        cKind = pKind;
        cLayout = pLayout;
        cSection = pSection;
        cGroup = pGroup;
        cBits = pBits;
        cLastMember = pLastMember;
        afterValue(token);
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        SparrowhawkSchemaExtensions.Layout layout;
        if (schema == listSchema) {
            layout = listLayout;
        } else {
            layout = schema.getExtension(SparrowhawkSchemaExtensions.KEY);
            listSchema = schema;
            listLayout = layout;
        }
        if (layout == null || layout.memberInfo != null) {
            throw new SerializationException("Not a Sparrowhawk list schema: " + schema.id());
        }
        long token = beforeValue(schema);

        int pKind = cKind;
        int pCount = cCount;
        boolean pSparse = cSparse;

        if (++depth > MAX_DEPTH) {
            throw new SerializationException("Maximum Sparrowhawk nesting depth exceeded");
        }
        cKind = K_LIST;
        cCount = 0;
        cSparse = layout.sparse;

        consumer.accept(listState, this);

        if (size >= 0 && cCount != size) {
            throw new SerializationException(
                    "List reported size " + size + " but wrote " + cCount + " elements: " + schema.id());
        }
        prependUVarint(Sparrowhawk.typedListHeader(cCount, layout.elementTag));
        depth--;

        cKind = pKind;
        cCount = pCount;
        cSparse = pSparse;
        afterValue(token);
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        SparrowhawkSchemaExtensions.Layout layout;
        if (schema == mapSchema) {
            layout = mapLayout;
        } else {
            layout = schema.getExtension(SparrowhawkSchemaExtensions.KEY);
            mapSchema = schema;
            mapLayout = layout;
        }
        if (layout == null || layout.memberInfo != null) {
            throw new SerializationException("Not a Sparrowhawk map schema: " + schema.id());
        }
        long token = beforeValue(schema);
        int mark = used();
        int kMark = keys.length - kCur;

        int pKind = cKind;
        var pLayout = cLayout;
        int pCount = cCount;
        boolean pSparse = cSparse;

        if (++depth > MAX_DEPTH) {
            throw new SerializationException("Maximum Sparrowhawk nesting depth exceeded");
        }
        cKind = K_MAP;
        cLayout = layout;
        cCount = 0;
        cSparse = layout.sparse;

        consumer.accept(mapState, mapSerializer);

        int n = cCount;
        if (n == 0) {
            prependByte(Sparrowhawk.EMPTY_BYTE_LIST);
        } else {
            prependUVarint(Sparrowhawk.typedListHeader(n, layout.elementTag));
            // This map's key block sits at the top of the backward key scratch (nested maps consumed
            // theirs at their own close); one block copy, then pop it.
            int kLen = (keys.length - kCur) - kMark;
            ensure(kLen);
            cur -= kLen;
            System.arraycopy(keys, kCur, buf, cur, kLen);
            kCur += kLen;
            prependUVarint(Sparrowhawk.typedListHeader(n, Sparrowhawk.LIST_LEN_DELIMITED));
            prependByte((byte) (2 * Sparrowhawk.MAP_FIELDSET + 1));
            prependUVarint(Sparrowhawk.byteListHeader(used() - mark));
        }
        depth--;

        cKind = pKind;
        cLayout = pLayout;
        cCount = pCount;
        cSparse = pSparse;
        afterValue(token);
    }

    private final SparrowhawkBackwardMapSerializer mapSerializer = new SparrowhawkBackwardMapSerializer();

    private final class SparrowhawkBackwardMapSerializer implements MapSerializer {
        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            cCount++;
            prependKeyByteList(key);
            valueSerializer.accept(state, SparrowhawkBackwardSerializer.this);
        }
    }

    // ===== member/element bookkeeping =====

    private long beforeValue(Schema schema) {
        switch (cKind) {
            case K_STRUCT -> memberStart(schema);
            case K_INLINE -> {
                if (schema.memberIndex() != cInlineMember) {
                    throw new SerializationException(
                            "Presence-inline struct wrote unexpected member " + schema.id());
                }
                cInlineMember = -1;
            }
            case K_LIST -> {
                cCount++;
                if (cSparse) {
                    return used();
                }
            }
            case K_MAP -> {
                if (cSparse) {
                    return used();
                }
            }
            default -> {
                // K_TOP: nothing to do.
            }
        }
        return NO_TOKEN;
    }

    private void afterValue(long token) {
        if (token != NO_TOKEN) {
            long valueLen = used() - token;
            prependByte(Sparrowhawk.SPARSE_PRESENT_MARKER);
            prependUVarint(Sparrowhawk.byteListHeader(valueLen + 1));
        }
    }

    /** Single present member: no group state machine; header and prefix prepend from the layout. */
    private void writeStructInline(SerializableStruct struct, SparrowhawkSchemaExtensions.Layout layout, int mi) {
        int mark = used();
        int pKind = cKind;
        var pLayout = cLayout;
        int pInline = cInlineMember;
        if (++depth > MAX_DEPTH) {
            throw new SerializationException("Maximum Sparrowhawk nesting depth exceeded");
        }
        cKind = K_INLINE;
        cLayout = layout;
        cInlineMember = mi;

        struct.serializeMembers(this);

        if (cInlineMember != -1) {
            throw new SerializationException("presenceBits() reported one member but none was written");
        }
        int in = layout.memberInfo[mi];
        int section = (in >>> 6) & 3;
        int group = in >>> 8;
        if (group > 0) {
            prependUVarint(group - 1);
            prependUVarint((1L << ((in & 0x3F) + 3)) | section | Sparrowhawk.CONTINUATION);
        } else {
            prependUVarint((1L << ((in & 0x3F) + 3)) | section);
        }
        prependUVarint(Sparrowhawk.byteListHeader(used() - mark));
        depth--;

        cKind = pKind;
        cLayout = pLayout;
        cInlineMember = pInline;
    }

    private void memberStart(Schema member) {
        int mi = member.memberIndex();
        int[] info = cLayout.memberInfo;
        if (mi >= cLastMember || mi >= info.length) {
            throw new SerializationException(
                    "Backward serialization requires strictly descending memberIndex order: " + member.id());
        }
        cLastMember = mi;
        int in = info[mi];
        int section = (in >>> 6) & 3;
        int group = in >>> 8;
        if (section != cSection || group != cGroup) {
            closeGroup();
            cSection = section;
            cGroup = group;
            cBits = 0;
        }
        cBits |= 1L << (in & 0x3F);
    }

    /** Prepends the finished group's exact header (and continuation offset) before its values. */
    private void closeGroup() {
        if (cSection >= 0) {
            if (cGroup > 0) {
                prependUVarint(cGroup - 1);
                // Prepend the header after the offset so the wire reads [header][offset][values].
                prependUVarint((cBits << 3) | cSection | Sparrowhawk.CONTINUATION);
            } else {
                prependUVarint((cBits << 3) | cSection);
            }
            cSection = -1;
        }
    }

    // ===== scalar writes =====

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        long token = beforeValue(schema);
        prependByte(value ? (byte) 3 : (byte) 1);
        afterValue(token);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        writeInteger(schema, value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        writeInteger(schema, value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        long token = beforeValue(schema);
        prependUVarint(Integer.toUnsignedLong(Sparrowhawk.zigzag(value)));
        afterValue(token);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        long token = beforeValue(schema);
        prependUVarint(Sparrowhawk.zigzag(value));
        afterValue(token);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        long token = beforeValue(schema);
        ensure(4);
        cur -= 4;
        INT_LE.set(buf, cur, Float.floatToIntBits(value));
        afterValue(token);
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        long token = beforeValue(schema);
        prependFixed8(Double.doubleToLongBits(value));
        afterValue(token);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        long token = beforeValue(schema);
        prependFixed8(Double.doubleToLongBits(value.toEpochMilli() / 1000d));
        afterValue(token);
    }

    @Override
    public void writeString(Schema schema, String value) {
        long token = beforeValue(schema);
        byte[] latin1 = CompactStringAccess.latin1Bytes(value);
        if (latin1 != null) {
            int high = SparrowhawkSerializer.countHigh(latin1);
            int utf8Len = latin1.length + high;
            ensure(utf8Len);
            cur -= utf8Len;
            SparrowhawkSerializer.rawLatin1AsUtf8(latin1, high, buf, cur);
            prependUVarint(Sparrowhawk.byteListHeader(utf8Len));
        } else {
            byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
            prependBytes(utf8);
            prependUVarint(Sparrowhawk.byteListHeader(utf8.length));
        }
        afterValue(token);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        long token = beforeValue(schema);
        int len = value.remaining();
        if (len >= EXTERN_MIN_BYTES) {
            ensure(len);
            cur -= len;
            addHole(value, len);
        } else {
            ensure(len);
            cur -= len;
            value.get(value.position(), buf, cur, len);
        }
        prependUVarint(Sparrowhawk.byteListHeader(len));
        afterValue(token);
    }

    private void addHole(ByteBuffer value, int len) {
        int i = holeCount;
        if (i == holeDist.length) {
            holeDist = Arrays.copyOf(holeDist, i * 2);
            holeData = Arrays.copyOf(holeData, i * 2);
            holeRefs = Arrays.copyOf(holeRefs, i * 2);
        }
        holeDist[i] = used();
        holeData[i] = ((long) value.position() << 32) | len;
        holeRefs[i] = value;
        holeCount = i + 1;
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        long token = beforeValue(schema);
        byte[] b = value.toByteArray();
        prependBytes(b);
        prependUVarint(Sparrowhawk.byteListHeader(b.length));
        afterValue(token);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        long token = beforeValue(schema);
        int mark = used();
        byte[] mantissa = value.unscaledValue().toByteArray();
        prependBytes(mantissa);
        prependUVarint(Sparrowhawk.byteListHeader(mantissa.length));
        prependUVarint((1L << 3) | Sparrowhawk.T_LIST);
        prependUVarint(Integer.toUnsignedLong(Sparrowhawk.zigzag(-value.scale())));
        prependUVarint((1L << 3) | Sparrowhawk.T_VARINT);
        prependUVarint(Sparrowhawk.byteListHeader(used() - mark));
        afterValue(token);
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        throw new SerializationException("Sparrowhawk does not support document types");
    }

    @Override
    public void writeNull(Schema schema) {
        switch (cKind) {
            case K_LIST -> {
                if (!cSparse) {
                    throw new SerializationException("Cannot write null to a non-sparse Sparrowhawk list");
                }
                cCount++;
                prependByte(Sparrowhawk.EMPTY_BYTE_LIST);
            }
            case K_MAP -> {
                if (!cSparse) {
                    throw new SerializationException("Cannot write null to a non-sparse Sparrowhawk map");
                }
                prependByte(Sparrowhawk.EMPTY_BYTE_LIST);
            }
            default -> {
                // Absent struct member or top level: nothing to write.
            }
        }
    }

    // ===== backward buffer primitives =====

    private void ensure(int needed) {
        if (cur < needed) {
            grow(needed);
        }
    }

    private void grow(int needed) {
        int usedBytes = used();
        byte[] bigger = new byte[Math.max(buf.length * 2, usedBytes + needed + DEFAULT_BUF)];
        System.arraycopy(buf, cur, bigger, bigger.length - usedBytes, usedBytes);
        buf = bigger;
        cur = bigger.length - usedBytes;
    }

    private void prependByte(byte b) {
        ensure(1);
        buf[--cur] = b;
    }

    private void prependBytes(byte[] b) {
        ensure(b.length);
        cur -= b.length;
        System.arraycopy(b, 0, buf, cur, b.length);
    }

    /**
     * Varint prepend. Encodings up to eight bytes use a single right-aligned long store: the store's
     * low bytes land below the new cursor, in space that is unwritten by definition, so nothing is
     * clobbered.
     */
    private void prependUVarint(long v) {
        int n = Sparrowhawk.uvarintSize(v);
        if (n <= 8) {
            ensure(8);
            long enc = (2 * v + 1) << (n - 1);
            LONG_LE.set(buf, cur - 8, enc << ((8 - n) * 8));
            cur -= n;
        } else {
            ensure(9);
            cur -= 9;
            buf[cur] = 0;
            LONG_LE.set(buf, cur + 1, v);
        }
    }

    private static void writeExactUVarint(byte[] b, int at, long v, int n) {
        if (n == 9) {
            b[at] = 0;
            for (int i = 0; i < 8; i++) {
                b[at + 1 + i] = (byte) (v >>> (8 * i));
            }
        } else {
            long enc = (2 * v + 1) << (n - 1);
            for (int i = 0; i < n; i++) {
                b[at + i] = (byte) (enc >>> (8 * i));
            }
        }
    }

    private void prependFixed8(long bits) {
        ensure(8);
        cur -= 8;
        LONG_LE.set(buf, cur, bits);
    }

    /** Prepends an encoded key byte-list into the backward key scratch. */
    private void prependKeyByteList(String key) {
        byte[] latin1 = CompactStringAccess.latin1Bytes(key);
        byte[] utf8 = null;
        int utf8Len;
        int high = 0;
        if (latin1 != null) {
            high = SparrowhawkSerializer.countHigh(latin1);
            utf8Len = latin1.length + high;
        } else {
            utf8 = key.getBytes(StandardCharsets.UTF_8);
            utf8Len = utf8.length;
        }
        long header = Sparrowhawk.byteListHeader(utf8Len);
        int headerLen = Sparrowhawk.uvarintSize(header);
        int total = headerLen + utf8Len;
        if (kCur < total) {
            int usedKeys = keys.length - kCur;
            byte[] bigger = new byte[Math.max(keys.length * 2, usedKeys + total + DEFAULT_KEYS)];
            System.arraycopy(keys, kCur, bigger, bigger.length - usedKeys, usedKeys);
            keys = bigger;
            kCur = bigger.length - usedKeys;
        }
        kCur -= total;
        if (headerLen <= 8 && kCur + headerLen >= 8) {
            // Right-aligned store: the junk low bytes land below kCur, in unwritten key scratch.
            long enc = (2 * header + 1) << (headerLen - 1);
            LONG_LE.set(keys, kCur + headerLen - 8, enc << ((8 - headerLen) * 8));
        } else {
            writeExactUVarint(keys, kCur, header, headerLen);
        }
        if (latin1 != null) {
            SparrowhawkSerializer.rawLatin1AsUtf8(latin1, high, keys, kCur + headerLen);
        } else {
            System.arraycopy(utf8, 0, keys, kCur + headerLen, utf8Len);
        }
    }

    // ===== pooling =====

    private static final class Pool extends StripedPool<SparrowhawkBackwardSerializer, Void> {
        @Override
        protected SparrowhawkBackwardSerializer create(Void context) {
            return new SparrowhawkBackwardSerializer();
        }

        @Override
        protected boolean canPool(SparrowhawkBackwardSerializer s) {
            return true;
        }

        @Override
        protected void prepareForPool(SparrowhawkBackwardSerializer s) {
            if (s.buf.length > MAX_POOLED_BUF) {
                s.buf = new byte[DEFAULT_BUF];
                s.cur = s.buf.length;
            }
            if (s.keys.length > MAX_POOLED_BUF) {
                s.keys = new byte[DEFAULT_KEYS];
                s.kCur = s.keys.length;
            }
            if (s.holeDist.length > MAX_POOLED_HOLES) {
                s.holeDist = new int[DEFAULT_HOLES];
                s.holeData = new long[DEFAULT_HOLES];
                s.holeRefs = new ByteBuffer[DEFAULT_HOLES];
            }
        }

        @Override
        protected boolean reset(SparrowhawkBackwardSerializer s, Void context) {
            s.resetState();
            return true;
        }
    }
}
