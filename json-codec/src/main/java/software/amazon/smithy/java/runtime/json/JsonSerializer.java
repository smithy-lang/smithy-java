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
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
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
    public void writeBoolean(SdkSchema schema, boolean value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeByte(SdkSchema schema, byte value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeShort(SdkSchema schema, short value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeBlob(SdkSchema schema, byte[] value) {
        try {
            stream.writeVal(Base64.getEncoder().encodeToString(value));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeInteger(SdkSchema schema, int value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeLong(SdkSchema schema, long value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeFloat(SdkSchema schema, float value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeDouble(SdkSchema schema, double value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeBigInteger(SdkSchema schema, BigInteger value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeString(SdkSchema schema, String value) {
        try {
            stream.writeVal(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeTimestamp(SdkSchema schema, Instant value) {
        var formatter = useTimestampFormat && schema.hasTrait(TimestampFormatTrait.class)
            ? TimestampFormatter.of(schema.getTrait(TimestampFormatTrait.class))
            : defaultTimestampFormat;
        formatter.serializeToUnderlyingFormat(schema, value, this);
    }

    @Override
    public <T> void writeStruct(SdkSchema schema, T structState, BiConsumer<T, ShapeSerializer> consumer) {
        try {
            stream.writeObjectStart();
            consumer.accept(structState, new JsonStructSerializer(this, stream, useJsonName));
            stream.writeObjectEnd();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public <T> void writeList(SdkSchema schema, T listState, BiConsumer<T, ShapeSerializer> consumer) {
        try {
            stream.writeArrayStart();
            consumer.accept(listState, new ListSerializer(this, this::writeComma));
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
    public <T> void writeMap(SdkSchema schema, T mapState, BiConsumer<T, MapSerializer> consumer) {
        try {
            stream.writeObjectStart();
            consumer.accept(mapState, new JsonMapSerializer(this, stream));
            stream.writeObjectEnd();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeDocument(SdkSchema schema, Document value) {
        // Document values in JSON are serialized inline by receiving the data model contents of the document.
        value.serializeContents(this);
    }

    @Override
    public void writeNull(SdkSchema schema) {
        try {
            stream.writeNull();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
