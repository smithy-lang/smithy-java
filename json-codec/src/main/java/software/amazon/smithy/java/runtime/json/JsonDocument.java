/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import com.jsoniter.ValueType;
import com.jsoniter.any.Any;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.core.serde.document.DocumentDeserializer;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class JsonDocument implements Document {

    private static final SdkSchema STRING_MAP_KEY = SdkSchema.memberBuilder("key", PreludeSchemas.STRING)
        .id(PreludeSchemas.DOCUMENT.id())
        .build();

    private final Any any;
    private final TimestampFormatter defaultTimestampFormat;
    private final boolean useJsonName;
    private final boolean useTimestampFormat;
    private final ShapeType type;
    private final SdkSchema schema;

    JsonDocument(
        com.jsoniter.any.Any any,
        boolean useJsonName,
        TimestampFormatter defaultTimestampFormat,
        boolean useTimestampFormat
    ) {
        this.any = any;
        this.useJsonName = useJsonName;
        this.defaultTimestampFormat = defaultTimestampFormat;
        this.useTimestampFormat = useTimestampFormat;

        // Guess the type from the underlying JSON value.
        this.type = switch (any.valueType()) {
            case NUMBER -> {
                String val = any.toString();
                if (val.contains(".")) {
                    yield ShapeType.DOUBLE;
                } else {
                    yield ShapeType.INTEGER;
                }
            }
            case ARRAY -> ShapeType.LIST;
            case OBJECT -> ShapeType.MAP;
            case STRING -> ShapeType.STRING;
            case BOOLEAN -> ShapeType.BOOLEAN;
            default -> ShapeType.DOCUMENT;
        };
        this.schema = PreludeSchemas.getSchemaForType(type);
    }

    @Override
    public ShapeType type() {
        return type;
    }

    @Override
    public boolean asBoolean() {
        return any.valueType() == ValueType.BOOLEAN ? any.toBoolean() : Document.super.asBoolean();
    }

    @Override
    public byte asByte() {
        return any.valueType() == ValueType.NUMBER ? (byte) any.toInt() : Document.super.asByte();
    }

    @Override
    public short asShort() {
        return any.valueType() == ValueType.NUMBER ? (short) any.toInt() : Document.super.asShort();
    }

    @Override
    public int asInteger() {
        return any.valueType() == ValueType.NUMBER ? any.toInt() : Document.super.asInteger();
    }

    @Override
    public long asLong() {
        return any.valueType() == ValueType.NUMBER ? any.toLong() : Document.super.asLong();
    }

    @Override
    public float asFloat() {
        return any.valueType() == ValueType.NUMBER ? any.toFloat() : Document.super.asFloat();
    }

    @Override
    public double asDouble() {
        return any.valueType() == ValueType.NUMBER ? any.toDouble() : Document.super.asDouble();
    }

    @Override
    public BigInteger asBigInteger() {
        return any.valueType() == ValueType.NUMBER ? BigInteger.valueOf(any.toLong()) : Document.super.asBigInteger();
    }

    @Override
    public BigDecimal asBigDecimal() {
        return any.valueType() == ValueType.NUMBER ? BigDecimal.valueOf(any.toLong()) : Document.super.asBigDecimal();
    }

    @Override
    public String asString() {
        return any.valueType() == ValueType.STRING ? any.toString() : Document.super.asString();
    }

    @Override
    public byte[] asBlob() {
        // Base64 decode JSON blobs.
        return any.valueType() == ValueType.STRING
            ? Base64.getDecoder().decode(any.toString())
            : Document.super.asBlob();
    }

    @Override
    public Instant asTimestamp() {
        TimestampFormatter format = defaultTimestampFormat;
        return switch (any.valueType()) {
            case NUMBER -> format.createFromNumber(any.toDouble());
            case STRING -> format.parseFromString(any.toString(), true); // fail if the format does not accept strings.
            default -> {
                throw new IllegalStateException(
                    "Expected a string or number value for a timestamp, but found " + any.valueType()
                );
            }
        };
    }

    @Override
    public List<Document> asList() {
        if (any.valueType() != ValueType.ARRAY) {
            return Document.super.asList();
        }

        List<Document> result = new ArrayList<>();
        for (com.jsoniter.any.Any value : any) {
            result.add(new JsonDocument(value, useJsonName, defaultTimestampFormat, useTimestampFormat));
        }

        return result;
    }

    @Override
    public Map<String, Document> asStringMap() {
        if (any.valueType() != ValueType.OBJECT) {
            return Document.super.asStringMap();
        } else {
            Map<String, Document> result = new LinkedHashMap<>();
            for (var entry : any.asMap().entrySet()) {
                result.put(
                    entry.getKey(),
                    new JsonDocument(entry.getValue(), useJsonName, defaultTimestampFormat, useTimestampFormat)
                );
            }
            return result;
        }
    }

    @Override
    public Document getMember(String memberName) {
        if (any.valueType() == ValueType.OBJECT) {
            var result = any.get(memberName);
            if (result != null) {
                return new JsonDocument(result, useJsonName, defaultTimestampFormat, useTimestampFormat);
            }
        }
        return null;
    }

    @Override
    public void serializeContents(ShapeSerializer encoder) {
        switch (type()) {
            case BOOLEAN -> encoder.writeBoolean(schema, asBoolean());
            case BYTE -> encoder.writeByte(schema, asByte());
            case SHORT -> encoder.writeShort(schema, asShort());
            case INTEGER, INT_ENUM -> encoder.writeInteger(schema, asInteger());
            case LONG -> encoder.writeLong(schema, asLong());
            case FLOAT -> encoder.writeFloat(schema, asFloat());
            case DOUBLE -> encoder.writeDouble(schema, asDouble());
            case BIG_INTEGER -> encoder.writeBigInteger(schema, asBigInteger());
            case BIG_DECIMAL -> encoder.writeBigDecimal(schema, asBigDecimal());
            case STRING, ENUM -> encoder.writeString(schema, asString());
            case BLOB -> encoder.writeBlob(schema, asBlob());
            case TIMESTAMP -> encoder.writeTimestamp(schema, asTimestamp());
            case DOCUMENT -> encoder.writeDocument(this);
            case MAP -> encoder.writeMap(schema, asStringMap(), (stringMap, mapSerializer) -> {
                for (var entry : stringMap.entrySet()) {
                    mapSerializer.writeEntry(
                        STRING_MAP_KEY,
                        entry.getKey(),
                        entry.getValue(),
                        Document::serializeContents
                    );
                }
            });
            case LIST -> encoder.writeList(schema, asList(), (list, c) -> {
                for (Document entry : list) {
                    entry.serialize(c);
                }
            });
            default -> throw new SdkSerdeException("Cannot serialize unexpected JSON value: " + any);
        }
    }

    @Override
    public <T extends SerializableShape> void deserializeInto(SdkShapeBuilder<T> builder) {
        builder.deserialize(new JsonDocumentDeserializer(this));
    }

    /**
     * Customized version of DocumentDeserializer to account for the settings of the JSON codec.
     */
    private final class JsonDocumentDeserializer extends DocumentDeserializer {
        JsonDocumentDeserializer(Document value) {
            super(value);
        }

        @Override
        protected DocumentDeserializer deserializer(Document value) {
            return new JsonDocumentDeserializer(value);
        }

        @Override
        public void readStruct(SdkSchema schema, BiConsumer<SdkSchema, ShapeDeserializer> eachEntry) {
            for (var member : schema.members()) {
                var jsonName = member.hasTrait(JsonNameTrait.class)
                    ? member.getTrait(JsonNameTrait.class).getValue()
                    : member.memberName();
                var value = getMember(jsonName);
                if (value != null) {
                    eachEntry.accept(member, new DocumentDeserializer(value));
                }
            }
        }

        @Override
        public Instant readTimestamp(SdkSchema schema) {
            TimestampFormatter format = defaultTimestampFormat;

            if (useTimestampFormat && schema.hasTrait(TimestampFormatTrait.class)) {
                format = TimestampFormatter.of(schema.getTrait(TimestampFormatTrait.class));
            }

            return switch (any.valueType()) {
                case NUMBER -> format.createFromNumber(any.toDouble());
                case STRING -> format.parseFromString(any.toString(), true);
                default -> throw new IllegalStateException(
                    "Expected a string or number value for a timestamp, but found " + any.valueType()
                );
            };
        }
    }
}
