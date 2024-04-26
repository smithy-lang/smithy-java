/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

public abstract class SpecificShapeDeserializer implements ShapeDeserializer {

    /**
     * Invoked when an unexpected shape is encountered.
     *
     * @param schema Unexpected encountered schema.
     * @return Returns an exception to throw.
     */
    protected abstract RuntimeException throwForInvalidState(SdkSchema schema);

    @Override
    public byte[] readBlob(SdkSchema schema) {
        throw throwForInvalidState(schema);
    }

    @Override

    public byte readByte(SdkSchema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public short readShort(SdkSchema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public int readInteger(SdkSchema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public long readLong(SdkSchema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public float readFloat(SdkSchema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public double readDouble(SdkSchema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public BigInteger readBigInteger(SdkSchema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public BigDecimal readBigDecimal(SdkSchema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public String readString(SdkSchema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public boolean readBoolean(SdkSchema schema) {
        return false;
    }

    @Override
    public Document readDocument() {
        throw throwForInvalidState(PreludeSchemas.DOCUMENT);
    }

    @Override
    public Instant readTimestamp(SdkSchema schema) {
        throw throwForInvalidState(schema);
    }

    @Override
    public <T> void readStruct(SdkSchema schema, T state, StructMemberConsumer<T> consumer) {
        throw throwForInvalidState(schema);
    }

    @Override
    public <T> void readList(SdkSchema schema, T state, ListMemberConsumer<T> consumer) {
        throw throwForInvalidState(schema);
    }

    @Override
    public <T> void readStringMap(SdkSchema schema, T state, MapMemberConsumer<String, T> consumer) {
        throw throwForInvalidState(schema);
    }
}
