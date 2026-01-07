/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.document.DocumentDeserializer;
import software.amazon.smithy.java.core.serde.document.DocumentUtils;
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

    public static Document of(String value, JsonSettings settings) {
        return new StringDocument(value, settings);
    }

    public static Document of(boolean value, JsonSettings settings) {
        return new BooleanDocument(value, settings);
    }

    public static Document of(Number value, JsonSettings settings) {
        return switch (value) {
            case Byte b -> new ByteDocument(b, settings);
            case Short s -> new ShortDocument(s, settings);
            case Integer i -> new IntegerDocument(i, settings);
            case Long l -> new LongDocument(l, settings);
            case Float f -> new FloatDocument(f, settings);
            case Double d -> new DoubleDocument(d, settings);
            case BigInteger bi -> new NumberDocument(bi, settings, PreludeSchemas.BIG_INTEGER);
            case BigDecimal bd -> new NumberDocument(bd, settings, PreludeSchemas.BIG_DECIMAL);
            default -> throw new IllegalArgumentException("Unsupported Number: %s".formatted(value.getClass()));
        };
    }

    public static Document of(List<Document> values, JsonSettings settings) {
        return new ListDocument(values, settings);
    }

    public static Document of(Map<String, Document> values, JsonSettings settings) {
        return new MapDocument(values, settings);
    }

    record StringDocument(String value, JsonSettings settings) implements Document {
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
        public ShapeDeserializer createDeserializer() {
            return new JsonDocumentDeserializer(settings, this);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeString(PreludeSchemas.STRING, value);
        }

        @Override
        public boolean equals(Object obj) {
            return Document.equals(this, obj);
        }
    }

    record NumberDocument(Number value, JsonSettings settings, Schema schema) implements Document {
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
            return value instanceof BigInteger bi ? bi : BigInteger.valueOf(value.longValue());
        }

        @Override
        public BigDecimal asBigDecimal() {
            return value instanceof BigDecimal bd ? bd : BigDecimal.valueOf(value.doubleValue());
        }

        @Override
        public Instant asTimestamp() {
            // Always use the default JSON timestamp format with untyped documents.
            return TimestampResolver.readTimestamp(value, settings.timestampResolver().defaultFormat());
        }

        @Override
        public ShapeDeserializer createDeserializer() {
            return new JsonDocumentDeserializer(settings, this);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            DocumentUtils.serializeNumber(serializer, schema, value);
        }

        @Override
        public boolean equals(Object obj) {
            return Document.equals(this, obj);
        }
    }

    // Primitive numeric document types - no auto-boxing
    record ByteDocument(byte value, JsonSettings settings) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.BYTE;
        }

        @Override
        public byte asByte() {
            return value;
        }

        @Override
        public short asShort() {
            return value;
        }

        @Override
        public int asInteger() {
            return value;
        }

        @Override
        public long asLong() {
            return value;
        }

        @Override
        public float asFloat() {
            return value;
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public BigInteger asBigInteger() {
            return BigInteger.valueOf(value);
        }

        @Override
        public BigDecimal asBigDecimal() {
            return BigDecimal.valueOf((double) value);
        }

        @Override
        public Instant asTimestamp() {
            return TimestampResolver.readTimestamp(value, settings.timestampResolver().defaultFormat());
        }

        @Override
        public ShapeDeserializer createDeserializer() {
            return new JsonDocumentDeserializer(settings, this);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeByte(PreludeSchemas.BYTE, value);
        }

        @Override
        public boolean equals(Object obj) {
            return Document.equals(this, obj);
        }
    }

    record ShortDocument(short value, JsonSettings settings) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.SHORT;
        }

        @Override
        public byte asByte() {
            return (byte) value;
        }

        @Override
        public short asShort() {
            return value;
        }

        @Override
        public int asInteger() {
            return value;
        }

        @Override
        public long asLong() {
            return value;
        }

        @Override
        public float asFloat() {
            return value;
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public BigInteger asBigInteger() {
            return BigInteger.valueOf(value);
        }

        @Override
        public BigDecimal asBigDecimal() {
            return BigDecimal.valueOf((double) value);
        }

        @Override
        public Instant asTimestamp() {
            return TimestampResolver.readTimestamp(value, settings.timestampResolver().defaultFormat());
        }

        @Override
        public ShapeDeserializer createDeserializer() {
            return new JsonDocumentDeserializer(settings, this);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeShort(PreludeSchemas.SHORT, value);
        }

        @Override
        public boolean equals(Object obj) {
            return Document.equals(this, obj);
        }
    }

    record IntegerDocument(int value, JsonSettings settings) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.INTEGER;
        }

        @Override
        public byte asByte() {
            return (byte) value;
        }

        @Override
        public short asShort() {
            return (short) value;
        }

        @Override
        public int asInteger() {
            return value;
        }

        @Override
        public long asLong() {
            return value;
        }

        @Override
        public float asFloat() {
            return value;
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public BigInteger asBigInteger() {
            return BigInteger.valueOf(value);
        }

        @Override
        public BigDecimal asBigDecimal() {
            return BigDecimal.valueOf((double) value);
        }

        @Override
        public Instant asTimestamp() {
            return TimestampResolver.readTimestamp(value, settings.timestampResolver().defaultFormat());
        }

        @Override
        public ShapeDeserializer createDeserializer() {
            return new JsonDocumentDeserializer(settings, this);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeInteger(PreludeSchemas.INTEGER, value);
        }

        @Override
        public boolean equals(Object obj) {
            return Document.equals(this, obj);
        }
    }

    record LongDocument(long value, JsonSettings settings) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.LONG;
        }

        @Override
        public byte asByte() {
            return (byte) value;
        }

        @Override
        public short asShort() {
            return (short) value;
        }

        @Override
        public int asInteger() {
            return (int) value;
        }

        @Override
        public long asLong() {
            return value;
        }

        @Override
        public float asFloat() {
            return value;
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public BigInteger asBigInteger() {
            return BigInteger.valueOf(value);
        }

        @Override
        public BigDecimal asBigDecimal() {
            return BigDecimal.valueOf((double) value);
        }

        @Override
        public Instant asTimestamp() {
            return TimestampResolver.readTimestamp(value, settings.timestampResolver().defaultFormat());
        }

        @Override
        public ShapeDeserializer createDeserializer() {
            return new JsonDocumentDeserializer(settings, this);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeLong(PreludeSchemas.LONG, value);
        }

        @Override
        public boolean equals(Object obj) {
            return Document.equals(this, obj);
        }
    }

    record FloatDocument(float value, JsonSettings settings) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.FLOAT;
        }

        @Override
        public byte asByte() {
            return (byte) value;
        }

        @Override
        public short asShort() {
            return (short) value;
        }

        @Override
        public int asInteger() {
            return (int) value;
        }

        @Override
        public long asLong() {
            return (long) value;
        }

        @Override
        public float asFloat() {
            return value;
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public BigInteger asBigInteger() {
            return BigInteger.valueOf((long) value);
        }

        @Override
        public BigDecimal asBigDecimal() {
            return BigDecimal.valueOf(value);
        }

        @Override
        public Instant asTimestamp() {
            return TimestampResolver.readTimestamp(value, settings.timestampResolver().defaultFormat());
        }

        @Override
        public ShapeDeserializer createDeserializer() {
            return new JsonDocumentDeserializer(settings, this);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeFloat(PreludeSchemas.FLOAT, value);
        }

        @Override
        public boolean equals(Object obj) {
            return Document.equals(this, obj);
        }
    }

    record DoubleDocument(double value, JsonSettings settings) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.DOUBLE;
        }

        @Override
        public byte asByte() {
            return (byte) value;
        }

        @Override
        public short asShort() {
            return (short) value;
        }

        @Override
        public int asInteger() {
            return (int) value;
        }

        @Override
        public long asLong() {
            return (long) value;
        }

        @Override
        public float asFloat() {
            return (float) value;
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public BigInteger asBigInteger() {
            return BigInteger.valueOf((long) value);
        }

        @Override
        public BigDecimal asBigDecimal() {
            return BigDecimal.valueOf(value);
        }

        @Override
        public Instant asTimestamp() {
            return TimestampResolver.readTimestamp(value, settings.timestampResolver().defaultFormat());
        }

        @Override
        public ShapeDeserializer createDeserializer() {
            return new JsonDocumentDeserializer(settings, this);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeDouble(PreludeSchemas.DOUBLE, value);
        }

        @Override
        public boolean equals(Object obj) {
            return Document.equals(this, obj);
        }
    }

    record BooleanDocument(boolean value, JsonSettings settings) implements Document {
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

        @Override
        public ShapeDeserializer createDeserializer() {
            return new JsonDocumentDeserializer(settings, this);
        }

        @Override
        public boolean equals(Object obj) {
            return Document.equals(this, obj);
        }
    }

    record ListDocument(List<Document> values, JsonSettings settings) implements Document {
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
        public ShapeDeserializer createDeserializer() {
            return new JsonDocumentDeserializer(settings, this);
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

        @Override
        public boolean equals(Object obj) {
            return Document.equals(this, obj);
        }
    }

    record MapDocument(Map<String, Document> values, JsonSettings settings) implements Document {
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
        public ShapeDeserializer createDeserializer() {
            return new JsonDocumentDeserializer(settings, this);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeMap(PreludeSchemas.DOCUMENT, values, values.size(), (stringMap, mapSerializer) -> {
                for (var e : stringMap.entrySet()) {
                    mapSerializer.writeEntry(STRING_MAP_KEY, e.getKey(), e.getValue(), (document, ser) -> {
                        if (document == null) {
                            ser.writeNull(PreludeSchemas.DOCUMENT);
                        } else {
                            document.serializeContents(ser);
                        }
                    });
                }
            });
        }

        @Override
        public boolean equals(Object obj) {
            return Document.equals(this, obj);
        }
    }

    /**
     * Customized version of DocumentDeserializer to account for the settings of the JSON codec.
     */
    private static final class JsonDocumentDeserializer extends DocumentDeserializer {

        private final JsonSettings settings;

        JsonDocumentDeserializer(JsonSettings settings, Document value) {
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
