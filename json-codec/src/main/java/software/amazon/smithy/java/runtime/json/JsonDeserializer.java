/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import com.jsoniter.JsonIterator;
import com.jsoniter.ValueType;
import com.jsoniter.spi.JsonException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;

final class JsonDeserializer implements ShapeDeserializer {

    private JsonIterator iter;
    private final TimestampResolver timestampResolver;
    private final JsonFieldMapper fieldMapper;
    private final Consumer<JsonIterator> returnHandle;

    JsonDeserializer(
        JsonIterator iter,
        TimestampResolver timestampResolver,
        JsonFieldMapper fieldMapper,
        Consumer<JsonIterator> returnHandle
    ) {
        this.iter = iter;
        this.timestampResolver = timestampResolver;
        this.fieldMapper = fieldMapper;
        this.returnHandle = returnHandle;
    }

    @Override
    public void close() {
        returnHandle.accept(iter);
        iter = null;
    }

    @Override
    public byte[] readBlob(Schema schema) {
        try {
            String content = iter.readString();
            return Base64.getDecoder().decode(content);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public byte readByte(Schema schema) {
        try {
            return (byte) iter.readShort();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public short readShort(Schema schema) {
        try {
            return iter.readShort();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public int readInteger(Schema schema) {
        try {
            return iter.readInt();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public long readLong(Schema schema) {
        try {
            return iter.readLong();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public float readFloat(Schema schema) {
        try {
            if (iter.whatIsNext() == ValueType.STRING) {
                return switch (iter.readString()) {
                    case "NaN" -> Float.NaN;
                    case "-Infinity" -> Float.NEGATIVE_INFINITY;
                    case "Infinity" -> Float.POSITIVE_INFINITY;
                    default -> throw new SerializationException("Expected float, received unrecognized string");
                };
            }
            return iter.readFloat();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public double readDouble(Schema schema) {
        try {
            if (iter.whatIsNext() == ValueType.STRING) {
                return switch (iter.readString()) {
                    case "NaN" -> Double.NaN;
                    case "-Infinity" -> Double.NEGATIVE_INFINITY;
                    case "Infinity" -> Double.POSITIVE_INFINITY;
                    default -> throw new SerializationException("Expected double, received unrecognized string");
                };
            }
            return iter.readDouble();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        try {
            return iter.readBigInteger();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        try {
            return iter.readBigDecimal();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public String readString(Schema schema) {
        try {
            return iter.readString();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public boolean readBoolean(Schema schema) {
        try {
            return iter.readBoolean();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public JsonDocument readDocument() {
        try {
            var any = iter.readAny().mustBeValid();
            // Return a regular null here if the result was null.
            if (any.valueType() == ValueType.NULL) {
                return null;
            } else {
                return new JsonDocument(any, fieldMapper, timestampResolver);
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        try {
            var format = timestampResolver.resolve(schema);
            return TimestampResolver.readTimestamp(iter.readAny(), format);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        try {
            for (var field = iter.readObject(); field != null; field = iter.readObject()) {
                var member = fieldMapper.fieldToMember(schema, field);
                if (member == null) {
                    iter.skip();
                } else {
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
            while (iter.readArray()) {
                listMemberConsumer.accept(state, this);
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> mapMemberConsumer) {
        try {
            for (var field = iter.readObject(); field != null; field = iter.readObject()) {
                mapMemberConsumer.accept(state, field, this);
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public boolean isNull() {
        try {
            return iter.whatIsNext() == ValueType.NULL;
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> T readNull() {
        try {
            if (!iter.readNull()) {
                throw new SerializationException("Attempted to read non-null value as null");
            }
        } catch (IOException e) {
            throw new SerializationException(e);
        }
        return null;
    }
}
