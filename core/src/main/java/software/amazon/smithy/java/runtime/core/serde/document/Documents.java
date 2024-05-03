/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class Documents {

    static final SdkSchema LIST_SCHEMA = SdkSchema.builder()
        .id(PreludeSchemas.DOCUMENT.id())
        .type(ShapeType.LIST)
        .members(SdkSchema.memberBuilder("member", PreludeSchemas.DOCUMENT))
        .build();

    static final SdkSchema STR_MAP_SCHEMA = SdkSchema.builder()
        .id(PreludeSchemas.DOCUMENT.id())
        .type(ShapeType.MAP)
        .members(
            SdkSchema.memberBuilder("key", PreludeSchemas.STRING).id(PreludeSchemas.DOCUMENT.id()).build(),
            SdkSchema.memberBuilder("value", PreludeSchemas.DOCUMENT).id(PreludeSchemas.DOCUMENT.id()).build()
        )
        .build();

    private Documents() {}

    record BooleanDocument(SdkSchema schema, boolean value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.BOOLEAN;
        }

        @Override
        public boolean asBoolean() {
            return value;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeBoolean(schema, value);
        }

        @Override
        public Document normalize() {
            return schema == PreludeSchemas.BOOLEAN ? this : Document.super.normalize();
        }
    }

    record ByteDocument(SdkSchema schema, byte value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.BYTE;
        }

        @Override
        public byte asByte() {
            return value;
        }

        @Override
        public short asShort() {
            return value;
        }

        @Override
        public int asInteger() {
            return value;
        }

        @Override
        public long asLong() {
            return value;
        }

        @Override
        public float asFloat() {
            return value;
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public BigInteger asBigInteger() {
            return BigInteger.valueOf(value);
        }

        @Override
        public BigDecimal asBigDecimal() {
            return new BigDecimal(value);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeByte(schema, value);
        }

        @Override
        public Document normalize() {
            return schema == PreludeSchemas.BYTE ? this : Document.super.normalize();
        }
    }

    record ShortDocument(SdkSchema schema, short value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.SHORT;
        }

        @Override
        public byte asByte() {
            return (byte) value;
        }

        @Override
        public short asShort() {
            return value;
        }

        @Override
        public int asInteger() {
            return value;
        }

        @Override
        public long asLong() {
            return value;
        }

        @Override
        public float asFloat() {
            return value;
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public BigInteger asBigInteger() {
            return BigInteger.valueOf(value);
        }

        @Override
        public BigDecimal asBigDecimal() {
            return new BigDecimal(value);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeShort(schema, value);
        }

        @Override
        public Document normalize() {
            return schema == PreludeSchemas.SHORT ? this : Document.super.normalize();
        }
    }

    record IntegerDocument(SdkSchema schema, int value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.INTEGER;
        }

        @Override
        public byte asByte() {
            return (byte) value;
        }

        @Override
        public short asShort() {
            return (short) value;
        }

        @Override
        public int asInteger() {
            return value;
        }

        @Override
        public long asLong() {
            return value;
        }

        @Override
        public float asFloat() {
            return value;
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public BigInteger asBigInteger() {
            return BigInteger.valueOf(value);
        }

        @Override
        public BigDecimal asBigDecimal() {
            return new BigDecimal(value);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeInteger(schema, value);
        }

        @Override
        public Document normalize() {
            return schema == PreludeSchemas.INTEGER ? this : Document.super.normalize();
        }
    }

    record LongDocument(SdkSchema schema, long value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.LONG;
        }

        @Override
        public byte asByte() {
            return (byte) value;
        }

        @Override
        public short asShort() {
            return (short) value;
        }

        @Override
        public int asInteger() {
            return (int) value;
        }

        @Override
        public long asLong() {
            return value;
        }

        @Override
        public float asFloat() {
            return value;
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public BigInteger asBigInteger() {
            return BigInteger.valueOf(value);
        }

        @Override
        public BigDecimal asBigDecimal() {
            return new BigDecimal(value);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeLong(schema, value);
        }

        @Override
        public Document normalize() {
            return schema == PreludeSchemas.LONG ? this : Document.super.normalize();
        }
    }

    record FloatDocument(SdkSchema schema, float value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.FLOAT;
        }

        @Override
        public byte asByte() {
            return (byte) value;
        }

        @Override
        public short asShort() {
            return (short) value;
        }

        @Override
        public int asInteger() {
            return (int) value;
        }

        @Override
        public long asLong() {
            return (long) value;
        }

        @Override
        public float asFloat() {
            return value;
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public BigInteger asBigInteger() {
            return BigInteger.valueOf((long) value);
        }

        @Override
        public BigDecimal asBigDecimal() {
            return new BigDecimal(value);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeFloat(schema, value);
        }

        @Override
        public Document normalize() {
            return schema == PreludeSchemas.FLOAT ? this : Document.super.normalize();
        }
    }

    record DoubleDocument(SdkSchema schema, double value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.DOUBLE;
        }

        @Override
        public byte asByte() {
            return (byte) value;
        }

        @Override
        public short asShort() {
            return (short) value;
        }

        @Override
        public int asInteger() {
            return (int) value;
        }

        @Override
        public long asLong() {
            return (long) value;
        }

        @Override
        public float asFloat() {
            return (float) value;
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public BigInteger asBigInteger() {
            return BigInteger.valueOf((long) value);
        }

        @Override
        public BigDecimal asBigDecimal() {
            return new BigDecimal(value);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeDouble(schema, value);
        }

        @Override
        public Document normalize() {
            return schema == PreludeSchemas.DOUBLE ? this : Document.super.normalize();
        }
    }

    record BigIntegerDocument(SdkSchema schema, BigInteger value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.BIG_INTEGER;
        }

        @Override
        public byte asByte() {
            return value.byteValueExact();
        }

        @Override
        public short asShort() {
            return value.shortValueExact();
        }

        @Override
        public int asInteger() {
            return value.intValueExact();
        }

        @Override
        public long asLong() {
            return value.longValueExact();
        }

        @Override
        public float asFloat() {
            return value.floatValue();
        }

        @Override
        public double asDouble() {
            return value.doubleValue();
        }

        @Override
        public BigInteger asBigInteger() {
            return value;
        }

        @Override
        public BigDecimal asBigDecimal() {
            return new BigDecimal(value);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeBigInteger(schema, value);
        }

        @Override
        public Document normalize() {
            return schema == PreludeSchemas.BIG_INTEGER ? this : Document.super.normalize();
        }
    }

    record BigDecimalDocument(SdkSchema schema, BigDecimal value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.BIG_DECIMAL;
        }

        @Override
        public byte asByte() {
            return value.byteValueExact();
        }

        @Override
        public short asShort() {
            return value.shortValueExact();
        }

        @Override
        public int asInteger() {
            return value.intValueExact();
        }

        @Override
        public long asLong() {
            return value.longValueExact();
        }

        @Override
        public float asFloat() {
            return value.floatValue();
        }

        @Override
        public double asDouble() {
            return value.doubleValue();
        }

        @Override
        public BigInteger asBigInteger() {
            return value.toBigInteger();
        }

        @Override
        public BigDecimal asBigDecimal() {
            return value;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeBigDecimal(schema, value);
        }

        @Override
        public Document normalize() {
            return schema == PreludeSchemas.BIG_DECIMAL ? this : Document.super.normalize();
        }
    }

    record StringDocument(SdkSchema schema, String value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.STRING;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeString(schema, value);
        }

        @Override
        public Document normalize() {
            return schema == PreludeSchemas.STRING ? this : Document.super.normalize();
        }
    }

    record BlobDocument(SdkSchema schema, byte[] value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.BLOB;
        }

        @Override
        public byte[] asBlob() {
            return value;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeBlob(schema, value);
        }

        // Records don't generate this same equals behavior for byte arrays, so customize it.
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o == null || getClass() != o.getClass()) {
                return false;
            } else {
                BlobDocument that = (BlobDocument) o;
                return Arrays.equals(value, that.value);
            }
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }

        @Override
        public Document normalize() {
            return schema == PreludeSchemas.BLOB ? this : Document.super.normalize();
        }
    }

    record TimestampDocument(SdkSchema schema, Instant value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.TIMESTAMP;
        }

        @Override
        public Instant asTimestamp() {
            return value;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeTimestamp(schema, value);
        }

        @Override
        public Document normalize() {
            return schema == PreludeSchemas.TIMESTAMP ? this : Document.super.normalize();
        }
    }

    record ListDocument(SdkSchema schema, List<Document> values) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.LIST;
        }

        @Override
        public List<Document> asList() {
            return values;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeList(schema, values, (values, ser) -> {
                for (var element : values) {
                    element.serializeContents(ser);
                }
            });
        }
    }

    record StringMapDocument(SdkSchema schema, Map<String, Document> members) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.MAP;
        }

        @Override
        public Map<String, Document> asStringMap() {
            return members;
        }

        @Override
        public Document getMember(String memberName) {
            return members.get(memberName);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeMap(schema, members, (members, s) -> {
                var key = schema.member("key");
                for (var entry : members.entrySet()) {
                    s.writeEntry(key, entry.getKey(), entry.getValue(), Document::serializeContents);
                }
            });
        }
    }

    record StructureDocument(SdkSchema schema, Map<String, Document> members) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.STRUCTURE;
        }

        @Override
        public Document getMember(String memberName) {
            return members.get(memberName);
        }

        @Override
        public Map<String, Document> asStringMap() {
            return members;
        }

        @Override
        public void serializeContents(ShapeSerializer encoder) {
            encoder.writeStruct(schema, members, (members, structSerializer) -> {
                for (var entry : members.entrySet()) {
                    entry.getValue().serializeContents(structSerializer);
                }
            });
        }
    }

    // If the document is only used to serialize a shape to a document, then the typed document nodes don't need
    // to be created.
    static final class LazilyCreatedTypedDocument implements Document {

        private final SerializableShape shape;
        private volatile transient Document createdDocument;

        LazilyCreatedTypedDocument(SerializableShape shape) {
            this.shape = shape;
        }

        private Document getDocument() {
            var result = createdDocument;
            if (result == null) {
                var parser = new DocumentParser();
                shape.serialize(parser);
                this.createdDocument = result = parser.getResult();
            }
            return result;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            shape.serialize(serializer);
        }

        @Override
        public ShapeType type() {
            return getDocument().type();
        }

        @Override
        public boolean asBoolean() {
            return getDocument().asBoolean();
        }

        @Override
        public byte asByte() {
            return getDocument().asByte();
        }

        @Override
        public short asShort() {
            return getDocument().asShort();
        }

        @Override
        public int asInteger() {
            return getDocument().asInteger();
        }

        @Override
        public long asLong() {
            return getDocument().asLong();
        }

        @Override
        public float asFloat() {
            return getDocument().asFloat();
        }

        @Override
        public double asDouble() {
            return getDocument().asDouble();
        }

        @Override
        public BigInteger asBigInteger() {
            return getDocument().asBigInteger();
        }

        @Override
        public BigDecimal asBigDecimal() {
            return getDocument().asBigDecimal();
        }

        @Override
        public Number asNumber() {
            return getDocument().asNumber();
        }

        @Override
        public String asString() {
            return getDocument().asString();
        }

        @Override
        public byte[] asBlob() {
            return getDocument().asBlob();
        }

        @Override
        public Instant asTimestamp() {
            return getDocument().asTimestamp();
        }

        @Override
        public List<Document> asList() {
            return getDocument().asList();
        }

        @Override
        public Map<String, Document> asStringMap() {
            return getDocument().asStringMap();
        }

        @Override
        public Document getMember(String memberName) {
            return getDocument().getMember(memberName);
        }

        @Override
        public String toString() {
            return getDocument().toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (obj instanceof LazilyCreatedTypedDocument) {
                return getDocument().equals(((LazilyCreatedTypedDocument) obj).getDocument());
            } else if (obj instanceof Document) {
                return getDocument().equals(obj);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return getDocument().hashCode();
        }
    }
}
