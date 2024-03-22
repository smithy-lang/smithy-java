/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class HttpLabelSerializer extends SpecificShapeSerializer {

    private final BiConsumer<String, String> labelReceiver;

    HttpLabelSerializer(BiConsumer<String, String> labelReceiver) {
        this.labelReceiver = Objects.requireNonNull(labelReceiver);
    }

    @Override
    protected RuntimeException throwForInvalidState(SdkSchema schema) {
        throw new UnsupportedOperationException(schema + " is not supported in HTTP labels");
    }

    @Override
    public void writeBoolean(SdkSchema schema, boolean value) {
        labelReceiver.accept(schema.memberName(), Boolean.toString(value));
    }

    @Override
    public void writeByte(SdkSchema schema, byte value) {
        labelReceiver.accept(schema.memberName(), Byte.toString(value));
    }

    @Override
    public void writeShort(SdkSchema schema, short value) {
        labelReceiver.accept(schema.memberName(), Short.toString(value));
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        labelReceiver.accept(schema.memberName(), Integer.toString(value));
    }

    @Override
    public void writeLong(SdkSchema schema, long value) {
        labelReceiver.accept(schema.memberName(), Long.toString(value));
    }

    @Override
    public void writeFloat(SdkSchema schema, float value) {
        labelReceiver.accept(schema.memberName(), Float.toString(value));
    }

    @Override
    public void writeDouble(SdkSchema schema, double value) {
        labelReceiver.accept(schema.memberName(), Double.toString(value));
    }

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {
        labelReceiver.accept(schema.memberName(), value.toString());
    }

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        labelReceiver.accept(schema.memberName(), value.toString());
    }

    @Override
    public void writeString(SdkSchema schema, String value) {
        labelReceiver.accept(schema.memberName(), value);
    }

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {
        labelReceiver.accept(schema.memberName(), schema.getTrait(TimestampFormatTrait.class)
                .map(TimestampFormatter::of)
                .orElse(TimestampFormatter.Prelude.DATE_TIME)
                .formatToString(value));
    }
}
