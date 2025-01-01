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
import java.util.Map;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.datastream.DataStream;

record ValidatingDeserializer(
        ShapeValidator validator,
        ShapeDeserializer deserializer) implements ShapeDeserializer {

    @Override
    public boolean readBoolean(Schema schema) {
        validator.validateBoolean(schema);
        return deserializer.readBoolean(schema);
    }

    @Override
    public ByteBuffer readBlob(Schema schema) {
        var value = deserializer.readBlob(schema);
        validator.validateBlob(schema, value);
        return value;
    }

    @Override
    public byte readByte(Schema schema) {
        var value = deserializer.readByte(schema);
        validator.validateByte(schema, value);
        return value;
    }

    @Override
    public short readShort(Schema schema) {
        var value = deserializer.readShort(schema);
        validator.validateShort(schema, value);
        return value;
    }

    @Override
    public int readInteger(Schema schema) {
        var value = deserializer.readInteger(schema);
        validator.validateInteger(schema, value);
        return value;
    }

    @Override
    public long readLong(Schema schema) {
        var value = deserializer.readLong(schema);
        validator.validateLong(schema, value);
        return value;
    }

    @Override
    public float readFloat(Schema schema) {
        var value = deserializer.readFloat(schema);
        validator.validateFloat(schema, value);
        return value;
    }

    @Override
    public double readDouble(Schema schema) {
        var value = deserializer.readDouble(schema);
        validator.validateDouble(schema, value);
        return value;
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        var value = deserializer.readBigInteger(schema);
        validator.validateBigInteger(schema, value);
        return value;
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        var value = deserializer.readBigDecimal(schema);
        validator.validateBigDecimal(schema, value);
        return value;
    }

    @Override
    public String readString(Schema schema) {
        var value = deserializer.readString(schema);
        validator.validateString(schema, value);
        return value;
    }

    @Override
    public Document readDocument() {
        return deserializer.readDocument();
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        var value = deserializer.readTimestamp(schema);
        validator.validateTimestamp(schema, value);
        return value;
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
        var validatorState = validator.startStruct(schema);
        deserializer.readStruct(
                schema,
                state,
                new ValidatingStructMemberConsumer<T>(validator, consumer));
        validator.endStruct(schema, validatorState);
    }

    @Override
    public <T extends List<?>> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
        int size = deserializer.containerSize();
        var validatingConsumer = new ValidatingListMemberConsumer<>(validator, consumer);
        if (size == -1) {
            var validatorState = validator.startList(schema);
            deserializer.readList(
                    schema,
                    state,
                    validatingConsumer);
            validator.endListWithSizeCheck(state, validatorState, schema);
        } else {
            var validatorState = validator.startList(schema, size);
            deserializer.readList(
                    schema,
                    state,
                    validatingConsumer);
            validator.endList(state, validatorState, schema);
        }

    }

    @Override
    public <T extends Map<?, ?>> void readStringMap(
            Schema schema,
            T state,
            MapMemberConsumer<String, T> consumer
    ) {
        int size = deserializer.containerSize();
        if (size == -1) {
            var validatorState = validator.startMap(schema);
            deserializer.readStringMap(
                    schema,
                    state,
                    new ValidatingMapMemberConsumer<>(validator, consumer));
            validator.endMap(schema, state.size(), validatorState);
        } else {
            var varlidatorState = validator.startMap(schema, size);
            deserializer.readStringMap(
                    schema,
                    state,
                    new ValidatingMapMemberConsumer<>(validator, consumer));
            validator.endMap(varlidatorState);
        }
    }

    @Override
    public DataStream readDataStream(Schema schema) {
        var value = deserializer.readDataStream(schema);
        if (value.hasByteBuffer()) {
            validator.validateBlob(schema, value.waitForByteBuffer());
        }
        return value;
    }

    @Override
    public Flow.Publisher<? extends SerializableStruct> readEventStream(Schema schema) {
        return deserializer.readEventStream(schema);
    }

    @Override
    public int containerSize() {
        return deserializer.containerSize();
    }

    @Override
    public <T> T readNull() {
        return deserializer.readNull();
    }

    @Override
    public boolean isNull() {
        return deserializer.isNull();
    }

    @Override
    public void close() {
        deserializer.close();
    }

    public List<ValidationError> getErrors() {
        return validator.getErrors();
    }

    private record ValidatingListMemberConsumer<T>(
            ShapeValidator validator,
            ListMemberConsumer<T> delegate) implements ListMemberConsumer<T> {
        @Override
        public void accept(T state, ShapeDeserializer memberDeserializer) {
            validator.betweenListElements(0);
            delegate.accept(state, new ValidatingDeserializer(validator, memberDeserializer));
        }
    }

    private record ValidatingStructMemberConsumer<T>(
            ShapeValidator validator,
            StructMemberConsumer<T> delegate) implements StructMemberConsumer<T> {
        @Override
        public void accept(T state, Schema memberSchema, ShapeDeserializer memberDeserializer) {
            delegate.accept(state, memberSchema, new ValidatingDeserializer(validator, memberDeserializer));
        }
    }

    private record ValidatingMapMemberConsumer<K, T>(
            ShapeValidator validator,
            MapMemberConsumer<K, T> delegate) implements MapMemberConsumer<K, T> {

        @Override
        public void accept(T state, K key, ShapeDeserializer memberDeserializer) {
            delegate.accept(state, key, new ValidatingDeserializer(validator, memberDeserializer));
        }
    }
}
