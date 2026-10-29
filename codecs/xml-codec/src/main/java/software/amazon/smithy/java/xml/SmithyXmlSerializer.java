/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.codecs.commons.NumberCodec;
import software.amazon.smithy.java.codecs.commons.OutputBuffers;
import software.amazon.smithy.java.codecs.commons.StripedPool;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

final class SmithyXmlSerializer extends InterceptingSerializer {

    private static final TimestampFormatTrait.Format DEFAULT_FORMAT = TimestampFormatTrait.Format.DATE_TIME;
    private static final int DEFAULT_BUF_SIZE = 4096;
    private static final int MAX_CACHEABLE_SCRATCH = DEFAULT_BUF_SIZE * 4;

    record AcquireContext(XmlNamespaceTrait defaultNamespace, XmlInfo xmlInfo, OutputStream sink, OutputBuffers pool) {}

    private static final StripedPool<SmithyXmlSerializer, AcquireContext> POOL = new XmlStripedPool();

    private byte[] buf;
    private byte[] base64Scratch;
    private int pos;
    private int sizeHint = DEFAULT_BUF_SIZE;
    private OutputStream sink;
    private OutputBuffers pool;
    private XmlNamespaceTrait defaultNamespace;
    private XmlInfo xmlInfo;

    private final StructElementSerializer structElementSerializer = new StructElementSerializer();
    private final StructAttributeSerializer structAttributeSerializer = new StructAttributeSerializer();
    private final ValueSerializer valueSerializer = new ValueSerializer();

    private byte[][] currentNameTable;
    private boolean[] currentIsAttributeTable;
    private boolean[] currentIsFlattenedTable;

    // When true, the opening tag is not yet closed with '>'.
    // writeStruct will close it after writing attributes; scalar writes close it immediately.
    private boolean pendingClose;

    private SmithyXmlSerializer(XmlNamespaceTrait defaultNamespace, XmlInfo xmlInfo, OutputBuffers pool) {
        this.defaultNamespace = defaultNamespace;
        this.xmlInfo = xmlInfo;
        this.pool = pool;
        this.buf = pool.acquire(DEFAULT_BUF_SIZE);
        this.pos = 0;
    }

    static SmithyXmlSerializer acquire(
            XmlNamespaceTrait defaultNamespace,
            XmlInfo xmlInfo,
            OutputStream sink,
            OutputBuffers pool
    ) {
        return POOL.acquire(new AcquireContext(defaultNamespace, xmlInfo, sink, pool));
    }

    static void release(SmithyXmlSerializer serializer) {
        POOL.release(serializer);
    }

    DataStream detach(String contentType) {
        byte[] detached = buf;
        int length = pos;
        sizeHint = OutputBuffers.nextSizeHint(sizeHint, length);
        buf = null;
        pos = 0;
        return DataStream.ofLeasedBytes(detached, 0, length, contentType, pool.recycler());
    }

    private void releaseBuffer() {
        if (buf != null) {
            pool.release(buf);
            buf = null;
        }
    }

