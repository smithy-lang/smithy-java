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
import software.amazon.smithy.java.runtime.core.schema.Schema;
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
    public byte[] readBlob(Schema schema) {
        try {
            String content = iter.readString();
            return decoder.decode(content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte readByte(Schema schema) {
        try {
            return (byte) iter.readShort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public short readShort(Schema schema) {
        try {
            return iter.readShort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int readInteger(Schema schema) {
        try {
            return iter.readInt();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public long readLong(Schema schema) {
        try {
            return iter.readLong();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public float readFloat(Schema schema) {
        try {
            return iter.readFloat();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public double readDouble(Schema schema) {
        try {
            return iter.readDouble();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        try {
            return iter.readBigInteger();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        try {
            return iter.readBigDecimal();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String readString(Schema schema) {
        try {
            return iter.readString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean readBoolean(Schema schema) {
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
    public Instant readTimestamp(Schema schema) {
        return readDocument().asTimestamp();
    }

    @Override
    public void readStruct(Schema schema, BiConsumer<Schema, ShapeDeserializer> eachEntry) {
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

    private Schema resolveMember(Schema schema, String field) {
        for (Schema m : schema.members()) {
            if (useJsonName && m.hasTrait(JsonNameTrait.class)) {
                if (m.getTrait(JsonNameTrait.class).getValue().equals(field)) {
                    return m;
                }
            } else if (m.memberName().equals(field)) {
                return m;
            }
        }
        return null;
    }

    @Override
    public void readList(Schema schema, Consumer<ShapeDeserializer> eachElement) {
        try {
            while (iter.readArray()) {
                eachElement.accept(this);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void readStringMap(Schema schema, BiConsumer<String, ShapeDeserializer> eachEntry) {
        try {
            for (var field = iter.readObject(); field != null; field = iter.readObject()) {
                eachEntry.accept(field, this);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void readIntMap(Schema schema, BiConsumer<Integer, ShapeDeserializer> eachEntry) {
        try {
            for (var field = iter.readObject(); field != null; field = iter.readObject()) {
                eachEntry.accept(Integer.parseInt(field), this);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void readLongMap(Schema schema, BiConsumer<Long, ShapeDeserializer> eachEntry) {
        try {
            for (var field = iter.readObject(); field != null; field = iter.readObject()) {
                eachEntry.accept(Long.parseLong(field), this);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
