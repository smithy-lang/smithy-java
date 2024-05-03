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
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class HttpHeaderDeserializer implements ShapeDeserializer {

    private final String value;

    HttpHeaderDeserializer(String value) {
        this.value = value;
    }

    @Override
    public boolean readBoolean(SdkSchema schema) {
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalStateException("Expected header for " + schema.id() + " to be a boolean");
        };
    }

    @Override
    public byte[] readBlob(SdkSchema schema) {
        try {
            return Base64.getDecoder().decode(value.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "Expected header for " + schema.id() + " to be a blob, but the "
                    + "value does not contain valid base64 encoded data"
            );
        }
    }

    @Override
    public byte readByte(SdkSchema schema) {
        return Byte.parseByte(value);
    }

    @Override
    public short readShort(SdkSchema schema) {
        return Short.parseShort(value);
    }

    @Override
    public int readInteger(SdkSchema schema) {
        return Integer.parseInt(value);
    }

    @Override
    public long readLong(SdkSchema schema) {
        return Long.parseLong(value);
    }

    @Override
    public float readFloat(SdkSchema schema) {
        return Float.parseFloat(value);
    }

    @Override
    public double readDouble(SdkSchema schema) {
        return Double.parseDouble(value);
    }

    @Override
    public BigInteger readBigInteger(SdkSchema schema) {
        return new BigInteger(value);
    }

    @Override
    public BigDecimal readBigDecimal(SdkSchema schema) {
        return new BigDecimal(value);
    }

    @Override
    public String readString(SdkSchema schema) {
        return value;
    }

    @Override
    public Document readDocument() {
        throw new UnsupportedOperationException("Documents are not supported in HTTP header bindings");
    }

    @Override
    public Instant readTimestamp(SdkSchema schema) {
        var trait = schema.getTrait(TimestampFormatTrait.class);
        TimestampFormatter formatter = trait != null
            ? TimestampFormatter.of(trait)
            : TimestampFormatter.Prelude.HTTP_DATE;
        return formatter.readFromString(value, false); // headers always are strings.
    }

    @Override
    public <T> void readStruct(SdkSchema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        throw new UnsupportedOperationException("Structures are not supported in HTTP header bindings");
    }

    @Override
    public <T> void readList(SdkSchema schema, T state, ListMemberConsumer<T> listMemberConsumer) {
        throw new UnsupportedOperationException("List header support not yet implemented");
    }

    @Override
    public <T> void readStringMap(SdkSchema schema, T state, MapMemberConsumer<String, T> mapMemberConsumer) {
        throw new UnsupportedOperationException("List map support not yet implemented");
    }
}
