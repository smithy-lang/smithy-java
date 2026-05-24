/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
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

    // --- Specialized non-sparse list methods ---

    @Override
    public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeStruct(memberSchema, v);
            }
        }
        builder.append(']');
    }

    @Override
    public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeString(memberSchema, v);
            }
        }
        builder.append(']');
    }

    @Override
    public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeBoolean(memberSchema, v);
            }
        }
        builder.append(']');
    }

    @Override
    public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeByte(memberSchema, v);
            }
        }
        builder.append(']');
    }

    @Override
    public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeShort(memberSchema, v);
            }
        }
        builder.append(']');
    }

    @Override
    public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeInteger(memberSchema, v);
            }
        }
        builder.append(']');
    }

    @Override
    public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeLong(memberSchema, v);
            }
        }
        builder.append(']');
    }

    @Override
    public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeFloat(memberSchema, v);
            }
        }
        builder.append(']');
    }

    @Override
    public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeDouble(memberSchema, v);
            }
        }
        builder.append(']');
    }

    @Override
    public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeBigInteger(memberSchema, v);
            }
        }
        builder.append(']');
    }

    @Override
    public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeBigDecimal(memberSchema, v);
            }
        }
        builder.append(']');
    }

    @Override
    public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeBlob(memberSchema, v);
            }
        }
        builder.append(']');
    }

    @Override
    public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeTimestamp(memberSchema, v);
            }
        }
        builder.append(']');
    }

    @Override
    public void writeDocumentList(Schema schema, List<? extends Document> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeDocument(memberSchema, v);
            }
        }
        builder.append(']');
    }

    @Override
    public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeString(memberSchema, v.getValue());
            }
        }
        builder.append(']');
    }

    @Override
    public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                writeInteger(memberSchema, v.getValue());
            }
        }
        builder.append(']');
    }

    // --- Specialized sparse list methods ---

    @Override
    public void writeSparseStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeStruct(memberSchema, v);
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeString(memberSchema, v);
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBoolean(memberSchema, v);
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeByte(memberSchema, v);
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeShort(memberSchema, v);
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeInteger(memberSchema, v);
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeLong(memberSchema, v);
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeFloat(memberSchema, v);
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeDouble(memberSchema, v);
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBigInteger(memberSchema, v);
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBigDecimal(memberSchema, v);
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeBlob(memberSchema, v);
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeTimestamp(memberSchema, v);
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseDocumentList(Schema schema, List<? extends Document> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeDocument(memberSchema, v);
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeString(memberSchema, v.getValue());
                }
            }
        }
        builder.append(']');
    }

    @Override
    public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        builder.append('[');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var v : values) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                if (v == null) {
                    writeNull(memberSchema);
                } else {
                    writeInteger(memberSchema, v.getValue());
                }
            }
        }
        builder.append(']');
    }

    // --- Specialized non-sparse map methods ---

    @Override
    public void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeStruct(valueSchema, entry.getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeStringMap(
            Schema schema,
            Map<String, String> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeString(valueSchema, entry.getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeBoolean(valueSchema, entry.getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeByte(valueSchema, entry.getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeShortMap(
            Schema schema,
            Map<String, Short> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeShort(valueSchema, entry.getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeInteger(valueSchema, entry.getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeLong(valueSchema, entry.getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeFloatMap(
            Schema schema,
            Map<String, Float> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeFloat(valueSchema, entry.getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeDoubleMap(
            Schema schema,
            Map<String, Double> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeDouble(valueSchema, entry.getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeBigInteger(valueSchema, entry.getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeBigDecimal(valueSchema, entry.getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeBlob(valueSchema, entry.getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeTimestamp(valueSchema, entry.getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeDocument(valueSchema, entry.getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeString(valueSchema, entry.getValue().getValue());
            }
        }
        builder.append('}');
    }

    @Override
    public void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                writeInteger(valueSchema, entry.getValue().getValue());
            }
        }
        builder.append('}');
    }

    // --- Specialized sparse map methods ---

    @Override
    public void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeStruct(valueSchema, entry.getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseStringMap(
            Schema schema,
            Map<String, String> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeString(valueSchema, entry.getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeBoolean(valueSchema, entry.getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseByteMap(
            Schema schema,
            Map<String, Byte> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeByte(valueSchema, entry.getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseShortMap(
            Schema schema,
            Map<String, Short> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeShort(valueSchema, entry.getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeInteger(valueSchema, entry.getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseLongMap(
            Schema schema,
            Map<String, Long> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeLong(valueSchema, entry.getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseFloatMap(
            Schema schema,
            Map<String, Float> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeFloat(valueSchema, entry.getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseDoubleMap(
            Schema schema,
            Map<String, Double> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeDouble(valueSchema, entry.getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeBigInteger(valueSchema, entry.getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeBigDecimal(valueSchema, entry.getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeBlob(valueSchema, entry.getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeTimestamp(valueSchema, entry.getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeDocument(valueSchema, entry.getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeString(valueSchema, entry.getValue().getValue());
                }
            }
        }
        builder.append('}');
    }

    @Override
    public void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        builder.append('{');
        if (schema.hasTrait(TraitKey.SENSITIVE_TRAIT)) {
            builder.append(REDACTED);
        } else {
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) {
                    builder.append(", ");
                } else {
                    first = false;
                }
                append(keySchema, entry.getKey());
                builder.append('=');
                if (entry.getValue() == null) {
                    writeNull(valueSchema);
                } else {
                    writeInteger(valueSchema, entry.getValue().getValue());
                }
            }
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
