/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jackson;

import static com.fasterxml.jackson.databind.node.JsonNodeType.MISSING;
import static com.fasterxml.jackson.databind.node.JsonNodeType.NULL;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
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
import software.amazon.smithy.java.runtime.core.serde.document.DiscriminatorException;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.core.serde.document.DocumentDeserializer;
import software.amazon.smithy.java.runtime.json.JsonCodec;
import software.amazon.smithy.java.runtime.json.TimestampResolver;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

final class JacksonDocument implements Document {

    private static final Schema STRING_MAP_KEY = Schema.structureBuilder(PreludeSchemas.DOCUMENT.id())
        .putMember("key", PreludeSchemas.STRING)
        .build()
        .member("key");

    private final JsonNode root;
    private final JsonCodec.Settings settings;
    private final ShapeType type;
    private final Schema schema;

    JacksonDocument(
        JsonNode root,
        JsonCodec.Settings settings
    ) {
        this.root = root;
        this.settings = settings;

        // Determine the type from the underlying JSON value.
        this.type = switch (root.getNodeType()) {
            case ARRAY -> ShapeType.LIST;
            case BINARY -> ShapeType.BLOB;
            case BOOLEAN -> ShapeType.BOOLEAN;
            case NUMBER -> switch (root.numberType()) {
                case INT -> ShapeType.INTEGER;
                case LONG -> ShapeType.LONG;
                case BIG_INTEGER -> ShapeType.BIG_INTEGER;
                case FLOAT -> ShapeType.FLOAT;
                case DOUBLE -> ShapeType.DOUBLE;
                case BIG_DECIMAL -> ShapeType.BIG_DECIMAL;
            };
            case OBJECT -> ShapeType.MAP;
            case STRING -> ShapeType.STRING;
            default -> throw new SerializationException("Expected JSON document: " + root.asToken());
        };
        this.schema = PreludeSchemas.getSchemaForType(type);
    }

    @Override
    public ShapeType type() {
        return type;
    }

    @Override
    public ShapeId discriminator() {
        if (!root.isObject()) {
            throw new DiscriminatorException("Cannot get a document discriminator from a " + type() + " document");
        }

        String discriminator = null;
        var member = root.get("__type");
        if (member.isTextual()) {
            discriminator = member.asText();
        }

        return DocumentDeserializer.parseDiscriminator(discriminator, settings.defaultNamespace());
    }

    @Override
    public boolean asBoolean() {
        return root.isBoolean() ? root.asBoolean() : Document.super.asBoolean();
    }

    @Override
    public byte asByte() {
        return root.isNumber() ? (byte) root.asInt() : Document.super.asByte();
    }

    @Override
    public short asShort() {
        return root.isNumber() ? (short) root.asInt() : Document.super.asShort();
    }

    @Override
    public int asInteger() {
        return root.isNumber() ? root.asInt() : Document.super.asInteger();
    }

    @Override
    public long asLong() {
        return root.isNumber() ? root.asLong() : Document.super.asLong();
    }

    @Override
    public float asFloat() {
        return root.isNumber() ? (float) root.asDouble() : Document.super.asFloat();
    }

    @Override
    public double asDouble() {
        return root.isNumber() ? root.asDouble() : Document.super.asDouble();
    }

    @Override
    public BigInteger asBigInteger() {
        return root.isNumber() ? root.bigIntegerValue() : Document.super.asBigInteger();
    }

    @Override
    public BigDecimal asBigDecimal() {
        return root.isNumber() ? root.decimalValue() : Document.super.asBigDecimal();
    }

    @Override
    public String asString() {
        return root.isTextual() ? root.asText() : Document.super.asString();
    }

    @Override
    public ByteBuffer asBlob() {
        if (root.isBinary()) {
            try {
                return ByteBuffer.wrap(root.binaryValue());
            } catch (IOException e) {
                throw new SerializationException(e);
            }
        }
        if (root.isTextual()) {
            // Base64 decode JSON blobs.
            try {
                return ByteBuffer.wrap(Base64.getDecoder().decode(root.textValue()));
            } catch (IllegalArgumentException e) {
                throw new SerializationException("Invalid base64", e);
            }
        }
        return Document.super.asBlob(); // this always fails
    }

    @Override
    public Instant asTimestamp() {
        Object val;
        if (root.isTextual()) {
            val = root.textValue();
        } else if (root.isNumber()) {
            val = root.numberValue();
        } else {
            throw new SerializationException("Expected a timestamp, but found " + type());
        }
        // Always use the default JSON timestamp format with untyped documents.
        return TimestampResolver.readTimestamp(val, settings.timestampResolver().defaultFormat());
    }

    @Override
    public List<Document> asList() {
        if (!root.isArray()) {
            return Document.super.asList();
        }

        List<Document> result = new ArrayList<>();
        for (int i = 0; i < root.size(); i++) {
            result.add(new JacksonDocument(root.get(i), settings));
        }

        return result;
    }

    @Override
    public Map<String, Document> asStringMap() {
        if (!root.isObject()) {
            return Document.super.asStringMap();
        } else {
            Map<String, Document> result = new LinkedHashMap<>();
            for (Iterator<String> iter = root.fieldNames(); iter.hasNext();) {
                var key = iter.next();
                result.put(key, new JacksonDocument(root.get(key), settings));
            }
            return result;
        }
    }

    @Override
    public Document getMember(String memberName) {
        if (root.isObject()) {
            var memberDocument = root.get(memberName);
            if (memberDocument != null &&
                memberDocument.getNodeType() != NULL
                && memberDocument.getNodeType() != MISSING) {
                return new JacksonDocument(memberDocument, settings);
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
            default -> throw new SerializationException("Cannot serialize unexpected JSON value: " + root);
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
        JacksonDocument that = (JacksonDocument) o;
        return type == that.type
            && settings.fieldMapper().getClass() == that.settings.fieldMapper().getClass()
            && settings.timestampResolver().equals(that.settings.timestampResolver())
            && root.equals(that.root);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, root, settings.timestampResolver());
    }

    @Override
    public String toString() {
        return "JsonDocument{root=" + root + ", settings.timestampResolver()=" + settings.timestampResolver()
            + ", memberToField=" + settings.fieldMapper() + '}';
    }

    /**
     * Customized version of DocumentDeserializer to account for the settings of the JSON codec.
     */
    private static final class JsonDocumentDeserializer extends DocumentDeserializer {

        private final JacksonDocument jsonDocument;

        JsonDocumentDeserializer(JacksonDocument value) {
            super(value);
            this.jsonDocument = value;
        }

        @Override
        protected DocumentDeserializer deserializer(Document nextValue) {
            return new JsonDocumentDeserializer((JacksonDocument) nextValue);
        }

        @Override
        public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
            for (var member : schema.members()) {
                var nextValue = jsonDocument.getMember(jsonDocument.settings.fieldMapper().memberToField(member));
                if (nextValue != null) {
                    structMemberConsumer.accept(state, member, deserializer(nextValue));
                }
            }
        }

        @Override
        public Instant readTimestamp(Schema schema) {
            var format = jsonDocument.settings.timestampResolver().resolve(schema);
            return TimestampResolver.readTimestamp(
                jsonDocument.root.isNumber()
                    ? jsonDocument.root.numberValue()
                    : jsonDocument.root.textValue(),
                format
            );
        }
    }
}
