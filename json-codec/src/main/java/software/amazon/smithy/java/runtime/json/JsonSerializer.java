/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import com.jsoniter.output.JsonStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.ListSerializer;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class JsonSerializer implements ShapeSerializer {

    private final boolean useJsonName;
    private final JsonStream stream;
    private final TimestampFormatter defaultTimestampFormat;
    private final boolean useTimestampFormat;

    JsonSerializer(
        OutputStream sink,
        boolean useJsonName,
        TimestampFormatter defaultTimestampFormat,
        boolean useTimestampFormat
    ) {
        this.useJsonName = useJsonName;
        this.stream = new JsonStream(sink, 2048);
        this.useTimestampFormat = useTimestampFormat;
        this.defaultTimestampFormat = defaultTimestampFormat;
    }

    @Override
    public void flush() {
        try {
            stream.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeShort(Schema schema, short value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeBlob(Schema schema, byte[] value) {
        try {
            stream.writeVal(Base64.getEncoder().encodeToString(value));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeLong(Schema schema, long value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeString(Schema schema, String value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        var formatter = useTimestampFormat && schema.hasTrait(TimestampFormatTrait.class)
            ? TimestampFormatter.of(schema.getTrait(TimestampFormatTrait.class))
            : defaultTimestampFormat;
        formatter.serializeToUnderlyingFormat(schema, value, this);
    }

    @Override
    public void writeStruct(Schema schema, Consumer<ShapeSerializer> consumer) {
        try {
            stream.writeObjectStart();
            consumer.accept(new JsonStructSerializer(this, stream, useJsonName));
            stream.writeObjectEnd();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeList(Schema schema, Consumer<ShapeSerializer> consumer) {
        try {
            stream.writeArrayStart();
            consumer.accept(new ListSerializer(this, this::writeComma));
            stream.writeArrayEnd();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeComma(int position) {
        if (position > 0) {
            try {
                stream.writeMore();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void writeMap(Schema schema, Consumer<MapSerializer> consumer) {
        try {
            stream.writeObjectStart();
            consumer.accept(new JsonMapSerializer(this, stream));
            stream.writeObjectEnd();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        // Document values in JSON are serialized inline by receiving the data model contents of the document.
        value.serializeContents(this);
    }

    @Override
    public void writeNull(Schema schema) {
        try {
            stream.writeNull();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
