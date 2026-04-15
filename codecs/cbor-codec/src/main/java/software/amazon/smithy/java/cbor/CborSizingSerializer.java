/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static software.amazon.smithy.java.cbor.CborConstants.ONE_BYTE;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Sizing-only ShapeSerializer that computes the exact CBOR byte count without writing anything.
 * Used as pass 1 of 2-pass serialization to enable exact buffer allocation.
 */
final class CborSizingSerializer implements ShapeSerializer {

    int size;
    private final SizingStructSerializer structSerializer = new SizingStructSerializer();
    private final SizingMapSerializer mapSerializer = new SizingMapSerializer();
    private SizingDocumentContents sizingDocumentContents;

    // ---- Size computation helpers ----

    /** Returns the CBOR encoded size of an integer value (type byte + data bytes). */
    static int cborIntegerSize(long value) {
        if (value < 0) {
            value = -value - 1;
        }
        if (value < ONE_BYTE) {
            return 1;
        } else if (value <= 0xFFL) {
            return 2;
        } else if (value <= 0xFFFFL) {
            return 3;
        } else if (value <= 0xFFFF_FFFFL) {
            return 5;
        } else {
            return 9;
        }
    }

    /** Returns the CBOR tag+length header size for a given length (text string, byte string, array, map). */
    static int cborHeaderSize(int length) {
        if (length < ONE_BYTE) {
            return 1;
        } else if (length <= 0xFF) {
            return 2;
        } else if (length <= 0xFFFF) {
            return 3;
        } else {
            return 5;
        }
    }

    /**
     * Computes the exact UTF-8 byte length of a Java String without allocation.
     * For all-ASCII strings (99% of API traffic), this is just {@code s.length()}.
     */
    static int utf8ByteLength(String s) {
        int charLen = s.length();
        int extra = 0;
        for (int i = 0; i < charLen; i++) {
            char c = s.charAt(i);
            if (c >= 0x80) {
                if (c < 0x800) {
                    extra += 1; // 2-byte UTF-8
                } else if (Character.isHighSurrogate(c)) {
                    extra += 2; // 4-byte UTF-8 (surrogate pair)
                    i++; // skip low surrogate
                } else {
                    extra += 2; // 3-byte UTF-8
                }
            }
        }
        return charLen + extra;
    }

    // ---- ShapeSerializer implementation ----

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        size += 1;
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        size += cborIntegerSize(value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        size += cborIntegerSize(value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        size += cborIntegerSize(value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        size += cborIntegerSize(value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        size += 5; // type byte + 4 float bytes
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        size += 9; // type byte + 8 double bytes
    }

    @Override
    public void writeString(Schema schema, String value) {
        int utf8Len = utf8ByteLength(value);
        size += cborHeaderSize(utf8Len) + utf8Len;
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        int len = value.remaining();
        size += cborHeaderSize(len) + len;
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        size += cborHeaderSize(value.length) + value.length;
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        long millis = value.toEpochMilli();
        size += 1; // TAG byte
        if (millis % 1000 == 0) {
            size += cborIntegerSize(millis / 1000);
        } else {
            size += 9; // double
        }
    }

    @Override
    public void writeNull(Schema schema) {
        size += 1;
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        sizeBigInteger(value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        size += 1; // TAG_DECIMAL byte
        size += 1; // array(2) header byte (always 1 byte since count is 2)
        size += cborIntegerSize(-value.scale()); // exponent
        sizeBigInteger(value.unscaledValue()); // mantissa
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        size += 1; // MAP_STREAM byte
        struct.serializeMembers(structSerializer);
        size += 1; // BREAK byte
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int listSize, BiConsumer<T, ShapeSerializer> consumer) {
        size += cborHeaderSize(listSize); // array header with definite length
        consumer.accept(listState, this);
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int mapSize, BiConsumer<T, MapSerializer> consumer) {
        size += cborHeaderSize(mapSize); // map header with definite length
        consumer.accept(mapState, mapSerializer);
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        if (value.type() != ShapeType.STRUCTURE) {
            value.serializeContents(this);
        } else {
            if (sizingDocumentContents == null) {
                sizingDocumentContents = new SizingDocumentContents(this);
            }
            value.serializeContents(sizingDocumentContents);
        }
    }

    private void sizeBigInteger(BigInteger value) {
        int bits = value.bitLength();
        if (bits < 64) {
            size += cborIntegerSize(value.longValue());
        } else if (bits == 64) {
            size += 9; // type + 8 bytes
        } else {
            byte[] bytes = value.toByteArray();
            size += 1; // TAG byte
            size += cborHeaderSize(bytes.length) + bytes.length;
        }
    }

    // ---- Struct serializer: sizes field names + values ----

    private final class SizingStructSerializer implements ShapeSerializer {
        private void sizeFieldName(Schema schema) {
            // Field name is a CBOR text string: header + ASCII member name bytes
            String name = schema.memberName();
            int nameLen = name.length(); // member names are always ASCII
            size += cborHeaderSize(nameLen) + nameLen;
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeBoolean(schema, value);
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeByte(schema, value);
        }

        @Override
        public void writeShort(Schema schema, short value) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeShort(schema, value);
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeInteger(schema, value);
        }

        @Override
        public void writeLong(Schema schema, long value) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeLong(schema, value);
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeFloat(schema, value);
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeDouble(schema, value);
        }

        @Override
        public void writeString(Schema schema, String value) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeString(schema, value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeBlob(schema, value);
        }

        @Override
        public void writeBlob(Schema schema, byte[] value) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeBlob(schema, value);
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeTimestamp(schema, value);
        }

        @Override
        public void writeNull(Schema schema) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeNull(schema);
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeBigInteger(schema, value);
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeBigDecimal(schema, value);
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeStruct(schema, struct);
        }

        @Override
        public <T> void writeList(
                Schema schema,
                T listState,
                int listSize,
                BiConsumer<T, ShapeSerializer> consumer
        ) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeList(schema, listState, listSize, consumer);
        }

        @Override
        public <T> void writeMap(
                Schema schema,
                T mapState,
                int mapSize,
                BiConsumer<T, MapSerializer> consumer
        ) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeMap(schema, mapState, mapSize, consumer);
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            sizeFieldName(schema);
            CborSizingSerializer.this.writeDocument(schema, value);
        }
    }

    // ---- Map serializer: sizes map key + value ----

    private final class SizingMapSerializer implements MapSerializer {
        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            // Map key is a CBOR text string
            int keyUtf8Len = utf8ByteLength(key);
            size += cborHeaderSize(keyUtf8Len) + keyUtf8Len;
            valueSerializer.accept(state, CborSizingSerializer.this);
        }
    }

    // ---- Document struct serializer (sizes __type field) ----

    private static final class SizingDocumentContents extends SpecificShapeSerializer {
        private final CborSizingSerializer parent;

        SizingDocumentContents(CborSizingSerializer parent) {
            this.parent = parent;
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            parent.size += 1; // MAP_STREAM
            // __type field: text string "6 bytes" + value string
            int typeKeyLen = 6; // "__type"
            parent.size += cborHeaderSize(typeKeyLen) + typeKeyLen;
            String typeValue = schema.id().toString();
            int typeValueUtf8Len = utf8ByteLength(typeValue);
            parent.size += cborHeaderSize(typeValueUtf8Len) + typeValueUtf8Len;
            struct.serializeMembers(parent.structSerializer);
            parent.size += 1; // BREAK
        }
    }
}
