/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkException;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.traits.SensitiveTrait;

/**
 * Implements the toString method for shapes, taking the sensitive trait into account.
 */
public final class ToStringSerializer implements ShapeSerializer {

    private final StringBuilder builder = new StringBuilder();
    private String indentString = "";

    public static String serialize(SerializableShape shape) {
        ToStringSerializer serializer = new ToStringSerializer();
        shape.serialize(serializer);
        return serializer.toString();
    }

    private ToStringSerializer append(SdkSchema schema, String value) {
        if (value == null) {
            append("null");
        }
        if (schema != null && schema.hasTrait(SensitiveTrait.class)) {
            builder.append("(redacted)");
        } else {
            append(value);
        }
        return this;
    }

    private ToStringSerializer append(char value) {
        builder.append(value);
        if (value == '\n') {
            builder.append(indentString);
        }
        return this;
    }

    private ToStringSerializer append(String value) {
        builder.append(Objects.requireNonNullElse(value, "null"));
        return this;
    }

    private ToStringSerializer appendStringWithPotentialNewlines(SdkSchema schema, String value) {
        if (value == null) {
            append("null");
            return this;
        } else {
            value = value.replace(System.lineSeparator(), System.lineSeparator() + indentString);
            return append(schema, value);
        }
    }

    private ToStringSerializer indent() {
        indentString += "    ";
        return this;
    }

    private ToStringSerializer dedent() {
        indentString = indentString.substring(4);
        return this;
    }

    @Override
    public void flush() {}

    @Override
    public String toString() {
        return builder.toString().trim();
    }

    @Override
    public StructSerializer beginStruct(SdkSchema schema) {
        append(schema.id().toString()).append(':').indent().append(System.lineSeparator());

        return new StructSerializer() {
            @Override
            public void endStruct() {
                dedent().append(System.lineSeparator());
            }

            @Override
            public void member(SdkSchema member, Consumer<ShapeSerializer> memberWriter) {
                append(member.memberName()).append(": ");
                // Throw if a value isn't written.
                RequiredWriteSerializer.assertWrite(
                    ToStringSerializer.this,
                    () -> new SdkException("Structure member did not write a value for " + schema),
                    memberWriter
                );
                append(System.lineSeparator());
            }
        };
    }

    @Override
    public void beginList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        indent();
        consumer.accept(new ListSerializer(this, this::writeComma));
        dedent();
    }

    private void writeComma(int position) {
        if (position > 0) {
            append(',').append(System.lineSeparator());
        }
    }

    @Override
    public void beginMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
        indent();
        append(System.lineSeparator());

        consumer.accept(new MapSerializer() {
            @Override
            public void entry(String key, Consumer<ShapeSerializer> valueSerializer) {
                writeString(schema.member("key"), key);
                append(": ");
                valueSerializer.accept(ToStringSerializer.this);
                append(System.lineSeparator());
            }

            @Override
            public void entry(int key, Consumer<ShapeSerializer> valueSerializer) {
                writeInteger(schema.member("key"), key);
                append(": ");
                valueSerializer.accept(ToStringSerializer.this);
                append(System.lineSeparator());
            }

            @Override
            public void entry(long key, Consumer<ShapeSerializer> valueSerializer) {
                writeLong(schema.member("key"), key);
                append(": ");
                valueSerializer.accept(ToStringSerializer.this);
                append(System.lineSeparator());
            }
        });

        dedent();
    }

    @Override
    public void writeBoolean(SdkSchema schema, boolean value) {
        append(schema, Boolean.toString(value));
    }

    @Override
    public void writeShort(SdkSchema schema, short value) {
        append(schema, Short.toString(value));
    }

    @Override
    public void writeByte(SdkSchema schema, byte value) {
        append(schema, Byte.toString(value));
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        append(schema, Integer.toString(value));
    }

    @Override
    public void writeLong(SdkSchema schema, long value) {
        append(schema, Long.toString(value));
    }

    @Override
    public void writeFloat(SdkSchema schema, float value) {
        append(schema, Float.toString(value));
    }

    @Override
    public void writeDouble(SdkSchema schema, double value) {
        append(schema, Double.toString(value));
    }

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {
        append(schema, value.toString());
    }

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        append(schema, value.toString());
    }

    @Override
    public void writeString(SdkSchema schema, String value) {
        appendStringWithPotentialNewlines(schema, value);
    }

    @Override
    public void writeBlob(SdkSchema schema, byte[] value) {
        append(schema, Base64.getEncoder().encodeToString(value));
    }

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {
        append(schema, TimestampFormatter.Prelude.DATE_TIME.formatToString(value));
    }

    @Override
    public void writeDocument(Document value) {
        append("Document (" + value.type()).append("):");
        indent();
        append(System.lineSeparator());
        value.serializeContents(this);
        dedent();
    }
}
