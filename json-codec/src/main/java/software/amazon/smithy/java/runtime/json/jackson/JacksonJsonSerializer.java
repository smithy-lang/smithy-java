/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ListSerializer;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeType;

final class JacksonJsonSerializer implements ShapeSerializer {

    JsonGenerator generator;
    final JsonCodec.Settings settings;

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
            generator.writeString(Base64.getEncoder().encodeToString(value));
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
            if (Float.isNaN(value)) {
                generator.writeString("NaN");
            } else if (Float.isInfinite(value)) {
                if (Float.POSITIVE_INFINITY == value) {
                    generator.writeString("Infinity");
                } else {
                    generator.writeString("-Infinity");
                }
            } else {
                int intValue = (int) value;
                if (value - intValue > 0) {
                    generator.writeNumber(value);
                } else {
                    generator.writeNumber(intValue);
                }
            }
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        try {
            if (Double.isNaN(value)) {
                generator.writeString("NaN");
            } else if (Double.isInfinite(value)) {
                if (Double.POSITIVE_INFINITY == value) {
                    generator.writeString("Infinity");
                } else {
                    generator.writeString("-Infinity");
                }
            } else {
                long longValue = (long) value;
                if (value - longValue > 0) {
                    generator.writeNumber(value);
                } else {
                    generator.writeNumber(longValue);
                }
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
            struct.serializeMembers(new JacksonStructSerializer(this));
            generator.writeEndObject();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void writeList(Schema schema, T listState, BiConsumer<T, ShapeSerializer> consumer) {
        try {
            generator.writeStartArray();
            consumer.accept(listState, new ListSerializer(this, (pos) -> {}));
            generator.writeEndArray();
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, BiConsumer<T, MapSerializer> consumer) {
        try {
            generator.writeStartObject();
            consumer.accept(mapState, new JacksonMapSerializer(this));
            generator.writeEndObject();
        } catch (Exception e) {
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
                        generator.writeStartObject();
                        generator.writeStringField("__type", schema.id().toString());
                        struct.serializeMembers(new JacksonStructSerializer(JacksonJsonSerializer.this));
                        generator.writeEndObject();
                    } catch (Exception e) {
                        throw new SerializationException(e);
                    }
                }
            });
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
