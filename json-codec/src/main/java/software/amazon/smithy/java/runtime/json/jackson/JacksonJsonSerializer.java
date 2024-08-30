/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeType;

final class JacksonJsonSerializer implements ShapeSerializer {

    private JsonGenerator generator;
    private final JsonCodec.Settings settings;
    private SerializeDocumentContents serializeDocumentContents;
    private final ShapeSerializer structSerializer = new JsonStructSerializer();

    JacksonJsonSerializer(
        JsonGenerator generator,
        JsonCodec.Settings settings
    ) {
        this.generator = generator;
        this.settings = settings;
    }

    @Override
    public void flush() {
        try {
            generator.flush();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void close() {
        try {
            generator.close();
            generator = null;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        try {
            generator.writeBoolean(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        try {
            generator.writeNumber(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeShort(Schema schema, short value) {
        try {
            generator.writeNumber(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        try {
            generator.writeBinary(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        try {
            generator.writeBinary(new ByteBufferBackedInputStream(value), value.remaining());
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        try {
            generator.writeNumber(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeLong(Schema schema, long value) {
        try {
            generator.writeNumber(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        try {
            if (Float.isFinite(value)) {
                int intValue = (int) value;
                if (value - intValue > 0) {
                    generator.writeNumber(value);
                } else {
                    generator.writeNumber(intValue);
                }
            } else if (Float.isNaN(value)) {
                generator.writeString("NaN");
            } else if (Float.POSITIVE_INFINITY == value) {
                generator.writeString("Infinity");
            } else {
                generator.writeString("-Infinity");
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        try {
            if (Double.isFinite(value)) {
                long longValue = (long) value;
                if (value - longValue > 0) {
                    generator.writeNumber(value);
                } else {
                    generator.writeNumber(longValue);
                }
            } else if (Double.isNaN(value)) {
                generator.writeString("NaN");
            } else if (Double.POSITIVE_INFINITY == value) {
                generator.writeString("Infinity");
            } else {
                generator.writeString("-Infinity");
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        try {
            generator.writeNumber(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        try {
            generator.writeNumber(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeString(Schema schema, String value) {
        try {
            generator.writeString(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        settings.timestampResolver().resolve(schema).writeToSerializer(schema, value, this);
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        try {
            generator.writeStartObject();
            struct.serializeMembers(structSerializer);
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private final class JsonStructSerializer extends InterceptingSerializer {
        @Override
        protected ShapeSerializer before(Schema schema) {
            try {
                final String fieldName = settings.fieldMapper().memberToField(schema);
                generator.writeFieldName(JacksonJsonSerdeProvider.SERIALIZED_STRINGS.create(fieldName));
                return JacksonJsonSerializer.this;
            } catch (IOException e) {
                throw new SerializationException(e);
            }
        }
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        try {
            generator.writeStartArray();
            consumer.accept(listState, this);
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        try {
            generator.writeStartObject();
            consumer.accept(mapState, new JsonMapSerializer());
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private final class JsonMapSerializer implements MapSerializer {
        @Override
        public <T> void writeEntry(
            Schema keySchema,
            String key,
            T state,
            BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            try {
                generator.writeFieldName(key);
                valueSerializer.accept(state, JacksonJsonSerializer.this);
            } catch (IOException e) {
                throw new SerializationException(e);
            }
        }
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        // Document values in JSON are serialized inline by receiving the data model contents of the document.
        if (value.type() != ShapeType.STRUCTURE) {
            value.serializeContents(this);
        } else {
            if (serializeDocumentContents == null) {
                serializeDocumentContents = new SerializeDocumentContents(this);
            }
            value.serializeContents(serializeDocumentContents);
        }
    }

    private static final class SerializeDocumentContents extends SpecificShapeSerializer {
        private final JacksonJsonSerializer parent;

        SerializeDocumentContents(JacksonJsonSerializer parent) {
            this.parent = parent;
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            try {
                parent.generator.writeStartObject();
                parent.generator.writeStringField("__type", schema.id().toString());
                struct.serializeMembers(parent.structSerializer);
                parent.generator.writeEndObject();
            } catch (Exception e) {
                throw new SerializationException(e);
            }
        }
    }

    @Override
    public void writeNull(Schema schema) {
        try {
            generator.writeNull();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }
}
