/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.jackson;

import static tools.jackson.core.JsonToken.END_ARRAY;
import static tools.jackson.core.JsonToken.END_OBJECT;
import static tools.jackson.core.JsonToken.VALUE_NULL;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.BigNumbers;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonDocuments;
import software.amazon.smithy.java.json.JsonSchemaExtensions;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.model.shapes.ShapeType;
import tools.jackson.core.Base64Variants;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

final class JacksonJsonDeserializer implements ShapeDeserializer {

    private JsonParser parser;
    private final JsonSettings settings;

    // Set by readDiscriminatedUnion immediately before invoking the resolved variant's StructMemberConsumer.
    // When set, the next readStruct call reads the variant's fields from the still-open union object, where
    // the parser sits on the discriminator value, rather than from an object of its own.
    private boolean flatStructContinuation;

    // Fields that preceded the discriminator, read as documents and replayed into the variant before it reads
    // the rest of the object from the parser. Allocated on first use, since most input puts the discriminator
    // first.
    private PendingFields pending;

    private static final class PendingFields {
        String[] names = new String[4];
        Document[] values = new Document[4];
        int count;

        void add(String name, Document value) {
            if (count == names.length) {
                names = Arrays.copyOf(names, count * 2);
                values = Arrays.copyOf(values, count * 2);
            }
            names[count] = name;
            values[count] = value;
            count++;
        }

        void clear() {
            Arrays.fill(names, 0, count, null);
            Arrays.fill(values, 0, count, null);
            count = 0;
        }
    }

