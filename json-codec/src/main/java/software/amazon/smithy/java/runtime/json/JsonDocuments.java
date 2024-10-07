/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.core.serde.document.DocumentDeserializer;
import software.amazon.smithy.java.runtime.core.serde.document.DocumentUtils;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class JsonDocuments {

    private static final Schema STRING_MAP_KEY;

    static {
        var tempSchema = Schema.structureBuilder(PreludeSchemas.DOCUMENT.id())
            .putMember("key", PreludeSchemas.STRING)
            .build();
        STRING_MAP_KEY = tempSchema.mapKeyMember();
    }

    private JsonDocuments() {}

    public static Document createString(String value, JsonCodec.Settings settings) {
        return new StringDocument(value, settings);
    }

    public static Document createBoolean(boolean value, JsonCodec.Settings settings) {
        return new BooleanDocument(value, settings);
    }

    public static Document createNumber(Number value, JsonCodec.Settings settings) {
        return new NumberDocument(value, settings, DocumentUtils.getSchemaForNumber(value));
    }

    public static Document createList(List<Document> values, JsonCodec.Settings settings) {
        return new ListDocument(values, settings);
    }

    public static Document createMap(Map<String, Document> values, JsonCodec.Settings settings) {
        return new MapDocument(values, settings);
    }

    record StringDocument(String value, JsonCodec.Settings settings) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.STRING;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public ByteBuffer asBlob() {
            try {
                // Base64 decode JSON blobs.
                return ByteBuffer.wrap(Base64.getDecoder().decode(value));
            } catch (IllegalArgumentException e) {
                throw new SerializationException("Expected a base64 encoded blob value", e);
            }
        }

        @Override
        public Instant asTimestamp() {
            // Always use the default JSON timestamp format with untyped documents.
            return TimestampResolver.readTimestamp(value, settings.timestampResolver().defaultFormat());
        }

        @Override
        public <T extends SerializableShape> void deserializeInto(ShapeBuilder<T> builder) {
            builder.deserialize(new JsonDocumentDeserializer(settings, this));
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeString(PreludeSchemas.STRING, value);
        }
    }

    record NumberDocument(Number value, JsonCodec.Settings settings, Schema schema) implements Document {
        @Override
        public ShapeType type() {
            return schema.type();
        }

        @Override
        public byte asByte() {
            return value.byteValue();
        }

        @Override
        public short asShort() {
            return value.shortValue();
        }

        @Override
        public int asInteger() {
            return value.intValue();
        }

        @Override
        public long asLong() {
            return value.longValue();
        }

        @Override
        public float asFloat() {
            return value.floatValue();
        }

        @Override
        public double asDouble() {
            return value.doubleValue();
        }

        @Override
        public BigInteger asBigInteger() {
            return value instanceof BigInteger ? (BigInteger) value : BigInteger.valueOf(value.longValue());
        }

        @Override
        public BigDecimal asBigDecimal() {
            return value instanceof BigDecimal ? (BigDecimal) value : BigDecimal.valueOf(value.doubleValue());
        }

        @Override
        public Instant asTimestamp() {
            // Always use the default JSON timestamp format with untyped documents.
            return TimestampResolver.readTimestamp(value, settings.timestampResolver().defaultFormat());
        }

        @Override
        public <T extends SerializableShape> void deserializeInto(ShapeBuilder<T> builder) {
            builder.deserialize(new JsonDocumentDeserializer(settings, this));
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            DocumentUtils.serializeNumber(serializer, schema, value);
        }
    }

    record BooleanDocument(boolean value, JsonCodec.Settings settings) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.BOOLEAN;
        }

        @Override
        public boolean asBoolean() {
            return value;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeBoolean(PreludeSchemas.BOOLEAN, value);
        }
    }

    record ListDocument(List<Document> values, JsonCodec.Settings settings) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.LIST;
        }

        @Override
        public List<Document> asList() {
            return values;
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public <T extends SerializableShape> void deserializeInto(ShapeBuilder<T> builder) {
            builder.deserialize(new JsonDocumentDeserializer(settings, this));
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeList(PreludeSchemas.DOCUMENT, values, values.size(), (values, ser) -> {
                for (var element : values) {
                    if (element == null) {
                        ser.writeNull(PreludeSchemas.DOCUMENT);
                    } else {
                        element.serialize(ser);
                    }
                }
            });
        }
    }

    record MapDocument(Map<String, Document> values, JsonCodec.Settings settings) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.MAP;
        }

        @Override
        public ShapeId discriminator() {
            String discriminator = null;
            var member = values.get("__type");
            if (member != null && member.type() == ShapeType.STRING) {
                discriminator = member.asString();
            }
            return DocumentDeserializer.parseDiscriminator(discriminator, settings.defaultNamespace());
        }

        @Override
        public Map<String, Document> asStringMap() {
            return values;
        }

        @Override
        public Document getMember(String memberName) {
            return values.get(memberName);
        }

        @Override
        public Set<String> getMemberNames() {
            return values.keySet();
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public <T extends SerializableShape> void deserializeInto(ShapeBuilder<T> builder) {
            builder.deserialize(new JsonDocumentDeserializer(settings, this));
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeMap(PreludeSchemas.DOCUMENT, values, values.size(), (stringMap, mapSerializer) -> {
                for (var e : stringMap.entrySet()) {
                    mapSerializer.writeEntry(STRING_MAP_KEY, e.getKey(), e.getValue(), Document::serializeContents);
                }
            });
        }
    }

    /**
     * Customized version of DocumentDeserializer to account for the settings of the JSON codec.
     */
    private static final class JsonDocumentDeserializer extends DocumentDeserializer {

        private final JsonCodec.Settings settings;

        JsonDocumentDeserializer(JsonCodec.Settings settings, Document value) {
            super(value);
            this.settings = settings;
        }

        @Override
        protected DocumentDeserializer deserializer(Document nextValue) {
            return new JsonDocumentDeserializer(settings, nextValue);
        }

        @Override
        public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
            for (var member : schema.members()) {
                var nextValue = readDocument().getMember(settings.fieldMapper().memberToField(member));
                if (nextValue != null) {
                    structMemberConsumer.accept(state, member, deserializer(nextValue));
                }
            }
        }

        @Override
        public Instant readTimestamp(Schema schema) {
            var format = settings.timestampResolver().resolve(schema);
            return TimestampResolver.readTimestamp(readDocument().asObject(), format);
        }
    }
}
