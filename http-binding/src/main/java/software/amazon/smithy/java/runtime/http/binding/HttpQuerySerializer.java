/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ListSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class HttpQuerySerializer extends SpecificShapeSerializer {

    private final BiConsumer<String, String> queryWriter;

    public HttpQuerySerializer(BiConsumer<String, String> queryWriter) {
        this.queryWriter = queryWriter;
    }

    @Override
    protected RuntimeException throwForInvalidState(SdkSchema schema) {
        throw new UnsupportedOperationException(schema + " is not supported in HTTP query");
    }

    @Override
    public void beginList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        consumer.accept(new ListSerializer(this, position -> {}));
    }

    void writeQuery(SdkSchema schema, Supplier<String> supplier) {
        var queryTrait = schema.getTrait(HttpQueryTrait.class);
        if (queryTrait != null) {
            queryWriter.accept(queryTrait.getValue(), supplier.get());
        }
    }

    @Override
    public void writeBoolean(SdkSchema schema, boolean value) {
        writeQuery(schema, () -> value ? "true" : "false");
    }

    @Override
    public void writeShort(SdkSchema schema, short value) {
        writeQuery(schema, () -> Short.toString(value));
    }

    @Override
    public void writeByte(SdkSchema schema, byte value) {
        writeQuery(schema, () -> Byte.toString(value));
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        writeQuery(schema, () -> Integer.toString(value));
    }

    @Override
    public void writeLong(SdkSchema schema, long value) {
        writeQuery(schema, () -> Long.toString(value));
    }

    @Override
    public void writeFloat(SdkSchema schema, float value) {
        writeQuery(schema, () -> Float.toString(value));
    }

    @Override
    public void writeDouble(SdkSchema schema, double value) {
        writeQuery(schema, () -> Double.toString(value));
    }

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {
        writeQuery(schema, value::toString);
    }

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        writeQuery(schema, value::toString);
    }

    @Override
    public void writeString(SdkSchema schema, String value) {
        writeQuery(schema, () -> value);
    }

    @Override
    public void writeBlob(SdkSchema schema, byte[] value) {
        writeQuery(schema, () -> Base64.getEncoder().encodeToString(value));
    }

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {
        var trait = schema.getTrait(TimestampFormatTrait.class);
        TimestampFormatter formatter = trait != null
            ? TimestampFormatter.of(trait)
            : TimestampFormatter.Prelude.DATE_TIME;
        writeQuery(schema, () -> formatter.formatToString(value));
    }
}
