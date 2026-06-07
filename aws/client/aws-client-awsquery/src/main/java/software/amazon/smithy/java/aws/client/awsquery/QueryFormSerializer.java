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
import java.util.function.BiConsumer;
import software.amazon.smithy.java.codecs.commons.NumberCodec;
import software.amazon.smithy.java.codecs.commons.StripedPool;
import software.amazon.smithy.java.codecs.commons.TimestampCodec;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
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
    private static final int MAX_CACHEABLE_BUF = DEFAULT_BUF_SIZE * 4;

    record AcquireContext(QueryVariant variant, String action, String version) {}

    private static final StripedPool<QueryFormSerializer, AcquireContext> POOL =
            new StripedPool<>() {
                @Override
                protected QueryFormSerializer create(AcquireContext ctx) {
                    return new QueryFormSerializer();
                }

                @Override
                protected boolean canPool(QueryFormSerializer s) {
                    return true;
                }

                @Override
                protected void prepareForPool(QueryFormSerializer s) {
                    if (s.buf.length > MAX_CACHEABLE_BUF) {
                        s.buf = new byte[DEFAULT_BUF_SIZE];
                    }
                }

                @Override
                protected boolean reset(QueryFormSerializer s, AcquireContext ctx) {
                    s.prefixLen = 0;
                    s.prefixDepth = 0;
                    s.pos = 0;
                    return true;
                }
            };

    private byte[] buf;
    private int pos;
    private QueryVariant variant;

    private byte[] prefixBuf = new byte[128];
    private int prefixLen = 0;
    private int[] prefixStack = new int[8];
    private int prefixDepth = 0;

    private final ListItemSerializer listSerializer = new ListItemSerializer();
    private final QueryMapSerializer mapSerializer = new QueryMapSerializer();
    private final MapValueSerializer mapValueSerializer = new MapValueSerializer();

    private QueryFormSerializer() {
        this.buf = new byte[DEFAULT_BUF_SIZE];
    }

    @SuppressWarnings("deprecation")
    static QueryFormSerializer acquire(QueryVariant variant, String action, String version) {
        QueryFormSerializer s = POOL.acquire(new AcquireContext(variant, action, version));
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
        ByteBuffer result = ByteBuffer.wrap(buf, 0, pos);
        POOL.release(this);
        return result;
    }

    private void ensureCapacity(int needed) {
        int required = pos + needed;
        if (required > buf.length) {
            buf = Arrays.copyOf(buf, Math.max(required, buf.length + (buf.length >> 1)));
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

    private void writeUrlEncoded(String s) {
        int len = s.length();
        for (int i = 0; i < len; i++) {
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
        writeKeyPrefix(key, 25);
        pos = NumberCodec.writeDouble(buf, pos, value);
    }

    private void writeParamFloat(byte[] key, float value) {
        writeKeyPrefix(key, 15);
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
        if (variant == QueryVariant.EC2_QUERY) {
            writeEc2List(schema, listState, size, consumer);
        } else {
            writeAwsQueryList(schema, listState, size, consumer);
        }
    }

    private <T> void writeAwsQueryList(
            Schema schema,
            T listState,
            int size,
            BiConsumer<T, ShapeSerializer> consumer
    ) {
        if (schema.isMember()) {
            pushPrefix(getMemberNameBytes(schema));
        }

        if (size == 0) {
            writeEmptyValue();
            if (schema.isMember()) {
                popPrefix();
            }
            return;
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

        listSerializer.reset(memberNameBytes, flattened);
        consumer.accept(listState, listSerializer);

        if (schema.isMember()) {
            popPrefix();
        }
    }

    private <T> void writeEc2List(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        if (schema.isMember()) {
            pushPrefix(getMemberNameBytes(schema));
        }

        if (size == 0) {
            if (schema.isMember()) {
                popPrefix();
            }
            return;
        }

        listSerializer.reset(null, true);
        consumer.accept(listState, listSerializer);

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
                writeIndexedKeyPrefix(15);
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
                writeIndexedKeyPrefix(25);
                pos = NumberCodec.writeDouble(buf, pos, value);
            }
            index++;
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            writeIndexedKeyPrefix(64);
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
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        if (variant == QueryVariant.EC2_QUERY) {
            throw new SerializationException("EC2 Query protocol does not support map serialization");
        }

        boolean flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
        Schema keySchema = schema.mapKeyMember();
        Schema valueSchema = schema.mapValueMember();

        if (schema.isMember()) {
            pushPrefix(getMemberNameBytes(schema));
        }

        var keyXmlName = keySchema.getTrait(TraitKey.XML_NAME_TRAIT);
        var valueXmlName = valueSchema.getTrait(TraitKey.XML_NAME_TRAIT);

        byte[] keyNameBytes = keyXmlName != null ? keyXmlName.getValue().getBytes(StandardCharsets.UTF_8) : KEY;
        byte[] valueNameBytes = valueXmlName != null ? valueXmlName.getValue().getBytes(StandardCharsets.UTF_8) : VALUE;
        byte[] entryNameBytes = flattened ? null : ENTRY;

        mapSerializer.reset(entryNameBytes, keyNameBytes, valueNameBytes, flattened);
        consumer.accept(mapState, mapSerializer);

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

        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            if (flattened) {
                pushIndexPrefix(index);
            } else {
                pushPrefixWithIndex(entryNameBytes, index);
            }

            writeParam(keyNameBytes, key);

            pushPrefix(valueNameBytes);
            valueSerializer.accept(state, mapValueSerializer);
            popPrefix();

            popPrefix();
            index++;
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

            listSerializer.reset(memberNameBytes, flattened);
            consumer.accept(listState, listSerializer);
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

            mapSerializer.reset(entryNameBytes, keyNameBytes, valueNameBytes, flattened);
            consumer.accept(mapState, mapSerializer);
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
                writePrefixEquals(15);
                pos = NumberCodec.writeFloat(buf, pos, value);
            }
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            if (!Double.isFinite(value)) {
                writePrefixEquals(9);
                pos = NumberCodec.writeNonFiniteDouble(buf, pos, value);
            } else {
                writePrefixEquals(25);
                pos = NumberCodec.writeDouble(buf, pos, value);
            }
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            writePrefixEquals(64);
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
        writeKeyPrefix(key, 64);
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
}
