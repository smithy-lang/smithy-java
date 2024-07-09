/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;

/**
 * A deserializer for Document types.
 *
 * <p>This class was designed to be extended so that codecs can customize how data is extracted from documents.
 */
public class DocumentDeserializer implements ShapeDeserializer {

    private final Document value;

    public DocumentDeserializer(Document value) {
        this.value = value;
    }

    /**
     * Create a DocumentDeserializer to recursively deserialize a value.
     *
     * @param value Value to deserialize.
     * @return the created deserializer.
     */
    protected DocumentDeserializer deserializer(Document value) {
        return new DocumentDeserializer(value);
    }

    @Override
    public String readString(Schema schema) {
        return value.asString();
    }

    @Override
    public boolean readBoolean(Schema schema) {
        return value.asBoolean();
    }

    @Override
    public ByteBuffer readBlob(Schema schema) {
        return value.asBlob();
    }

    @Override
    public byte readByte(Schema schema) {
        return value.asByte();
    }

    @Override
    public short readShort(Schema schema) {
        return value.asShort();
    }

    @Override
    public int readInteger(Schema schema) {
        return value.asInteger();
    }

    @Override
    public long readLong(Schema schema) {
        return value.asLong();
    }

    @Override
    public float readFloat(Schema schema) {
        return value.asFloat();
    }

    @Override
    public double readDouble(Schema schema) {
        return value.asDouble();
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        return value.asBigInteger();
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        return value.asBigDecimal();
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        return value.asTimestamp();
    }

    @Override
    public Document readDocument() {
        return value;
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        for (var member : value.getMemberNames()) {
            if (member.equals("__type")) {
                continue;
            }
            var memberSchema = schema.member(member);
            if (memberSchema != null) {
                var memberValue = value.getMember(member);
                if (memberValue != null) {
                    structMemberConsumer.accept(state, memberSchema, deserializer(memberValue));
                }
            } else {
                structMemberConsumer.unknownMember(state, member);
            }
        }
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> listMemberConsumer) {
        for (var element : value.asList()) {
            listMemberConsumer.accept(state, deserializer(element));
        }
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> mapMemberConsumer) {
        var map = value.asStringMap();
        for (var entry : map.entrySet()) {
            mapMemberConsumer.accept(state, entry.getKey(), deserializer(entry.getValue()));
        }
    }

    @Override
    public boolean isNull() {
        return value == null;
    }

    @Override
    public <T> T readNull() {
        if (value != null) {
            throw new SerializationException("Attempted to read non-null value as null");
        }
        return null;
    }
}
