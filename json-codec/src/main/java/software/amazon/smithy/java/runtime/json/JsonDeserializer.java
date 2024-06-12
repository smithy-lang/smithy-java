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
        } catch (JsonException | IOException | IllegalArgumentException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public byte readByte(Schema schema) {
        try {
            return (byte) iter.readShort();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public short readShort(Schema schema) {
        try {
            return iter.readShort();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public int readInteger(Schema schema) {
        try {
            return iter.readInt();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public long readLong(Schema schema) {
        try {
            return iter.readLong();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public float readFloat(Schema schema) {
        try {
            return iter.readFloat();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public double readDouble(Schema schema) {
        try {
            return iter.readDouble();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        try {
            return iter.readBigInteger();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        try {
            return iter.readBigDecimal();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public String readString(Schema schema) {
        try {
            return iter.readString();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public boolean readBoolean(Schema schema) {
        try {
            return iter.readBoolean();
        } catch (JsonException | IOException e) {
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
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        try {
            var format = timestampResolver.resolve(schema);
            return TimestampResolver.readTimestamp(iter.readAny(), format);
        } catch (JsonException | IOException e) {
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
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> listMemberConsumer) {
        try {
            while (iter.readArray()) {
                listMemberConsumer.accept(state, this);
            }
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> mapMemberConsumer) {
        try {
            for (var field = iter.readObject(); field != null; field = iter.readObject()) {
                mapMemberConsumer.accept(state, field, this);
            }
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }
}
