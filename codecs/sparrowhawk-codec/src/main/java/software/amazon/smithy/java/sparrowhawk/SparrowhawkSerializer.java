/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import java.io.OutputStream;
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
 * A {@link ShapeSerializer} producing Sparrowhawk payloads in a single streaming pass with optimistic
 * in-place length prefixes and one deferred compaction.
 *
 * <p>Because schema members are sorted so memberIndex order equals on-wire order, values stream directly
 * into the output buffer in wire order. Each struct reserves one byte for its byte-length prefix (the
 * common width) and backfills it on close; when the prefix needs more bytes, or bytes must be inserted
 * before already-written content (sparse-element wrappers, a map's key list), a <em>gap</em> record is
 * appended instead of moving anything. A single global {@code pendingDelta} counter makes nested length
 * corrections compose bottom-up: a container's final content length is its raw byte span plus the delta
 * accumulated inside it, so parents never revisit children.
 *
 * <p>{@link #finish()} must copy out of the pooled buffer anyway, so gap compaction rides that one copy:
 * segments between gaps are block-copied and gap bytes are materialized in between. A payload with no
 * gaps is a single {@code copyOfRange}. No byte is ever moved more than once, at any nesting depth. Large
 * blobs skip the staging write entirely: an <em>extern</em> gap references the caller's buffer and
 * compaction copies from it directly, so blob bytes are only ever copied once.
 *
 * <p>Structures serialize through one of four paths: <b>presence-inline</b> (exactly one leaf member is
 * present, so the prefix, header, and value are written forward with no reservation), <b>presence-fast</b>
 * (the struct reports {@link SerializableStruct#presenceBits}, so section headers are written inline
 * exactly), <b>incremental</b> (presence unknown; group headers get pessimistic reservations backfilled on
 * group close), and the rare <b>unordered</b> fallback for hand-written structs that dispatch members out
 * of memberIndex order (rolled back and re-serialized through a sorting collector).
 */
final class SparrowhawkSerializer implements ShapeSerializer {

    private static final VarHandle LONG_LE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT_LE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    private static final int DEFAULT_BUF = 4096;
    private static final int DEFAULT_KEYS = 512;
    private static final int DEFAULT_GAPS = 64;
    private static final int MAX_POOLED_BUF = 1 << 17;
    private static final int MAX_POOLED_GAPS = 1 << 12;
    private static final int MAX_DEPTH = 1000;

    /** Blobs at least this large are referenced by an extern gap instead of staged into the buffer. */
    private static final int EXTERN_MIN_BYTES = 64;

    // Container kinds for the active (innermost) container.
    private static final int K_TOP = 0;
    private static final int K_STRUCT_FAST = 1;
    private static final int K_STRUCT_INCR = 2;
    private static final int K_LIST = 3;
    private static final int K_MAP = 4;
    private static final int K_INLINE = 5;
    private static final int K_MAP1 = 6;

    // Gap kinds (low 4 bits of gap meta).
    private static final int GAP_PREFIX = 0;
    private static final int GAP_SHRINK = 1;
    private static final int GAP_WRAP = 2;
    private static final int GAP_MAP_HEAD = 3;
    private static final int GAP_EXTERN = 4;

    private static final long NO_TOKEN = Long.MIN_VALUE;

    private static final StripedPool<SparrowhawkSerializer, Void> POOL = new Pool();

    byte[] buf;
    private int pos;

    /** Map keys, kept out of the value stream so they can be inserted as a block at compaction. */
    private byte[] keyScratch;
    private int kPos;

    /**
     * Per-map-entry key spans in keyScratch, packed (from << 32 | to). The live array is a stack: a
     * closing map archives its own spans into gapSpans (referenced by its map-head gap) and truncates back
     * to its mark, so an outer map's spans never include those of maps nested in its values. Adjacent
     * spans of the same map are coalesced as they are recorded, so leaf maps hold a single span.
     */
    private long[] liveSpans;
    private int liveSpanCount;
    private long[] gapSpans;
    private int gapSpanCount;

    // Gap log: position in buf, meta (kind | tag<<4 | skip<<8 | n<<16), and a kind-specific payload.
    // Extern gaps stash their original append index in the n field; gapRefs is indexed by it and is
    // never permuted by the sort.
    private int[] gapPos;
    private long[] gapMeta;
    private long[] gapData;
    private int gapCount;
    private long[] gapSortKeys;
    private long[] gapTmp;
    private ByteBuffer[] gapRefs;

    /** Net bytes the recorded gaps will add to (or remove from) the raw buffer at compaction. */
    private int pendingDelta;

    // ----- active container state; suspended/resumed via call-stack locals around nested containers -----
    private int cKind = K_TOP;
    private SparrowhawkSchemaExtensions.Layout cLayout;
    private long cPresence;
    private int cSection = -1;
    private int cGroup = -1;
    private long cBits;
    /** Incremental path: reserved group-header slot, packed (position << 8 | reservedLength), or -1. */
    private long cGroupReserve = -1;
    private int cLastMember = -1;
    private int cCount;
    private int cKeyBytes;
    /** First liveSpans index belonging to the active map; spans below it must never be coalesced into. */
    private int cSpanMark;
    private boolean cSparse;
    private int depth;

    // Per-site one-entry layout memos: collection-heavy payloads resolve the same value schema
    // repeatedly, but structs and maps alternate schemas in nested shapes, so each container writer
    // memoizes independently.
    private Schema structSchema;
    private SparrowhawkSchemaExtensions.Layout structLayout;
    private Schema listSchema;
    private SparrowhawkSchemaExtensions.Layout listLayout;
    private Schema mapSchema;
    private SparrowhawkSchemaExtensions.Layout mapLayout;

    private final OutputStream sink;
    private final SparrowhawkMapSerializer mapSerializer = new SparrowhawkMapSerializer();

    SparrowhawkSerializer() {
        this(null);
    }

    SparrowhawkSerializer(OutputStream sink) {
        this.sink = sink;
        this.buf = new byte[DEFAULT_BUF];
        this.keyScratch = new byte[DEFAULT_KEYS];
        this.liveSpans = new long[DEFAULT_GAPS];
        this.gapSpans = new long[DEFAULT_GAPS];
        this.gapPos = new int[DEFAULT_GAPS];
        this.gapMeta = new long[DEFAULT_GAPS];
        this.gapData = new long[DEFAULT_GAPS];
    }

    static SparrowhawkSerializer acquire() {
        return POOL.acquire(null);
    }

    static void release(SparrowhawkSerializer serializer) {
        POOL.release(serializer);
    }

    /**
     * Compacts and returns the serialized top-level value as a freshly-allocated buffer, resetting this
     * serializer for reuse. Only valid when all containers are complete.
     */
    ByteBuffer finish() {
        ByteBuffer result = compact();
        resetState();
        return result;
    }

    private ByteBuffer compact() {
        if (gapCount == 0) {
            return ByteBuffer.wrap(Arrays.copyOfRange(buf, 0, pos));
        }
        // Containers close inner-first, so gaps usually arrive with non-increasing positions: walking
        // them in reverse yields ascending positions with the sort's tie order (equal positions emit
        // later-appended first) without sorting or permuting anything.
        boolean descending = true;
        for (int i = 1; i < gapCount; i++) {
            if (gapPos[i] > gapPos[i - 1]) {
                descending = false;
                break;
            }
        }
        if (!descending) {
            sortGaps();
        }
        int size = pos + pendingDelta;
        // The 8-byte pad lets the varint writer store a full little-endian long without bounds checks.
        byte[] result = new byte[size + 8];
        int in = 0;
        int out = 0;
        for (int i = 0; i < gapCount; i++) {
            int g = descending ? gapCount - 1 - i : i;
            int at = gapPos[g];
            int seg = at - in;
            System.arraycopy(buf, in, result, out, seg);
            out += seg;
            long meta = gapMeta[g];
            int kind = (int) (meta & 0xF);
            in = at + (int) ((meta >>> 8) & 0xFF);
            switch (kind) {
                case GAP_PREFIX -> out = writeUVarintAt(result, out, gapData[g]);
                case GAP_SHRINK -> {
                    // Nothing to write; the skipped input bytes are simply dropped.
                }
                case GAP_WRAP -> {
                    out = writeUVarintAt(result, out, Sparrowhawk.byteListHeader(gapData[g] + 1));
                    result[out++] = Sparrowhawk.SPARSE_PRESENT_MARKER;
                }
                case GAP_EXTERN -> {
                    long d = gapData[g];
                    int len = (int) d;
                    gapRefs[(int) (meta >>> 16)].get((int) (d >>> 32), result, out, len);
                    out += len;
                }
                default -> {
                    // GAP_MAP_HEAD: fieldset, key list header, keys, value list header.
                    int tag = (int) ((meta >>> 4) & 0xF);
                    int n = (int) (meta >>> 16);
                    result[out++] = (byte) (2 * Sparrowhawk.MAP_FIELDSET + 1);
                    out = writeUVarintAt(result, out, Sparrowhawk.typedListHeader(n, Sparrowhawk.LIST_LEN_DELIMITED));
                    long spans = gapData[g];
                    int spanFrom = (int) (spans >>> 32);
                    int spanTo = (int) spans;
                    int runFrom = -1;
                    int runTo = -1;
                    for (int s = spanFrom; s < spanTo; s++) {
                        int from = (int) (gapSpans[s] >>> 32);
                        int to = (int) gapSpans[s];
                        if (from == runTo) {
                            runTo = to;
                        } else {
                            if (runFrom >= 0) {
                                System.arraycopy(keyScratch, runFrom, result, out, runTo - runFrom);
                                out += runTo - runFrom;
                            }
                            runFrom = from;
                            runTo = to;
                        }
                    }
                    if (runFrom >= 0) {
                        System.arraycopy(keyScratch, runFrom, result, out, runTo - runFrom);
                        out += runTo - runFrom;
                    }
                    out = writeUVarintAt(result, out, Sparrowhawk.typedListHeader(n, tag));
                }
            }
        }
        System.arraycopy(buf, in, result, out, pos - in);
        out += pos - in;
        return ByteBuffer.wrap(result, 0, out);
    }

    /**
     * Sorts gaps ascending by position; ties (an insertion at the same offset as a nested container's
     * prefix) resolve to later-appended first, because outer wrappers close later but their bytes precede.
     * Only needed when gap positions did not arrive monotonically (sparse wrappers and incremental group
     * headers arrive ascending); sorts packed (position, complemented append index) keys.
     */
    private void sortGaps() {
        int k = gapCount;
        if (gapSortKeys == null || gapSortKeys.length < k) {
            gapSortKeys = new long[Math.max(k, DEFAULT_GAPS)];
            gapTmp = new long[gapSortKeys.length];
        }
        long[] keys = gapSortKeys;
        // Positions arrive mostly descending even when not perfectly so: fill the keys in reverse so the
        // sort sees nearly-ascending input. The low bits carry (k-1 - originalIndex), which makes equal
        // positions order later-appended first.
        for (int i = 0; i < k; i++) {
            keys[i] = ((long) gapPos[k - 1 - i] << 32) | i;
        }
        Arrays.sort(keys, 0, k);
        long[] tmp = gapTmp;
        for (int i = 0; i < k; i++) {
            int src = k - 1 - (int) (keys[i] & 0xFFFFFFFFL);
            tmp[i] = gapMeta[src];
        }
        System.arraycopy(tmp, 0, gapMeta, 0, k);
        for (int i = 0; i < k; i++) {
            int src = k - 1 - (int) (keys[i] & 0xFFFFFFFFL);
            tmp[i] = gapData[src];
        }
        System.arraycopy(tmp, 0, gapData, 0, k);
        for (int i = 0; i < k; i++) {
            gapPos[i] = (int) (keys[i] >>> 32);
        }
    }

    @Override
    public void flush() {
        if (sink == null) {
            return;
        }
        try {
            if (depth == 0 && pos > 0) {
                ByteBuffer result = compact();
                sink.write(result.array(), result.position(), result.remaining());
                resetState();
            }
            sink.flush();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void close() {
        if (sink == null) {
            return;
        }
        try {
            if (depth == 0 && pos > 0) {
                ByteBuffer result = compact();
                sink.write(result.array(), result.position(), result.remaining());
                resetState();
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private void resetState() {
        if (gapRefs != null && gapCount > 0) {
            Arrays.fill(gapRefs, 0, Math.min(gapCount, gapRefs.length), null);
        }
        pos = 0;
        kPos = 0;
        liveSpanCount = 0;
        gapSpanCount = 0;
        gapCount = 0;
        pendingDelta = 0;
        cKind = K_TOP;
        cLayout = null;
        cPresence = 0;
        cSection = -1;
        cGroup = -1;
        cBits = 0;
        cGroupReserve = -1;
        cLastMember = -1;
        cCount = 0;
        cKeyBytes = 0;
        cSpanMark = 0;
        cSparse = false;
        depth = 0;
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
            throw new SerializationException("Not a Sparrowhawk structure schema: " + targetId(schema));
        }
        long token = beforeValue(schema);
        long presence = layout.memberCount == 0 ? 0 : struct.presenceBits();
        if (presence >= 0 && layout.memberCount <= 63) {
            if ((presence >>> layout.memberCount) != 0) {
                throw new SerializationException(
                        "presenceBits() reported bits for non-existent members of " + targetId(schema));
            }
            if ((presence & (presence - 1)) == 0 && (presence & layout.leafMembers) != 0) {
                writeStructInline(struct, layout, presence);
            } else {
                writeStructFast(schema, struct, layout, presence);
            }
        } else {
            writeStructIncr(struct, layout);
        }
        afterValue(token);
    }

    /**
     * Presence-fast path: section headers are computed from presence slices and written before their
     * values; nothing is reserved, so there is no group to close. When every member is present the
     * precomputed template supplies all headers at once.
     */
    private void writeStructFast(
            Schema schema,
            SerializableStruct struct,
            SparrowhawkSchemaExtensions.Layout layout,
            long presence
    ) {
        ensureBuf(1);
        int prefixPos = pos++;
        int contentStart = pos;
        int deltaSnapshot = pendingDelta;

        int pKind = cKind;
        var pLayout = cLayout;
        long pPresence = cPresence;
        int pSection = cSection;
        int pGroup = cGroup;
        int pLastMember = cLastMember;
        int pCount = cCount;

        if (++depth > MAX_DEPTH) {
            throw new SerializationException("Maximum Sparrowhawk nesting depth exceeded");
        }
        cKind = K_STRUCT_FAST;
        cLayout = layout;
        cPresence = presence;
        cSection = -1;
        cGroup = -1;
        cLastMember = -1;
        cCount = 0;

        struct.serializeMembers(this);

        if (cCount != Long.bitCount(presence)) {
            throw new SerializationException(
                    "presenceBits() of " + targetId(schema) + " reported " + Long.bitCount(presence)
                            + " members but serializeMembers wrote " + cCount);
        }
        long content = (pos - contentStart) + (pendingDelta - deltaSnapshot);
        backfillPrefix(prefixPos, Sparrowhawk.byteListHeader(content));
        depth--;

        cKind = pKind;
        cLayout = pLayout;
        cPresence = pPresence;
        cSection = pSection;
        cGroup = pGroup;
        cLastMember = pLastMember;
        cCount = pCount;
    }

    /**
     * Presence-inline path: exactly one leaf member is present, so the scalar writer itself emits the
     * byte-length prefix, the single-bit section header, and the value, all forward.
     */
    private void writeStructInline(
            SerializableStruct struct,
            SparrowhawkSchemaExtensions.Layout layout,
            long presence
    ) {
        int pKind = cKind;
        var pLayout = cLayout;
        long pPresence = cPresence;
        int pCount = cCount;

        if (++depth > MAX_DEPTH) {
            throw new SerializationException("Maximum Sparrowhawk nesting depth exceeded");
        }
        cKind = K_INLINE;
        cLayout = layout;
        cPresence = presence;
        cCount = 0;

        struct.serializeMembers(this);

        if (cCount != 1) {
            throw new SerializationException(
                    "presenceBits() reported one member but serializeMembers wrote " + cCount);
        }
        depth--;

        cKind = pKind;
        cLayout = pLayout;
        cPresence = pPresence;
        cCount = pCount;
    }

    /** Incremental path: presence unknown (or more than 63 members); group headers are reserved and shrunk. */
    private void writeStructIncr(SerializableStruct struct, SparrowhawkSchemaExtensions.Layout layout) {
        ensureBuf(1);
        int prefixPos = pos++;
        int contentStart = pos;
        int deltaSnapshot = pendingDelta;
        int gapSnapshot = gapCount;
        int keySnapshot = kPos;
        int spanSnapshot = liveSpanCount;

        int pKind = cKind;
        var pLayout = cLayout;
        long pPresence = cPresence;
        int pSection = cSection;
        int pGroup = cGroup;
        long pBits = cBits;
        long pGroupReserve = cGroupReserve;
        int pLastMember = cLastMember;
        int pCount = cCount;

        if (++depth > MAX_DEPTH) {
            throw new SerializationException("Maximum Sparrowhawk nesting depth exceeded");
        }
        cKind = K_STRUCT_INCR;
        cLayout = layout;
        cPresence = 0;
        cSection = -1;
        cGroup = -1;
        cBits = 0;
        cGroupReserve = -1;
        cLastMember = -1;
        cCount = 0;

        try {
            struct.serializeMembers(this);
        } catch (OutOfOrder e) {
            // A hand-written struct emitted members out of memberIndex order. Roll this struct back and
            // re-serialize it through the sorting collector.
            pos = contentStart;
            pendingDelta = deltaSnapshot;
            if (gapRefs != null && gapCount > gapSnapshot) {
                Arrays.fill(gapRefs, gapSnapshot, Math.min(gapCount, gapRefs.length), null);
            }
            gapCount = gapSnapshot;
            kPos = keySnapshot;
            liveSpanCount = spanSnapshot;
            cSection = -1;
            cGroup = -1;
            cBits = 0;
            cGroupReserve = -1;
            cLastMember = -1;
            cCount = 0;
            serializeUnordered(struct);
        }

        closeGroup();
        long content = (pos - contentStart) + (pendingDelta - deltaSnapshot);
        backfillPrefix(prefixPos, Sparrowhawk.byteListHeader(content));
        depth--;

        cKind = pKind;
        cLayout = pLayout;
        cPresence = pPresence;
        cSection = pSection;
        cGroup = pGroup;
        cBits = pBits;
        cGroupReserve = pGroupReserve;
        cLastMember = pLastMember;
        cCount = pCount;
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
            throw new SerializationException("Not a Sparrowhawk list schema: " + targetId(schema));
        }
        long token = beforeValue(schema);
        // List headers are count-prefixed, so a known size writes the header forward with no reservation.
        int headerPos = -1;
        if (size >= 0) {
            putUVarint(Sparrowhawk.typedListHeader(size, layout.elementTag));
        } else {
            ensureBuf(1);
            headerPos = pos++;
        }

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

        if (headerPos >= 0) {
            backfillPrefix(headerPos, Sparrowhawk.typedListHeader(cCount, layout.elementTag));
        } else if (cCount != size) {
            throw new SerializationException(
                    "List reported size " + size + " but wrote " + cCount + " elements: " + targetId(schema));
        }
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
            throw new SerializationException("Not a Sparrowhawk map schema: " + targetId(schema));
        }
        long token = beforeValue(schema);
        if (size == 0) {
            ensureBuf(1);
            buf[pos++] = Sparrowhawk.EMPTY_BYTE_LIST;
            afterValue(token);
            return;
        }
        if (size == 1) {
            writeSingletonMap(schema, mapState, consumer, layout);
            afterValue(token);
            return;
        }
        ensureBuf(1);
        int prefixPos = pos++;
        int valuesStart = pos;
        int deltaSnapshot = pendingDelta;
        int spanMark = liveSpanCount;

        int pKind = cKind;
        var pLayout = cLayout;
        int pCount = cCount;
        int pKeyBytes = cKeyBytes;
        int pSpanMark = cSpanMark;
        boolean pSparse = cSparse;

        if (++depth > MAX_DEPTH) {
            throw new SerializationException("Maximum Sparrowhawk nesting depth exceeded");
        }
        cKind = K_MAP;
        cLayout = layout;
        cCount = 0;
        cKeyBytes = 0;
        cSpanMark = spanMark;
        cSparse = layout.sparse;

        consumer.accept(mapState, mapSerializer);

        int n = cCount;
        if (n == 0) {
            backfillPrefix(prefixPos, 0);
        } else {
            long keyHeader = Sparrowhawk.typedListHeader(n, Sparrowhawk.LIST_LEN_DELIMITED);
            long valueHeader = Sparrowhawk.typedListHeader(n, layout.elementTag);
            int headBytes = 1 + Sparrowhawk.uvarintSize(keyHeader) + cKeyBytes + Sparrowhawk.uvarintSize(valueHeader);
            long valuesLen = (pos - valuesStart) + (pendingDelta - deltaSnapshot);
            // Archive this map's spans (inner maps already consumed and truncated theirs, so the live
            // range holds exactly this map's keys) and insert the map head before the values.
            int archiveFrom = gapSpanCount;
            int mySpans = liveSpanCount - spanMark;
            if (gapSpanCount + mySpans > gapSpans.length) {
                gapSpans = Arrays.copyOf(gapSpans, Math.max(gapSpans.length * 2, gapSpanCount + mySpans));
            }
            System.arraycopy(liveSpans, spanMark, gapSpans, gapSpanCount, mySpans);
            gapSpanCount += mySpans;
            addGap(valuesStart,
                    GAP_MAP_HEAD | (long) layout.elementTag << 4 | (long) n << 16,
                    ((long) archiveFrom << 32) | gapSpanCount,
                    headBytes);
            backfillPrefix(prefixPos, Sparrowhawk.byteListHeader(headBytes + valuesLen));
        }
        liveSpanCount = spanMark;
        depth--;

        cKind = pKind;
        cLayout = pLayout;
        cCount = pCount;
        cKeyBytes = pKeyBytes;
        cSpanMark = pSpanMark;
        cSparse = pSparse;
        afterValue(token);
    }

    /**
     * Single-entry map: the fieldset, key list, and value list are written fully forward (the key goes
     * straight into the value stream, not keyScratch), leaving only the byte-length prefix to backfill.
     */
    private <T> void writeSingletonMap(
            Schema schema,
            T mapState,
            BiConsumer<T, MapSerializer> consumer,
            SparrowhawkSchemaExtensions.Layout layout
    ) {
        ensureBuf(3);
        int prefixPos = pos++;
        int contentStart = pos;
        int deltaSnapshot = pendingDelta;
        buf[pos++] = (byte) (2 * Sparrowhawk.MAP_FIELDSET + 1);
        buf[pos++] = (byte) (2 * Sparrowhawk.typedListHeader(1, Sparrowhawk.LIST_LEN_DELIMITED) + 1);

        int pKind = cKind;
        var pLayout = cLayout;
        int pCount = cCount;
        boolean pSparse = cSparse;

        if (++depth > MAX_DEPTH) {
            throw new SerializationException("Maximum Sparrowhawk nesting depth exceeded");
        }
        cKind = K_MAP1;
        cLayout = layout;
        cCount = 0;
        cSparse = layout.sparse;

        consumer.accept(mapState, mapSerializer);

        if (cCount != 1) {
            throw new SerializationException(
                    "Map reported size 1 but wrote " + cCount + " entries: " + targetId(schema));
        }
        long content = (pos - contentStart) + (pendingDelta - deltaSnapshot);
        backfillPrefix(prefixPos, Sparrowhawk.byteListHeader(content));
        depth--;

        cKind = pKind;
        cLayout = pLayout;
        cCount = pCount;
        cSparse = pSparse;
    }

    private final class SparrowhawkMapSerializer implements MapSerializer {
        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            if (cKind == K_MAP1) {
                if (cCount != 0) {
                    throw new SerializationException("Map reported size 1 but wrote multiple entries");
                }
                cCount = 1;
                putUtf8ByteList(key);
                putUVarint(Sparrowhawk.typedListHeader(1, cLayout.elementTag));
                valueSerializer.accept(state, SparrowhawkSerializer.this);
                return;
            }
            cCount++;
            int from = kPos;
            putUtf8ByteListToKeys(key);
            cKeyBytes += kPos - from;
            // Coalesce with the previous span when the keys are adjacent (no nested map wrote keys in
            // between) and it belongs to this map (never merge across the span mark).
            if (liveSpanCount > cSpanMark && (int) liveSpans[liveSpanCount - 1] == from) {
                liveSpans[liveSpanCount - 1] = (liveSpans[liveSpanCount - 1] & 0xFFFFFFFF00000000L) | kPos;
            } else {
                if (liveSpanCount == liveSpans.length) {
                    liveSpans = Arrays.copyOf(liveSpans, liveSpanCount * 2);
                }
                liveSpans[liveSpanCount++] = ((long) from << 32) | kPos;
            }
            valueSerializer.accept(state, SparrowhawkSerializer.this);
        }
    }

    // ===== member/element bookkeeping =====

    /**
     * Called before every value written into the active container. For struct members this enforces
     * ordering and presence and writes/reserves section headers; for sparse collection elements it returns
     * a token (value position and delta snapshot) that {@link #afterValue} turns into a wrapper gap.
     */
    private long beforeValue(Schema schema) {
        switch (cKind) {
            case K_STRUCT_FAST -> memberStartFast(schema);
            case K_STRUCT_INCR -> memberStartIncr(schema.memberIndex());
            case K_LIST -> {
                cCount++;
                if (cSparse) {
                    return ((long) pos << 32) | (pendingDelta & 0xFFFFFFFFL);
                }
            }
            case K_MAP, K_MAP1 -> {
                if (cSparse) {
                    return ((long) pos << 32) | (pendingDelta & 0xFFFFFFFFL);
                }
            }
            case K_INLINE -> throw new SerializationException(
                    "Single-member presence-inline struct wrote an unexpected container member");
            default -> {
                // K_TOP: nothing to do.
            }
        }
        return NO_TOKEN;
    }

    private void afterValue(long token) {
        if (token != NO_TOKEN) {
            int at = (int) (token >>> 32);
            long valueLen = (pos - at) + (pendingDelta - (int) token);
            // Wrapper: byte-list prefix over (marker byte + value).
            int wrapBytes = Sparrowhawk.uvarintSize(Sparrowhawk.byteListHeader(valueLen + 1)) + 1;
            addGap(at, GAP_WRAP, valueLen, wrapBytes);
        }
    }

    private void memberStartFast(Schema member) {
        int mi = member.memberIndex();
        int[] info = cLayout.memberInfo;
        if (mi <= cLastMember || mi >= info.length) {
            throw new SerializationException(
                    "presenceBits() was provided but members were not serialized in memberIndex order");
        }
        cLastMember = mi;
        if ((cPresence & (1L << mi)) == 0) {
            throw new SerializationException(
                    "serializeMembers wrote member " + member.id() + " that presenceBits() reported absent");
        }
        int in = info[mi];
        int section = (in >>> 6) & 3;
        int group = in >>> 8;
        if (section != cSection || group != cGroup) {
            long bits = (cPresence >>> cLayout.sliceShift[mi]) & cLayout.sliceMask[mi];
            long header = (bits << 3) | section | (group > 0 ? Sparrowhawk.CONTINUATION : 0);
            putUVarint(header);
            if (group > 0) {
                putUVarint(group - 1);
            }
            cSection = section;
            cGroup = group;
        }
        cCount++;
    }

    private void memberStartIncr(int mi) {
        if (mi >= cLayout.memberInfo.length) {
            throw new SerializationException("Member is not part of the serialized structure's schema");
        }
        if (mi <= cLastMember) {
            throw OutOfOrder.INSTANCE;
        }
        cLastMember = mi;
        int in = cLayout.memberInfo[mi];
        int section = (in >>> 6) & 3;
        int group = in >>> 8;
        if (section != cSection || group != cGroup) {
            closeGroup();
            // Reserve the widest header this group could need; closeGroup backfills and shrinks.
            int width = Math.min(
                    cLayout.sectionCount[section] - group * Sparrowhawk.FIELDS_PER_GROUP,
                    Sparrowhawk.FIELDS_PER_GROUP);
            long maxHeader = (((1L << width) - 1) << 3) | section | (group > 0 ? Sparrowhawk.CONTINUATION : 0);
            int reserve = Sparrowhawk.uvarintSize(maxHeader) + (group > 0 ? Sparrowhawk.uvarintSize(group - 1) : 0);
            ensureBuf(reserve);
            cGroupReserve = ((long) pos << 8) | reserve;
            pos += reserve;
            cSection = section;
            cGroup = group;
            cBits = 0;
        }
        cBits |= 1L << (in & 0x3F);
        cCount++;
    }

    private void closeGroup() {
        long reserve = cGroupReserve;
        if (reserve >= 0) {
            int at = (int) (reserve >>> 8);
            int reserved = (int) (reserve & 0xFF);
            long header = (cBits << 3) | cSection | (cGroup > 0 ? Sparrowhawk.CONTINUATION : 0);
            int w = putUVarintAt(at, header);
            if (cGroup > 0) {
                w = putUVarintAt(w, cGroup - 1);
            }
            int actual = w - at;
            if (actual < reserved) {
                addGap(w, GAP_SHRINK | (long) (reserved - actual) << 8, 0, actual - reserved);
            }
            cGroupReserve = -1;
        }
    }

    /**
     * Fills a one-byte prefix reservation with the varint of {@code value}, or records a widening gap when
     * the varint needs more than one byte.
     */
    private void backfillPrefix(int at, long value) {
        if (value < 128) {
            buf[at] = (byte) (2 * value + 1);
        } else {
            addGap(at, GAP_PREFIX | (1L << 8), value, Sparrowhawk.uvarintSize(value) - 1);
        }
    }

    private void addGap(int at, long meta, long data, int delta) {
        int i = gapCount;
        if (i == gapPos.length) {
            gapPos = Arrays.copyOf(gapPos, i * 2);
            gapMeta = Arrays.copyOf(gapMeta, i * 2);
            gapData = Arrays.copyOf(gapData, i * 2);
        }
        gapPos[i] = at;
        gapMeta[i] = meta;
        gapData[i] = data;
        gapCount = i + 1;
        pendingDelta += delta;
    }

    /**
     * Records a gap that copies {@code len} bytes straight from the caller's buffer at compaction. The
     * buffer is referenced by its append index (stashed in the meta n-field), never mutated, and released
     * on reset; callers must not modify its contents before serialization completes, which it does
     * synchronously within {@code serialize}/{@code flush}.
     */
    private void addExternGap(ByteBuffer value, int len) {
        int i = gapCount;
        addGap(pos, GAP_EXTERN | (long) i << 16, ((long) value.position() << 32) | len, len);
        if (gapRefs == null) {
            gapRefs = new ByteBuffer[gapPos.length];
        } else if (gapRefs.length < gapPos.length) {
            gapRefs = Arrays.copyOf(gapRefs, gapPos.length);
        }
        gapRefs[i] = value;
    }

    private static String targetId(Schema schema) {
        return (schema.isMember() ? schema.memberTarget() : schema).id().toString();
    }

    // ===== unordered fallback =====

    /** Control-flow signal for out-of-order member dispatch; never observed by callers. */
    private static final class OutOfOrder extends RuntimeException {
        static final OutOfOrder INSTANCE = new OutOfOrder();

        private OutOfOrder() {
            super(null, null, false, false);
        }
    }

    private void serializeUnordered(SerializableStruct struct) {
        var collector = new UnorderedCollector();
        struct.serializeMembers(collector);
        collector.emit();
    }

    /**
     * Rare-path collector for structs whose serializeMembers dispatches out of memberIndex order. Member
     * values are encoded standalone into a local buffer (containers through a nested serializer, so their
     * bytes are already compacted), sorted by memberIndex, and emitted with exact section headers.
     */
    private final class UnorderedCollector implements ShapeSerializer {
        private byte[] local = new byte[256];
        private int localPos;
        private int n;
        private int[] members = new int[8];
        private long[] spans = new long[8];

        private void ensureLocal(int needed) {
            if (local.length - localPos < needed) {
                local = Arrays.copyOf(local, Math.max(local.length * 2, localPos + needed));
            }
        }

        private void record(Schema schema, int from) {
            if (n == members.length) {
                members = Arrays.copyOf(members, n * 2);
                spans = Arrays.copyOf(spans, n * 2);
            }
            members[n] = schema.memberIndex();
            spans[n] = ((long) from << 32) | localPos;
            n++;
        }

        void emit() {
            // Insertion sort by memberIndex; member counts are small.
            for (int i = 1; i < n; i++) {
                int m = members[i];
                long s = spans[i];
                int j = i - 1;
                while (j >= 0 && members[j] > m) {
                    members[j + 1] = members[j];
                    spans[j + 1] = spans[j];
                    j--;
                }
                members[j + 1] = m;
                spans[j + 1] = s;
            }
            // All values are buffered, so every group's bits are known before its values are written:
            // emit exact headers directly, then the group's values, group by group.
            int i = 0;
            while (i < n) {
                int in = cLayout.memberInfo[members[i]];
                int section = (in >>> 6) & 3;
                int group = in >>> 8;
                long bits = 0;
                int j = i;
                while (j < n) {
                    if (j > i && members[j] == members[j - 1]) {
                        throw new SerializationException(
                                "Duplicate member serialized at memberIndex " + members[j]);
                    }
                    int in2 = cLayout.memberInfo[members[j]];
                    if (((in2 >>> 6) & 3) != section || (in2 >>> 8) != group) {
                        break;
                    }
                    bits |= 1L << (in2 & 0x3F);
                    j++;
                }
                putUVarint((bits << 3) | section | (group > 0 ? Sparrowhawk.CONTINUATION : 0));
                if (group > 0) {
                    putUVarint(group - 1);
                }
                for (int k = i; k < j; k++) {
                    long span = spans[k];
                    int from = (int) (span >>> 32);
                    int len = (int) span - from;
                    ensureBuf(len);
                    System.arraycopy(local, from, buf, pos, len);
                    pos += len;
                    cCount++;
                }
                i = j;
            }
            // Leave the group state clear so the caller's closeGroup is a no-op.
            cSection = -1;
            cGroup = -1;
            cBits = 0;
            cGroupReserve = -1;
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            standalone(schema, s -> s.writeStruct(schema, struct));
        }

        @Override
        public <T> void writeList(Schema schema, T state, int size, BiConsumer<T, ShapeSerializer> consumer) {
            standalone(schema, s -> s.writeList(schema, state, size, consumer));
        }

        @Override
        public <T> void writeMap(Schema schema, T state, int size, BiConsumer<T, MapSerializer> consumer) {
            standalone(schema, s -> s.writeMap(schema, state, size, consumer));
        }

        private void standalone(Schema schema, SerializableShapeWriter writer) {
            int from = localPos;
            SparrowhawkSerializer nested = acquire();
            nested.depth = depth;
            try {
                writer.write(nested);
                ByteBuffer bytes = nested.finish();
                int len = bytes.remaining();
                ensureLocal(len);
                bytes.get(local, localPos, len);
                localPos += len;
            } finally {
                release(nested);
            }
            record(schema, from);
        }

        private void scalar(Schema schema, int maxLen, ScalarWriter writer) {
            ensureLocal(maxLen);
            int from = localPos;
            localPos = writer.write(local, localPos);
            record(schema, from);
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            scalar(schema, 1, (b, p) -> {
                b[p] = value ? (byte) 3 : (byte) 1;
                return p + 1;
            });
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
            scalar(schema, 9, (b, p) -> rawUVarint(b, p, Integer.toUnsignedLong(Sparrowhawk.zigzag(value))));
        }

        @Override
        public void writeLong(Schema schema, long value) {
            scalar(schema, 9, (b, p) -> rawUVarint(b, p, Sparrowhawk.zigzag(value)));
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            scalar(schema, 4, (b, p) -> {
                INT_LE.set(b, p, Float.floatToIntBits(value));
                return p + 4;
            });
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            scalar(schema, 8, (b, p) -> {
                LONG_LE.set(b, p, Double.doubleToLongBits(value));
                return p + 8;
            });
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            scalar(schema, 8, (b, p) -> {
                LONG_LE.set(b, p, Double.doubleToLongBits(value.toEpochMilli() / 1000d));
                return p + 8;
            });
        }

        @Override
        public void writeString(Schema schema, String value) {
            byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
            scalar(schema, 9 + utf8.length, (b, p) -> {
                int q = rawUVarint(b, p, Sparrowhawk.byteListHeader(utf8.length));
                System.arraycopy(utf8, 0, b, q, utf8.length);
                return q + utf8.length;
            });
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            int len = value.remaining();
            scalar(schema, 9 + len, (b, p) -> {
                int q = rawUVarint(b, p, Sparrowhawk.byteListHeader(len));
                value.duplicate().get(b, q, len);
                return q + len;
            });
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            byte[] bytes = value.toByteArray();
            scalar(schema, 9 + bytes.length, (b, p) -> {
                int q = rawUVarint(b, p, Sparrowhawk.byteListHeader(bytes.length));
                System.arraycopy(bytes, 0, b, q, bytes.length);
                return q + bytes.length;
            });
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            byte[] mantissa = value.unscaledValue().toByteArray();
            long expZ = Integer.toUnsignedLong(Sparrowhawk.zigzag(-value.scale()));
            long mantissaHeader = Sparrowhawk.byteListHeader(mantissa.length);
            long content = 1 + Sparrowhawk.uvarintSize(expZ)
                    + 1
                    + Sparrowhawk.uvarintSize(mantissaHeader)
                    + mantissa.length;
            scalar(schema, 9 + (int) content, (b, p) -> {
                int q = rawUVarint(b, p, Sparrowhawk.byteListHeader(content));
                q = rawUVarint(b, q, (1L << 3) | Sparrowhawk.T_VARINT);
                q = rawUVarint(b, q, expZ);
                q = rawUVarint(b, q, (1L << 3) | Sparrowhawk.T_LIST);
                q = rawUVarint(b, q, mantissaHeader);
                System.arraycopy(mantissa, 0, b, q, mantissa.length);
                return q + mantissa.length;
            });
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            throw new SerializationException("Sparrowhawk does not support document types");
        }

        @Override
        public void writeNull(Schema schema) {
            // An absent member: nothing to record.
        }
    }

    @FunctionalInterface
    private interface SerializableShapeWriter {
        void write(ShapeSerializer serializer);
    }

    @FunctionalInterface
    private interface ScalarWriter {
        int write(byte[] target, int at);
    }

    // ===== inline single-member path =====

    /**
     * Writes the byte-length prefix and single-bit section header for the presence-inline path; the
     * caller follows with exactly {@code valueBytes} bytes of value.
     */
    private void inlineStart(Schema member, long valueBytes) {
        int mi = member.memberIndex();
        int[] info = cLayout.memberInfo;
        if (cCount != 0 || mi >= info.length || (cPresence & (1L << mi)) == 0) {
            throw new SerializationException(
                    "Single-member presence-inline struct wrote unexpected member " + member.id());
        }
        cCount = 1;
        int in = info[mi];
        int section = (in >>> 6) & 3;
        int group = in >>> 8;
        long header = (1L << ((in & 0x3F) + 3)) | section | (group > 0 ? Sparrowhawk.CONTINUATION : 0);
        long headBytes = Sparrowhawk.uvarintSize(header) + (group > 0 ? Sparrowhawk.uvarintSize(group - 1) : 0);
        putUVarint(Sparrowhawk.byteListHeader(headBytes + valueBytes));
        putUVarint(header);
        if (group > 0) {
            putUVarint(group - 1);
        }
    }

    private void inlineString(Schema schema, String value) {
        byte[] latin1 = CompactStringAccess.latin1Bytes(value);
        if (latin1 != null) {
            int high = countHigh(latin1);
            int utf8Len = latin1.length + high;
            long bh = Sparrowhawk.byteListHeader(utf8Len);
            inlineStart(schema, Sparrowhawk.uvarintSize(bh) + (long) utf8Len);
            putUVarint(bh);
            putLatin1AsUtf8(latin1, high);
        } else {
            byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
            long bh = Sparrowhawk.byteListHeader(utf8.length);
            inlineStart(schema, Sparrowhawk.uvarintSize(bh) + (long) utf8.length);
            putUVarint(bh);
            putBytes(utf8);
        }
    }

    // ===== scalar writes =====

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        if (cKind == K_INLINE) {
            inlineStart(schema, 1);
            ensureBuf(1);
            buf[pos++] = value ? (byte) 3 : (byte) 1;
            return;
        }
        long token = beforeValue(schema);
        ensureBuf(1);
        buf[pos++] = value ? (byte) 3 : (byte) 1;
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
        long z = Integer.toUnsignedLong(Sparrowhawk.zigzag(value));
        if (cKind == K_INLINE) {
            inlineStart(schema, Sparrowhawk.uvarintSize(z));
            putUVarint(z);
            return;
        }
        long token = beforeValue(schema);
        putUVarint(z);
        afterValue(token);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        long z = Sparrowhawk.zigzag(value);
        if (cKind == K_INLINE) {
            inlineStart(schema, Sparrowhawk.uvarintSize(z));
            putUVarint(z);
            return;
        }
        long token = beforeValue(schema);
        putUVarint(z);
        afterValue(token);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        int bits = Float.floatToIntBits(value);
        if (cKind == K_INLINE) {
            inlineStart(schema, 4);
            putFixed4(bits);
            return;
        }
        long token = beforeValue(schema);
        putFixed4(bits);
        afterValue(token);
    }

    private void putFixed4(int bits) {
        ensureBuf(4);
        INT_LE.set(buf, pos, bits);
        pos += 4;
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        if (cKind == K_INLINE) {
            inlineStart(schema, 8);
            putFixed8(Double.doubleToLongBits(value));
            return;
        }
        long token = beforeValue(schema);
        putFixed8(Double.doubleToLongBits(value));
        afterValue(token);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        long bits = Double.doubleToLongBits(value.toEpochMilli() / 1000d);
        if (cKind == K_INLINE) {
            inlineStart(schema, 8);
            putFixed8(bits);
            return;
        }
        long token = beforeValue(schema);
        putFixed8(bits);
        afterValue(token);
    }

    private void putFixed8(long bits) {
        ensureBuf(8);
        LONG_LE.set(buf, pos, bits);
        pos += 8;
    }

    @Override
    public void writeString(Schema schema, String value) {
        if (cKind == K_INLINE) {
            inlineString(schema, value);
            return;
        }
        long token = beforeValue(schema);
        putUtf8ByteList(value);
        afterValue(token);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        int len = value.remaining();
        long bh = Sparrowhawk.byteListHeader(len);
        if (cKind == K_INLINE) {
            inlineStart(schema, Sparrowhawk.uvarintSize(bh) + (long) len);
            putUVarint(bh);
            putBlobBody(value, len);
            return;
        }
        long token = beforeValue(schema);
        putUVarint(bh);
        putBlobBody(value, len);
        afterValue(token);
    }

    private void putBlobBody(ByteBuffer value, int len) {
        if (len >= EXTERN_MIN_BYTES) {
            addExternGap(value, len);
        } else {
            ensureBuf(len);
            value.get(value.position(), buf, pos, len);
            pos += len;
        }
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        byte[] b = value.toByteArray();
        long bh = Sparrowhawk.byteListHeader(b.length);
        if (cKind == K_INLINE) {
            inlineStart(schema, Sparrowhawk.uvarintSize(bh) + (long) b.length);
            putUVarint(bh);
            putBytes(b);
            return;
        }
        long token = beforeValue(schema);
        putUVarint(bh);
        putBytes(b);
        afterValue(token);
    }

    // BigDecimal encodes as a nested struct with a varint section (field 0: zigzag exponent, where
    // exponent = -scale) and a list section (field 0: unscaled value as a big-endian byte list), matching
    // the reference BigDecimalHolder.
    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        byte[] mantissa = value.unscaledValue().toByteArray();
        long expZ = Integer.toUnsignedLong(Sparrowhawk.zigzag(-value.scale()));
        long mantissaHeader = Sparrowhawk.byteListHeader(mantissa.length);
        // Both section headers ((1<<3)|T_VARINT and (1<<3)|T_LIST) encode in one byte.
        long content = 1 + Sparrowhawk.uvarintSize(expZ)
                + 1
                + Sparrowhawk.uvarintSize(mantissaHeader)
                + mantissa.length;
        long bh = Sparrowhawk.byteListHeader(content);
        if (cKind == K_INLINE) {
            inlineStart(schema, Sparrowhawk.uvarintSize(bh) + content);
        } else {
            long token = beforeValue(schema);
            putBigDecimalBody(bh, expZ, mantissaHeader, mantissa);
            afterValue(token);
            return;
        }
        putBigDecimalBody(bh, expZ, mantissaHeader, mantissa);
    }

    private void putBigDecimalBody(long bh, long expZ, long mantissaHeader, byte[] mantissa) {
        putUVarint(bh);
        putUVarint((1L << 3) | Sparrowhawk.T_VARINT);
        putUVarint(expZ);
        putUVarint((1L << 3) | Sparrowhawk.T_LIST);
        putUVarint(mantissaHeader);
        putBytes(mantissa);
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
                ensureBuf(1);
                buf[pos++] = Sparrowhawk.EMPTY_BYTE_LIST;
            }
            case K_MAP, K_MAP1 -> {
                if (!cSparse) {
                    throw new SerializationException("Cannot write null to a non-sparse Sparrowhawk map");
                }
                ensureBuf(1);
                buf[pos++] = Sparrowhawk.EMPTY_BYTE_LIST;
            }
            case K_STRUCT_FAST, K_INLINE -> {
                if ((cPresence & (1L << schema.memberIndex())) != 0) {
                    throw new SerializationException(
                            "writeNull for member " + schema.id() + " that presenceBits() reported present");
                }
            }
            default -> {
                // Absent struct member or top level: nothing to write.
            }
        }
    }

    // ===== buffer primitives =====

    private void ensureBuf(int needed) {
        if (buf.length - pos < needed) {
            buf = Arrays.copyOf(buf, Math.max(buf.length * 2, pos + needed));
        }
    }

    private void putUVarint(long v) {
        ensureBuf(9);
        pos = rawUVarint(buf, pos, v);
    }

    private static int rawUVarint(byte[] b, int at, long v) {
        int bits = 64 - Long.numberOfLeadingZeros(v | 1);
        if (bits < 8) {
            b[at] = (byte) (2 * v + 1);
            return at + 1;
        }
        if (bits > 56) {
            b[at] = 0;
            LONG_LE.set(b, at + 1, v);
            return at + 9;
        }
        int n = 1 + (bits - 1) / 7;
        long enc = (2 * v + 1) << (n - 1);
        LONG_LE.set(b, at, enc);
        return at + n;
    }

    /** Writes a varint at an absolute position into already-reserved space; returns the position after it. */
    private int putUVarintAt(int at, long v) {
        int bits = 64 - Long.numberOfLeadingZeros(v | 1);
        if (bits < 8) {
            buf[at] = (byte) (2 * v + 1);
            return at + 1;
        }
        if (bits > 56) {
            buf[at] = 0;
            for (int i = 0; i < 8; i++) {
                buf[at + 1 + i] = (byte) (v >>> (8 * i));
            }
            return at + 9;
        }
        int n = 1 + (bits - 1) / 7;
        long enc = (2 * v + 1) << (n - 1);
        for (int i = 0; i < n; i++) {
            buf[at + i] = (byte) (enc >>> (8 * i));
        }
        return at + n;
    }

    /** Like {@link #putUVarintAt} but for the compaction output array (guaranteed 8-byte pad). */
    private static int writeUVarintAt(byte[] b, int at, long v) {
        int bits = 64 - Long.numberOfLeadingZeros(v | 1);
        if (bits < 8) {
            b[at] = (byte) (2 * v + 1);
            return at + 1;
        }
        if (bits > 56) {
            b[at] = 0;
            LONG_LE.set(b, at + 1, v);
            return at + 9;
        }
        int n = 1 + (bits - 1) / 7;
        long enc = (2 * v + 1) << (n - 1);
        LONG_LE.set(b, at, enc);
        return at + n;
    }

    private void putBytes(byte[] b) {
        ensureBuf(b.length);
        System.arraycopy(b, 0, buf, pos, b.length);
        pos += b.length;
    }

    static int countHigh(byte[] latin1) {
        // Count bytes >= 0x80 (each becomes two UTF-8 bytes), eight bytes at a stride: the sign bits of a
        // word, masked and popcounted, are exactly the high-byte count.
        int high = 0;
        int n = latin1.length;
        int i = 0;
        for (; i + 8 <= n; i += 8) {
            high += Long.bitCount((long) LONG_LE.get(latin1, i) & 0x8080808080808080L);
        }
        for (; i < n; i++) {
            if (latin1[i] < 0) {
                high++;
            }
        }
        return high;
    }

    static int rawLatin1AsUtf8(byte[] latin1, int high, byte[] dst, int at) {
        if (high == 0) {
            System.arraycopy(latin1, 0, dst, at, latin1.length);
            return at + latin1.length;
        }
        for (byte b : latin1) {
            int c = b & 0xFF;
            if (c < 0x80) {
                dst[at++] = (byte) c;
            } else {
                dst[at++] = (byte) (0xC0 | (c >>> 6));
                dst[at++] = (byte) (0x80 | (c & 0x3F));
            }
        }
        return at;
    }

    private void putLatin1AsUtf8(byte[] latin1, int high) {
        ensureBuf(latin1.length + high);
        pos = rawLatin1AsUtf8(latin1, high, buf, pos);
    }

    private void putUtf8ByteList(String s) {
        byte[] latin1 = CompactStringAccess.latin1Bytes(s);
        if (latin1 != null) {
            int high = countHigh(latin1);
            putUVarint(Sparrowhawk.byteListHeader(latin1.length + high));
            putLatin1AsUtf8(latin1, high);
        } else {
            byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
            putUVarint(Sparrowhawk.byteListHeader(utf8.length));
            putBytes(utf8);
        }
    }

    /** Like {@link #putUtf8ByteList} but writing into keyScratch. */
    private void putUtf8ByteListToKeys(String s) {
        byte[] latin1 = CompactStringAccess.latin1Bytes(s);
        if (latin1 != null) {
            int high = countHigh(latin1);
            ensureKeys(9 + latin1.length + high);
            kPos = rawUVarint(keyScratch, kPos, Sparrowhawk.byteListHeader(latin1.length + high));
            kPos = rawLatin1AsUtf8(latin1, high, keyScratch, kPos);
        } else {
            byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
            ensureKeys(9 + utf8.length);
            kPos = rawUVarint(keyScratch, kPos, Sparrowhawk.byteListHeader(utf8.length));
            System.arraycopy(utf8, 0, keyScratch, kPos, utf8.length);
            kPos += utf8.length;
        }
    }

    private void ensureKeys(int needed) {
        if (keyScratch.length - kPos < needed) {
            keyScratch = Arrays.copyOf(keyScratch, Math.max(keyScratch.length * 2, kPos + needed));
        }
    }

    // ===== pooling =====

    private static final class Pool extends StripedPool<SparrowhawkSerializer, Void> {
        @Override
        protected SparrowhawkSerializer create(Void context) {
            return new SparrowhawkSerializer();
        }

        @Override
        protected boolean canPool(SparrowhawkSerializer s) {
            return s.sink == null;
        }

        @Override
        protected void prepareForPool(SparrowhawkSerializer s) {
            if (s.buf.length > MAX_POOLED_BUF) {
                s.buf = new byte[DEFAULT_BUF];
            }
            if (s.keyScratch.length > MAX_POOLED_BUF) {
                s.keyScratch = new byte[DEFAULT_KEYS];
            }
            if (s.gapPos.length > MAX_POOLED_GAPS) {
                s.gapPos = new int[DEFAULT_GAPS];
                s.gapMeta = new long[DEFAULT_GAPS];
                s.gapData = new long[DEFAULT_GAPS];
            }
            if (s.gapRefs != null && s.gapRefs.length > MAX_POOLED_GAPS) {
                s.gapRefs = null;
            }
            if (s.gapSortKeys != null && s.gapSortKeys.length > MAX_POOLED_GAPS) {
                s.gapSortKeys = null;
                s.gapTmp = null;
            }
            if (s.liveSpans.length > MAX_POOLED_GAPS) {
                s.liveSpans = new long[DEFAULT_GAPS];
            }
            if (s.gapSpans.length > MAX_POOLED_GAPS) {
                s.gapSpans = new long[DEFAULT_GAPS];
            }
        }

        @Override
        protected boolean reset(SparrowhawkSerializer s, Void context) {
            s.resetState();
            return true;
        }
    }
}
