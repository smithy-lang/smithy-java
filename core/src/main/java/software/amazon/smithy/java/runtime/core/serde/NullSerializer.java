/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

/**
 * Serializes nothing.
 *
 * @see ShapeSerializer#nullSerializer()
 */
final class NullSerializer implements ShapeSerializer {

    static final NullSerializer INSTANCE = new NullSerializer();

    @Override
    public <T> void writeStruct(SdkSchema schema, T structState, BiConsumer<T, ShapeSerializer> consumer) {}

    @Override
    public <T> void writeList(SdkSchema schema, T listState, BiConsumer<T, ShapeSerializer> consumer) {}

    @Override
    public <T> void writeMap(SdkSchema schema, T mapState, BiConsumer<T, MapSerializer> consumer) {}

    @Override
    public void writeBoolean(SdkSchema schema, boolean value) {}

    @Override
    public void writeByte(SdkSchema schema, byte value) {}

    @Override
    public void writeShort(SdkSchema schema, short value) {}

    @Override
    public void writeInteger(SdkSchema schema, int value) {}

    @Override
    public void writeLong(SdkSchema schema, long value) {}

    @Override
    public void writeFloat(SdkSchema schema, float value) {}

    @Override
    public void writeDouble(SdkSchema schema, double value) {}

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {}

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {}

    @Override
    public void writeString(SdkSchema schema, String value) {}

    @Override
    public void writeBlob(SdkSchema schema, byte[] value) {}

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {}

    @Override
    public void writeDocument(SdkSchema schema, Document value) {}

    @Override
    public void writeNull(SdkSchema schema) {}
}
