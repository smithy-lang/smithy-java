/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ListSerializer;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.StructSerializer;

/**
 * Responsible for turning a shape into an Any using it's serializeShape method.
 */
final class AnyParser implements ShapeSerializer {

    private Any result;

    Any getResult() {
        return Objects.requireNonNull(result, "result was not set by the shape serializer");
    }

    @Override
    public StructSerializer beginStruct(SdkSchema schema) {
        result = null;
        Map<String, Any> members = new LinkedHashMap<>();

        return new StructSerializer() {
            @Override
            public void endStruct() {
                result = new StructAny(members, schema);
            }

            @Override
            public void member(SdkSchema member, Consumer<ShapeSerializer> memberWriter) {
                AnyParser parser = new AnyParser();
                memberWriter.accept(parser);
                members.put(member.memberName(), parser.result);
            }
        };
    }

    @Override
    public void beginList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        List<Any> elements = new ArrayList<>();
        AnyParser elementParser = new AnyParser();
        ListSerializer serializer = new ListSerializer(this, () -> {
        }, () -> {
            elements.add(elementParser.result);
            elementParser.result = null;
        });
        consumer.accept(serializer);
        result = Any.of(elements);
    }

    @Override
    public void beginMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
        Map<Any, Any> entries = new LinkedHashMap<>();
        consumer.accept(new MapSerializer() {
            @Override
            public void entry(String key, Consumer<ShapeSerializer> valueSerializer) {
                AnyParser p = new AnyParser();
                valueSerializer.accept(p);
                entries.put(Any.of(key), p.result);
            }

            @Override
            public void entry(int key, Consumer<ShapeSerializer> valueSerializer) {
                AnyParser p = new AnyParser();
                valueSerializer.accept(p);
                entries.put(Any.of(key), p.result);
            }

            @Override
            public void entry(long key, Consumer<ShapeSerializer> valueSerializer) {
                AnyParser p = new AnyParser();
                valueSerializer.accept(p);
                entries.put(Any.of(key), p.result);
            }
        });
        result = Any.of(entries);
    }

    @Override
    public void writeBoolean(SdkSchema schema, boolean value) {
        result = Any.of(value, schema);
    }

    @Override
    public void writeByte(SdkSchema schema, byte value) {
        result = Any.of(value, schema);
    }

    @Override
    public void writeShort(SdkSchema schema, short value) {
        result = Any.of(value, schema);
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        result = Any.of(value, schema);
    }

    @Override
    public void writeLong(SdkSchema schema, long value) {
        result = Any.of(value, schema);
    }

    @Override
    public void writeFloat(SdkSchema schema, float value) {
        result = Any.of(value, schema);
    }

    @Override
    public void writeDouble(SdkSchema schema, double value) {
        result = Any.of(value, schema);
    }

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {
        result = Any.of(value, schema);
    }

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        result = Any.of(value, schema);
    }

    @Override
    public void writeString(SdkSchema schema, String value) {
        result = Any.of(value, schema);
    }

    @Override
    public void writeBlob(SdkSchema schema, byte[] value) {
        result = Any.of(value, schema);
    }

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {
        result = Any.of(value, schema);
    }

    @Override
    public void writeShape(SdkSchema schema, SerializableShape value) {
        value.serialize(this);
    }

    @Override
    public void writeDocument(SdkSchema schema, Any value) {
        result = value;
    }

    @Override
    public void flush() throws IOException {
        // Do nothing.
    }
}
