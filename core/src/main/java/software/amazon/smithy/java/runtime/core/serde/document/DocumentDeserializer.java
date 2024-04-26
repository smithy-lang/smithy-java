/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
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
    public String readString(SdkSchema schema) {
        return value.asString();
    }

    @Override
    public boolean readBoolean(SdkSchema schema) {
        return value.asBoolean();
    }

    @Override
    public byte[] readBlob(SdkSchema schema) {
        return value.asBlob();
    }

    @Override
    public byte readByte(SdkSchema schema) {
        return value.asByte();
    }

    @Override
    public short readShort(SdkSchema schema) {
        return value.asShort();
    }

    @Override
    public int readInteger(SdkSchema schema) {
        return value.asInteger();
    }

    @Override
    public long readLong(SdkSchema schema) {
        return value.asLong();
    }

    @Override
    public float readFloat(SdkSchema schema) {
        return value.asFloat();
    }

    @Override
    public double readDouble(SdkSchema schema) {
        return value.asDouble();
    }

    @Override
    public BigInteger readBigInteger(SdkSchema schema) {
        return value.asBigInteger();
    }

    @Override
    public BigDecimal readBigDecimal(SdkSchema schema) {
        return value.asBigDecimal();
    }

    @Override
    public Instant readTimestamp(SdkSchema schema) {
        return value.asTimestamp();
    }

    @Override
    public Document readDocument() {
        return value;
    }

    @Override
    public <T> void readStruct(SdkSchema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        for (var memberSchema : schema.members()) {
            var memberValue = value.getMember(memberSchema.memberName());
            if (memberValue != null) {
                structMemberConsumer.accept(state, memberSchema, deserializer(memberValue));
            }
        }
    }

    @Override
    public <T> void readList(SdkSchema schema, T state, ListMemberConsumer<T> listMemberConsumer) {
        for (var element : value.asList()) {
            listMemberConsumer.accept(state, deserializer(element));
        }
    }

    @Override
    public <T> void readStringMap(SdkSchema schema, T state, MapMemberConsumer<String, T> mapMemberConsumer) {
        var map = value.asStringMap();
        for (var entry : map.entrySet()) {
            mapMemberConsumer.accept(state, entry.getKey(), deserializer(entry.getValue()));
        }
    }
}
