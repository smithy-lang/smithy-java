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
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class HttpQuerySerializer extends SpecificShapeSerializer {

    private final BiConsumer<String, String> queryWriter;

    public HttpQuerySerializer(BiConsumer<String, String> queryWriter) {
        this.queryWriter = queryWriter;
    }

    @Override
    public void writeList(Schema schema, Consumer<ShapeSerializer> consumer) {
        consumer.accept(new ListSerializer(this, position -> {}));
    }

    void writeQuery(Schema schema, Supplier<String> supplier) {
        var queryTrait = schema.getTrait(HttpQueryTrait.class);
        if (queryTrait != null) {
            queryWriter.accept(queryTrait.getValue(), supplier.get());
        }
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        writeQuery(schema, () -> value ? "true" : "false");
    }

    @Override
    public void writeShort(Schema schema, short value) {
        writeQuery(schema, () -> Short.toString(value));
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        writeQuery(schema, () -> Byte.toString(value));
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        writeQuery(schema, () -> Integer.toString(value));
    }

    @Override
    public void writeLong(Schema schema, long value) {
        writeQuery(schema, () -> Long.toString(value));
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        writeQuery(schema, () -> Float.toString(value));
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        writeQuery(schema, () -> Double.toString(value));
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        writeQuery(schema, value::toString);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        writeQuery(schema, value::toString);
    }

    @Override
    public void writeString(Schema schema, String value) {
        writeQuery(schema, () -> value);
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        writeQuery(schema, () -> Base64.getEncoder().encodeToString(value));
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        var trait = schema.getTrait(TimestampFormatTrait.class);
        TimestampFormatter formatter = trait != null
            ? TimestampFormatter.of(trait)
            : TimestampFormatter.Prelude.DATE_TIME;
        writeQuery(schema, () -> formatter.formatToString(value));
    }
}
