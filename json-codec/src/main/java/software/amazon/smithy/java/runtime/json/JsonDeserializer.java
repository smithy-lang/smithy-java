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
    public <T> void readStruct(SdkSchema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        try {
            for (var field = iter.readObject(); field != null; field = iter.readObject()) {
                var member = resolveMember(schema, field);
                if (member == null) {
                    iter.skip();
                } else {
                    structMemberConsumer.accept(state, member, this);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SdkSchema resolveMember(SdkSchema schema, String field) {
        for (SdkSchema m : schema.members()) {
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
    public <T> void readList(SdkSchema schema, T state, ListMemberConsumer<T> listMemberConsumer) {
        try {
            while (iter.readArray()) {
                listMemberConsumer.accept(state, this);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public <T> void readStringMap(SdkSchema schema, T state, MapMemberConsumer<String, T> mapMemberConsumer) {
        try {
            for (var field = iter.readObject(); field != null; field = iter.readObject()) {
                mapMemberConsumer.accept(state, field, this);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
