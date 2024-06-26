/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jackson;

import static com.fasterxml.jackson.core.JsonToken.END_ARRAY;
import static com.fasterxml.jackson.core.JsonToken.END_OBJECT;
import static com.fasterxml.jackson.core.JsonToken.VALUE_NULL;
import static com.fasterxml.jackson.core.JsonToken.VALUE_STRING;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Locale;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.json.JsonCodec;
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
            if (parser.currentToken().isNumeric()) {
                return parser.getFloatValue();
            }
            if (parser.getCurrentToken() == VALUE_STRING) {
                return Float.parseFloat(parser.getText().trim());
            }
            throw new SerializationException("Unexpected token: " + parser.getCurrentToken());
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public double readDouble(Schema schema) {
        try {
            if (parser.currentToken().isNumeric()) {
                return parser.getDoubleValue();
            }
            if (parser.getCurrentToken() == VALUE_STRING) {
                return Double.parseDouble(parser.getText().trim());
            }
            throw new SerializationException("Unexpected token: " + parser.getCurrentToken());
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
    public JacksonDocument readDocument() {
        try {
            return new JacksonDocument(parser.readValueAsTree(), settings);
        } catch (Exception e) {
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
                    // expensive, but short way to match the json-iter error
                    "Expected a timestamp, but found " + ((JsonNode) parser.readValueAsTree())
                        .getNodeType()
                        .name()
                        .toLowerCase(Locale.US)
                );
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        try {
            if (parser.currentToken() == null) {
                parser.nextToken();
            }
            for (var token = parser.nextToken(); token != END_OBJECT; token = parser.nextToken()) {
                var memberName = parser.getText();
                token = parser.nextToken();
                var member = settings.fieldMapper().fieldToMember(schema, memberName);
                if (member != null) {
                    if (token != VALUE_NULL) {
                        structMemberConsumer.accept(state, member, this);
                    }
                } else {
                    if (schema.type() != ShapeType.UNION || !settings.forbidUnknownUnionMembers()) {
                        structMemberConsumer.unknownMember(state, memberName);
                    } else {
                        throw new SerializationException("Unknown member " + memberName + " encountered");
                    }
                    if (token.isStructStart()) {
                        parser.skipChildren();
                    }
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
            for (var token = parser.nextToken(); token != END_OBJECT; token = parser.nextToken()) {
                var key = parser.getText();
                parser.nextToken();
                mapMemberConsumer.accept(state, key, this);
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
