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
        if (type() == ShapeType.DOCUMENT) {
            serializer.writeDocument(schema, this);
        } else {
            serializeContents(serializer);
        }
    }

    @Override
    public void serializeContents(ShapeSerializer serializer) {
        var s = schema;
        var d = document;
        switch (s.type()) {
            case STRING, ENUM -> serializer.writeString(s, d.asString());
            case BOOLEAN -> serializer.writeBoolean(s, d.asBoolean());
            case INTEGER, INT_ENUM -> serializer.writeInteger(s, d.asInteger());
            case LONG -> serializer.writeLong(s, d.asLong());
            case DOUBLE -> serializer.writeDouble(s, d.asDouble());
            case FLOAT -> serializer.writeFloat(s, d.asFloat());
            case BYTE -> serializer.writeByte(s, d.asByte());
            case SHORT -> serializer.writeShort(s, d.asShort());
            case BIG_INTEGER -> serializer.writeBigInteger(s, d.asBigInteger());
            case BIG_DECIMAL -> serializer.writeBigDecimal(s, d.asBigDecimal());
            case TIMESTAMP -> serializer.writeTimestamp(s, d.asTimestamp());
            case BLOB -> serializer.writeBlob(s, d.asBlob());
            case DOCUMENT -> d.serializeContents(serializer);
            case LIST, SET -> {
                serializer.writeList(s, d.asList(), d.size(), (values, ser) -> {
                    for (var element : values) {
                        if (element == null) {
                            ser.writeNull(s.listMember());
                        } else {
                            element.serialize(ser);
                        }
                    }
                });
            }
            case MAP -> {
                serializer.writeMap(s, d.asStringMap(), d.size(), (members, ms) -> {
                    var key = s.mapKeyMember();
                    for (var entry : members.entrySet()) {
                        if (entry.getValue() == null) {
                            ms.writeEntry(key, entry.getKey(), null, (t, v) -> v.writeNull(s.mapValueMember()));
                        } else {
                            ms.writeEntry(key, entry.getKey(), entry.getValue(), Document::serialize);
                        }
                    }
                });
            }
            default -> throw new UnsupportedOperationException("Unsupported type: " + s.type());
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
