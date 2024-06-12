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
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.core.serde.document.DocumentDeserializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class JsonDocument implements Document {

    private static final Schema STRING_MAP_KEY = Schema.memberBuilder("key", PreludeSchemas.STRING)
        .id(PreludeSchemas.DOCUMENT.id())
        .build();

    private final Any any;
    private final JsonFieldMapper fieldMapper;
    private final TimestampResolver timestampResolver;
    private final ShapeType type;
    private final Schema schema;

    JsonDocument(
        com.jsoniter.any.Any any,
        JsonFieldMapper fieldMapper,
        TimestampResolver timestampResolver
    ) {
        this.any = any;
        this.fieldMapper = fieldMapper;
        this.timestampResolver = timestampResolver;

        // Determine the type from the underlying JSON value.
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
            // The default case should never be reached.
            default -> throw new SerializationException("Expected JSON document: " + any.mustBeValid());
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
        return any.valueType() == ValueType.NUMBER ? BigDecimal.valueOf(any.toDouble()) : Document.super.asBigDecimal();
    }

    @Override
    public String asString() {
        return any.valueType() == ValueType.STRING ? any.toString() : Document.super.asString();
    }

    @Override
    public byte[] asBlob() {
        if (any.valueType() != ValueType.STRING) {
            return Document.super.asBlob(); // this always fails
        } else {
            try {
                // Base64 decode JSON blobs.
                return Base64.getDecoder().decode(any.toString());
            } catch (IllegalArgumentException e) {
                throw new SerializationException("Expected a base64 encoded blob value", e);
            }
        }
    }

    @Override
    public Instant asTimestamp() {
        // Always use the default JSON timestamp format with untyped documents.
        return TimestampResolver.readTimestamp(any, timestampResolver.defaultFormat());
    }

    @Override
    public List<Document> asList() {
        if (any.valueType() != ValueType.ARRAY) {
            return Document.super.asList();
        }

        List<Document> result = new ArrayList<>();
        for (var value : any) {
            result.add(new JsonDocument(value, fieldMapper, timestampResolver));
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
                    new JsonDocument(entry.getValue(), fieldMapper, timestampResolver)
                );
            }
            return result;
        }
    }

    @Override
    public Document getMember(String memberName) {
        if (any.valueType() == ValueType.OBJECT) {
            var memberDocument = any.get(memberName);
            if (memberDocument.valueType() != ValueType.NULL && memberDocument.valueType() != ValueType.INVALID) {
                return new JsonDocument(memberDocument, fieldMapper, timestampResolver);
            }
        }
        return null;
    }

    @Override
    public void serializeContents(ShapeSerializer encoder) {
        switch (type()) {
            case BOOLEAN -> encoder.writeBoolean(schema, asBoolean());
            case INTEGER -> encoder.writeInteger(schema, asInteger());
            case DOUBLE -> encoder.writeDouble(schema, asDouble());
            case STRING -> encoder.writeString(schema, asString());
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
                    entry.serializeContents(c);
                }
            });
            // When type is set in the ctor, it only allows the above types; the default switch should never happen.
            default -> throw new SerializationException("Cannot serialize unexpected JSON value: " + any);
        }
    }

    @Override
    public <T extends SerializableShape> void deserializeInto(ShapeBuilder<T> builder) {
        builder.deserialize(new JsonDocumentDeserializer(this));
    }

    // JSON documents are considered equal if they have the same Any and if they have the same JSON settings.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JsonDocument that = (JsonDocument) o;
        return type == that.type
            && fieldMapper.getClass() == that.fieldMapper.getClass()
            && timestampResolver.equals(that.timestampResolver)
            && any.equals(that.any);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, any, timestampResolver);
    }

    @Override
    public String toString() {
        return "JsonDocument{any=" + any + ", timestampResolver=" + timestampResolver
            + ", memberToField=" + fieldMapper + '}';
    }

    /**
     * Customized version of DocumentDeserializer to account for the settings of the JSON codec.
     */
    private static final class JsonDocumentDeserializer extends DocumentDeserializer {

        private final JsonDocument jsonDocument;

        JsonDocumentDeserializer(JsonDocument value) {
            super(value);
            this.jsonDocument = value;
        }

        @Override
        protected DocumentDeserializer deserializer(Document nextValue) {
            return new JsonDocumentDeserializer((JsonDocument) nextValue);
        }

        @Override
        public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
            for (var member : schema.members()) {
                var nextValue = jsonDocument.getMember(jsonDocument.fieldMapper.memberToField(member));
                if (nextValue != null) {
                    structMemberConsumer.accept(state, member, deserializer(nextValue));
                }
            }
        }

        @Override
        public Instant readTimestamp(Schema schema) {
            var format = jsonDocument.timestampResolver.resolve(schema);
            return TimestampResolver.readTimestamp(jsonDocument.any, format);
        }
    }
}
