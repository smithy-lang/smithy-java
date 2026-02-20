/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.document.DocumentParser;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Receives list values and makes sure they are all unique.
 */
final class ValidatorOfUniqueItems implements ShapeSerializer {
    // Implementation notes:
    // Scalar list values are all added to a set directly to check for uniqueness.
    // List and map values that are empty are added to a set as empty collections.
    // Non-empty list and map values are converted to a document to capture the value in-memory to check for uniqueness.
    // Null values are not supported in lists with unique items, so it causes a validation error.
    // Structure/unions are converted into a Map<String, Object> using SerializableStruct#getMemberValue on each member.

    private final Schema container;
    private final DocumentParser parser = new DocumentParser();
    private final Set<Object> values = new HashSet<>();
    private final Validator.ShapeValidator validator;
    private int position = 0;

    static <T> void validate(
            Schema container,
            T state,
            BiConsumer<T, ShapeSerializer> consumer,
            Validator.ShapeValidator validator
    ) {
        consumer.accept(state, new ValidatorOfUniqueItems(container, validator));
    }

    private ValidatorOfUniqueItems(Schema container, Validator.ShapeValidator validator) {
        this.container = container;
        this.validator = validator;
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        addValue(value);
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        addValue(value);
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        writeBlob(schema, ByteBuffer.wrap(value));
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        addValue(value);
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        addValue(value);
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        addValue(value);
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        addValue(value);
    }

    @Override
    public void writeLong(Schema schema, long value) {
        addValue(value);
    }

    @Override
    public void writeShort(Schema schema, short value) {
        addValue(value);
    }

    @Override
    public void writeString(Schema schema, String value) {
        addValue(value);
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        addValue(value);
    }

    @Override
    public void writeNull(Schema schema) {
        // Note that the uniqueItems trait and sparse trait conflict, so this isn't supported.
        addError("null found in unique items");
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        addError("Double found in unique items");
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        addError("Float found in unique items");
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        addError("Document found in unique items");
    }

    @Override
    public void writeDataStream(Schema schema, DataStream value) {
        addError("Data stream found in unique items");
    }

    @Override
    public void writeEventStream(Schema schema, EventStream<? extends SerializableStruct> value) {
        addError("Event stream found in unique items");
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        if (size == 0) {
            addValue(Collections.emptyList());
        } else {
            parser.writeList(schema, listState, size, consumer);
            addValue(parser.clearResult());
        }
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        if (size == 0) {
            addValue(Collections.emptyMap());
        } else {
            parser.writeMap(schema, mapState, size, consumer);
            addValue(parser.clearResult());
        }
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        Map<String, Object> map = new HashMap<>();
        for (var member : schema.members()) {
            var value = struct.getMemberValue(member);
            if (value != null) {
                map.put(member.memberName(), value);
            }
        }
        addValue(map);
    }

    private void addValue(Object value) {
        if (!values.add(value)) {
            addError(null);
        } else {
            position++;
        }
    }

    private void addError(String message) {
        validator.swapPath(position);
        var error = message == null
                ? new ValidationError.UniqueItemConflict(validator.createPath(), position, container)
                : new ValidationError.UniqueItemConflict(validator.createPath(), position, container, message);
        validator.addError(error);
        position++;
    }
}
