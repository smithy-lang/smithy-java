/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.traits.MediaTypeTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class HttpPathLabelDeserializer implements ShapeDeserializer {

    private final String value;

    HttpPathLabelDeserializer(String value) {
        this.value = value;
    }

    @Override
    public boolean readBoolean(Schema schema) {
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new SerializationException("Invalid boolean");
        };
    }

    @Override
    public byte[] readBlob(Schema schema) {
        try {
            return Base64.getDecoder().decode(value.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new SerializationException("invalid base64", e);
        }
    }

    @Override
    public byte readByte(Schema schema) {
        try {
            return Byte.parseByte(value);
        } catch (NumberFormatException e) {
            throw new SerializationException("Invalid byte", e);
        }
    }

    @Override
    public short readShort(Schema schema) {
        try {
            return Short.parseShort(value);
        } catch (NumberFormatException e) {
            throw new SerializationException("Invalid short", e);
        }
    }

    @Override
    public int readInteger(Schema schema) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new SerializationException("Invalid integer", e);
        }
    }

    @Override
    public long readLong(Schema schema) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new SerializationException("Invalid long", e);
        }
    }

    @Override
    public float readFloat(Schema schema) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new SerializationException("Invalid float", e);
        }
    }

    @Override
    public double readDouble(Schema schema) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new SerializationException("Invalid double", e);
        }
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        try {
            return new BigInteger(value);
        } catch (NumberFormatException e) {
            throw new SerializationException("Invalid BigInteger", e);
        }
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new SerializationException("Invalid BigDecimal", e);
        }
    }

    @Override
    public String readString(Schema schema) {
        if (schema.hasTrait(MediaTypeTrait.class)) {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        }
        return value;
    }

    @Override
    public Document readDocument() {
        throw new UnsupportedOperationException("Documents are not supported in HTTP path bindings");
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        var trait = schema.getTrait(TimestampFormatTrait.class);
        TimestampFormatter formatter = trait != null
            ? TimestampFormatter.of(trait)
            : TimestampFormatter.Prelude.DATE_TIME;
        return formatter.readFromString(value, false); // labels are always strings
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        throw new UnsupportedOperationException("Structures are not supported in HTTP path bindings");
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> listMemberConsumer) {
        throw new UnsupportedOperationException("Lists are not supported in HTTP path bindings");
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> mapMemberConsumer) {
        throw new UnsupportedOperationException("Maps are not supported in HTTP path bindings");
    }

    @Override
    public boolean isNull() {
        return value == null;
    }
}