    @Override
    public void flush() {
        try {
            if (sink != null && pos > 0) {
                sink.write(buf, 0, pos);
                pos = 0;
                sink.flush();
            }
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void close() {
        try {
            if (sink != null && pos > 0) {
                sink.write(buf, 0, pos);
                pos = 0;
            }
        } catch (IOException e) {
            throw new SerializationException(e);
        } finally {
            release(this);
        }
    }

    private void ensureCapacity(int needed) {
        if (pos + needed > buf.length) {
            grow(needed);
        }
    }

    private void grow(int needed) {
        buf = pool.grow(buf, pos, pos + needed);
    }

    private byte[] base64Scratch(int dataLen) {
        int capacity = ByteBufferUtils.base64EncodedSize(dataLen);
        byte[] scratch = base64Scratch;
        if (scratch == null || scratch.length < capacity) {
            scratch = base64Scratch = new byte[capacity];
        }
        return scratch;
    }

    @Override
    protected ShapeSerializer before(Schema schema) {
        byte[] nameBytes = resolveTopLevelName(schema);
        byte[] nsBytes = resolveTopLevelNamespace(schema);

        int nsLen = nsBytes != null ? nsBytes.length : 0;
        ensureCapacity(1 + nameBytes.length + nsLen);
        buf[pos++] = '<';
        System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
        pos += nameBytes.length;
        if (nsBytes != null) {
            System.arraycopy(nsBytes, 0, buf, pos, nsBytes.length);
            pos += nsBytes.length;
        }
        pendingClose = true;
        return valueSerializer;
    }

    @Override
    protected void after(Schema schema) {
        byte[] nameBytes = resolveTopLevelName(schema);
        writeCloseTag(nameBytes);
    }

    private byte[] resolveTopLevelName(Schema schema) {
        var trait = schema.getTrait(TraitKey.XML_NAME_TRAIT);
        if (trait != null) {
            return trait.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        var ext = getStructExtension(schema);
        if (ext != null) {
            return ext.structNameBytes();
        }
        if (schema.isMember()) {
            return schema.memberTarget().id().getName().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return schema.id().getName().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] resolveTopLevelNamespace(Schema schema) {
        var ext = getStructExtension(schema);
        if (ext != null && ext.namespaceBytes() != null) {
            return ext.namespaceBytes();
        }
        var ns = schema.getTrait(TraitKey.XML_NAMESPACE_TRAIT);
        if (ns == null) {
            ns = defaultNamespace;
        }
        if (ns != null) {
            return buildNamespaceBytes(ns);
        }
        return null;
    }

    private static XmlSchemaExtensions.StructExtension getStructExtension(Schema schema) {
        Schema target = schema.isMember() ? schema.memberTarget() : schema;
        var ext = target.getExtension(XmlSchemaExtensions.KEY);
        if (ext instanceof XmlSchemaExtensions.StructExtension se) {
            return se;
        }
        return null;
    }

    private static byte[] buildNamespaceBytes(XmlNamespaceTrait ns) {
        String prefix = ns.getPrefix().orElse(null);
        String uri = ns.getUri();
        String escapedUri = uri.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        if (prefix == null || prefix.isEmpty()) {
            return (" xmlns=\"" + escapedUri + "\"").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return (" xmlns:" + prefix + "=\"" + escapedUri + "\"").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private void writeOpenTag(byte[] nameBytes) {
        ensureCapacity(1 + nameBytes.length + 1);
        buf[pos++] = '<';
        System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
        pos += nameBytes.length;
        buf[pos++] = '>';
    }

    private void writeCloseTag(byte[] nameBytes) {
        ensureCapacity(3 + nameBytes.length);
        buf[pos++] = '<';
        buf[pos++] = '/';
        System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
        pos += nameBytes.length;
        buf[pos++] = '>';
    }

    private byte[] resolveMemberNameBytes(Schema schema) {
        byte[][] table = currentNameTable;
        int idx = schema.memberIndex();
        if (table != null && idx >= 0 && idx < table.length && table[idx] != null) {
            return table[idx];
        }
        var ext = schema.getExtension(XmlSchemaExtensions.KEY);
        if (ext instanceof XmlSchemaExtensions.MemberExtension me) {
            return me.nameBytes();
        }
        String name = schema.memberName();
        return name.getBytes(StandardCharsets.UTF_8);
    }

    private void closePendingTag() {
        if (pendingClose) {
            ensureCapacity(1);
            buf[pos++] = '>';
            pendingClose = false;
        }
    }

    private byte[] resolveMemberNamespaceBytes(Schema schema) {
        var ext = schema.getExtension(XmlSchemaExtensions.KEY);
        if (ext instanceof XmlSchemaExtensions.MemberExtension me) {
            return me.namespaceBytes();
        }
        var ns = schema.getDirectTrait(TraitKey.XML_NAMESPACE_TRAIT);
        if (ns != null) {
            return buildNamespaceBytes(ns);
        }
        return null;
    }

    private static String formatTimestamp(Schema schema, Instant value) {
        return TimestampFormatter.of(
                schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT),
                DEFAULT_FORMAT).writeString(value);
    }

    private static class XmlStripedPool extends StripedPool<SmithyXmlSerializer, AcquireContext> {
        @Override
        protected SmithyXmlSerializer create(AcquireContext ctx) {
            var s = new SmithyXmlSerializer(ctx.defaultNamespace(), ctx.xmlInfo(), ctx.pool());
            s.sink = ctx.sink();
            return s;
        }

        @Override
        protected void cleanup(SmithyXmlSerializer s) {
            s.sink = null;
            s.releaseBuffer();
            // Clear the client pool reference because idle serializers remain in a JVM-wide pool.
            s.pool = null;
        }

        @Override
        protected boolean canPool(SmithyXmlSerializer s) {
            return true;
        }

        @Override
        protected void prepareForPool(SmithyXmlSerializer s) {
            if (s.base64Scratch != null && s.base64Scratch.length > MAX_CACHEABLE_SCRATCH) {
                s.base64Scratch = null;
            }
        }

        @Override
        protected boolean reset(SmithyXmlSerializer s, AcquireContext ctx) {
            s.pos = 0;
            // A failed write can leave a pending '>'. Clear it before the next document.
            s.pendingClose = false;
            s.sink = ctx.sink();
            s.defaultNamespace = ctx.defaultNamespace();
            s.xmlInfo = ctx.xmlInfo();
            s.currentNameTable = null;
            s.currentIsAttributeTable = null;
            s.currentIsFlattenedTable = null;
            s.pool = ctx.pool();
            s.buf = s.pool.acquire(s.sizeHint);
            return true;
        }
    }

    private final class StructElementSerializer extends InterceptingSerializer {
        @Override
        protected ShapeSerializer before(Schema schema) {
            int idx = schema.memberIndex();
            if (currentIsAttributeTable != null && idx >= 0
                    && idx < currentIsAttributeTable.length
                    && currentIsAttributeTable[idx]) {
                return ShapeSerializer.nullSerializer();
            }
            if (currentIsFlattenedTable != null && idx >= 0
                    && idx < currentIsFlattenedTable.length
                    && currentIsFlattenedTable[idx]) {
                return valueSerializer;
            }
            byte[] nameBytes = resolveMemberNameBytes(schema);
            ensureCapacity(1 + nameBytes.length);
            buf[pos++] = '<';
            System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
            pos += nameBytes.length;
            byte[] nsBytes = resolveMemberNamespaceBytes(schema);
            if (nsBytes != null) {
                ensureCapacity(nsBytes.length);
                System.arraycopy(nsBytes, 0, buf, pos, nsBytes.length);
                pos += nsBytes.length;
            }
            pendingClose = true;
            return valueSerializer;
        }

        @Override
        protected void after(Schema schema) {
            int idx = schema.memberIndex();
            if (currentIsAttributeTable != null && idx >= 0
                    && idx < currentIsAttributeTable.length
                    && currentIsAttributeTable[idx]) {
                return;
            }
            if (currentIsFlattenedTable != null && idx >= 0
                    && idx < currentIsFlattenedTable.length
                    && currentIsFlattenedTable[idx]) {
                return;
            }
            byte[] nameBytes = resolveMemberNameBytes(schema);
            writeCloseTag(nameBytes);
        }
    }

    private final class StructAttributeSerializer extends InterceptingSerializer {
        @Override
        protected ShapeSerializer before(Schema schema) {
            int idx = schema.memberIndex();
            if (currentIsAttributeTable != null && idx >= 0
                    && idx < currentIsAttributeTable.length
                    && currentIsAttributeTable[idx]) {
                return new InlineAttributeSerializer(schema);
            }
            return ShapeSerializer.nullSerializer();
        }
    }

    private final class InlineAttributeSerializer extends SpecificShapeSerializer {
        private final byte[] nameBytes;

        InlineAttributeSerializer(Schema schema) {
            this.nameBytes = resolveMemberNameBytes(schema);
        }

        private void writeAttrPrefix() {
            ensureCapacity(1 + nameBytes.length + 2);
            buf[pos++] = ' ';
            System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
            pos += nameBytes.length;
            buf[pos++] = '=';
            buf[pos++] = '"';
        }

        private void writeAttrSuffix() {
            ensureCapacity(1);
            buf[pos++] = '"';
        }

        @Override
        public void writeString(Schema schema, String value) {
            writeAttrPrefix();
            ensureCapacity(XmlWriteUtils.maxEscapedAttributeBytes(value));
            pos = XmlWriteUtils.writeEscapedAttribute(buf, pos, value);
            writeAttrSuffix();
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            writeAttrPrefix();
            ensureCapacity(5);
            pos = NumberCodec.writeBoolean(buf, pos, value);
            writeAttrSuffix();
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            writeAttrPrefix();
            ensureCapacity(11);
            pos = NumberCodec.writeInt(buf, pos, value);
            writeAttrSuffix();
        }

        @Override
        public void writeLong(Schema schema, long value) {
            writeAttrPrefix();
            ensureCapacity(20);
            pos = NumberCodec.writeLong(buf, pos, value);
            writeAttrSuffix();
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            if (Float.isFinite(value)) {
                writeAttrPrefix();
                ensureCapacity(NumberCodec.FLOAT_MAX_BYTES);
                pos = NumberCodec.writeFloat(buf, pos, value);
                writeAttrSuffix();
            } else {
                writeAttrPrefix();
                ensureCapacity(9);
                pos = NumberCodec.writeNonFiniteFloat(buf, pos, value);
                writeAttrSuffix();
            }
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            if (Double.isFinite(value)) {
                writeAttrPrefix();
                ensureCapacity(NumberCodec.DOUBLE_MAX_BYTES);
                pos = NumberCodec.writeDouble(buf, pos, value);
                writeAttrSuffix();
            } else {
                writeAttrPrefix();
                ensureCapacity(9);
                pos = NumberCodec.writeNonFiniteDouble(buf, pos, value);
                writeAttrSuffix();
            }
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            writeString(schema, formatTimestamp(schema, value));
        }

        @Override
        public void writeNull(Schema schema) {}
    }

    private final class ValueSerializer implements ShapeSerializer {
        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            var ext = getStructExtension(schema);

            byte[][] savedNameTable = currentNameTable;
            boolean[] savedIsAttrTable = currentIsAttributeTable;
            boolean[] savedIsFlatTable = currentIsFlattenedTable;

            if (ext != null) {
                currentNameTable = ext.nameTable();
                currentIsAttributeTable = ext.isAttributeTable();
                currentIsFlattenedTable = ext.isFlattenedTable();

                if (ext.hasAttributes()) {
                    struct.serializeMembers(structAttributeSerializer);
                }
            } else {
                currentNameTable = null;
                currentIsAttributeTable = null;
                currentIsFlattenedTable = null;
            }

            closePendingTag();
            struct.serializeMembers(structElementSerializer);

            currentNameTable = savedNameTable;
            currentIsAttributeTable = savedIsAttrTable;
            currentIsFlattenedTable = savedIsFlatTable;
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            consumer.accept(listState, beginList(schema));
        }

        private ListItemSerializer beginList(Schema schema) {
            closePendingTag();
            var ext = schema.getExtension(XmlSchemaExtensions.KEY);
            byte[] memberNameBytes;
            if (ext instanceof XmlSchemaExtensions.ListExtension le) {
                memberNameBytes = le.memberNameBytes();
            } else {
                var info = xmlInfo.getListInfo(schema);
                memberNameBytes = info.memberName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            Schema listMember = schema.listMember();
            byte[] memberNsBytes = null;
            var ns = listMember.getDirectTrait(TraitKey.XML_NAMESPACE_TRAIT);
            if (ns != null) {
                memberNsBytes = buildNamespaceBytes(ns);
            }
            return new ListItemSerializer(memberNameBytes, memberNsBytes);
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            consumer.accept(mapState, beginMap(schema));
        }

        private XmlMapEntrySerializer beginMap(Schema schema) {
            closePendingTag();
            var ext = schema.getExtension(XmlSchemaExtensions.KEY);
            byte[] entryBytes, keyBytes, valueBytes;
            if (ext instanceof XmlSchemaExtensions.MapExtension(byte[] entryNameBytes, byte[] keyNameBytes, byte[] valueNameBytes)) {
                entryBytes = entryNameBytes;
                keyBytes = keyNameBytes;
                valueBytes = valueNameBytes;
            } else {
                var info = xmlInfo.getMapInfo(schema);
                entryBytes = info.entryName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                keyBytes = info.keyName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                valueBytes = info.valueName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            byte[] keyNsBytes = null;
            byte[] valueNsBytes = null;
            Schema keyMember = schema.mapKeyMember();
            Schema valueMember = schema.mapValueMember();
            var keyNs = keyMember.getDirectTrait(TraitKey.XML_NAMESPACE_TRAIT);
            if (keyNs != null) {
                keyNsBytes = buildNamespaceBytes(keyNs);
            }
            var valueNs = valueMember.getDirectTrait(TraitKey.XML_NAMESPACE_TRAIT);
            if (valueNs != null) {
                valueNsBytes = buildNamespaceBytes(valueNs);
            }
            return new XmlMapEntrySerializer(entryBytes, keyBytes, valueBytes, keyNsBytes, valueNsBytes);
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            closePendingTag();
            ensureCapacity(5);
            pos = NumberCodec.writeBoolean(buf, pos, value);
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            closePendingTag();
            ensureCapacity(4);
            pos = NumberCodec.writeInt(buf, pos, value);
        }

        @Override
        public void writeShort(Schema schema, short value) {
            closePendingTag();
            ensureCapacity(6);
            pos = NumberCodec.writeInt(buf, pos, value);
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            closePendingTag();
            ensureCapacity(11);
            pos = NumberCodec.writeInt(buf, pos, value);
        }

        @Override
        public void writeLong(Schema schema, long value) {
            closePendingTag();
            ensureCapacity(20);
            pos = NumberCodec.writeLong(buf, pos, value);
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            closePendingTag();
            ensureCapacity(NumberCodec.FLOAT_MAX_BYTES);
            pos = NumberCodec.writeFloatFull(buf, pos, value);
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            closePendingTag();
            ensureCapacity(NumberCodec.DOUBLE_MAX_BYTES);
            pos = NumberCodec.writeDoubleFull(buf, pos, value);
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            closePendingTag();
            ensureCapacity(value.bitLength() / 3 + 2);
            pos = NumberCodec.writeBigInteger(buf, pos, value);
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            closePendingTag();
            String s = value.toString();
            ensureCapacity(s.length());
            pos = writeAsciiString(buf, pos, s);
        }

        @SuppressWarnings("deprecation")
        private static int writeAsciiString(byte[] buf, int pos, String s) {
            int len = s.length();
            s.getBytes(0, len, buf, pos);
            return pos + len;
        }

        @Override
        public void writeString(Schema schema, String value) {
            closePendingTag();
            ensureCapacity(XmlWriteUtils.maxEscapedTextBytes(value));
            pos = XmlWriteUtils.writeEscapedText(buf, pos, value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            closePendingTag();
            int remaining = value.remaining();
            ensureCapacity(ByteBufferUtils.base64EncodedSize(remaining));
            pos += ByteBufferUtils.base64EncodeTo(value, buf, pos, base64Scratch(remaining));
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            closePendingTag();
            writeTextContent(formatTimestamp(schema, value));
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            closePendingTag();
        }

        @Override
        public void writeNull(Schema schema) {
            closePendingTag();
        }

        private void writeTextContent(String value) {
            ensureCapacity(XmlWriteUtils.maxEscapedTextBytes(value));
            pos = XmlWriteUtils.writeEscapedText(buf, pos, value);
        }

        @Override
        public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeBoolean(memberSchema, v);
                }
            } else {
                for (var v : values) {
                    e.writeBoolean(memberSchema, v);
                }
            }

        }

        @Override
        public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeBoolean(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeBoolean(memberSchema, v);
                    }
                }
            }

        }

        @Override
        public void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeBoolean(valueSchema, v);
                m.endEntry();
            });

        }

        @Override
        public void writeSparseBooleanMap(
                Schema schema,
                Map<String, Boolean> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeBoolean(valueSchema, v);
                }
                m.endEntry();
            });

        }

        @Override
        public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeByte(memberSchema, v);
                }
            } else {
                for (var v : values) {
                    e.writeByte(memberSchema, v);
                }
            }

        }

        @Override
        public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeByte(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeByte(memberSchema, v);
                    }
                }
            }

        }

        @Override
        public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeByte(valueSchema, v);
                m.endEntry();
            });

        }

        @Override
        public void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeByte(valueSchema, v);
                }
                m.endEntry();
            });

        }

        @Override
        public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeShort(memberSchema, v);
                }
            } else {
                for (var v : values) {
                    e.writeShort(memberSchema, v);
                }
            }

        }

        @Override
        public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeShort(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeShort(memberSchema, v);
                    }
                }
            }

        }

        @Override
        public void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeShort(valueSchema, v);
                m.endEntry();
            });

        }

        @Override
        public void writeSparseShortMap(
                Schema schema,
                Map<String, Short> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeShort(valueSchema, v);
                }
                m.endEntry();
            });

        }

        @Override
        public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeInteger(memberSchema, v);
                }
            } else {
                for (var v : values) {
                    e.writeInteger(memberSchema, v);
                }
            }

        }

        @Override
        public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeInteger(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeInteger(memberSchema, v);
                    }
                }
            }

        }

        @Override
        public void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeInteger(valueSchema, v);
                m.endEntry();
            });

        }

        @Override
        public void writeSparseIntegerMap(
                Schema schema,
                Map<String, Integer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeInteger(valueSchema, v);
                }
                m.endEntry();
            });

        }

        @Override
        public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeLong(memberSchema, v);
                }
            } else {
                for (var v : values) {
                    e.writeLong(memberSchema, v);
                }
            }

        }

        @Override
        public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeLong(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeLong(memberSchema, v);
                    }
                }
            }

        }

        @Override
        public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeLong(valueSchema, v);
                m.endEntry();
            });

        }

        @Override
        public void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeLong(valueSchema, v);
                }
                m.endEntry();
            });

        }

        @Override
        public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeFloat(memberSchema, v);
                }
            } else {
                for (var v : values) {
                    e.writeFloat(memberSchema, v);
                }
            }

        }

        @Override
        public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeFloat(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeFloat(memberSchema, v);
                    }
                }
            }

        }

        @Override
        public void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeFloat(valueSchema, v);
                m.endEntry();
            });

        }

        @Override
        public void writeSparseFloatMap(
                Schema schema,
                Map<String, Float> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeFloat(valueSchema, v);
                }
                m.endEntry();
            });

        }

        @Override
        public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeDouble(memberSchema, v);
                }
            } else {
                for (var v : values) {
                    e.writeDouble(memberSchema, v);
                }
            }

        }

        @Override
        public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeDouble(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeDouble(memberSchema, v);
                    }
                }
            }

        }

        @Override
        public void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeDouble(valueSchema, v);
                m.endEntry();
            });

        }

        @Override
        public void writeSparseDoubleMap(
                Schema schema,
                Map<String, Double> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeDouble(valueSchema, v);
                }
                m.endEntry();
            });

        }

        @Override
        public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeBigInteger(memberSchema, v);
                }
            } else {
                for (var v : values) {
                    e.writeBigInteger(memberSchema, v);
                }
            }

        }

        @Override
        public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeBigInteger(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeBigInteger(memberSchema, v);
                    }
                }
            }

        }

        @Override
        public void writeBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeBigInteger(valueSchema, v);
                m.endEntry();
            });

        }

        @Override
        public void writeSparseBigIntegerMap(
                Schema schema,
                Map<String, BigInteger> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeBigInteger(valueSchema, v);
                }
                m.endEntry();
            });

        }

        @Override
        public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeBigDecimal(memberSchema, v);
                }
            } else {
                for (var v : values) {
                    e.writeBigDecimal(memberSchema, v);
                }
            }

        }

        @Override
        public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeBigDecimal(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeBigDecimal(memberSchema, v);
                    }
                }
            }

        }

        @Override
        public void writeBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeBigDecimal(valueSchema, v);
                m.endEntry();
            });

        }

        @Override
        public void writeSparseBigDecimalMap(
                Schema schema,
                Map<String, BigDecimal> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeBigDecimal(valueSchema, v);
                }
                m.endEntry();
            });

        }

        @Override
        public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeString(memberSchema, v);
                }
            } else {
                for (var v : values) {
                    e.writeString(memberSchema, v);
                }
            }

        }

        @Override
        public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeString(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeString(memberSchema, v);
                    }
                }
            }

        }

        @Override
        public void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeString(valueSchema, v);
                m.endEntry();
            });

        }

        @Override
        public void writeSparseStringMap(
                Schema schema,
                Map<String, String> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeString(valueSchema, v);
                }
                m.endEntry();
            });

        }

        @Override
        public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeBlob(memberSchema, v);
                }
            } else {
                for (var v : values) {
                    e.writeBlob(memberSchema, v);
                }
            }

        }

        @Override
        public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeBlob(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeBlob(memberSchema, v);
                    }
                }
            }

        }

        @Override
        public void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeBlob(valueSchema, v);
                m.endEntry();
            });

        }

        @Override
        public void writeSparseBlobMap(
                Schema schema,
                Map<String, ByteBuffer> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeBlob(valueSchema, v);
                }
                m.endEntry();
            });

        }

        @Override
        public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeTimestamp(memberSchema, v);
                }
            } else {
                for (var v : values) {
                    e.writeTimestamp(memberSchema, v);
                }
            }

        }

        @Override
        public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeTimestamp(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeTimestamp(memberSchema, v);
                    }
                }
            }

        }

        @Override
        public void writeTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeTimestamp(valueSchema, v);
                m.endEntry();
            });

        }

        @Override
        public void writeSparseTimestampMap(
                Schema schema,
                Map<String, Instant> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeTimestamp(valueSchema, v);
                }
                m.endEntry();
            });

        }

        @Override
        public void writeDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeDocument(memberSchema, v);
                }
            } else {
                for (var v : values) {
                    e.writeDocument(memberSchema, v);
                }
            }

        }

        @Override
        public void writeSparseDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeDocument(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeDocument(memberSchema, v);
                    }
                }
            }

        }

        @Override
        public void writeDocumentMap(
                Schema schema,
                Map<String, Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeDocument(valueSchema, v);
                m.endEntry();
            });

        }

        @Override
        public void writeSparseDocumentMap(
                Schema schema,
                Map<String, Document> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeDocument(valueSchema, v);
                }
                m.endEntry();
            });

        }

        @Override
        public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeString(memberSchema, v.getValue());
                }
            } else {
                for (var v : values) {
                    e.writeString(memberSchema, v.getValue());
                }
            }

        }

        @Override
        public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeString(memberSchema, v.getValue());
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeString(memberSchema, v.getValue());
                    }
                }
            }

        }

        @Override
        public void writeEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeString(valueSchema, v.getValue());
                m.endEntry();
            });

        }

        @Override
        public void writeSparseEnumMap(
                Schema schema,
                Map<String, ? extends SmithyEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeString(valueSchema, v.getValue());
                }
                m.endEntry();
            });

        }

        @Override
        public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeInteger(memberSchema, v.getValue());
                }
            } else {
                for (var v : values) {
                    e.writeInteger(memberSchema, v.getValue());
                }
            }

        }

        @Override
        public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeInteger(memberSchema, v.getValue());
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeInteger(memberSchema, v.getValue());
                    }
                }
            }

        }

        @Override
        public void writeIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeInteger(valueSchema, v.getValue());
                m.endEntry();
            });

        }

        @Override
        public void writeSparseIntEnumMap(
                Schema schema,
                Map<String, ? extends SmithyIntEnum> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeInteger(valueSchema, v.getValue());
                }
                m.endEntry();
            });

        }

        @Override
        public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    e.writeStruct(memberSchema, v);
                }
            } else {
                for (var v : values) {
                    e.writeStruct(memberSchema, v);
                }
            }

        }

        @Override
        public void writeSparseStructList(
                Schema schema,
                List<? extends SerializableStruct> values,
                Schema memberSchema
        ) {
            var e = beginList(schema);
            if (values instanceof RandomAccess) {
                for (int i = 0, n = values.size(); i < n; i++) {
                    var v = values.get(i);
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeStruct(memberSchema, v);
                    }
                }
            } else {
                for (var v : values) {
                    if (v == null) {
                        e.writeNull(memberSchema);
                    } else {
                        e.writeStruct(memberSchema, v);
                    }
                }
            }

        }

        @Override
        public void writeStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                SmithyXmlSerializer.this.valueSerializer.writeStruct(valueSchema, v);
                m.endEntry();
            });

        }

        @Override
        public void writeSparseStructMap(
                Schema schema,
                Map<String, ? extends SerializableStruct> values,
                Schema keySchema,
                Schema valueSchema
        ) {
            var m = beginMap(schema);
            values.forEach((k, v) -> {
                m.beginEntry(k);
                if (v == null) {
                    SmithyXmlSerializer.this.valueSerializer.writeNull(valueSchema);
                } else {
                    SmithyXmlSerializer.this.valueSerializer.writeStruct(valueSchema, v);
                }
                m.endEntry();
            });

        }
    }

    private final class ListItemSerializer extends InterceptingSerializer {
        private final byte[] memberNameBytes;
        private final byte[] nsBytes;

        ListItemSerializer(byte[] memberNameBytes, byte[] nsBytes) {
            this.memberNameBytes = memberNameBytes;
            this.nsBytes = nsBytes;
        }

        @Override
        protected ShapeSerializer before(Schema schema) {
            int nsLen = nsBytes != null ? nsBytes.length : 0;
            ensureCapacity(1 + memberNameBytes.length + nsLen);
            buf[pos++] = '<';
            System.arraycopy(memberNameBytes, 0, buf, pos, memberNameBytes.length);
            pos += memberNameBytes.length;
            if (nsBytes != null) {
                System.arraycopy(nsBytes, 0, buf, pos, nsBytes.length);
                pos += nsBytes.length;
            }
            pendingClose = true;
            return valueSerializer;
        }

        @Override
        protected void after(Schema schema) {
            writeCloseTag(memberNameBytes);
        }
    }

    private final class XmlMapEntrySerializer implements MapSerializer {
        private final byte[] entryBytes;
        private final byte[] keyBytes;
        private final byte[] valueBytes;
        private final byte[] keyNsBytes;
        private final byte[] valueNsBytes;

        XmlMapEntrySerializer(
                byte[] entryBytes,
                byte[] keyBytes,
                byte[] valueBytes,
                byte[] keyNsBytes,
                byte[] valueNsBytes
        ) {
            this.entryBytes = entryBytes;
            this.keyBytes = keyBytes;
            this.valueBytes = valueBytes;
            this.keyNsBytes = keyNsBytes;
            this.valueNsBytes = valueNsBytes;
        }

        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            beginEntry(key);
            valueSerializer.accept(state, SmithyXmlSerializer.this.valueSerializer);
            endEntry();
        }

        void beginEntry(String key) {
            writeOpenTag(entryBytes);

            writeTagWithNsClosed(keyBytes, keyNsBytes);
            ensureCapacity(XmlWriteUtils.maxEscapedTextBytes(key));
            pos = XmlWriteUtils.writeEscapedText(buf, pos, key);
            writeCloseTag(keyBytes);

            writeTagWithNsOpen(valueBytes, valueNsBytes);
            pendingClose = true;
        }

        void endEntry() {
            writeCloseTag(valueBytes);
            writeCloseTag(entryBytes);
        }

        private void writeTagWithNsClosed(byte[] nameBytes, byte[] nsBytes) {
            int nsLen = nsBytes != null ? nsBytes.length : 0;
            ensureCapacity(1 + nameBytes.length + nsLen + 1);
            buf[pos++] = '<';
            System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
            pos += nameBytes.length;
            if (nsBytes != null) {
                System.arraycopy(nsBytes, 0, buf, pos, nsBytes.length);
                pos += nsBytes.length;
            }
            buf[pos++] = '>';
        }

        private void writeTagWithNsOpen(byte[] nameBytes, byte[] nsBytes) {
            int nsLen = nsBytes != null ? nsBytes.length : 0;
            ensureCapacity(1 + nameBytes.length + nsLen);
            buf[pos++] = '<';
            System.arraycopy(nameBytes, 0, buf, pos, nameBytes.length);
            pos += nameBytes.length;
            if (nsBytes != null) {
                System.arraycopy(nsBytes, 0, buf, pos, nsBytes.length);
                pos += nsBytes.length;
            }
        }
    }
}
