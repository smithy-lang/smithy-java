/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.traits.SensitiveTrait;

/**
 * Implements the toString method for shapes, taking the sensitive trait into account.
 */
public final class ToStringSerializer implements ShapeSerializer {

    private final StringBuilder builder = new StringBuilder();

    public static String serialize(SerializableShape shape) {
        ToStringSerializer serializer = new ToStringSerializer();
        shape.serialize(serializer);
        return serializer.toString();
    }

    @Override
    public String toString() {
        return builder.toString().trim();
    }

    private void append(SdkSchema schema, Object value) {
        if (schema.hasTrait(SensitiveTrait.class)) {
            builder.append("*REDACTED*");
        } else {
            builder.append(value);
        }
    }

    @Override
    public void writeStruct(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        builder.append(schema.id().getName()).append('[');
        consumer.accept(ShapeSerializer.ofDelegatingConsumer(new StructureMemberWriter()));
        builder.append(']');
    }

    private final class StructureMemberWriter implements BiConsumer<SdkSchema, Consumer<ShapeSerializer>> {
        private boolean isFirst = true;

        @Override
        public void accept(SdkSchema member, Consumer<ShapeSerializer> memberWriter) {
            if (!isFirst) {
                builder.append(", ");
            } else {
                isFirst = false;
            }
            builder.append(member.memberName()).append('=');
            memberWriter.accept(ToStringSerializer.this);
        }
    }

    @Override
    public void writeList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        builder.append('[');
        consumer.accept(new ListSerializer(this, this::writeComma));
        builder.append(']');
    }

    private void writeComma(int position) {
        if (position > 0) {
            builder.append(", ");
        }
    }

    @Override
    public void writeMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
        builder.append('{');
        consumer.accept(new MapSerializer() {
            private boolean isFirst = true;

            @Override
            public void writeEntry(SdkSchema keySchema, String key, Consumer<ShapeSerializer> valueSerializer) {
                if (!isFirst) {
                    builder.append(", ");
                } else {
                    isFirst = false;
                }
                append(keySchema, key);
                builder.append('=');
                valueSerializer.accept(ToStringSerializer.this);
            }
        });
        builder.append('}');
    }

    @Override
    public void writeBoolean(SdkSchema schema, boolean value) {
        append(schema, value);
    }

    @Override
    public void writeShort(SdkSchema schema, short value) {
        append(schema, value);
    }

    @Override
    public void writeByte(SdkSchema schema, byte value) {
        append(schema, value);
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        append(schema, value);
    }

    @Override
    public void writeLong(SdkSchema schema, long value) {
        append(schema, value);
    }

    @Override
    public void writeFloat(SdkSchema schema, float value) {
        builder.append(value);
    }

    @Override
    public void writeDouble(SdkSchema schema, double value) {
        append(schema, value);
    }

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {
        append(schema, value);
    }

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        append(schema, value);
    }

    @Override
    public void writeString(SdkSchema schema, String value) {
        append(schema, value);
    }

    @Override
    public void writeBlob(SdkSchema schema, byte[] value) {
        if (schema.hasTrait(SensitiveTrait.class)) {
            append(schema, value);
        } else {
            for (var b : value) {
                builder.append(Integer.toHexString(b));
            }
        }
    }

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {
        append(schema, value);
    }

    @Override
    public void writeDocument(SdkSchema schema, Document value) {
        builder.append(value.type()).append('.').append("Document[");
        value.serializeContents(this);
        builder.append(']');
    }

    @Override
    public void writeNull(SdkSchema schema) {
        builder.append("null");
    }
}
