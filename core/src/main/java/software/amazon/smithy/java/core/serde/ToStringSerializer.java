/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * Implements the toString method for shapes, taking the sensitive trait into account.
 */
public final class ToStringSerializer implements ShapeSerializer {

    private static final String REDACTED = "*REDACTED*";

    private final StringBuilder builder = new StringBuilder();

    public static String serialize(SerializableShape shape) {
        ToStringSerializer serializer = new ToStringSerializer();
        shape.serialize(serializer);
        return serializer.toString();
    }

    @Override
    public void close() {
        builder.setLength(0);
        builder.trimToSize();
    }

    @Override
    public String toString() {
        return builder.toString().trim();
    }

    private void append(Schema schema, Object value) {
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            builder.append(value);
        }
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        var name = schema.isMember() ? schema.memberTarget().id().getName() : schema.id().getName();
        builder.append(name).append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            struct.serializeMembers(new StructureWriter(this));
        }
        builder.append(']');
    }

    private static final class StructureWriter extends InterceptingSerializer {
        private final ToStringSerializer toStringSerializer;
        private boolean isFirst = true;

        private StructureWriter(ToStringSerializer toStringSerializer) {
            this.toStringSerializer = toStringSerializer;
        }

        @Override
        protected ShapeSerializer before(Schema schema) {
            if (!isFirst) {
                toStringSerializer.builder.append(", ");
            } else {
                isFirst = false;
            }
            toStringSerializer.builder.append(schema.memberName()).append('=');
            return toStringSerializer;
        }
    }

    @Override
    public <T> void writeList(Schema schema, T state, int size, BiConsumer<T, ShapeSerializer> consumer) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            consumer.accept(state, new ListSerializer(this, this::writeComma));
        }
        builder.append(']');
    }

    private void writeComma(int position) {
        if (position > 0) {
            builder.append(", ");
        }
    }

    @Override
    public <T> void writeMap(Schema schema, T state, int size, BiConsumer<T, MapSerializer> consumer) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            consumer.accept(state, new ToStringMapSerializer(this));
        }
        builder.append('}');
    }

    private static final class ToStringMapSerializer implements MapSerializer {
        private final ToStringSerializer serializer;
        private boolean isFirst = true;

        ToStringMapSerializer(ToStringSerializer serializer) {
            this.serializer = serializer;
        }

        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            if (!isFirst) {
                serializer.builder.append(", ");
            } else {
                isFirst = false;
            }
            serializer.append(keySchema, key);
            serializer.builder.append('=');
            valueSerializer.accept(state, serializer);
        }
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        append(schema, value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        append(schema, value);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        append(schema, value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        append(schema, value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        append(schema, value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        builder.append(value);
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        append(schema, value);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        append(schema, value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        append(schema, value);
    }

    @Override
    public void writeString(Schema schema, String value) {
        append(schema, value);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            append(schema, value);
        } else {
            value.mark();
            while (value.hasRemaining()) {
                builder.append(Integer.toHexString(value.get()));
            }
            value.reset();
        }
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        append(schema, value);
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        builder.append(value.type()).append('.').append("Document[");
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            value.serializeContents(this);
        }
        builder.append(']');
    }

    @Override
    public void writeNull(Schema schema) {
        builder.append("null");
    }
}
