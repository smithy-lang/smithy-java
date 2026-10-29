/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.codecs.commons.CompactStringAccess;
import software.amazon.smithy.java.codecs.commons.NumberCodec;
import software.amazon.smithy.java.codecs.commons.OutputBuffers;
import software.amazon.smithy.java.codecs.commons.StripedPool;
import software.amazon.smithy.java.codecs.commons.TimestampCodec;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * Form-urlencoded serializer for both {@code awsQuery} and {@code ec2Query} protocols.
 *
 * <p>Writes directly to an internal byte array buffer. Instances are pooled via a striped
 * lock-free pool to avoid per-request allocation of both the serializer and its buffer.
 */
final class QueryFormSerializer implements ShapeSerializer {

    enum QueryVariant {
        AWS_QUERY,
        EC2_QUERY
    }

    private static final byte[] ACTION_PREFIX = "Action=".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VERSION_PREFIX = "&Version=".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MEMBER = "member".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENTRY = "entry".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY = "key".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VALUE = "value".getBytes(StandardCharsets.UTF_8);

    static final boolean[] UNRESERVED = new boolean[128];
    static final byte[] PERCENT_ENCODED = new byte[256 * 3];

    static {
        for (int c = 'A'; c <= 'Z'; c++)
            UNRESERVED[c] = true;
        for (int c = 'a'; c <= 'z'; c++)
            UNRESERVED[c] = true;
        for (int c = '0'; c <= '9'; c++)
            UNRESERVED[c] = true;
        UNRESERVED['-'] = true;
        UNRESERVED['.'] = true;
        UNRESERVED['_'] = true;
        UNRESERVED['~'] = true;

        byte[] HEX = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        for (int b = 0; b < 256; b++) {
            int off = b * 3;
            PERCENT_ENCODED[off] = '%';
            PERCENT_ENCODED[off + 1] = HEX[(b >> 4) & 0xF];
            PERCENT_ENCODED[off + 2] = HEX[b & 0xF];
        }
    }

    private static final int DEFAULT_BUF_SIZE = 1024;

    private static final int MAX_BYTES_PER_CHAR = 9;
    private static final int MAX_BYTES_PER_LATIN1_BYTE = 6;

    record AcquireContext(QueryVariant variant, String action, String version, OutputBuffers pool) {}

    private static final StripedPool<QueryFormSerializer, AcquireContext> POOL =
            new StripedPool<>() {
                @Override
                protected QueryFormSerializer create(AcquireContext ctx) {
                    return new QueryFormSerializer(ctx.pool());
                }

                @Override
                protected void cleanup(QueryFormSerializer s) {
                    s.releaseBuffer();
                    // Clear the client pool reference because idle serializers remain in a JVM-wide pool.
                    s.pool = null;
                }

                @Override
                protected boolean canPool(QueryFormSerializer s) {
                    return true;
                }

                @Override
                protected boolean reset(QueryFormSerializer s, AcquireContext ctx) {
                    s.prefixLen = 0;
                    s.prefixDepth = 0;
                    s.pos = 0;
                    s.pool = ctx.pool();
                    s.buf = s.pool.acquire(s.sizeHint);
                    return true;
                }
            };

    private byte[] buf;
    private int pos;
    private int sizeHint = DEFAULT_BUF_SIZE;
    private OutputBuffers pool;
    private QueryVariant variant;

    private byte[] prefixBuf = new byte[128];
    private int prefixLen = 0;
    private int[] prefixStack = new int[8];
    private int prefixDepth = 0;

    private final ListItemSerializer listSerializer = new ListItemSerializer();
    private final QueryMapSerializer mapSerializer = new QueryMapSerializer();
    private final MapValueSerializer mapValueSerializer = new MapValueSerializer();

    private QueryFormSerializer(OutputBuffers pool) {
        this.pool = pool;
        this.buf = pool.acquire(DEFAULT_BUF_SIZE);
    }

    private void releaseBuffer() {
        if (buf != null) {
            pool.release(buf);
            buf = null;
        }
    }

    @SuppressWarnings("deprecation")
    static QueryFormSerializer acquire(QueryVariant variant, String action, String version, OutputBuffers pool) {
        QueryFormSerializer s = POOL.acquire(new AcquireContext(variant, action, version, pool));
        s.variant = variant;

        int headerLen = ACTION_PREFIX.length + action.length() + VERSION_PREFIX.length + version.length();
        s.ensureCapacity(headerLen);
        System.arraycopy(ACTION_PREFIX, 0, s.buf, 0, ACTION_PREFIX.length);
        s.pos = ACTION_PREFIX.length;
        action.getBytes(0, action.length(), s.buf, s.pos);
        s.pos += action.length();
        System.arraycopy(VERSION_PREFIX, 0, s.buf, s.pos, VERSION_PREFIX.length);
        s.pos += VERSION_PREFIX.length;
        version.getBytes(0, version.length(), s.buf, s.pos);
        s.pos += version.length();
        return s;
    }

    ByteBuffer finish() {
        sizeHint = OutputBuffers.nextSizeHint(sizeHint, pos);
        ByteBuffer result = ByteBuffer.wrap(Arrays.copyOf(buf, pos));
        POOL.release(this);
        return result;
    }

