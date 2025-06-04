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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

final class CborSerializer implements ShapeSerializer {
    private static final int MAP_STREAM = TYPE_MAP | INDEFINITE;
    private static final int ARRAY_STREAM = TYPE_ARRAY | INDEFINITE;

    private boolean[] collection = new boolean[4];
    private int collectionIdx = -1;
    private final Sink sink;
    private final CborMapSerializer mapSerializer = new CborMapSerializer();
    private final CborStructSerializer structSerializer = new CborStructSerializer();
    private SerializeDocumentContents serializeDocumentContents;

    public CborSerializer(Sink sink) {
        this.sink = sink;
    }

    private void startMap(int size) {
        boolean indefinite = size < 0;
        if (indefinite) {
            sink.write(MAP_STREAM);
        } else {
            tagAndLength(TYPE_MAP, size);
        }
        startCollection(indefinite);
    }

    private void startArray(int size) {
        boolean indefinite = size < 0;
        if (indefinite) {
            sink.write(ARRAY_STREAM);
        } else {
            tagAndLength(TYPE_ARRAY, size);
        }
        startCollection(indefinite);
    }

    private void startCollection(boolean indefinite) {
        int idx = ++collectionIdx;
        boolean[] coll = collection;
        int l = coll.length;
        if (idx == l) {
            collection = (coll = Arrays.copyOf(coll, l + (l >> 1)));
        }
        coll[idx] = indefinite;
    }

    private void endMap() {
        if (collection[collectionIdx--]) {
            sink.write(TYPE_SIMPLE_BREAK_STREAM);
        }
    }

    private void endArray() {
        if (collection[collectionIdx--]) {
            sink.write(TYPE_SIMPLE_BREAK_STREAM);
        }
    }

    private void tagAndLength(int type, int len) {
        if (len < ONE_BYTE) {
            sink.write(type | len);
        } else if (len <= 0xFF) {
            sink.write(type | ONE_BYTE);
            sink.write(len);
        } else if (len <= 0xFFFF) {
            sink.write(type | TWO_BYTES);
            write2Nonnegative(len);
        } else {
            sink.write(type | FOUR_BYTES);
            write4Nonnegative(len);
        }
    }

    private void write8Nonnegative(long l) {
        sink.write((int) ((l >> 56) & 0xFF));
        sink.write((int) ((l >> 48) & 0xFF));
        sink.write((int) ((l >> 40) & 0xFF));
        sink.write((int) ((l >> 32) & 0xFF));
        sink.write((int) ((l >> 24) & 0xFF));
        sink.write((int) ((l >> 16) & 0xFF));
        sink.write((int) ((l >> 8) & 0xFF));
        sink.write((int) ((l) & 0xFF));
    }

    private void write4Nonnegative(int i) {
        sink.write((i >> 24) & 0xFF);
        sink.write((i >> 16) & 0xFF);
        sink.write((i >> 8) & 0xFF);
        sink.write((i) & 0xFF);
    }

    private void write2Nonnegative(int i) {
        sink.write((i >> 8) & 0xFF);
        sink.write((i) & 0xFF);
    }

    private void writeLong(long l) {
        byte type;
        if (l < 0) {
            l = -l - 1;
            type = TYPE_NEGINT;
        } else {
            type = TYPE_POSINT;
        }

        if (l < ONE_BYTE) {
            sink.write(type | (int) l);
        } else if (l <= 0xFFL) {
            sink.write(type | ONE_BYTE);
            sink.write((int) l);
        } else if (l <= 0xFFFFL) {
            sink.write(type | TWO_BYTES);
            write2Nonnegative((int) l);
        } else if (l <= 0xFFFF_FFFFL) {
            sink.write(type | FOUR_BYTES);
            write4Nonnegative((int) l);
        } else {
            sink.write(type | EIGHT_BYTES);
            write8Nonnegative(l);
        }
    }

    private void writeBytes0(int type, byte[] b, int off, int len) {
        tagAndLength(type, len);
        sink.write(b, off, len);
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        sink.write(MAP_STREAM);
        startCollection(true);
        struct.serializeMembers(structSerializer);
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
        sink.write(value ? TYPE_SIMPLE_TRUE : TYPE_SIMPLE_FALSE);
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
        sink.write(TYPE_SIMPLE_FLOAT);
        write4Nonnegative(Float.floatToRawIntBits(value));
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        sink.write(TYPE_SIMPLE_DOUBLE);
        write8Nonnegative(Double.doubleToRawLongBits(value));
    }

    @Override
    public void writeString(Schema schema, String value) {
        byte[] str = value.getBytes(StandardCharsets.UTF_8);
        writeBytes0(TYPE_TEXTSTRING, str, 0, str.length);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        tagAndLength(TYPE_BYTESTRING, value.remaining());
        sink.write(value);
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        writeBytes0(TYPE_BYTESTRING, value, 0, value.length);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        double epochSeconds = value.toEpochMilli() / 1000D;
        sink.write(TYPE_TAG | TAG_TIME_EPOCH);
        writeDouble(schema, epochSeconds);
    }

    @Override
    public void writeNull(Schema schema) {
        sink.write(TYPE_SIMPLE_NULL);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        writeBigInteger(value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        sink.write(TYPE_TAG | TAG_DECIMAL);
        tagAndLength(TYPE_ARRAY, 2);
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
                sink.write(type | EIGHT_BYTES);
                write8Nonnegative(value.longValue() ^ signum);
            } else {
                byte[] bytes = value.toByteArray();
                byte tag;
                if (signum < 0) {
                    tag = TAG_NEG_BIG_INT;
                    flipBytes(bytes);
                } else {
                    tag = TAG_POS_BIG_INT;
                }
                sink.write(TYPE_TAG | tag);
                writeBytes0(TYPE_BYTESTRING, bytes, 0, bytes.length);
            }
        }
    }

    private final class CborStructSerializer extends InterceptingSerializer {
        @Override
        protected ShapeSerializer before(Schema schema) {
            String name = schema.memberName();
            tagAndLength(TYPE_TEXTSTRING, name.length());
            sink.writeAscii(name);
            return CborSerializer.this;
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
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            writeBytes0(TYPE_TEXTSTRING, keyBytes, 0, keyBytes.length);
            valueSerializer.accept(state, CborSerializer.this);
        }
    }

    private static final class SerializeDocumentContents extends SpecificShapeSerializer {
        private final CborSerializer parent;

        SerializeDocumentContents(CborSerializer parent) {
            this.parent = parent;
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            try {
                parent.startMap(-1);
                parent.tagAndLength(TYPE_TEXTSTRING, 6);
                parent.sink.writeAscii("__type");
                parent.writeString(null, schema.id().toString());
                struct.serializeMembers(parent.structSerializer);
                parent.endMap();
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }
    }
}
