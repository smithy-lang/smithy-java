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

final class AwsQueryFormSerializer implements ShapeSerializer {
    private static final byte[] ACTION_PREFIX = "Action=".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VERSION_PREFIX = "&Version=".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MEMBER = "member".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENTRY = "entry".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY = "key".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VALUE = "value".getBytes(StandardCharsets.UTF_8);

    private final FormUrlEncodedSink sink;

    private byte[][] prefixCache = new byte[8][];
    private int prefixDepth = 0;

    private final ListItemSerializer listSerializer = new ListItemSerializer();
    private final QueryMapSerializer mapSerializer = new QueryMapSerializer();

    AwsQueryFormSerializer(String action, String version) {
        this.sink = new FormUrlEncodedSink();
        sink.writeBytes(ACTION_PREFIX, 0, ACTION_PREFIX.length);
        sink.writeAscii(action);
        sink.writeBytes(VERSION_PREFIX, 0, VERSION_PREFIX.length);
        sink.writeAscii(version);
    }

    ByteBuffer finish() {
        return sink.finish();
    }

    private void writeParam(byte[] key, String value) {
        sink.writeByte('&');
        writeCurrentPrefix();
        if (prefixDepth > 0) {
            sink.writeByte('.');
        }
        sink.writeBytes(key, 0, key.length);
        sink.writeByte('=');
        sink.writeUrlEncoded(value);
    }

    private void writeParam(String key, String value) {
        sink.writeByte('&');
        writeCurrentPrefix();
        if (prefixDepth > 0) {
            sink.writeByte('.');
        }
        sink.writeUrlEncoded(key);
        sink.writeByte('=');
        sink.writeUrlEncoded(value);
    }

    private void writeCurrentPrefix() {
        for (int i = 0; i < prefixDepth; i++) {
            if (i > 0) {
                sink.writeByte('.');
            }
            sink.writeBytes(prefixCache[i], 0, prefixCache[i].length);
        }
    }

    private void pushPrefix(String prefix) {
        pushPrefix(encodePrefix(prefix));
    }

    private void pushPrefix(byte[] prefix) {
        ensurePrefixCacheCapacity();
        prefixCache[prefixDepth++] = prefix;
    }

    private void pushIndexedPrefix(byte[] base, int index) {
        ensurePrefixCacheCapacity();
        prefixCache[prefixDepth++] = encodeIndexedPrefix(base, index);
    }

    private void pushIndexPrefix(int index) {
        ensurePrefixCacheCapacity();
        prefixCache[prefixDepth++] = encodeIndex(index);
    }

    private void ensurePrefixCacheCapacity() {
        if (prefixDepth >= prefixCache.length) {
            prefixCache = Arrays.copyOf(prefixCache, prefixCache.length * 2);
        }
    }

    private void popPrefix() {
        prefixDepth--;
    }

    private byte[] encodePrefix(String prefix) {
        FormUrlEncodedSink tmp = new FormUrlEncodedSink(prefix.length() * 3);
        tmp.writeUrlEncoded(prefix);
        ByteBuffer bb = tmp.finish();
        byte[] result = new byte[bb.remaining()];
        bb.get(result);
        return result;
    }

    @SuppressWarnings("deprecation")
    private byte[] encodeIndexedPrefix(byte[] base, int index) {
        String indexStr = Integer.toString(index);
        byte[] result = new byte[base.length + 1 + indexStr.length()];
        System.arraycopy(base, 0, result, 0, base.length);
        result[base.length] = '.';
        indexStr.getBytes(0, indexStr.length(), result, base.length + 1);
        return result;
    }

    @SuppressWarnings("deprecation")
    private byte[] encodeIndex(int index) {
        String indexStr = Integer.toString(index);
        byte[] result = new byte[indexStr.length()];
        indexStr.getBytes(0, indexStr.length(), result, 0);
        return result;
    }

    private static String getMemberName(Schema schema) {
        var xmlName = schema.getTrait(TraitKey.XML_NAME_TRAIT);
        if (xmlName != null) {
            return xmlName.getValue();
        }
        return schema.memberName();
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        if (schema.isMember()) {
            String memberName = getMemberName(schema);
            if (memberName != null) {
                pushPrefix(memberName);
                struct.serializeMembers(this);
                popPrefix();
                return;
            }
        }
        struct.serializeMembers(this);
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        boolean flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
        Schema memberSchema = schema.listMember();

        if (schema.isMember()) {
            pushPrefix(getMemberName(schema));
        }

        if (size == 0) {
            writeEmptyValue();
            if (schema.isMember()) {
                popPrefix();
            }
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

        if (schema.isMember()) {
            popPrefix();
        }
    }

    private void writeEmptyValue() {
        sink.writeByte('&');
        writeCurrentPrefix();
        sink.writeByte('=');
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
                pushIndexedPrefix(memberNameBytes, index);
            }
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            pushIndexedMemberPrefix();
            index++;
            struct.serializeMembers(AwsQueryFormSerializer.this);
            popPrefix();
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            pushIndexedMemberPrefix();
            index++;
            AwsQueryFormSerializer.this.writeList(schema, listState, size, consumer);
            popPrefix();
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            pushIndexedMemberPrefix();
            index++;
            AwsQueryFormSerializer.this.writeMap(schema, mapState, size, consumer);
            popPrefix();
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            writeIndexedParam(value ? "true" : "false");
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            writeIndexedParam(Byte.toString(value));
        }

        @Override
        public void writeShort(Schema schema, short value) {
            writeIndexedParam(Short.toString(value));
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            writeIndexedParam(Integer.toString(value));
        }

