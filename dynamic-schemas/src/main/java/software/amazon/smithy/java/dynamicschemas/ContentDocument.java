/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * A wrapper around another document that changes the serialized schema.
 *
 * @param document Document to wrap.
 * @param schema Schema to use instead.
 */
record ContentDocument(Document document, Schema schema) implements Document {
    @Override
    public ShapeType type() {
        return schema.type();
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        // If it is literally a document, then don't unwrap it.
        if (type() == ShapeType.DOCUMENT) {
            serializer.writeDocument(schema, this);
        } else {
            serializeContents(serializer);
        }
    }

    @Override
    public void serializeContents(ShapeSerializer serializer) {
        switch (type()) {
            case BOOLEAN -> serializer.writeBoolean(schema, asBoolean());
            case BYTE -> serializer.writeByte(schema, asByte());
            case SHORT -> serializer.writeShort(schema, asShort());
            case INTEGER, INT_ENUM -> serializer.writeInteger(schema, asInteger());
            case LONG -> serializer.writeLong(schema, asLong());
            case FLOAT -> serializer.writeFloat(schema, asFloat());
            case DOUBLE -> serializer.writeDouble(schema, asDouble());
            case BIG_INTEGER -> serializer.writeBigInteger(schema, asBigInteger());
            case BIG_DECIMAL -> serializer.writeBigDecimal(schema, asBigDecimal());
            case STRING, ENUM -> serializer.writeString(schema, asString());
            case TIMESTAMP -> serializer.writeTimestamp(schema, asTimestamp());
            case BLOB -> serializer.writeBlob(schema, asBlob());
            case DOCUMENT -> document.serializeContents(serializer);
            case LIST, SET -> {
                serializer.writeList(schema, asList(), size(), (values, ser) -> {
                    for (var element : values) {
                        if (element == null) {
                            ser.writeNull(schema.listMember());
                        } else {
                            element.serialize(ser);
                        }
                    }
                });
            }
            case MAP -> {
                serializer.writeMap(schema, asStringMap(), size(), (members, s) -> {
                    var key = schema.mapKeyMember();
                    for (var entry : members.entrySet()) {
                        if (entry.getValue() == null) {
                            s.writeEntry(key, entry.getKey(), null, (t, v) -> v.writeNull(schema.mapValueMember()));
                        } else {
                            s.writeEntry(key, entry.getKey(), entry.getValue(), Document::serialize);
                        }
                    }
                });
            }
            // Note that Structure and Union are always going to be a StructDocument and appear here.
            default -> throw new UnsupportedOperationException("Unsupported type: " + type());
        }
    }

    @Override
    public BigDecimal asBigDecimal() {
        return document.asBigDecimal();
    }

    @Override
    public BigInteger asBigInteger() {
        return document.asBigInteger();
    }

    @Override
    public ByteBuffer asBlob() {
        return document.asBlob();
    }

    @Override
    public boolean asBoolean() {
        return document.asBoolean();
    }

    @Override
    public byte asByte() {
        return document.asByte();
    }

    @Override
    public double asDouble() {
        return document.asDouble();
    }

    @Override
    public float asFloat() {
        return document.asFloat();
    }

    @Override
    public int asInteger() {
        return document.asInteger();
    }

    @Override
    public List<Document> asList() {
        return document.asList();
    }

    @Override
    public long asLong() {
        return document.asLong();
    }

    @Override
    public Number asNumber() {
        return document.asNumber();
    }

    @Override
    public Object asObject() {
        return document.asObject();
    }

    @Override
    public <T extends SerializableShape> T asShape(ShapeBuilder<T> builder) {
        return document.asShape(builder);
    }

    @Override
    public short asShort() {
        return document.asShort();
    }

    @Override
    public String asString() {
        return document.asString();
    }

    @Override
    public Map<String, Document> asStringMap() {
        return document.asStringMap();
    }

    @Override
    public Instant asTimestamp() {
        return document.asTimestamp();
    }

    @Override
    public int size() {
        return document.size();
    }

    @Override
    public Document getMember(String memberName) {
        return document.getMember(memberName);
    }

    @Override
    public Set<String> getMemberNames() {
        return document.getMemberNames();
    }
}
