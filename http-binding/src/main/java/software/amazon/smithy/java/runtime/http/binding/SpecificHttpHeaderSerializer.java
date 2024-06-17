/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;

final class SpecificHttpHeaderSerializer extends SpecificShapeSerializer {

    private final Schema headerSchema;
    private final HttpHeaderSerializer delegate;

    public SpecificHttpHeaderSerializer(Schema headerSchema, HttpHeaderSerializer delegate) {
        this.headerSchema = headerSchema;
        this.delegate = delegate;
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        delegate.writeBoolean(headerSchema, value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        delegate.writeShort(headerSchema, value);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        delegate.writeByte(headerSchema, value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        delegate.writeInteger(headerSchema, value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        delegate.writeLong(headerSchema, value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        delegate.writeFloat(headerSchema, value);
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        delegate.writeDouble(headerSchema, value);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        delegate.writeBigInteger(headerSchema, value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        delegate.writeBigDecimal(headerSchema, value);
    }

    @Override
    public void writeString(Schema schema, String value) {
        delegate.writeString(headerSchema, value);
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        delegate.writeBlob(headerSchema, value);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        delegate.writeTimestamp(headerSchema, value);
    }
}
