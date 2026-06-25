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
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * A wrapper around another document that changes the serialized schema.
 *
 */
final class ContentDocument implements Document {

    private final Schema schema;
    private final Object value;

    ContentDocument(Schema schema, Object value) {
        this.schema = schema;
        this.value = value;
    }

    Object rawValue() {
        return value;
    }

    boolean isScalar() {
        return switch (schema.type()) {
            case LIST, SET, MAP -> false;
            default -> true;
        };
    }

    @Override
    public ShapeType type() {
        return schema.type();
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializeContents(serializer);
    }

    @Override
    public void serializeContents(ShapeSerializer serializer) {
        // Keep in sync with serializeScalar, split for C2 inline budget.
        var s = schema;
        switch (s.type()) {
            case STRING, ENUM -> serializer.writeString(s, (String) value);
            case BOOLEAN -> serializer.writeBoolean(s, (Boolean) value);
            case INTEGER, INT_ENUM -> serializer.writeInteger(s, ((Number) value).intValue());
            case LONG -> serializer.writeLong(s, ((Number) value).longValue());
            case DOUBLE -> serializer.writeDouble(s, ((Number) value).doubleValue());
            case FLOAT -> serializer.writeFloat(s, ((Number) value).floatValue());
            case BYTE -> serializer.writeByte(s, ((Number) value).byteValue());
            case SHORT -> serializer.writeShort(s, ((Number) value).shortValue());
            case TIMESTAMP -> serializer.writeTimestamp(s, (Instant) value);
            default -> serializeNonScalar(serializer, s);
        }
    }