        @Override
        public void writeLong(Schema schema, long value) {
            writeIndexedParam(Long.toString(value));
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            if (Float.isNaN(value)) {
                writeIndexedParam("NaN");
            } else if (Float.isInfinite(value)) {
                writeIndexedParam(value > 0 ? "Infinity" : "-Infinity");
            } else {
                writeIndexedParam(Float.toString(value));
            }
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            if (Double.isNaN(value)) {
                writeIndexedParam("NaN");
            } else if (Double.isInfinite(value)) {
                writeIndexedParam(value > 0 ? "Infinity" : "-Infinity");
            } else {
                writeIndexedParam(Double.toString(value));
            }
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            writeIndexedParam(value.toString());
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            writeIndexedParam(value.toPlainString());
        }

        @Override
        public void writeString(Schema schema, String value) {
            writeIndexedParam(value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            writeIndexedParam(ByteBufferUtils.base64Encode(value));
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            TimestampFormatter formatter = TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME);
            writeIndexedParam(formatter.writeString(value));
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            throw new SerializationException("AWS Query protocol does not support document types");
        }

        @Override
        public void writeNull(Schema schema) {
            index++;
        }

        private void writeIndexedParam(String value) {
            sink.writeByte('&');
            writeCurrentPrefix();
            if (prefixDepth > 0) {
                sink.writeByte('.');
            }
            if (flattened) {
                sink.writeInt(index);
            } else {
                sink.writeBytes(memberNameBytes, 0, memberNameBytes.length);
                sink.writeByte('.');
                sink.writeInt(index);
            }
            sink.writeByte('=');
            sink.writeUrlEncoded(value);
            index++;
        }
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        boolean flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
        Schema keySchema = schema.mapKeyMember();
        Schema valueSchema = schema.mapValueMember();

        if (schema.isMember()) {
            pushPrefix(getMemberName(schema));
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
                pushIndexedPrefix(entryNameBytes, index);
            }

            writeParam(keyNameBytes, key);

            pushPrefix(valueNameBytes);
            valueSerializer.accept(state, mapValueSerializer);
            popPrefix();

            popPrefix();
            index++;
        }
    }

    private final MapValueSerializer mapValueSerializer = new MapValueSerializer();

    private final class MapValueSerializer implements ShapeSerializer {
        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            struct.serializeMembers(AwsQueryFormSerializer.this);
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
            writeValueParam(value ? "true" : "false");
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            writeValueParam(Byte.toString(value));
        }

        @Override
        public void writeShort(Schema schema, short value) {
            writeValueParam(Short.toString(value));
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            writeValueParam(Integer.toString(value));
        }

        @Override
        public void writeLong(Schema schema, long value) {
            writeValueParam(Long.toString(value));
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            if (Float.isNaN(value)) {
                writeValueParam("NaN");
            } else if (Float.isInfinite(value)) {
                writeValueParam(value > 0 ? "Infinity" : "-Infinity");
            } else {
                writeValueParam(Float.toString(value));
            }
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            if (Double.isNaN(value)) {
                writeValueParam("NaN");
            } else if (Double.isInfinite(value)) {
                writeValueParam(value > 0 ? "Infinity" : "-Infinity");
            } else {
                writeValueParam(Double.toString(value));
            }
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            writeValueParam(value.toString());
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            writeValueParam(value.toPlainString());
        }

        @Override
        public void writeString(Schema schema, String value) {
            writeValueParam(value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            writeValueParam(ByteBufferUtils.base64Encode(value));
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            TimestampFormatter formatter = TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME);
            writeValueParam(formatter.writeString(value));
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            throw new SerializationException("AWS Query protocol does not support document types");
        }

        @Override
        public void writeNull(Schema schema) {}

        private void writeValueParam(String value) {
            sink.writeByte('&');
            writeCurrentPrefix();
            sink.writeByte('=');
            sink.writeUrlEncoded(value);
        }
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        writeParam(getMemberName(schema), value ? "true" : "false");
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        writeParam(getMemberName(schema), Byte.toString(value));
    }

    @Override
    public void writeShort(Schema schema, short value) {
        writeParam(getMemberName(schema), Short.toString(value));
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        writeParam(getMemberName(schema), Integer.toString(value));
    }

    @Override
    public void writeLong(Schema schema, long value) {
        writeParam(getMemberName(schema), Long.toString(value));
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        String memberName = getMemberName(schema);
        if (Float.isNaN(value)) {
            writeParam(memberName, "NaN");
        } else if (Float.isInfinite(value)) {
            writeParam(memberName, value > 0 ? "Infinity" : "-Infinity");
        } else {
            writeParam(memberName, Float.toString(value));
        }
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        String memberName = getMemberName(schema);
        if (Double.isNaN(value)) {
            writeParam(memberName, "NaN");
        } else if (Double.isInfinite(value)) {
            writeParam(memberName, value > 0 ? "Infinity" : "-Infinity");
        } else {
            writeParam(memberName, Double.toString(value));
        }
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        writeParam(getMemberName(schema), value.toString());
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        writeParam(getMemberName(schema), value.toPlainString());
    }

    @Override
    public void writeString(Schema schema, String value) {
        writeParam(getMemberName(schema), value);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        writeParam(getMemberName(schema), ByteBufferUtils.base64Encode(value));
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        TimestampFormatter formatter = TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME);
        writeParam(getMemberName(schema), formatter.writeString(value));
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        throw new SerializationException("AWS Query protocol does not support document types");
    }

    @Override
    public void writeNull(Schema schema) {}
}
