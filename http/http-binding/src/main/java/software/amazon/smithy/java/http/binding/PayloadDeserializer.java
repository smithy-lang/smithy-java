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
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.MemberSubsetCodec;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.document.Document;

final class PayloadDeserializer implements ShapeDeserializer {
    private final Codec payloadCodec;
    private final ByteBuffer body;

    PayloadDeserializer(Codec payloadCodec, ByteBuffer body) {
        this.payloadCodec = payloadCodec;
        this.body = body;
    }

    private ShapeDeserializer createDeserializer() {
        return payloadCodec.createDeserializer(body);
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

        return body;
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

        if (body.hasArray()) {
            int pos = body.arrayOffset() + body.position();
            int len = body.remaining();
            return new String(body.array(), pos, len, StandardCharsets.UTF_8);
        }

        return StandardCharsets.UTF_8.decode(body).toString();
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
            if (state instanceof ShapeBuilder<?> builder
                    && payloadCodec instanceof MemberSubsetCodec direct
                    && direct.deserialize(schema, builder, body.duplicate())) {
                return;
            }
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
