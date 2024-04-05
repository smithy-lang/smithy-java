/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import com.jsoniter.JsonIterator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Base64;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
import software.amazon.smithy.model.traits.JsonNameTrait;

final class JsonDeserializer implements ShapeDeserializer {

    private final JsonIterator iter;
    private final boolean useJsonName;
    private final Base64.Decoder decoder = Base64.getDecoder();
    private final TimestampFormatter defaultTimestampFormat;
    private final boolean useTimestampFormat;

    JsonDeserializer(
        byte[] source,
        boolean useJsonName,
        TimestampFormatter defaultTimestampFormat,
        boolean useTimestampFormat
    ) {
        this.useJsonName = useJsonName;
        this.useTimestampFormat = useTimestampFormat;
        this.defaultTimestampFormat = defaultTimestampFormat;
        if (source.length == 0) {
            throw new IllegalArgumentException("Cannot parse empty JSON string");
        }
        this.iter = JsonIterator.parse(source);
    }

    @Override
    public byte[] readBlob(SdkSchema schema) {
        try {
            String content = iter.readString();
            return decoder.decode(content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte readByte(SdkSchema schema) {
        try {
            return (byte) iter.readShort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public short readShort(SdkSchema schema) {
        try {
            return iter.readShort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int readInteger(SdkSchema schema) {
        try {
            return iter.readInt();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public long readLong(SdkSchema schema) {
        try {
            return iter.readLong();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public float readFloat(SdkSchema schema) {
        try {
            return iter.readFloat();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public double readDouble(SdkSchema schema) {
        try {
            return iter.readDouble();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public BigInteger readBigInteger(SdkSchema schema) {
        try {
            return iter.readBigInteger();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public BigDecimal readBigDecimal(SdkSchema schema) {
        try {
            return iter.readBigDecimal();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String readString(SdkSchema schema) {
        try {
            return iter.readString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean readBoolean(SdkSchema schema) {
        try {
            return iter.readBoolean();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public JsonDocument readDocument() {
        try {
            return new JsonDocument(iter.readAny(), useJsonName, defaultTimestampFormat, useTimestampFormat);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Instant readTimestamp(SdkSchema schema) {
        return readDocument().asTimestamp();
    }

    @Override
    public void readStruct(SdkSchema schema, BiConsumer<SdkSchema, ShapeDeserializer> eachEntry) {
        try {
            for (var field = iter.readObject(); field != null; field = iter.readObject()) {
                var member = resolveMember(schema, field);
                if (member == null) {
                    iter.skip();
                } else {
                    eachEntry.accept(member, this);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SdkSchema resolveMember(SdkSchema schema, String field) {
        SdkSchema member = null;
        if (useJsonName) {
            member = schema.findMember(
                m -> m.hasTrait(JsonNameTrait.class) && m.getTrait(JsonNameTrait.class).getValue().equals(field)
            );
        }

        if (member == null) {
            member = schema.member(field);
        }

        return member;
    }

    @Override
    public void readList(SdkSchema schema, Consumer<ShapeDeserializer> eachElement) {
        try {
            while (iter.readArray()) {
                eachElement.accept(this);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void readStringMap(SdkSchema schema, BiConsumer<String, ShapeDeserializer> eachEntry) {
        try {
            for (var field = iter.readObject(); field != null; field = iter.readObject()) {
                eachEntry.accept(field, this);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void readIntMap(SdkSchema schema, BiConsumer<Integer, ShapeDeserializer> eachEntry) {
        try {
            for (var field = iter.readObject(); field != null; field = iter.readObject()) {
                eachEntry.accept(Integer.parseInt(field), this);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void readLongMap(SdkSchema schema, BiConsumer<Long, ShapeDeserializer> eachEntry) {
        try {
            for (var field = iter.readObject(); field != null; field = iter.readObject()) {
                eachEntry.accept(Long.parseLong(field), this);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
