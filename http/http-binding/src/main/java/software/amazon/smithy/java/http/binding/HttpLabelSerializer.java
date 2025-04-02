/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;

final class HttpLabelSerializer extends SpecificShapeSerializer {

    private final BiConsumer<String, String> labelReceiver;

    HttpLabelSerializer(BiConsumer<String, String> labelReceiver) {
        this.labelReceiver = Objects.requireNonNull(labelReceiver);
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        labelReceiver.accept(schema.memberName(), Boolean.toString(value));
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        labelReceiver.accept(schema.memberName(), Byte.toString(value));
    }

    @Override
    public void writeShort(Schema schema, short value) {
        labelReceiver.accept(schema.memberName(), Short.toString(value));
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        labelReceiver.accept(schema.memberName(), Integer.toString(value));
    }

    @Override
    public void writeLong(Schema schema, long value) {
        labelReceiver.accept(schema.memberName(), Long.toString(value));
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        labelReceiver.accept(schema.memberName(), Float.toString(value));
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        labelReceiver.accept(schema.memberName(), Double.toString(value));
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        labelReceiver.accept(schema.memberName(), value.toString());
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        labelReceiver.accept(schema.memberName(), value.toString());
    }

    @Override
    public void writeString(Schema schema, String value) {
        if (value.isEmpty()) {
            throw new SerializationException("HTTP label for `" + schema.id() + "` cannot be empty");
        }
        labelReceiver.accept(schema.memberName(), value);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        var trait = schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
        TimestampFormatter formatter = trait != null
                ? TimestampFormatter.of(trait)
                : TimestampFormatter.Prelude.DATE_TIME;
        labelReceiver.accept(schema.memberName(), formatter.writeString(value));
    }
}
