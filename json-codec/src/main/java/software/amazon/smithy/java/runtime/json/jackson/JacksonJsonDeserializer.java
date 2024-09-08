/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jackson;

import static com.fasterxml.jackson.core.JsonToken.END_ARRAY;
import static com.fasterxml.jackson.core.JsonToken.VALUE_NULL;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.json.JsonCodec;
import software.amazon.smithy.java.runtime.json.JsonDocuments;
import software.amazon.smithy.java.runtime.json.TimestampResolver;
import software.amazon.smithy.model.shapes.ShapeType;

final class JacksonJsonDeserializer implements ShapeDeserializer {

    private JsonParser parser;
    private final JsonCodec.Settings settings;

    JacksonJsonDeserializer(
        JsonParser parser,
        JsonCodec.Settings settings
    ) {
        this.parser = parser;
        this.settings = settings;
        try {
            this.parser.nextToken();
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void close() {
        try {
            parser.close();
            parser = null;
        } catch (Exception e) {
            throw new SerializationException(e);
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
                case VALUE_STRING -> Float.parseFloat(parser.getText().trim()); // TODO: is this right?
                default -> throw new SerializationException("Unexpected token: " + parser.getCurrentToken());
            };
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public double readDouble(Schema schema) {
        try {
            return switch (parser.currentToken()) {
                case VALUE_NUMBER_FLOAT, VALUE_NUMBER_INT -> parser.getDoubleValue();
                case VALUE_STRING -> Double.parseDouble(parser.getText().trim()); // TODO: is this right?
                default -> throw new SerializationException("Unexpected token: " + parser.getCurrentToken());
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
            return parser.getBigIntegerValue();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        try {
            return parser.getDecimalValue();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public String readString(Schema schema) {
        try {
            return parser.getText();
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
            return switch (parser.currentToken()) {
                case VALUE_NULL -> null;
                case VALUE_STRING -> JsonDocuments.createString(parser.getText(), settings);
                case VALUE_TRUE -> JsonDocuments.createBoolean(true, settings);
                case VALUE_FALSE -> JsonDocuments.createBoolean(false, settings);
                case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> JsonDocuments.createNumber(
                    parser.getNumberValue(),
                    settings
                );
                case START_ARRAY -> {
                    List<Document> values = new ArrayList<>();
                    for (var token = parser.nextToken(); token != END_ARRAY; token = parser.nextToken()) {
                        values.add(readDocument());
                    }
                    yield JsonDocuments.createList(values, settings);
                }
                case START_OBJECT -> {
                    Map<String, Document> values = new LinkedHashMap<>();
                    for (var field = parser.nextFieldName(); field != null; field = parser.nextFieldName()) {
                        parser.nextToken();
                        values.put(field, readDocument());
                    }
                    yield JsonDocuments.createMap(values, settings);
                }
                default -> throw new SerializationException("Unexpected token: " + parser.getCurrentToken());
            };
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        try {
            var format = settings.timestampResolver().resolve(schema);
            if (parser.getCurrentToken() == JsonToken.VALUE_NUMBER_FLOAT
                || parser.getCurrentToken() == JsonToken.VALUE_NUMBER_INT) {
                return TimestampResolver.readTimestamp(parser.getNumberValue(), format);
            } else if (parser.getCurrentToken() == JsonToken.VALUE_STRING) {
                return TimestampResolver.readTimestamp(parser.getText(), format);
            } else {
                throw new SerializationException(
                    "Expected a timestamp, but found " + JsonToken.valueDescFor(parser.currentToken())
                );
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        try {
            for (var memberName = parser.nextFieldName(); memberName != null; memberName = parser.nextFieldName()) {
                var token = parser.nextToken();
                var member = settings.fieldMapper().fieldToMember(schema, memberName);
                if (member == null) {
                    if (schema.type() != ShapeType.UNION || !settings.forbidUnknownUnionMembers()) {
                        structMemberConsumer.unknownMember(state, memberName);
                    } else {
                        throw new SerializationException("Unknown member " + memberName + " encountered");
                    }
                    // Don't parse members of unknown members.
                    parser.skipChildren();
                } else if (token != VALUE_NULL) {
                    structMemberConsumer.accept(state, member, this);
                }
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> listMemberConsumer) {
        try {
            for (var token = parser.nextToken(); token != END_ARRAY; token = parser.nextToken()) {
                listMemberConsumer.accept(state, this);
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> mapMemberConsumer) {
        try {
            for (var fieldName = parser.nextFieldName(); fieldName != null; fieldName = parser.nextFieldName()) {
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
}