    JacksonJsonDeserializer(
            JsonParser parser,
            JsonSettings settings
    ) {
        this.parser = parser;
        this.settings = settings;
        try {
            this.parser.nextToken();
        } catch (JacksonException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void close() {
        if (parser != null && !parser.isClosed()) {
            try {
                var nextToken = parser.nextToken();
                // Capture token description before nulling parser to avoid NPE in describeToken().
                var tokenDesc = nextToken != null ? JsonToken.valueDescFor(nextToken) : null;
                parser.close();
                parser = null;
                if (tokenDesc != null) {
                    throw new SerializationException("Unexpected JSON content: " + tokenDesc);
                }
            } catch (SerializationException e) {
                throw e;
            } catch (Exception e) {
                parser = null;
                throw new SerializationException(e);
            }
        }
    }

    @Override
    public ByteBuffer readBlob(Schema schema) {
        try {
            return ByteBuffer.wrap(parser.getBinaryValue(Base64Variants.MIME_NO_LINEFEEDS));
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public byte readByte(Schema schema) {
        try {
            return parser.getByteValue();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public short readShort(Schema schema) {
        try {
            return parser.getShortValue();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public int readInteger(Schema schema) {
        try {
            return parser.getIntValue();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public long readLong(Schema schema) {
        try {
            return parser.getLongValue();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public float readFloat(Schema schema) {
        try {
            return switch (parser.currentToken()) {
                case VALUE_NUMBER_FLOAT, VALUE_NUMBER_INT -> parser.getFloatValue();
                case VALUE_STRING -> switch (parser.getString()) {
                    case "Infinity" -> Float.POSITIVE_INFINITY;
                    case "-Infinity" -> Float.NEGATIVE_INFINITY;
                    case "NaN" -> Float.NaN;
                    default -> throw new SerializationException("Expected float, found: " + describeToken());
                };
                default -> throw new SerializationException("Expected float, found: " + describeToken());
            };
        } catch (SerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public double readDouble(Schema schema) {
        try {
            return switch (parser.currentToken()) {
                case VALUE_NUMBER_FLOAT, VALUE_NUMBER_INT -> parser.getDoubleValue();
                case VALUE_STRING -> switch (parser.getString()) {
                    case "Infinity" -> Double.POSITIVE_INFINITY;
                    case "-Infinity" -> Double.NEGATIVE_INFINITY;
                    case "NaN" -> Double.NaN;
                    default -> throw new SerializationException("Expected double, found: " + describeToken());
                };
                default -> throw new SerializationException("Expected double, found: " + describeToken());
            };
        } catch (SerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        try {
            if (settings.useStringForArbitraryPrecision()) {
                if (parser.currentToken() != JsonToken.VALUE_STRING) {
                    throw new SerializationException("Expected string, found: " + describeToken());
                }
                // Jackson limits numeric tokens. String tokens need the same digit limit.
                return BigNumbers.parseBigInteger(parser.getText());
            }
            return BigNumbers.checkBigIntegerDigits(parser.getBigIntegerValue());
        } catch (SerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        try {
            if (settings.useStringForArbitraryPrecision()) {
                if (parser.currentToken() != JsonToken.VALUE_STRING) {
                    throw new SerializationException("Expected string, found: " + describeToken());
                }
                // Jackson limits numeric tokens. String tokens need the same digit limit.
                return BigNumbers.parseBigDecimal(parser.getText());
            }
            return BigNumbers.checkBigDecimalDigits(parser.getDecimalValue());
        } catch (SerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public String readString(Schema schema) {
        try {
            return parser.getString();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public boolean readBoolean(Schema schema) {
        try {
            return parser.getBooleanValue();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public Document readDocument() {
        try {
            var token = parser.currentToken();
            if (token == null) {
                throw new SerializationException("Expected a JSON value");
            }
            return switch (token) {
                case VALUE_NULL -> null;
                case VALUE_STRING -> JsonDocuments.of(parser.getString(), settings);
                case VALUE_TRUE -> JsonDocuments.of(true, settings);
                case VALUE_FALSE -> JsonDocuments.of(false, settings);
                case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> JsonDocuments.of(
                        boundedNumber(parser.getNumberValue()),
                        settings);
                case START_ARRAY -> {
                    List<Document> values = new ArrayList<>();
                    for (token = parser.nextToken(); token != END_ARRAY; token = parser.nextToken()) {
                        values.add(readDocument());
                    }
                    yield JsonDocuments.of(values, settings);
                }
                case START_OBJECT -> {
                    Map<String, Document> values = new LinkedHashMap<>();
                    for (var field = parser.nextName(); field != null; field = parser.nextName()) {
                        parser.nextToken();
                        values.put(field, readDocument());
                    }
                    yield JsonDocuments.of(values, settings);
                }
                default -> throw new SerializationException("Unexpected token: " + describeToken());
            };
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private String describeToken() {
        return JsonToken.valueDescFor(parser.currentToken());
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        try {
            var format = settings.timestampResolver().resolve(schema);
            return switch (parser.currentToken()) {
                case VALUE_NUMBER_FLOAT, VALUE_NUMBER_INT -> format.readFromNumber(parser.getNumberValue());
                case VALUE_STRING -> format.readFromString(parser.getString(), true);
                default -> throw new SerializationException("Expected a timestamp, but found " + describeToken());
            };
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        try {
            if (flatStructContinuation) {
                // The parser sits on the discriminator value inside the union's object. Replay any fields that
                // preceded the discriminator, then read the rest of the object below.
                flatStructContinuation = false;
                if (pending != null && pending.count > 0) {
                    replayPendingFields(schema, state, structMemberConsumer);
                }
            } else {
                var discriminated = JacksonJsonSerializer.discriminatedData(schema);
                if (discriminated != null) {
                    readDiscriminatedUnion(discriminated, state, structMemberConsumer);
                    return;
                }
            }
            var fieldToMember = settings.fieldMapper().fieldToMember(schema);
            for (var memberName = parser.nextName(); memberName != null; memberName = parser.nextName()) {
                if (parser.nextToken() != VALUE_NULL) {
                    var member = fieldToMember.member(memberName);
                    if (member != null) {
                        structMemberConsumer.accept(state, member, this);
                    } else if (schema.type() == ShapeType.STRUCTURE) {
                        structMemberConsumer.unknownMember(state, memberName);
                        parser.skipChildren();
                    } else if (memberName.equals("__type")) {
                        // Ignore __type on unknown union members.
                        parser.skipChildren();
                    } else if (settings.forbidUnknownUnionMembers()) {
                        throw new SerializationException("Unknown member " + memberName + " encountered");
                    } else {
                        structMemberConsumer.unknownMember(state, memberName);
                        parser.skipChildren();
                    }
                }
            }
        } catch (SerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    /**
     * Reads a {@code @discriminated} union. On entry the current token is the union object's START_OBJECT.
     *
     * <p>The parser cannot be rewound, so the fields in front of the discriminator, if any, are read as
     * documents. Once the discriminator resolves the variant, the variant's readStruct replays those documents
     * and then reads the remaining fields straight from the parser. When the discriminator comes first, which
     * is what serializers SHOULD produce, nothing is materialized.
     */
    private <T> void readDiscriminatedUnion(
            JsonSchemaExtensions.DiscriminatedData discriminated,
            T state,
            StructMemberConsumer<T> structMemberConsumer
    ) throws Exception {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new SerializationException("Expected an object for a discriminated union, but found "
                    + describeToken());
        }
        String fieldName = discriminated.discriminatorFieldName();
        try {
            for (var name = parser.nextName(); name != null; name = parser.nextName()) {
                var token = parser.nextToken();
                if (!name.equals(fieldName)) {
                    // Null fields are skipped, as in the regular struct loop.
                    if (token != VALUE_NULL) {
                        if (pending == null) {
                            pending = new PendingFields();
                        }
                        pending.add(name, readDocument());
                    }
                    continue;
                }

                if (token != JsonToken.VALUE_STRING) {
                    if (token == VALUE_NULL) {
                        throw missingDiscriminator(fieldName);
                    }
                    throw new SerializationException(
                            "Discriminated union discriminator field '" + fieldName + "' must be a string");
                }
                String value = parser.getString();
                Schema variant = discriminated.valueLookup().member(value);
                if (variant == null) {
                    // Skip the rest of the object, then record the unknown variant, as for a standard union.
                    clearPending();
                    for (var rest = parser.nextName(); rest != null; rest = parser.nextName()) {
                        parser.nextToken();
                        parser.skipChildren();
                    }
                    if (settings.forbidUnknownUnionMembers()) {
                        throw new SerializationException("Unknown discriminator value " + value + " encountered");
                    }
                    structMemberConsumer.unknownMember(state, value);
                    return;
                }

                flatStructContinuation = true;
                structMemberConsumer.accept(state, variant, this);
                if (flatStructContinuation) {
                    throw new SerializationException("Discriminated union variant " + variant.memberName()
                            + " was not read as a structure");
                }
                return;
            }
            // Without a discriminator, including in an empty object, no variant can be determined.
            throw missingDiscriminator(fieldName);
        } catch (Throwable e) {
            // Reset the state, so a caller that keeps using this deserializer after an error does not see a stale
            // continuation or captured fields.
            flatStructContinuation = false;
            clearPending();
            throw e;
        }
    }

    /**
     * Feeds the fields that preceded the discriminator to the resolved variant. The entries are taken and
     * cleared first, so a discriminated union nested in the remaining fields can reuse the holder.
     */
    private <T> void replayPendingFields(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        var names = pending.names;
        var values = pending.values;
        int count = pending.count;
        pending.count = 0;
        var fieldToMember = settings.fieldMapper().fieldToMember(schema);
        for (int i = 0; i < count; i++) {
            String name = names[i];
            Document value = values[i];
            names[i] = null;
            values[i] = null;
            var member = fieldToMember.member(name);
            if (member == null) {
                structMemberConsumer.unknownMember(state, name);
            } else if (value != null) {
                structMemberConsumer.accept(state, member, value.createDeserializer());
            }
        }
    }

    private void clearPending() {
        if (pending != null) {
            pending.clear();
        }
    }

    private static SerializationException missingDiscriminator(String fieldName) {
        return new SerializationException(
                "Discriminated union is missing a string discriminator field '" + fieldName + "'");
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> listMemberConsumer) {
        try {
            if (!parser.isExpectedStartArrayToken()) {
                throw new SerializationException("Expected a list, but found " + describeToken());
            }
            boolean notEoa;
            for (
                    var token = parser.nextToken();
                    (notEoa = (token != END_ARRAY)) && token != END_OBJECT && token != null;
                    token = parser.nextToken()) {
                listMemberConsumer.accept(state, this);
            }
            if (notEoa) {
                throw new SerializationException("Expected end of list, but found " + describeToken());
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> mapMemberConsumer) {
        try {
            for (var fieldName = parser.nextName(); fieldName != null; fieldName = parser.nextName()) {
                parser.nextToken();
                mapMemberConsumer.accept(state, fieldName, this);
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public boolean isNull() {
        return parser.currentToken() == VALUE_NULL;
    }

    @Override
    public <T> T readNull() {
        if (parser.currentToken() != VALUE_NULL) {
            throw new SerializationException("Attempted to read non-null value as null");
        }
        return null;
    }

    // Jackson's token limit does not enforce the significant-digit limit.
    private static Number boundedNumber(Number number) {
        if (number instanceof BigInteger b) {
            return BigNumbers.checkBigIntegerDigits(b);
        } else if (number instanceof BigDecimal d) {
            return BigNumbers.checkBigDecimalDigits(d);
        }
        return number;
    }
}
