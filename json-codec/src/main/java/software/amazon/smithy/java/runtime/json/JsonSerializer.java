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
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ListSerializer;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.StructSerializer;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
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
    public void flush() throws IOException {
        stream.flush();
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
        var formatter = schema.hasTrait(TimestampFormatTrait.class)
            ? TimestampFormatter.of(schema.getTrait(TimestampFormatTrait.class))
            : defaultTimestampFormat;
        formatter.serializeToUnderlyingFormat(schema, value, this);
    }

    @Override
    public StructSerializer beginStruct(SdkSchema schema) {
        try {
            stream.writeObjectStart();
            return new JsonStructSerializer(this, stream, useJsonName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void beginList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
        try {
            stream.writeArrayStart();
            consumer.accept(new ListSerializer(this, stream::writeMore));
            stream.writeArrayEnd();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void beginMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
        try {
            stream.writeObjectStart();
            var keySchema = schema.member("key");
            consumer.accept(new JsonMapSerializer(keySchema, this, stream));
            stream.writeObjectEnd();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
