/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
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
    public byte[] readBlob(Schema schema) {
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
    public void readStruct(Schema schema, BiConsumer<Schema, ShapeDeserializer> eachEntry) {
        for (var memberSchema : schema.members()) {
            var memberValue = value.getMember(memberSchema.memberName());
            if (memberValue != null) {
                eachEntry.accept(memberSchema, deserializer(memberValue));
            }
        }
    }

    @Override
    public void readList(Schema schema, Consumer<ShapeDeserializer> eachElement) {
        for (var element : value.asList()) {
            eachElement.accept(deserializer(element));
        }
    }

    @Override
    public void readStringMap(Schema schema, BiConsumer<String, ShapeDeserializer> eachEntry) {
        var map = value.asMap();
        for (var entry : map.entrySet()) {
            eachEntry.accept(entry.getKey().asString(), deserializer(entry.getValue()));
        }
    }

    @Override
    public void readIntMap(Schema schema, BiConsumer<Integer, ShapeDeserializer> eachEntry) {
        var map = value.asMap();
        for (var entry : map.entrySet()) {
            eachEntry.accept(entry.getKey().asInteger(), deserializer(entry.getValue()));
        }
    }

    @Override
    public void readLongMap(Schema schema, BiConsumer<Long, ShapeDeserializer> eachEntry) {
        var map = value.asMap();
        for (var entry : map.entrySet()) {
            eachEntry.accept(entry.getKey().asLong(), deserializer(entry.getValue()));
        }
    }
}
