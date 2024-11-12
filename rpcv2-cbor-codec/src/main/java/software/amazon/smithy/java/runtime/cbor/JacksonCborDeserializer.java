/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.cbor;

import static com.fasterxml.jackson.core.JsonToken.END_ARRAY;
import static com.fasterxml.jackson.core.JsonToken.VALUE_NULL;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

final class JacksonCborDeserializer implements ShapeDeserializer {

    private CBORParser parser;
    private final Rpcv2CborCodec.Settings settings;

    JacksonCborDeserializer(
        CBORParser parser,
        Rpcv2CborCodec.Settings settings
    ) {
        try {
            this.parser = parser;
            this.settings = settings;
            this.parser.nextToken();
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void close() {
        if (parser != null && !parser.isClosed()) {
            try {
                // Close the parser, but also ensure there's no trailing garbage input.
                var nextToken = parser.nextToken();
                parser.close();
                parser = null;
                if (nextToken != null) {
                    throw new SerializationException("Unexpected CBOR content: " + describeToken());
                }
            } catch (SerializationException e) {
                throw e;
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }
    }

    @Override
    public ByteBuffer readBlob(Schema schema) {
        try {
            return ByteBuffer.wrap(parser.getBinaryValue());
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
            return parser.getFloatValue();
        } catch (SerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public double readDouble(Schema schema) {
        try {
            return parser.getDoubleValue();
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
        throw new UnsupportedOperationException();
    }

    private String describeToken() {
        return JsonToken.valueDescFor(parser.currentToken());
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        try {
            var current = parser.currentToken();
            if (current == JsonToken.VALUE_NUMBER_INT) {
                return Instant.ofEpochMilli(parser.getLongValue() * 1000);
            } else if (current == JsonToken.VALUE_NUMBER_FLOAT) {
                return Instant.ofEpochMilli(Math.round(parser.getDoubleValue() * 1000d));
            } else {
                throw new SerializationException("Expected a timestamp, but found " + describeToken());
            }
        } catch (SerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        try {
            for (var memberName = parser.nextFieldName(); memberName != null; memberName = parser.nextFieldName()) {
                if (parser.nextToken() != VALUE_NULL) {
                    var member = schema.member(memberName);
                    if (member != null) {
                        structMemberConsumer.accept(state, member, this);
                    } else if (schema.type() == ShapeType.STRUCTURE) {
                        structMemberConsumer.unknownMember(state, memberName);
                        parser.skipChildren();
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
