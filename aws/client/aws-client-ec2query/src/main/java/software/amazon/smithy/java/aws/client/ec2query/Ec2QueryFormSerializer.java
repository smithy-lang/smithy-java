/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.ec2query;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.function.BiConsumer;
import software.amazon.smithy.aws.traits.protocols.Ec2QueryNameTrait;
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

// NOTE: This serializer shares significant structural similarity with AwsQueryFormSerializer in the
// aws-client-awsquery module. The duplication is intentional, both protocols are deprecated and
// self-contained modules are preferred over a shared dependency. If you fix a bug here, check awsquery too.
final class Ec2QueryFormSerializer implements ShapeSerializer {
    private static final byte[] ACTION_PREFIX = "Action=".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VERSION_PREFIX = "&Version=".getBytes(StandardCharsets.UTF_8);

    private final FormUrlEncodedSink sink;

    private byte[][] prefixCache = new byte[8][];
    private int prefixDepth = 0;

    private final ListItemSerializer listSerializer = new ListItemSerializer();

    Ec2QueryFormSerializer(String action, String version) {
        this.sink = new FormUrlEncodedSink();
        sink.writeBytes(ACTION_PREFIX, 0, ACTION_PREFIX.length);
        sink.writeAscii(action);
        sink.writeBytes(VERSION_PREFIX, 0, VERSION_PREFIX.length);
        sink.writeAscii(version);
    }

    ByteBuffer finish() {
        return sink.finish();
    }

    /**
     * EC2 Query key resolution:
     * 1. ec2QueryName trait value, if present
     * 2. xmlName trait value with first letter capitalized, if present
     * 3. member name with first letter capitalized
     */
    private static String getMemberName(Schema schema) {
        var ec2Name = schema.getTrait(TraitKey.get(Ec2QueryNameTrait.class));
        if (ec2Name != null) {
            return ec2Name.getValue();
        }
        var xmlName = schema.getTrait(TraitKey.XML_NAME_TRAIT);
        if (xmlName != null) {
            return capitalize(xmlName.getValue());
        }
        return capitalize(schema.memberName());
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty() || Character.isUpperCase(s.charAt(0))) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
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
    private byte[] encodeIndex(int index) {
        String indexStr = Integer.toString(index);
        byte[] result = new byte[indexStr.length()];
        indexStr.getBytes(0, indexStr.length(), result, 0);
        return result;
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
        // EC2 Query lists are always flattened - no .member. segment
        if (schema.isMember()) {
            pushPrefix(getMemberName(schema));
        }

        if (size == 0) {
            if (schema.isMember()) {
                popPrefix();
            }
            return;
        }

        listSerializer.reset();
        consumer.accept(listState, listSerializer);

        if (schema.isMember()) {
            popPrefix();
        }
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        throw new SerializationException("EC2 Query protocol does not support map serialization");
    }

    private final class ListItemSerializer implements ShapeSerializer {
        private int index;

        void reset() {
            this.index = 1;
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            pushIndexPrefix(index);
            index++;
            struct.serializeMembers(Ec2QueryFormSerializer.this);
            popPrefix();
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            pushIndexPrefix(index);
            index++;
            Ec2QueryFormSerializer.this.writeList(schema, listState, size, consumer);
            popPrefix();
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            throw new SerializationException("EC2 Query protocol does not support map serialization");
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
            throw new SerializationException("EC2 Query protocol does not support document types");
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
            sink.writeInt(index);
            sink.writeByte('=');
            sink.writeUrlEncoded(value);
            index++;
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
        throw new SerializationException("EC2 Query protocol does not support document types");
    }

    @Override
    public void writeNull(Schema schema) {}
}