    DataStream finishLeased(String contentType) {
        byte[] detached = buf;
        int length = pos;
        var recycler = pool.recycler();
        sizeHint = OutputBuffers.nextSizeHint(sizeHint, length);
        buf = null;
        pos = 0;
        POOL.release(this);
        return DataStream.ofLeasedBytes(detached, 0, length, contentType, recycler);
    }

    private void ensureCapacity(int needed) {
        int required = pos + needed;
        if (required > buf.length) {
            buf = pool.grow(buf, pos, required);
        }
    }

    private void pushPrefix(byte[] name) {
        if (prefixDepth >= prefixStack.length) {
            prefixStack = Arrays.copyOf(prefixStack, prefixStack.length * 2);
        }
        prefixStack[prefixDepth++] = prefixLen;
        int needed = name.length + (prefixLen > 0 ? 1 : 0);
        if (prefixLen + needed > prefixBuf.length) {
            prefixBuf = Arrays.copyOf(prefixBuf, Math.max(prefixLen + needed, prefixBuf.length * 2));
        }
        if (prefixLen > 0) {
            prefixBuf[prefixLen++] = '.';
        }
        System.arraycopy(name, 0, prefixBuf, prefixLen, name.length);
        prefixLen += name.length;
    }

    private void pushPrefixWithIndex(byte[] name, int index) {
        if (prefixDepth >= prefixStack.length) {
            prefixStack = Arrays.copyOf(prefixStack, prefixStack.length * 2);
        }
        prefixStack[prefixDepth++] = prefixLen;
        int indexLen = NumberCodec.digitCount(index);
        int needed = (prefixLen > 0 ? 1 : 0) + name.length + 1 + indexLen;
        if (prefixLen + needed > prefixBuf.length) {
            prefixBuf = Arrays.copyOf(prefixBuf, Math.max(prefixLen + needed, prefixBuf.length * 2));
        }
        if (prefixLen > 0) {
            prefixBuf[prefixLen++] = '.';
        }
        System.arraycopy(name, 0, prefixBuf, prefixLen, name.length);
        prefixLen += name.length;
        prefixBuf[prefixLen++] = '.';
        prefixLen = NumberCodec.writeInt(prefixBuf, prefixLen, index);
    }

    private void pushIndexPrefix(int index) {
        if (prefixDepth >= prefixStack.length) {
            prefixStack = Arrays.copyOf(prefixStack, prefixStack.length * 2);
        }
        prefixStack[prefixDepth++] = prefixLen;
        int indexLen = NumberCodec.digitCount(index);
        int needed = (prefixLen > 0 ? 1 : 0) + indexLen;
        if (prefixLen + needed > prefixBuf.length) {
            prefixBuf = Arrays.copyOf(prefixBuf, Math.max(prefixLen + needed, prefixBuf.length * 2));
        }
        if (prefixLen > 0) {
            prefixBuf[prefixLen++] = '.';
        }
        prefixLen = NumberCodec.writeInt(prefixBuf, prefixLen, index);
    }

    private void popPrefix() {
        prefixLen = prefixStack[--prefixDepth];
    }

