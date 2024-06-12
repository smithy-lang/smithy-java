/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import com.jsoniter.output.JsonStream;
import com.jsoniter.spi.JsonException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ListSerializer;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

final class JsonSerializer implements ShapeSerializer {

    JsonStream stream;
    final JsonFieldMapper fieldMapper;
    final TimestampResolver timestampResolver;
    private final Consumer<JsonStream> returnHandle;

    JsonSerializer(
        JsonStream stream,
        JsonFieldMapper fieldMapper,
        TimestampResolver timestampResolver,
        Consumer<JsonStream> returnHandle
    ) {
        this.stream = stream;
        this.timestampResolver = timestampResolver;
        this.fieldMapper = fieldMapper;
        this.returnHandle = returnHandle;
    }

    @Override
    public void flush() {
        try {
            stream.flush();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void close() {
        try {
            stream.close();
            returnHandle.accept(stream);
            stream = null;
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        try {
            stream.writeVal(value);
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        try {
            stream.writeVal(value);
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeShort(Schema schema, short value) {
        try {
            stream.writeVal(value);
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        try {
            stream.writeVal(Base64.getEncoder().encodeToString(value));
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        try {
            stream.writeVal(value);
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeLong(Schema schema, long value) {
        try {
            stream.writeVal(value);
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        try {
            stream.writeVal(value);
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        try {
            stream.writeVal(value);
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        try {
            stream.writeVal(value);
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        try {
            stream.writeVal(value);
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeString(Schema schema, String value) {
        try {
            stream.writeVal(value);
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        timestampResolver.resolve(schema).writeToSerializer(schema, value, this);
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        try {
            stream.writeObjectStart();
            struct.serializeMembers(new JsonStructSerializer(this, true));
            stream.writeObjectEnd();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void writeList(Schema schema, T listState, BiConsumer<T, ShapeSerializer> consumer) {
        try {
            stream.writeArrayStart();
            consumer.accept(listState, new ListSerializer(this, this::writeComma));
            stream.writeArrayEnd();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    private void writeComma(int position) {
        if (position > 0) {
            try {
                stream.writeMore();
            } catch (JsonException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, BiConsumer<T, MapSerializer> consumer) {
        try {
            stream.writeObjectStart();
            consumer.accept(mapState, new JsonMapSerializer(this, stream));
            stream.writeObjectEnd();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        // Document values in JSON are serialized inline by receiving the data model contents of the document.
        if (value.type() != ShapeType.STRUCTURE) {
            value.serializeContents(this);
        } else {
            value.serializeContents(new SpecificShapeSerializer() {
                @Override
                public void writeStruct(Schema schema, SerializableStruct struct) {
                    try {
                        stream.writeObjectStart();
                        stream.writeObjectField("__type");
                        stream.writeVal(schema.id().toString());
                        struct.serializeMembers(new JsonStructSerializer(JsonSerializer.this, false));
                        stream.writeObjectEnd();
                    } catch (IOException | JsonException e) {
                        throw new SerializationException(e);
                    }
                }
            });
        }
    }

    @Override
    public void writeNull(Schema schema) {
        try {
            stream.writeNull();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }
}
