/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * A no-op {@link ShapeDeserializer} for empty XML payloads.
 */
final class EmptyXmlDeserializer implements ShapeDeserializer {

    static final EmptyXmlDeserializer INSTANCE = new EmptyXmlDeserializer();

    private EmptyXmlDeserializer() {}

    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {}

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {}

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {}

    // Scalar reads are not valid at the top level of an empty XML body.
    @Override
    public boolean readBoolean(Schema schema) {
        throw empty(schema);
    }

    @Override
    public ByteBuffer readBlob(Schema schema) {
        throw empty(schema);
    }

    @Override
    public byte readByte(Schema schema) {
        throw empty(schema);
    }

    @Override
    public short readShort(Schema schema) {
        throw empty(schema);
    }

    @Override
    public int readInteger(Schema schema) {
        throw empty(schema);
    }

    @Override
    public long readLong(Schema schema) {
        throw empty(schema);
    }

    @Override
    public float readFloat(Schema schema) {
        throw empty(schema);
    }

    @Override
    public double readDouble(Schema schema) {
        throw empty(schema);
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        throw empty(schema);
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        throw empty(schema);
    }

    @Override
    public String readString(Schema schema) {
        throw empty(schema);
    }

    @Override
    public Document readDocument() {
        throw new SerializationException("Cannot read a document value from an empty XML payload");
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        throw empty(schema);
    }

    private static SerializationException empty(Schema schema) {
        return new SerializationException("Cannot read " + schema.id() + " value from an empty XML payload");
    }
}
