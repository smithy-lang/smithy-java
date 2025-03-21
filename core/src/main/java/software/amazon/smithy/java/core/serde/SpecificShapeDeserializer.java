/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.datastream.DataStream;

public abstract class SpecificShapeDeserializer implements ShapeDeserializer {

    /**
     * Invoked when an unexpected shape is encountered.
     *
     * @param schema Unexpected encountered schema.
     * @return Returns an exception to throw.
     */
    protected RuntimeException throwForInvalidState(Schema schema) {
        return new IllegalStateException("Unexpected schema type: " + schema);
    }

    @Override
    public ByteBuffer readBlob(Schema schema) {
        throw throwForInvalidState(schema);
    }

    @Override

    public byte readByte(Schema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public short readShort(Schema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public int readInteger(Schema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public long readLong(Schema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public float readFloat(Schema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public double readDouble(Schema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public String readString(Schema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public boolean readBoolean(Schema schema) {
        return false;
    }

    @Override
    public Document readDocument() {
        throw throwForInvalidState(PreludeSchemas.DOCUMENT);
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
        throw throwForInvalidState(schema);
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
        throw throwForInvalidState(schema);
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
        throw throwForInvalidState(schema);
    }

    @Override
    public boolean isNull() {
        throw new UnsupportedOperationException("cannot look ahead for null values");
    }

    @Override
    public Flow.Publisher<? extends SerializableStruct> readEventStream(Schema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public DataStream readDataStream(Schema schema) {
        throw throwForInvalidState(schema);
    }
}
