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
import software.amazon.smithy.java.runtime.core.schema.Schema;
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
    public void writeList(Schema schema, Consumer<ShapeSerializer> consumer) {
        consumer.accept(new ListSerializer(this, position -> {}));
    }

    void writeHeader(Schema schema, Supplier<String> supplier) {
        var headerTrait = schema.getTrait(HttpHeaderTrait.class);
        var field = headerTrait != null ? headerTrait.getValue() : schema.memberName();
        headerWriter.accept(field, supplier.get());
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        writeHeader(schema, () -> value ? "true" : "false");
    }

    @Override
    public void writeShort(Schema schema, short value) {
        writeHeader(schema, () -> Short.toString(value));
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        writeHeader(schema, () -> Byte.toString(value));
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        writeHeader(schema, () -> Integer.toString(value));
    }

    @Override
    public void writeLong(Schema schema, long value) {
        writeHeader(schema, () -> Long.toString(value));
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        writeHeader(schema, () -> Float.toString(value));
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        writeHeader(schema, () -> Double.toString(value));
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        writeHeader(schema, value::toString);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        writeHeader(schema, value::toString);
    }

    @Override
    public void writeString(Schema schema, String value) {
        writeHeader(schema, () -> value);
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        writeHeader(schema, () -> Base64.getEncoder().encodeToString(value));
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        writeHeader(
            schema,
            () -> {
                var trait = schema.getTrait(TimestampFormatTrait.class);
                TimestampFormatter formatter = trait != null
                    ? TimestampFormatter.of(trait)
                    : TimestampFormatter.Prelude.HTTP_DATE;
                return formatter.formatToString(value);
            }
        );
    }
}
