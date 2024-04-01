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
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class HttpHeaderSerializer extends SpecificShapeSerializer {

    private final BiConsumer<String, String> headerWriter;

    public HttpHeaderSerializer(BiConsumer<String, String> headerWriter) {
        this.headerWriter = headerWriter;
    }

    @Override
    protected RuntimeException throwForInvalidState(SdkSchema schema) {
        throw new UnsupportedOperationException(schema + " is not supported in HTTP headers");
    }

    @Override
    public void beginList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        consumer.accept(new ListSerializer(this, () -> {
        }));
    }

    void writeHeader(SdkSchema schema, Supplier<String> supplier) {
        String field = schema.getTrait(HttpHeaderTrait.class)
                .map(HttpHeaderTrait::getValue)
                .orElse(schema.memberName());
        headerWriter.accept(field, supplier.get());
    }

    @Override
    public void writeBoolean(SdkSchema schema, boolean value) {
        writeHeader(schema, () -> value ? "true" : "false");
    }

    @Override
    public void writeShort(SdkSchema schema, short value) {
        writeHeader(schema, () -> Short.toString(value));
    }

    @Override
    public void writeByte(SdkSchema schema, byte value) {
        writeHeader(schema, () -> Byte.toString(value));
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        writeHeader(schema, () -> Integer.toString(value));
    }

    @Override
    public void writeLong(SdkSchema schema, long value) {
        writeHeader(schema, () -> Long.toString(value));
    }

    @Override
    public void writeFloat(SdkSchema schema, float value) {
        writeHeader(schema, () -> Float.toString(value));
    }

    @Override
    public void writeDouble(SdkSchema schema, double value) {
        writeHeader(schema, () -> Double.toString(value));
    }

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {
        writeHeader(schema, value::toString);
    }

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        writeHeader(schema, value::toString);
    }

    @Override
    public void writeString(SdkSchema schema, String value) {
        writeHeader(schema, () -> value);
    }

    @Override
    public void writeBlob(SdkSchema schema, byte[] value) {
        writeHeader(schema, () -> Base64.getEncoder().encodeToString(value));
    }

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {
        writeHeader(
                schema,
                () -> schema.getTrait(TimestampFormatTrait.class)
                        .map(TimestampFormatter::of)
                        .orElse(TimestampFormatter.Prelude.HTTP_DATE)
                        .formatToString(value)
        );
    }
}
