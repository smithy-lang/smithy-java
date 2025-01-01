/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.serde.ListSerializer;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;

public class ValidatingSerializer implements ShapeSerializer, MapSerializer {

    private final ShapeValidator validator;

    public ValidatingSerializer(ShapeValidator validator) {
        this.validator = validator;
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        var state = validator.startStruct(schema);
        struct.serializeMembers(this);
        validator.endStruct(schema, state);
    }

    @Override
    public <T extends List<?>> void writeList(
            Schema schema,
            T listState,
            int size,
            BiConsumer<T, ShapeSerializer> consumer
    ) {
        var state = validator.startList(schema, size);
        consumer.accept(listState, new ListSerializer(this, validator::betweenListElements));
        validator.endList(listState, state, schema);
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        var state = validator.startMap(schema, size);
        consumer.accept(mapState, this);
        validator.endMap(state);
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        validator.validateBoolean(schema);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        validator.validateByte(schema, value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        validator.validateShort(schema, value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        validator.validateInteger(schema, value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        validator.validateLong(schema, value);
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        validator.validateFloat(schema, value);
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        validator.validateDouble(schema, value);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        validator.validateBigInteger(schema, value);
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        validator.validateBigDecimal(schema, value);
    }

    @Override
    public void writeString(Schema schema, String value) {
        validator.validateString(schema, value);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        validator.validateBlob(schema, value);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        validator.validateTimestamp(schema, value);
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        validator.validateDocument(schema, value);
    }

    @Override
    public void writeNull(Schema schema) {
        validator.validateNull(schema);
    }

    @Override
    public <T> void writeEntry(Schema keySchema, String key, T state, BiConsumer<T, ShapeSerializer> valueSerializer) {
        validator.startEntry(keySchema, key);
        valueSerializer.accept(state, this);
        validator.endEntry();
    }
}