    private void writeUrlEncodedAsciiBytes(byte[] data, int dataLen) {
        for (int i = 0; i < dataLen; i++) {
            int b = data[i] & 0xFF;
            if (b < 128 && UNRESERVED[b]) {
                buf[pos++] = data[i];
            } else {
                int off = b * 3;
                buf[pos] = PERCENT_ENCODED[off];
                buf[pos + 1] = PERCENT_ENCODED[off + 1];
                buf[pos + 2] = PERCENT_ENCODED[off + 2];
                pos += 3;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void writeUrlEncoded(String s) {
        byte[] latin1 = CompactStringAccess.latin1Bytes(s);
        if (latin1 != null) {
            writeUrlEncodedLatin1(latin1);
            return;
        }

        int len = s.length();
        boolean allUnreserved = true;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c >= 0x80) {
                allUnreserved = false;
                break;
            }
            allUnreserved &= UNRESERVED[c];
        }
        if (allUnreserved) {
            s.getBytes(0, len, buf, pos);
            pos += len;
            return;
        }

        int next = pos;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c >= 0x80) {
                ensureCapacity(next - pos + (len - i) * MAX_BYTES_PER_CHAR);
                // Read buf after ensureCapacity: it may have replaced the array.
                pos = writeUrlEncodedRemainder(buf, next, s, i);
                return;
            }
            if (UNRESERVED[c]) {
                buf[next++] = (byte) c;
            } else {
                int off = c * 3;
                buf[next] = PERCENT_ENCODED[off];
                buf[next + 1] = PERCENT_ENCODED[off + 1];
                buf[next + 2] = PERCENT_ENCODED[off + 2];
                next += 3;
            }
        }
        pos = next;
    }

    private void writeUrlEncodedLatin1(byte[] value) {
        boolean allUnreserved = true;
        boolean hasNonAscii = false;
        for (byte current : value) {
            int c = current & 0xff;
            hasNonAscii |= c >= 0x80;
            allUnreserved &= c < 0x80 && UNRESERVED[c];
        }
        if (allUnreserved) {
            System.arraycopy(value, 0, buf, pos, value.length);
            pos += value.length;
            return;
        }
        if (hasNonAscii) {
            ensureCapacity(value.length * MAX_BYTES_PER_LATIN1_BYTE);
        }

        for (byte current : value) {
            int c = current & 0xff;
            if (c < 0x80) {
                if (UNRESERVED[c]) {
                    buf[pos++] = current;
                } else {
                    int off = c * 3;
                    buf[pos] = PERCENT_ENCODED[off];
                    buf[pos + 1] = PERCENT_ENCODED[off + 1];
                    buf[pos + 2] = PERCENT_ENCODED[off + 2];
                    pos += 3;
                }
            } else {
                int b0 = 0xC0 | (c >> 6);
                int b1 = 0x80 | (c & 0x3F);
                System.arraycopy(PERCENT_ENCODED, b0 * 3, buf, pos, 3);
                pos += 3;
                System.arraycopy(PERCENT_ENCODED, b1 * 3, buf, pos, 3);
                pos += 3;
            }
        }
    }

    // Encodes the remaining arbitrary characters. The caller must reserve nine bytes per char.
    private static int writeUrlEncodedRemainder(byte[] buf, int pos, String s, int start) {
        int len = s.length();
        for (int i = start; i < len; i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                if (UNRESERVED[c]) {
                    buf[pos++] = (byte) c;
                } else {
                    int off = c * 3;
                    buf[pos] = PERCENT_ENCODED[off];
                    buf[pos + 1] = PERCENT_ENCODED[off + 1];
                    buf[pos + 2] = PERCENT_ENCODED[off + 2];
                    pos += 3;
                }
            } else if (c < 0x800) {
                int b0 = 0xC0 | (c >> 6);
                int b1 = 0x80 | (c & 0x3F);
                System.arraycopy(PERCENT_ENCODED, b0 * 3, buf, pos, 3);
                pos += 3;
                System.arraycopy(PERCENT_ENCODED, b1 * 3, buf, pos, 3);
                pos += 3;
            } else if (Character.isHighSurrogate(c) && i + 1 < len && Character.isLowSurrogate(s.charAt(i + 1))) {
                char low = s.charAt(++i);
                int cp = Character.toCodePoint(c, low);
                int b0 = 0xF0 | (cp >> 18);
                int b1 = 0x80 | ((cp >> 12) & 0x3F);
                int b2 = 0x80 | ((cp >> 6) & 0x3F);
                int b3 = 0x80 | (cp & 0x3F);
                System.arraycopy(PERCENT_ENCODED, b0 * 3, buf, pos, 3);
                pos += 3;
                System.arraycopy(PERCENT_ENCODED, b1 * 3, buf, pos, 3);
                pos += 3;
                System.arraycopy(PERCENT_ENCODED, b2 * 3, buf, pos, 3);
                pos += 3;
                System.arraycopy(PERCENT_ENCODED, b3 * 3, buf, pos, 3);
                pos += 3;
            } else {
                int b0 = 0xE0 | (c >> 12);
                int b1 = 0x80 | ((c >> 6) & 0x3F);
                int b2 = 0x80 | (c & 0x3F);
                System.arraycopy(PERCENT_ENCODED, b0 * 3, buf, pos, 3);
                pos += 3;
                System.arraycopy(PERCENT_ENCODED, b1 * 3, buf, pos, 3);
                pos += 3;
                System.arraycopy(PERCENT_ENCODED, b2 * 3, buf, pos, 3);
                pos += 3;
            }
        }
        return pos;
    }

    /**
     * Upper bound on the base-10 ASCII bytes {@link NumberCodec#writeBigInteger} writes to the form body.
     */
    static int maxBigIntegerLength(BigInteger value) {
        // log10(2) is just over 0.301; the larger factor plus constants cover rounding and a sign.
        int digits = (int) (value.bitLength() * 0.302) + 2;
        return 1 + digits;
    }

    /**
     * Writes "&prefix.key=" to the buffer. Used by top-level member serialization.
     */
    private void writeKeyPrefix(byte[] key, int maxValueSize) {
        ensureCapacity(1 + prefixLen + 1 + key.length + 1 + maxValueSize);
        buf[pos++] = '&';
        if (prefixLen > 0) {
            System.arraycopy(prefixBuf, 0, buf, pos, prefixLen);
            pos += prefixLen;
            buf[pos++] = '.';
        }
        System.arraycopy(key, 0, buf, pos, key.length);
        pos += key.length;
        buf[pos++] = '=';
    }

    /**
     * Writes "&prefix=" to the buffer (no key/dot). Used by map value serialization.
     */
    private void writePrefixEquals(int maxValueSize) {
        ensureCapacity(1 + prefixLen + 1 + maxValueSize);
        buf[pos++] = '&';
        if (prefixLen > 0) {
            System.arraycopy(prefixBuf, 0, buf, pos, prefixLen);
            pos += prefixLen;
        }
        buf[pos++] = '=';
    }

    private void writeParam(byte[] key, String value) {
        writeKeyPrefix(key, value.length() * 3);
        writeUrlEncoded(value);
    }

    private void writeParamBoolean(byte[] key, boolean value) {
        writeKeyPrefix(key, 5);
        pos = NumberCodec.writeBoolean(buf, pos, value);
    }

    private void writeParamInt(byte[] key, int value) {
        writeKeyPrefix(key, 11);
        pos = NumberCodec.writeInt(buf, pos, value);
    }

    private void writeParamLong(byte[] key, long value) {
        writeKeyPrefix(key, 20);
        pos = NumberCodec.writeLong(buf, pos, value);
    }

    private void writeParamDouble(byte[] key, double value) {
        writeKeyPrefix(key, NumberCodec.DOUBLE_MAX_BYTES);
        pos = NumberCodec.writeDouble(buf, pos, value);
    }

    private void writeParamFloat(byte[] key, float value) {
        writeKeyPrefix(key, NumberCodec.FLOAT_MAX_BYTES);
        pos = NumberCodec.writeFloat(buf, pos, value);
    }

    private void writeParamTimestampDirect(byte[] key, Instant value) {
        writeKeyPrefix(key, 30);
        pos = TimestampCodec.writeIso8601(buf, pos, value);
    }

    private byte[] getMemberNameBytes(Schema schema) {
        var ext = schema.getExtension(AwsQuerySchemaExtensions.KEY);
        if (ext == null) {
            return null;
        }
        return variant == QueryVariant.AWS_QUERY ? ext.awsQueryNameBytes() : ext.ec2QueryNameBytes();
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        if (schema.isMember()) {
            pushPrefix(getMemberNameBytes(schema));
            struct.serializeMembers(this);
            popPrefix();
        } else {
            struct.serializeMembers(this);
        }
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        if (!beginList(schema, size)) {
            return;
        }
        consumer.accept(listState, listSerializer);
        endList(schema);
    }

    // Call endList only when this method returns true.
    private boolean beginList(Schema schema, int size) {
        if (schema.isMember()) {
            pushPrefix(getMemberNameBytes(schema));
        }

        if (size == 0) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            if (schema.isMember()) {
                popPrefix();
            }
            return false;
        }

        if (variant == QueryVariant.EC2_QUERY) {
            listSerializer.push(null, true);
            return true;
        }

        var ext = schema.getExtension(AwsQuerySchemaExtensions.KEY);
        boolean flattened;
        byte[] memberNameBytes;
        if (ext != null && ext.listMemberNameBytes() != null) {
            flattened = ext.listFlattened();
            memberNameBytes = ext.listMemberNameBytes();
        } else if (ext != null && ext.listFlattened()) {
            flattened = true;
            memberNameBytes = null;
        } else {
            flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
            if (flattened) {
                memberNameBytes = null;
            } else {
                Schema memberSchema = schema.listMember();
                var xmlName = memberSchema.getTrait(TraitKey.XML_NAME_TRAIT);
                memberNameBytes = xmlName != null ? xmlName.getValue().getBytes(StandardCharsets.UTF_8) : MEMBER;
            }
        }

        listSerializer.push(memberNameBytes, flattened);
        return true;
    }

    private void endList(Schema schema) {
        listSerializer.pop();
        if (schema.isMember()) {
            popPrefix();
        }
    }

    private void writeEmptyValue() {
        ensureCapacity(1 + prefixLen + 1);
        buf[pos++] = '&';
        if (prefixLen > 0) {
            System.arraycopy(prefixBuf, 0, buf, pos, prefixLen);
            pos += prefixLen;
        }
        buf[pos++] = '=';
    }

    private final class ListItemSerializer implements ShapeSerializer {
        private byte[] memberNameBytes;
        private boolean flattened;
        private int index;

        void reset(byte[] memberNameBytes, boolean flattened) {
            this.memberNameBytes = memberNameBytes;
            this.flattened = flattened;
            this.index = 1;
        }

        // Nested collections reuse this serializer. Save the enclosing list's state until endList.
        private byte[][] savedMemberNameBytes = new byte[4][];
        private boolean[] savedFlattened = new boolean[4];
        private int[] savedIndex = new int[4];
        private int depth;

        void push(byte[] memberNameBytes, boolean flattened) {
            if (depth == savedIndex.length) {
                savedMemberNameBytes = Arrays.copyOf(savedMemberNameBytes, depth * 2);
                savedFlattened = Arrays.copyOf(savedFlattened, depth * 2);
                savedIndex = Arrays.copyOf(savedIndex, depth * 2);
            }
            savedMemberNameBytes[depth] = this.memberNameBytes;
            savedFlattened[depth] = this.flattened;
            savedIndex[depth] = this.index;
            depth++;
            reset(memberNameBytes, flattened);
        }

        void pop() {
            depth--;
            this.memberNameBytes = savedMemberNameBytes[depth];
            this.flattened = savedFlattened[depth];
            this.index = savedIndex[depth];
        }

        private void pushIndexedMemberPrefix() {
            if (flattened) {
                pushIndexPrefix(index);
            } else {
                pushPrefixWithIndex(memberNameBytes, index);
            }
        }

        /**
         * Writes "&prefix.memberName.index=" (or "&prefix.index=" if flattened) to the buffer.
         */
        private void writeIndexedKeyPrefix(int maxValueSize) {
            int indexLen = NumberCodec.digitCount(index);
            int memberPartLen = flattened ? indexLen : (memberNameBytes.length + 1 + indexLen);
            ensureCapacity(1 + prefixLen + (prefixLen > 0 ? 1 : 0) + memberPartLen + 1 + maxValueSize);
            buf[pos++] = '&';
            if (prefixLen > 0) {
                System.arraycopy(prefixBuf, 0, buf, pos, prefixLen);
                pos += prefixLen;
                buf[pos++] = '.';
            }
            if (flattened) {
                pos = NumberCodec.writeInt(buf, pos, index);
            } else {
                System.arraycopy(memberNameBytes, 0, buf, pos, memberNameBytes.length);
                pos += memberNameBytes.length;
                buf[pos++] = '.';
                pos = NumberCodec.writeInt(buf, pos, index);
            }
            buf[pos++] = '=';
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            pushIndexedMemberPrefix();
            index++;
            struct.serializeMembers(QueryFormSerializer.this);
            popPrefix();
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeList(schema, listState, size, consumer);
            popPrefix();
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeMap(schema, mapState, size, consumer);
            popPrefix();
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            writeIndexedKeyPrefix(5);
            pos = NumberCodec.writeBoolean(buf, pos, value);
            index++;
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            writeIndexedParamInt(value);
        }

        @Override
        public void writeShort(Schema schema, short value) {
            writeIndexedParamInt(value);
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            writeIndexedParamInt(value);
        }

        @Override
        public void writeLong(Schema schema, long value) {
            writeIndexedKeyPrefix(20);
            pos = NumberCodec.writeLong(buf, pos, value);
            index++;
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            if (!Float.isFinite(value)) {
                writeIndexedKeyPrefix(9);
                pos = NumberCodec.writeNonFiniteFloat(buf, pos, value);
            } else {
                writeIndexedKeyPrefix(NumberCodec.FLOAT_MAX_BYTES);
                pos = NumberCodec.writeFloat(buf, pos, value);
            }
            index++;
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            if (!Double.isFinite(value)) {
                writeIndexedKeyPrefix(9);
                pos = NumberCodec.writeNonFiniteDouble(buf, pos, value);
            } else {
                writeIndexedKeyPrefix(NumberCodec.DOUBLE_MAX_BYTES);
                pos = NumberCodec.writeDouble(buf, pos, value);
            }
            index++;
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            writeIndexedKeyPrefix(maxBigIntegerLength(value));
            pos = NumberCodec.writeBigInteger(buf, pos, value);
            index++;
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            writeIndexedKeyPrefix(NumberCodec.maxBigDecimalLength(value));
            pos = NumberCodec.writeBigDecimal(buf, pos, value);
            index++;
        }

        @Override
        public void writeString(Schema schema, String value) {
            writeIndexedKeyPrefix(value.length() * 3);
            writeUrlEncoded(value);
            index++;
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            byte[] encoded = ByteBufferUtils.base64EncodeToBytes(value);
            writeIndexedKeyPrefix(encoded.length * 3);
            writeUrlEncodedAsciiBytes(encoded, encoded.length);
            index++;
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            TimestampFormatTrait.Format fmt = resolveTimestampFormat(schema);
            if (fmt == TimestampFormatTrait.Format.DATE_TIME) {
                writeIndexedKeyPrefix(30);
                pos = TimestampCodec.writeIso8601(buf, pos, value);
                index++;
            } else if (fmt == TimestampFormatTrait.Format.EPOCH_SECONDS) {
                writeIndexedKeyPrefix(30);
                pos = TimestampCodec.writeEpochSeconds(buf, pos, value.getEpochSecond(), value.getNano());
                index++;
            } else if (fmt == TimestampFormatTrait.Format.HTTP_DATE) {
                writeIndexedKeyPrefix(90);
                writeHttpDateUrlEncoded(value);
                index++;
            } else {
                TimestampFormatter formatter = TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME);
                String formatted = formatter.writeString(value);
                writeIndexedKeyPrefix(formatted.length() * 3);
                writeUrlEncoded(formatted);
                index++;
            }
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            throw new SerializationException("Query protocols do not support document types");
        }

        @Override
        public void writeNull(Schema schema) {
            index++;
        }

        private void writeIndexedParamInt(int value) {
            writeIndexedKeyPrefix(11);
            pos = NumberCodec.writeInt(buf, pos, value);
            index++;
        }

        @Override
        public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeBooleanList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseBooleanList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeBooleanMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseBooleanMap(
                Schema schema,
                Map<String, Boolean> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseBooleanMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeByteList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseByteList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeByteMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseByteMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeShortList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseShortList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeShortMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseShortMap(
                Schema schema,
                Map<String, Short> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseShortMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeIntegerList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseIntegerList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeIntegerMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseIntegerMap(
                Schema schema,
                Map<String, Integer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseIntegerMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeLongList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseLongList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeLongMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseLongMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeFloatList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseFloatList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeFloatMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseFloatMap(
                Schema schema,
                Map<String, Float> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseFloatMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeDoubleList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseDoubleList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeDoubleMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseDoubleMap(
                Schema schema,
                Map<String, Double> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseDoubleMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeBigIntegerList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseBigIntegerList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeBigIntegerMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseBigIntegerMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeBigDecimalList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseBigDecimalList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeBigDecimalMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseBigDecimalMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeStringList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseStringList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeStringMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseStringMap(
                Schema schema,
                Map<String, String> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseStringMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeBlobList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseBlobList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeBlobMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseBlobMap(
                Schema schema,
                Map<String, ByteBuffer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseBlobMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeTimestampList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseTimestampList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeTimestampMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseTimestampMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeDocumentList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseDocumentList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeDocumentMap(
                Schema schema,
                Map<String, Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeDocumentMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseDocumentMap(
                Schema schema,
                Map<String, Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseDocumentMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeEnumList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseEnumList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeEnumMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseEnumMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeIntEnumList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseIntEnumList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeIntEnumMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseIntEnumMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeStructList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeSparseStructList(
                Schema schema,
                List<? extends SerializableStruct> values,
                Schema memberSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseStructList(schema, values, memberSchema);
            popPrefix();
        }

        @Override
        public void writeStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeStructMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }

        @Override
        public void writeSparseStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeSparseStructMap(schema, values, keySchema, valueSchema);
            popPrefix();
        }
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        if (!beginMap(schema, size)) {
            return;
        }
        consumer.accept(mapState, mapSerializer);
        endMap(schema);
    }

    private boolean beginMap(Schema schema, int size) {
        if (variant == QueryVariant.EC2_QUERY) {
            throw new SerializationException("EC2 Query protocol does not support map serialization");
        }

        boolean flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
        Schema keySchema = schema.mapKeyMember();
        Schema valueSchema = schema.mapValueMember();

        var keyXmlName = keySchema.getTrait(TraitKey.XML_NAME_TRAIT);
        var valueXmlName = valueSchema.getTrait(TraitKey.XML_NAME_TRAIT);

        byte[] keyNameBytes = keyXmlName != null ? keyXmlName.getValue().getBytes(StandardCharsets.UTF_8) : KEY;
        byte[] valueNameBytes = valueXmlName != null ? valueXmlName.getValue().getBytes(StandardCharsets.UTF_8) : VALUE;
        byte[] entryNameBytes = flattened ? null : ENTRY;

        if (schema.isMember()) {
            pushPrefix(getMemberNameBytes(schema));
        }
        mapSerializer.push(entryNameBytes, keyNameBytes, valueNameBytes, flattened);
        return true;
    }

    private void endMap(Schema schema) {
        mapSerializer.pop();
        if (schema.isMember()) {
            popPrefix();
        }
    }

    private final class QueryMapSerializer implements MapSerializer {
        private byte[] entryNameBytes;
        private byte[] keyNameBytes;
        private byte[] valueNameBytes;
        private boolean flattened;
        private int index;

        void reset(byte[] entryNameBytes, byte[] keyNameBytes, byte[] valueNameBytes, boolean flattened) {
            this.entryNameBytes = entryNameBytes;
            this.keyNameBytes = keyNameBytes;
            this.valueNameBytes = valueNameBytes;
            this.flattened = flattened;
            this.index = 1;
        }

        // Nested collections reuse this serializer. Save the enclosing map's state until endMap.
        private byte[][] savedNameBytes = new byte[12][];
        private boolean[] savedFlattened = new boolean[4];
        private int[] savedIndex = new int[4];
        private int depth;

        void push(byte[] entryNameBytes, byte[] keyNameBytes, byte[] valueNameBytes, boolean flattened) {
            if (depth == savedIndex.length) {
                savedNameBytes = Arrays.copyOf(savedNameBytes, depth * 6);
                savedFlattened = Arrays.copyOf(savedFlattened, depth * 2);
                savedIndex = Arrays.copyOf(savedIndex, depth * 2);
            }
            int base = depth * 3;
            savedNameBytes[base] = this.entryNameBytes;
            savedNameBytes[base + 1] = this.keyNameBytes;
            savedNameBytes[base + 2] = this.valueNameBytes;
            savedFlattened[depth] = this.flattened;
            savedIndex[depth] = this.index;
            depth++;
            reset(entryNameBytes, keyNameBytes, valueNameBytes, flattened);
        }

        void pop() {
            depth--;
            int base = depth * 3;
            this.entryNameBytes = savedNameBytes[base];
            this.keyNameBytes = savedNameBytes[base + 1];
            this.valueNameBytes = savedNameBytes[base + 2];
            this.flattened = savedFlattened[depth];
            this.index = savedIndex[depth];
        }

        void beginEntry(String key) {
            if (flattened) {
                pushIndexPrefix(index);
            } else {
                pushPrefixWithIndex(entryNameBytes, index);
            }
            writeParam(keyNameBytes, key);
            pushPrefix(valueNameBytes);
        }

        void endEntry() {
            popPrefix();
            popPrefix();
            index++;
        }

        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            beginEntry(key);
            valueSerializer.accept(state, mapValueSerializer);
            endEntry();
        }
    }

    private final class MapValueSerializer implements ShapeSerializer {
        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            struct.serializeMembers(QueryFormSerializer.this);
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            boolean flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
            Schema memberSchema = schema.listMember();

            if (size == 0) {
                writeEmptyValue();
                return;
            }

            byte[] memberNameBytes;
            if (flattened) {
                memberNameBytes = null;
            } else {
                var xmlName = memberSchema.getTrait(TraitKey.XML_NAME_TRAIT);
                memberNameBytes = xmlName != null ? xmlName.getValue().getBytes(StandardCharsets.UTF_8) : MEMBER;
            }

            listSerializer.push(memberNameBytes, flattened);
            consumer.accept(listState, listSerializer);
            listSerializer.pop();
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            boolean flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
            Schema keySchema = schema.mapKeyMember();
            Schema valueSchema = schema.mapValueMember();

            var keyXmlName = keySchema.getTrait(TraitKey.XML_NAME_TRAIT);
            var valueXmlName = valueSchema.getTrait(TraitKey.XML_NAME_TRAIT);

            byte[] keyNameBytes = keyXmlName != null ? keyXmlName.getValue().getBytes(StandardCharsets.UTF_8) : KEY;
            byte[] valueNameBytes =
                    valueXmlName != null ? valueXmlName.getValue().getBytes(StandardCharsets.UTF_8) : VALUE;
            byte[] entryNameBytes = flattened ? null : ENTRY;

            mapSerializer.push(entryNameBytes, keyNameBytes, valueNameBytes, flattened);
            consumer.accept(mapState, mapSerializer);
            mapSerializer.pop();
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            writePrefixEquals(5);
            pos = NumberCodec.writeBoolean(buf, pos, value);
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            writeValueParamInt(value);
        }

        @Override
        public void writeShort(Schema schema, short value) {
            writeValueParamInt(value);
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            writeValueParamInt(value);
        }

        @Override
        public void writeLong(Schema schema, long value) {
            writePrefixEquals(20);
            pos = NumberCodec.writeLong(buf, pos, value);
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            if (!Float.isFinite(value)) {
                writePrefixEquals(9);
                pos = NumberCodec.writeNonFiniteFloat(buf, pos, value);
            } else {
                writePrefixEquals(NumberCodec.FLOAT_MAX_BYTES);
                pos = NumberCodec.writeFloat(buf, pos, value);
            }
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            if (!Double.isFinite(value)) {
                writePrefixEquals(9);
                pos = NumberCodec.writeNonFiniteDouble(buf, pos, value);
            } else {
                writePrefixEquals(NumberCodec.DOUBLE_MAX_BYTES);
                pos = NumberCodec.writeDouble(buf, pos, value);
            }
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            writePrefixEquals(maxBigIntegerLength(value));
            pos = NumberCodec.writeBigInteger(buf, pos, value);
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            writePrefixEquals(NumberCodec.maxBigDecimalLength(value));
            pos = NumberCodec.writeBigDecimal(buf, pos, value);
        }

        @Override
        public void writeString(Schema schema, String value) {
            writePrefixEquals(value.length() * 3);
            writeUrlEncoded(value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            byte[] encoded = ByteBufferUtils.base64EncodeToBytes(value);
            writePrefixEquals(encoded.length * 3);
            writeUrlEncodedAsciiBytes(encoded, encoded.length);
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            TimestampFormatTrait.Format fmt = resolveTimestampFormat(schema);
            if (fmt == TimestampFormatTrait.Format.DATE_TIME) {
                writePrefixEquals(30);
                pos = TimestampCodec.writeIso8601(buf, pos, value);
            } else if (fmt == TimestampFormatTrait.Format.EPOCH_SECONDS) {
                writePrefixEquals(30);
                pos = TimestampCodec.writeEpochSeconds(buf, pos, value.getEpochSecond(), value.getNano());
            } else if (fmt == TimestampFormatTrait.Format.HTTP_DATE) {
                writePrefixEquals(90);
                writeHttpDateUrlEncoded(value);
            } else {
                TimestampFormatter formatter = TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME);
                String formatted = formatter.writeString(value);
                writePrefixEquals(formatted.length() * 3);
                writeUrlEncoded(formatted);
            }
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            throw new SerializationException("Query protocols do not support document types");
        }

        @Override
        public void writeNull(Schema schema) {}

        private void writeValueParamInt(int value) {
            writePrefixEquals(11);
            pos = NumberCodec.writeInt(buf, pos, value);
        }
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        writeParamBoolean(getMemberNameBytes(schema), value);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        writeParamInt(getMemberNameBytes(schema), value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        writeParamInt(getMemberNameBytes(schema), value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        writeParamInt(getMemberNameBytes(schema), value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        writeParamLong(getMemberNameBytes(schema), value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        byte[] key = getMemberNameBytes(schema);
        if (!Float.isFinite(value)) {
            writeKeyPrefix(key, 9);
            pos = NumberCodec.writeNonFiniteFloat(buf, pos, value);
        } else {
            writeParamFloat(key, value);
        }
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        byte[] key = getMemberNameBytes(schema);
        if (!Double.isFinite(value)) {
            writeKeyPrefix(key, 9);
            pos = NumberCodec.writeNonFiniteDouble(buf, pos, value);
        } else {
            writeParamDouble(key, value);
        }
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        byte[] key = getMemberNameBytes(schema);
        writeKeyPrefix(key, maxBigIntegerLength(value));
        pos = NumberCodec.writeBigInteger(buf, pos, value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        byte[] key = getMemberNameBytes(schema);
        writeKeyPrefix(key, NumberCodec.maxBigDecimalLength(value));
        pos = NumberCodec.writeBigDecimal(buf, pos, value);
    }

    @Override
    public void writeString(Schema schema, String value) {
        writeParam(getMemberNameBytes(schema), value);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        byte[] key = getMemberNameBytes(schema);
        byte[] encoded = ByteBufferUtils.base64EncodeToBytes(value);
        writeKeyPrefix(key, encoded.length * 3);
        writeUrlEncodedAsciiBytes(encoded, encoded.length);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        TimestampFormatTrait.Format fmt = resolveTimestampFormat(schema);
        byte[] key = getMemberNameBytes(schema);
        if (fmt == TimestampFormatTrait.Format.DATE_TIME) {
            writeParamTimestampDirect(key, value);
        } else if (fmt == TimestampFormatTrait.Format.EPOCH_SECONDS) {
            writeKeyPrefix(key, 30);
            pos = TimestampCodec.writeEpochSeconds(buf, pos, value.getEpochSecond(), value.getNano());
        } else if (fmt == TimestampFormatTrait.Format.HTTP_DATE) {
            writeKeyPrefix(key, 90);
            writeHttpDateUrlEncoded(value);
        } else {
            TimestampFormatter formatter = TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME);
            writeParam(key, formatter.writeString(value));
        }
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        throw new SerializationException("Query protocols do not support document types");
    }

    @Override
    public void writeNull(Schema schema) {}

    private final byte[] httpDateTmp = new byte[40];

    private void writeHttpDateUrlEncoded(Instant value) {
        int len = TimestampCodec.writeHttpDate(httpDateTmp, 0, value);
        writeUrlEncodedAsciiBytes(httpDateTmp, len);
    }

    private static TimestampFormatTrait.Format resolveTimestampFormat(Schema schema) {
        var ext = schema.getExtension(AwsQuerySchemaExtensions.KEY);
        if (ext != null && ext.timestampFormat() != null) {
            return ext.timestampFormat();
        }
        var trait = schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
        return trait != null ? trait.getFormat() : TimestampFormatTrait.Format.DATE_TIME;
    }

    @Override
    public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeBoolean(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listSerializer.writeBoolean(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBoolean(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBoolean(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeBoolean(valueSchema, v);
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeBoolean(valueSchema, v);
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeByte(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listSerializer.writeByte(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeByte(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeByte(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeByte(valueSchema, v);
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeByte(valueSchema, v);
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeShort(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listSerializer.writeShort(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeShort(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeShort(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeShort(valueSchema, v);
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeShort(valueSchema, v);
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeInteger(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listSerializer.writeInteger(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeInteger(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeInteger(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeInteger(valueSchema, v);
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeInteger(valueSchema, v);
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeLong(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listSerializer.writeLong(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeLong(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeLong(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeLong(valueSchema, v);
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeLong(valueSchema, v);
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeFloat(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listSerializer.writeFloat(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeFloat(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeFloat(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeFloat(valueSchema, v);
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeFloat(valueSchema, v);
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeDouble(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listSerializer.writeDouble(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeDouble(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeDouble(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeDouble(valueSchema, v);
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeDouble(valueSchema, v);
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeBigInteger(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listSerializer.writeBigInteger(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBigInteger(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBigInteger(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeBigInteger(valueSchema, v);
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeBigInteger(valueSchema, v);
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeBigDecimal(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listSerializer.writeBigDecimal(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBigDecimal(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBigDecimal(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeBigDecimal(valueSchema, v);
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeBigDecimal(valueSchema, v);
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeString(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listSerializer.writeString(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeString(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeString(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeString(valueSchema, v);
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeString(valueSchema, v);
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeBlob(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listSerializer.writeBlob(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBlob(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBlob(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeBlob(valueSchema, v);
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeBlob(valueSchema, v);
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeTimestamp(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listSerializer.writeTimestamp(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeTimestamp(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeTimestamp(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeTimestampMap(Schema schema, Map<String, Instant> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeTimestamp(valueSchema, v);
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeTimestamp(valueSchema, v);
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeDocument(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listSerializer.writeDocument(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeDocument(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeDocument(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeDocumentMap(Schema schema, Map<String, Document> values, Schema keySchema, Schema valueSchema) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeDocument(valueSchema, v);
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseDocumentMap(
            Schema schema,
            Map<String, Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeDocument(valueSchema, v);
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeString(memberSchema, v.getValue());
            }
        } else {
            for (var v : values) {
                listSerializer.writeString(memberSchema, v.getValue());
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeString(memberSchema, v.getValue());
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeString(memberSchema, v.getValue());
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeString(valueSchema, v.getValue());
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeString(valueSchema, v.getValue());
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeInteger(memberSchema, v.getValue());
            }
        } else {
            for (var v : values) {
                listSerializer.writeInteger(memberSchema, v.getValue());
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeInteger(memberSchema, v.getValue());
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeInteger(memberSchema, v.getValue());
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeInteger(valueSchema, v.getValue());
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeInteger(valueSchema, v.getValue());
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                listSerializer.writeStruct(memberSchema, v);
            }
        } else {
            for (var v : values) {
                listSerializer.writeStruct(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        if (!beginList(schema, values.size())) {
            return;
        }
        if (values instanceof RandomAccess) {
            for (int i = 0, n = values.size(); i < n; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeStruct(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeStruct(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            mapValueSerializer.writeStruct(valueSchema, v);
            mapSerializer.endEntry();
        });
        endMap(schema);
    }

    @Override
    public void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        if (!beginMap(schema, values.size())) {
            return;
        }
        values.forEach((k, v) -> {
            mapSerializer.beginEntry(k);
            if (v == null) {
                mapValueSerializer.writeNull(valueSchema);
            } else {
                mapValueSerializer.writeStruct(valueSchema, v);
            }
            mapSerializer.endEntry();
        });
        endMap(schema);
    }
}
