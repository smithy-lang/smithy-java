/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

/**
 * Thin wrapper that defers pooled serializer acquisition until a write is actually performed.
 * This avoids atomic pool operations when the serializer is created but never used (e.g., blob payloads).
 */
final class LazyXmlSerializer implements ShapeSerializer {

    private final XmlNamespaceTrait defaultNamespace;
    private final XmlInfo xmlInfo;
    private final OutputStream sink;
    private SmithyXmlSerializer delegate;

    LazyXmlSerializer(XmlNamespaceTrait defaultNamespace, XmlInfo xmlInfo, OutputStream sink) {
        this.defaultNamespace = defaultNamespace;
        this.xmlInfo = xmlInfo;
        this.sink = sink;
    }

    private SmithyXmlSerializer delegate() {
        if (delegate == null) {
            delegate = SmithyXmlSerializer.acquire(defaultNamespace, xmlInfo, sink);
        }
        return delegate;
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        delegate().writeStruct(schema, struct);
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        delegate().writeList(schema, listState, size, consumer);
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        delegate().writeMap(schema, mapState, size, consumer);
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        delegate().writeBoolean(schema, value);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        delegate().writeByte(schema, value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        delegate().writeShort(schema, value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        delegate().writeInteger(schema, value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        delegate().writeLong(schema, value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        delegate().writeFloat(schema, value);
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        delegate().writeDouble(schema, value);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        delegate().writeBigInteger(schema, value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        delegate().writeBigDecimal(schema, value);
    }

    @Override
    public void writeString(Schema schema, String value) {
        delegate().writeString(schema, value);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        delegate().writeBlob(schema, value);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        delegate().writeTimestamp(schema, value);
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        delegate().writeDocument(schema, value);
    }

    @Override
    public void writeNull(Schema schema) {
        delegate().writeNull(schema);
    }

    @Override
    public void flush() {
        if (delegate != null) {
            delegate.flush();
        }
    }

    @Override
    public void close() {
        if (delegate != null) {
            delegate.close();
        }
    }
}
