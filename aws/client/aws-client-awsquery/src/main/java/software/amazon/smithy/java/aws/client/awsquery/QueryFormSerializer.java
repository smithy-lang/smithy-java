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
 *
 * <p>Resolves every parameter name from the schema as it walks the shape. {@link QueryFormWriter} is
 * the generated-codec counterpart, which knows those names at emit time instead; the buffer, the
 * prefix stack, and the value encoders they share live in {@link QueryFormOutput}.
 */
final class QueryFormSerializer extends QueryFormOutput implements ShapeSerializer {

    enum QueryVariant {
        AWS_QUERY,
        EC2_QUERY
    }

    private static final byte[] MEMBER = "member".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENTRY = "entry".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY = "key".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VALUE = "value".getBytes(StandardCharsets.UTF_8);

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
                    s.resetOutput();
                    return true;
                }
            };

    private QueryVariant variant;

    private final ListItemSerializer listSerializer = new ListItemSerializer();
    private final QueryMapSerializer mapSerializer = new QueryMapSerializer();
    private final MapValueSerializer mapValueSerializer = new MapValueSerializer();

    private QueryFormSerializer() {}

    static QueryFormSerializer acquire(QueryVariant variant, String action, String version) {
        QueryFormSerializer s = POOL.acquire(new AcquireContext(variant, action, version));
        s.variant = variant;
        s.appendHeader(action, version);
        return s;
    }

    ByteBuffer finish() {
        ByteBuffer result = copyOut();
        POOL.release(this);
        return result;
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
        appendUrlEncoded(value);
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

        // Save/restore: a nested collection re-enters and reset()s this shared instance, which would
        // otherwise clobber the outer index mid-iteration.
        var savedMemberNameBytes = listSerializer.memberNameBytes;
        var savedFlattened = listSerializer.flattened;
        var savedIndex = listSerializer.index;

        listSerializer.reset(memberNameBytes, flattened);
        consumer.accept(listState, listSerializer);

        listSerializer.memberNameBytes = savedMemberNameBytes;
        listSerializer.flattened = savedFlattened;
        listSerializer.index = savedIndex;

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

        // Save/restore: a nested collection re-enters and reset()s this shared instance, which would
        // otherwise clobber the outer index mid-iteration.
        var savedMemberNameBytes = listSerializer.memberNameBytes;
        var savedFlattened = listSerializer.flattened;
        var savedIndex = listSerializer.index;

        listSerializer.reset(null, true);
        consumer.accept(listState, listSerializer);

        listSerializer.memberNameBytes = savedMemberNameBytes;
        listSerializer.flattened = savedFlattened;
        listSerializer.index = savedIndex;

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
            writeIndexedKeyPrefix(maxBigIntegerBytes(value));
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
            appendUrlEncoded(value);
            index++;
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            byte[] encoded = ByteBufferUtils.base64EncodeToBytes(value);
            writeIndexedKeyPrefix(encoded.length * 3);
            appendUrlEncodedBytes(encoded, encoded.length);
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
                writeIndexedKeyPrefix(MAX_HTTP_DATE_BYTES);
                appendHttpDate(value);
                index++;
            } else {
                TimestampFormatter formatter = TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME);
                String formatted = formatter.writeString(value);
                writeIndexedKeyPrefix(formatted.length() * 3);
                appendUrlEncoded(formatted);
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

        // Save/restore: a nested collection re-enters and reset()s this shared instance (see writeList).
        var savedEntry = mapSerializer.entryNameBytes;
        var savedKey = mapSerializer.keyNameBytes;
        var savedValue = mapSerializer.valueNameBytes;
        var savedFlattened = mapSerializer.flattened;
        var savedIndex = mapSerializer.index;

        mapSerializer.reset(entryNameBytes, keyNameBytes, valueNameBytes, flattened);
        consumer.accept(mapState, mapSerializer);

        mapSerializer.entryNameBytes = savedEntry;
        mapSerializer.keyNameBytes = savedKey;
        mapSerializer.valueNameBytes = savedValue;
        mapSerializer.flattened = savedFlattened;
        mapSerializer.index = savedIndex;

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

            // Save/restore for the same reason the top-level writeList does it: this map value may be
            // reached from inside a list, and reset()ing the shared instance would clobber its index.
            var savedMemberNameBytes = listSerializer.memberNameBytes;
            var savedFlattened = listSerializer.flattened;
            var savedIndex = listSerializer.index;

            listSerializer.reset(memberNameBytes, flattened);
            consumer.accept(listState, listSerializer);

            listSerializer.memberNameBytes = savedMemberNameBytes;
            listSerializer.flattened = savedFlattened;
            listSerializer.index = savedIndex;
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

            // A map value that is itself a map re-enters this shared instance, so the enclosing map's
            // entry index has to survive the nested iteration.
            var savedEntry = mapSerializer.entryNameBytes;
            var savedKey = mapSerializer.keyNameBytes;
            var savedValue = mapSerializer.valueNameBytes;
            var savedFlattened = mapSerializer.flattened;
            var savedIndex = mapSerializer.index;

            mapSerializer.reset(entryNameBytes, keyNameBytes, valueNameBytes, flattened);
            consumer.accept(mapState, mapSerializer);

            mapSerializer.entryNameBytes = savedEntry;
            mapSerializer.keyNameBytes = savedKey;
            mapSerializer.valueNameBytes = savedValue;
            mapSerializer.flattened = savedFlattened;
            mapSerializer.index = savedIndex;
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
            writePrefixEquals(maxBigIntegerBytes(value));
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
            appendUrlEncoded(value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            byte[] encoded = ByteBufferUtils.base64EncodeToBytes(value);
            writePrefixEquals(encoded.length * 3);
            appendUrlEncodedBytes(encoded, encoded.length);
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
                writePrefixEquals(MAX_HTTP_DATE_BYTES);
                appendHttpDate(value);
            } else {
                TimestampFormatter formatter = TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME);
                String formatted = formatter.writeString(value);
                writePrefixEquals(formatted.length() * 3);
                appendUrlEncoded(formatted);
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
        writeKeyPrefix(key, maxBigIntegerBytes(value));
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
        appendUrlEncodedBytes(encoded, encoded.length);
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
            writeKeyPrefix(key, MAX_HTTP_DATE_BYTES);
            appendHttpDate(value);
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

    /** Package-private so the generated path resolves the format from the same source at emit time. */
    static TimestampFormatTrait.Format resolveTimestampFormat(Schema schema) {
        var ext = schema.getExtension(AwsQuerySchemaExtensions.KEY);
        if (ext != null && ext.timestampFormat() != null) {
            return ext.timestampFormat();
        }
        var trait = schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
        return trait != null ? trait.getFormat() : TimestampFormatTrait.Format.DATE_TIME;
    }
}