    // Keep in sync with serializeContents, used for raw union values without a ContentDocument wrapper.
    static void serializeScalar(ShapeSerializer serializer, Schema schema, Object value) {
        switch (schema.type()) {
            case STRING, ENUM -> serializer.writeString(schema, (String) value);
            case BOOLEAN -> serializer.writeBoolean(schema, (Boolean) value);
            case INTEGER, INT_ENUM -> serializer.writeInteger(schema, ((Number) value).intValue());
            case LONG -> serializer.writeLong(schema, ((Number) value).longValue());
            case DOUBLE -> serializer.writeDouble(schema, ((Number) value).doubleValue());
            case FLOAT -> serializer.writeFloat(schema, ((Number) value).floatValue());
            case BYTE -> serializer.writeByte(schema, ((Number) value).byteValue());
            case SHORT -> serializer.writeShort(schema, ((Number) value).shortValue());
            case TIMESTAMP -> serializer.writeTimestamp(schema, (Instant) value);
            case BLOB -> serializer.writeBlob(schema, (ByteBuffer) value);
            case BIG_INTEGER -> serializer.writeBigInteger(schema, toBigInteger(value));
            case BIG_DECIMAL -> serializer.writeBigDecimal(schema, toBigDecimal(value));
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + schema.type());
        }
    }

    private static BigInteger toBigInteger(Object value) {
        return value instanceof BigInteger bi ? bi : BigInteger.valueOf(((Number) value).longValue());
    }

    private static BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) value).doubleValue());
    }

    private void serializeNonScalar(ShapeSerializer serializer, Schema s) {
        switch (s.type()) {
            case BLOB -> serializer.writeBlob(s, (ByteBuffer) value);
            case BIG_INTEGER -> serializer.writeBigInteger(s, asBigInteger());
            case BIG_DECIMAL -> serializer.writeBigDecimal(s, asBigDecimal());
            case LIST, SET -> serializer.writeList(s, this, size(), ContentDocument::serializeListContents);
            case MAP -> serializer.writeMap(s, this, size(), ContentDocument::serializeMapContents);
            default -> throw new UnsupportedOperationException("Unsupported type: " + s.type());
        }
    }

    @SuppressWarnings("unchecked")
    private static void serializeListContents(ContentDocument doc, ShapeSerializer ser) {
        Schema member = doc.schema.listMember();
        List<Document> list = (List<Document>) doc.value;
        // String/enum fast-path: monomorphic writeString loop, avoids per-element Document.serialize dispatch.
        ShapeType memberType = member.type();
        if (memberType == ShapeType.STRING || memberType == ShapeType.ENUM) {
            writeStringList(ser, list, member);
        } else {
            if (list instanceof RandomAccess) {
                for (int i = 0, n = list.size(); i < n; i++) {
                    Document e = list.get(i);
                    if (e == null) {
                        ser.writeNull(member);
                    } else {
                        e.serialize(ser);
                    }
                }
            } else {
                for (Document e : list) {
                    if (e == null) {
                        ser.writeNull(member);
                    } else {
                        e.serialize(ser);
                    }
                }
            }
        }
    }

    private static void writeStringList(ShapeSerializer ser, List<Document> list, Schema member) {
        if (list instanceof RandomAccess) {
            for (int i = 0, n = list.size(); i < n; i++) {
                Document e = list.get(i);
                if (e == null) {
                    ser.writeNull(member);
                } else {
                    ser.writeString(member, e.asString());
                }
            }
        } else {
            for (Document e : list) {
                if (e == null) {
                    ser.writeNull(member);
                } else {
                    ser.writeString(member, e.asString());
                }
            }
        }
    }

    // Non-capturing, concretely-typed consumers for MapSerializer#writeEntry, keeps value-write monomorphic.
    private static final BiConsumer<Schema, ShapeSerializer> NULL_VALUE_WRITER = (vm, ser) -> ser.writeNull(vm);
    private static final BiConsumer<StructDocument, ShapeSerializer> STRUCT_VALUE_WRITER =
            (d, ser) -> ser.writeStruct(d.schema(), d);
    private static final BiConsumer<ContentDocument, ShapeSerializer> STRING_VALUE_WRITER =
            (d, ser) -> ser.writeString(d.schema, (String) d.value);

    @SuppressWarnings("unchecked")
    private static void serializeMapContents(ContentDocument doc, MapSerializer ms) {
        Schema key = doc.schema.mapKeyMember();
        Schema valueMember = doc.schema.mapValueMember();
        Map<String, Document> map = (Map<String, Document>) doc.value;
        switch (valueMember.type()) {
            case STRUCTURE, UNION -> {
                for (var entry : map.entrySet()) {
                    var v = entry.getValue();
                    if (v == null) {
                        ms.writeEntry(key, entry.getKey(), valueMember, NULL_VALUE_WRITER);
                    } else {
                        ms.writeEntry(key, entry.getKey(), (StructDocument) v, STRUCT_VALUE_WRITER);
                    }
                }
            }
            case STRING, ENUM -> {
                for (var entry : map.entrySet()) {
                    var v = entry.getValue();
                    if (v == null) {
                        ms.writeEntry(key, entry.getKey(), valueMember, NULL_VALUE_WRITER);
                    } else {
                        ms.writeEntry(key, entry.getKey(), (ContentDocument) v, STRING_VALUE_WRITER);
                    }
                }
            }
            default -> {
                for (var entry : map.entrySet()) {
                    var v = entry.getValue();
                    if (v == null) {
                        ms.writeEntry(key, entry.getKey(), valueMember, NULL_VALUE_WRITER);
                    } else {
                        ms.writeEntry(key, entry.getKey(), v, Document::serialize);
                    }
                }
            }
        }
    }

    @Override
    public BigDecimal asBigDecimal() {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }

    @Override
    public BigInteger asBigInteger() {
        if (value instanceof BigInteger bi) {
            return bi;
        }
        return BigInteger.valueOf(((Number) value).longValue());
    }

    @Override
    public ByteBuffer asBlob() {
        return (ByteBuffer) value;
    }

    @Override
    public boolean asBoolean() {
        return (Boolean) value;
    }

    @Override
    public byte asByte() {
        return ((Number) value).byteValue();
    }

    @Override
    public double asDouble() {
        return ((Number) value).doubleValue();
    }

    @Override
    public float asFloat() {
        return ((Number) value).floatValue();
    }

    @Override
    public int asInteger() {
        return ((Number) value).intValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Document> asList() {
        return (List<Document>) value;
    }

    @Override
    public long asLong() {
        return ((Number) value).longValue();
    }

    @Override
    public Number asNumber() {
        return (Number) value;
    }

    @Override
    public Object asObject() {
        return switch (schema.type()) {
            case LIST, SET, MAP -> Document.super.asObject();
            // String, Boolean, boxed Number, Instant, ByteBuffer are all already valid asObject() results.
            default -> value;
        };
    }

    @Override
    public short asShort() {
        return ((Number) value).shortValue();
    }

    @Override
    public String asString() {
        return (String) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Document> asStringMap() {
        return (Map<String, Document>) value;
    }

    @Override
    public Instant asTimestamp() {
        return (Instant) value;
    }

    @Override
    public int size() {
        return switch (schema.type()) {
            case LIST, SET -> ((List<?>) value).size();
            case MAP -> ((Map<?, ?>) value).size();
            default -> -1;
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public Document getMember(String memberName) {
        if (schema.type() == ShapeType.MAP) {
            return ((Map<String, Document>) value).get(memberName);
        }
        return Document.super.getMember(memberName);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getMemberNames() {
        if (schema.type() == ShapeType.MAP) {
            return ((Map<String, Document>) value).keySet();
        }
        return Document.super.getMemberNames();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContentDocument that)) {
            return false;
        }
        return schema.equals(that.schema) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * schema.hashCode() + Objects.hashCode(value);
    }

    /**
     * Wraps another {@link Document} to give it a {@code document}-typed model {@link Schema}, delegating all value
     * accessors to the wrapped document.
     *
     * <p>Used only for members whose modeled shape is an (untyped) {@code document}: the inner value can be any
     * document kind, so unlike {@link ContentDocument} (which holds an already-unwrapped scalar/collection value for a
     * known scalar/aggregate schema) this type must forward every accessor. Document-typed members are uncommon and
     * never on the scalar/aggregate hot path, so the extra delegation here is not performance sensitive.
     */
    record SchemaDocument(Schema schema, Document document) implements Document {
        @Override
        public ShapeType type() {
            return schema.type();
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            if (schema.type() == ShapeType.DOCUMENT) {
                serializer.writeDocument(schema, this);
            } else {
                serializeContents(serializer);
            }
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            document.serializeContents(serializer);
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
}
