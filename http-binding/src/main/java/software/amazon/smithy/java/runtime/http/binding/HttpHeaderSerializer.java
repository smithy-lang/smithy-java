/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import static software.amazon.smithy.java.runtime.io.ByteBufferUtils.base64Encode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
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
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        // Consumer is generally going to be something generic - like a shared serializer for iterating
        // lists of strings and writing them back out to the delegate serializer (this). However, this
        // means writeHeader, below, will receive something like the schema for smithy.api#String
        // which does not have a httpHeader trait. So we wrap this in SpecificHttpHeaderSerializer,
        // which will use the header member's schema, and not the schema of the type we're writing
        // to call writeHeader
        consumer.accept(
            listState,
            new ListSerializer(new SpecificHttpHeaderSerializer(schema, this), HttpHeaderSerializer::noOpPosition)
        );
    }

    private static void noOpPosition(int position) {}

    private void writeHeader(Schema schema, String value) {
        if (value != null) {
            var headerTrait = schema.getTrait(HttpHeaderTrait.class);
            var field = headerTrait != null ? headerTrait.getValue() : schema.memberName();
            headerWriter.accept(field, value);
        }
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        writeHeader(schema, Boolean.toString(value));
    }

    @Override
    public void writeShort(Schema schema, short value) {
        writeHeader(schema, Short.toString(value));
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        writeHeader(schema, Byte.toString(value));
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        writeHeader(schema, Integer.toString(value));
    }

    @Override
    public void writeLong(Schema schema, long value) {
        writeHeader(schema, Long.toString(value));
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        writeHeader(schema, Float.toString(value));
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        writeHeader(schema, Double.toString(value));
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        writeHeader(schema, value.toString());
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        writeHeader(schema, value.toString());
    }

    @Override
    public void writeString(Schema schema, String value) {
        writeHeader(schema, value);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        writeHeader(schema, base64Encode(value));
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        var trait = schema.getTrait(TimestampFormatTrait.class);
        TimestampFormatter formatter = trait != null
            ? TimestampFormatter.of(trait)
            : TimestampFormatter.Prelude.HTTP_DATE;
        writeHeader(schema, formatter.writeString(value));
    }
}
