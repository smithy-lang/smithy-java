/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.JsonFieldMapper;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * High-performance JSON serializer that writes directly to a byte array buffer.
 *
 * <p>Eliminates Jackson's intermediate buffering by writing JSON bytes directly.
 * Uses pre-computed field name byte arrays from {@link SmithyJsonSchemaExtensions}
 * for zero-cost field name emission via {@link System#arraycopy}.
 */
final class SmithyJsonSerializer implements ShapeSerializer {

    private static final int MAX_DEPTH = 64;

    private byte[] buf;
    private int pos;
    private final OutputStream sink;
    private final JsonSettings settings;
    private final boolean useJsonName;

    // Nesting state for comma insertion
    private int depth;
    private final boolean[] needsComma = new boolean[MAX_DEPTH];

    // Cached field name table for the current struct being serialized.
    // Resolved once per writeStruct call via a single getExtension, then used
    // for all member writes — avoids per-field VarHandle acquire reads.
    private byte[][] currentFieldNameTable;

    // Inner serializers — cached (single instance per serializer)
    private final ShapeSerializer structSerializer = new StructSerializer();
    private final ShapeSerializer listElementSerializer = new ListElementSerializer();
    private final MapSerializer mapSerializer = new SmithyMapSerializer();
    private SerializeDocumentContents serializeDocumentContents;

    SmithyJsonSerializer(OutputStream sink, JsonSettings settings) {
        this.sink = sink;
        this.settings = settings;
        this.useJsonName = settings.fieldMapper() instanceof JsonFieldMapper.UseJsonNameTrait;
        this.buf = JsonBufferPool.acquire(JsonBufferPool.DEFAULT_SIZE);
        this.pos = 0;
        this.depth = 0;
    }

    private void ensureCapacity(int needed) {
        if (pos + needed > buf.length) {
            grow(needed);
        }
    }

    // Separate cold grow method helps JIT inline ensureCapacity's fast path
    private void grow(int needed) {
        buf = Arrays.copyOf(buf, Math.max(buf.length * 2, pos + needed));
    }

    @Override
    public void flush() {
        try {
            if (pos > 0) {
                sink.write(buf, 0, pos);
                pos = 0;
            }
            sink.flush();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void close() {
        try {
            if (pos > 0) {
                sink.write(buf, 0, pos);
                pos = 0;
            }
            JsonBufferPool.release(buf);
            buf = null;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    // ---- Value writers (no field name prefix) ----

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        byte[] bytes = value ? JsonWriteUtils.TRUE_BYTES : JsonWriteUtils.FALSE_BYTES;
        ensureCapacity(bytes.length);
        System.arraycopy(bytes, 0, buf, pos, bytes.length);
        pos += bytes.length;
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        ensureCapacity(4);
        pos = JsonWriteUtils.writeInt(buf, pos, value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        ensureCapacity(6);
        pos = JsonWriteUtils.writeInt(buf, pos, value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        ensureCapacity(11);
        pos = JsonWriteUtils.writeInt(buf, pos, value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        ensureCapacity(20);
        pos = JsonWriteUtils.writeLong(buf, pos, value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        if (Float.isFinite(value)) {
            ensureCapacity(24);
            pos = JsonWriteUtils.writeFloat(buf, pos, value);
        } else if (Float.isNaN(value)) {
            ensureCapacity(JsonWriteUtils.NAN_BYTES.length);
            System.arraycopy(JsonWriteUtils.NAN_BYTES, 0, buf, pos, JsonWriteUtils.NAN_BYTES.length);
            pos += JsonWriteUtils.NAN_BYTES.length;
        } else {
            byte[] bytes = value > 0 ? JsonWriteUtils.INF_BYTES : JsonWriteUtils.NEG_INF_BYTES;
            ensureCapacity(bytes.length);
            System.arraycopy(bytes, 0, buf, pos, bytes.length);
            pos += bytes.length;
        }
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        if (Double.isFinite(value)) {
            ensureCapacity(24);
            pos = JsonWriteUtils.writeDouble(buf, pos, value);
        } else if (Double.isNaN(value)) {
            ensureCapacity(JsonWriteUtils.NAN_BYTES.length);
            System.arraycopy(JsonWriteUtils.NAN_BYTES, 0, buf, pos, JsonWriteUtils.NAN_BYTES.length);
            pos += JsonWriteUtils.NAN_BYTES.length;
        } else {
            byte[] bytes = value > 0 ? JsonWriteUtils.INF_BYTES : JsonWriteUtils.NEG_INF_BYTES;
            ensureCapacity(bytes.length);
            System.arraycopy(bytes, 0, buf, pos, bytes.length);
            pos += bytes.length;
        }
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        String s = value.toString();
        ensureCapacity(s.length());
        pos = JsonWriteUtils.writeAsciiString(buf, pos, s);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        String s = value.toString();
        ensureCapacity(s.length());
        pos = JsonWriteUtils.writeAsciiString(buf, pos, s);
    }

    @Override
    public void writeString(Schema schema, String value) {
        ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(value));
        pos = JsonWriteUtils.writeQuotedString(buf, pos, value);
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        ensureCapacity(JsonWriteUtils.maxBase64Bytes(value.length));
        pos = JsonWriteUtils.writeBase64String(buf, pos, value, 0, value.length);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        int len = value.remaining();
        byte[] data;
        int off;
        if (value.hasArray()) {
            data = value.array();
            off = value.arrayOffset() + value.position();
        } else {
            data = ByteBufferUtils.getBytes(value.duplicate());
            off = 0;
        }
        ensureCapacity(JsonWriteUtils.maxBase64Bytes(len));
        pos = JsonWriteUtils.writeBase64String(buf, pos, data, off, len);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        settings.timestampResolver().resolve(schema).writeToSerializer(schema, value, this);
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;

        // Resolve field name table ONCE per struct (single VarHandle acquire read),
        // then all member writes use plain array indexing — no per-field getExtension.
        byte[][] savedTable = currentFieldNameTable;
        Schema structSchema = schema.isMember() ? schema.memberTarget() : schema;
        var ext = structSchema.getExtension(SmithyJsonSchemaExtensions.KEY);
        if (ext != null) {
            currentFieldNameTable = useJsonName ? ext.jsonFieldNameTable() : ext.memberFieldNameTable();
        } else {
            currentFieldNameTable = null;
        }

        struct.serializeMembers(structSerializer);

        currentFieldNameTable = savedTable; // restore for nested struct returns
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        ensureCapacity(2);
        buf[pos++] = '[';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        consumer.accept(listState, listElementSerializer);
        depth--;
        ensureCapacity(1);
        buf[pos++] = ']';
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        ensureCapacity(2);
        buf[pos++] = '{';
        depth++;
        if (depth >= MAX_DEPTH) {
            throw new SerializationException("Maximum nesting depth exceeded: " + MAX_DEPTH);
        }
        needsComma[depth] = false;
        consumer.accept(mapState, mapSerializer);
        depth--;
        ensureCapacity(1);
        buf[pos++] = '}';
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

    @Override
    public void writeNull(Schema schema) {
        ensureCapacity(JsonWriteUtils.NULL_BYTES.length);
        System.arraycopy(JsonWriteUtils.NULL_BYTES, 0, buf, pos, JsonWriteUtils.NULL_BYTES.length);
        pos += JsonWriteUtils.NULL_BYTES.length;
    }

    // ---- Comma management ----

    private void writeCommaIfNeeded() {
        if (needsComma[depth]) {
            if (pos >= buf.length) {
                grow(1);
            }
            buf[pos++] = ',';
        } else {
            needsComma[depth] = true;
        }
    }

    // ---- Field name writing (fused comma + name for single ensureCapacity call) ----

    private void writeFieldNameBytes(Schema schema) {
        // Use cached field name table (resolved once per writeStruct) — plain array index,
        // no VarHandle acquire per field. Falls back to per-member extension for edge cases.
        byte[] nameBytes;
        byte[][] table = currentFieldNameTable;
        int idx = schema.memberIndex();
        if (table != null && idx >= 0 && idx < table.length && table[idx] != null) {
            nameBytes = table[idx];
        } else {
            // Fallback for edge cases (e.g., document struct members without a parent table)
            var ext = schema.getExtension(SmithyJsonSchemaExtensions.KEY);
            nameBytes = useJsonName ? ext.jsonNameBytes() : ext.memberNameBytes();
        }
        int needed = nameBytes.length + 1; // +1 for potential comma
        if (pos + needed > buf.length) {
            grow(needed);
        }
        // Inline comma logic to avoid separate method call + capacity check
        if (needsComma[depth]) {
            buf[pos++] = ',';
        } else {
            needsComma[depth] = true;
        }
        System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
        pos += nameBytes.length;
    }

    // ---- Inner struct serializer: writes field name + value ----

    private final class StructSerializer implements ShapeSerializer {

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBoolean(schema, value);
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeByte(schema, value);
        }

        @Override
        public void writeShort(Schema schema, short value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeShort(schema, value);
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeInteger(schema, value);
        }

        @Override
        public void writeLong(Schema schema, long value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeLong(schema, value);
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeFloat(schema, value);
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeDouble(schema, value);
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBigInteger(schema, value);
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBigDecimal(schema, value);
        }

        @Override
        public void writeString(Schema schema, String value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeString(schema, value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeBlob(schema, value);
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeTimestamp(schema, value);
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeStruct(schema, struct);
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeList(schema, listState, size, consumer);
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeMap(schema, mapState, size, consumer);
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeDocument(schema, value);
        }

        @Override
        public void writeNull(Schema schema) {
            writeFieldNameBytes(schema);
            SmithyJsonSerializer.this.writeNull(schema);
        }
    }

    // ---- List element serializer: handles comma separation between elements ----

    private final class ListElementSerializer implements ShapeSerializer {
        private void beforeElement() {
            writeCommaIfNeeded();
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            beforeElement();
            SmithyJsonSerializer.this.writeBoolean(schema, value);
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            beforeElement();
            SmithyJsonSerializer.this.writeByte(schema, value);
        }

        @Override
        public void writeShort(Schema schema, short value) {
            beforeElement();
            SmithyJsonSerializer.this.writeShort(schema, value);
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            beforeElement();
            SmithyJsonSerializer.this.writeInteger(schema, value);
        }

        @Override
        public void writeLong(Schema schema, long value) {
            beforeElement();
            SmithyJsonSerializer.this.writeLong(schema, value);
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            beforeElement();
            SmithyJsonSerializer.this.writeFloat(schema, value);
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            beforeElement();
            SmithyJsonSerializer.this.writeDouble(schema, value);
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            beforeElement();
            SmithyJsonSerializer.this.writeBigInteger(schema, value);
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            beforeElement();
            SmithyJsonSerializer.this.writeBigDecimal(schema, value);
        }

        @Override
        public void writeString(Schema schema, String value) {
            beforeElement();
            SmithyJsonSerializer.this.writeString(schema, value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            beforeElement();
            SmithyJsonSerializer.this.writeBlob(schema, value);
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            beforeElement();
            SmithyJsonSerializer.this.writeTimestamp(schema, value);
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            beforeElement();
            SmithyJsonSerializer.this.writeStruct(schema, struct);
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            beforeElement();
            SmithyJsonSerializer.this.writeList(schema, listState, size, consumer);
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            beforeElement();
            SmithyJsonSerializer.this.writeMap(schema, mapState, size, consumer);
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            beforeElement();
            SmithyJsonSerializer.this.writeDocument(schema, value);
        }

        @Override
        public void writeNull(Schema schema) {
            beforeElement();
            SmithyJsonSerializer.this.writeNull(schema);
        }
    }

    // ---- Map serializer ----

    private final class SmithyMapSerializer implements MapSerializer {
        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            writeCommaIfNeeded();
            ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(key) + 1);
            pos = JsonWriteUtils.writeQuotedString(buf, pos, key);
            buf[pos++] = ':';
            valueSerializer.accept(state, SmithyJsonSerializer.this);
        }
    }

    // ---- Document struct serializer (writes __type) ----

    private static final class SerializeDocumentContents extends SpecificShapeSerializer {
        private final SmithyJsonSerializer parent;

        SerializeDocumentContents(SmithyJsonSerializer parent) {
            this.parent = parent;
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            parent.ensureCapacity(2);
            parent.buf[parent.pos++] = '{';
            parent.depth++;
            parent.needsComma[parent.depth] = false;
            if (parent.settings.serializeTypeInDocuments()) {
                parent.needsComma[parent.depth] = true;
                String typeValue = schema.id().toString();
                parent.ensureCapacity(JsonWriteUtils.maxQuotedStringBytes("__type")
                        + 1
                        + JsonWriteUtils.maxQuotedStringBytes(typeValue));
                parent.pos = JsonWriteUtils.writeQuotedString(parent.buf, parent.pos, "__type");
                parent.buf[parent.pos++] = ':';
                parent.pos = JsonWriteUtils.writeQuotedString(parent.buf, parent.pos, typeValue);
            }
            struct.serializeMembers(parent.structSerializer);
            parent.depth--;
            parent.buf[parent.pos++] = '}';
        }
    }
}
