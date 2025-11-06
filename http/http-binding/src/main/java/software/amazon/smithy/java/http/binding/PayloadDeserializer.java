/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.datastream.DataStream;

final class PayloadDeserializer implements ShapeDeserializer {
    private final Codec payloadCodec;
    private final DataStream body;

    PayloadDeserializer(Codec payloadCodec, DataStream body) {
        this.payloadCodec = payloadCodec;
        this.body = body;
    }

    private ByteBuffer resolveBodyBytes() {
        return body.asByteBuffer();
    }

    private ShapeDeserializer createDeserializer() {
        return payloadCodec.createDeserializer(resolveBodyBytes());
    }

    @Override
    public boolean readBoolean(Schema schema) {
        try (var deser = createDeserializer()) {
            return deser.readBoolean(schema);
        }
    }

    @Override
    public ByteBuffer readBlob(Schema schema) {
        if (isNull()) {
            return null;
        }

        return resolveBodyBytes();
    }

    @Override
    public byte readByte(Schema schema) {
        try (var deser = createDeserializer()) {
            return deser.readByte(schema);
        }
    }

    @Override
    public short readShort(Schema schema) {
        try (var deser = createDeserializer()) {
            return deser.readShort(schema);
        }
    }

    @Override
    public int readInteger(Schema schema) {
        try (var deser = createDeserializer()) {
            return deser.readInteger(schema);
        }
    }

    @Override
    public long readLong(Schema schema) {
        try (var deser = createDeserializer()) {
            return deser.readLong(schema);
        }
    }

    @Override
    public float readFloat(Schema schema) {
        try (var deser = createDeserializer()) {
            return deser.readFloat(schema);
        }
    }

    @Override
    public double readDouble(Schema schema) {
        try (var deser = createDeserializer()) {
            return deser.readDouble(schema);
        }
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        if (isNull()) {
            return null;
        }

        try (var deser = createDeserializer()) {
            return deser.readBigInteger(schema);
        }
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        if (isNull()) {
            return null;
        }

        try (var deser = createDeserializer()) {
            return deser.readBigDecimal(schema);
        }
    }

    @Override
    public String readString(Schema schema) {
        if (isNull()) {
            return null;
        }

        var buffer = body.asByteBuffer();
        int pos = buffer.arrayOffset() + buffer.position();
        int len = buffer.remaining();
        return new String(buffer.array(), pos, len, StandardCharsets.UTF_8);
    }

    @Override
    public Document readDocument() {
        if (isNull()) {
            return null;
        }

        try (var deser = createDeserializer()) {
            return deser.readDocument();
        }
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        if (isNull()) {
            return null;
        }

        try (var deser = createDeserializer()) {
            return deser.readTimestamp(schema);
        }
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
        if (!isNull()) {
            try (var deser = createDeserializer()) {
                deser.readStruct(schema, state, consumer);
            }
        }
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
        if (!isNull()) {
            try (var deser = createDeserializer()) {
                deser.readList(schema, state, consumer);
            }
        }
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
        if (!isNull()) {
            try (var deser = createDeserializer()) {
                deser.readStringMap(schema, state, consumer);
            }
        }
    }

    @Override
    public boolean isNull() {
        return body == null;
    }
}
