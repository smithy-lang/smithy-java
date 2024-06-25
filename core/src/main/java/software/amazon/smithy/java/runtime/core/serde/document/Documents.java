/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class Documents {

    static final Schema LIST_SCHEMA = Schema.builder()
        .id(PreludeSchemas.DOCUMENT.id())
        .type(ShapeType.LIST)
        .members(Schema.memberBuilder("member", PreludeSchemas.DOCUMENT))
        .build();

    static final Schema STR_MAP_SCHEMA = Schema.builder()
        .id(PreludeSchemas.DOCUMENT.id())
        .type(ShapeType.MAP)
        .members(
            Schema.memberBuilder("key", PreludeSchemas.STRING).id(PreludeSchemas.DOCUMENT.id()).build(),
            Schema.memberBuilder("value", PreludeSchemas.DOCUMENT).id(PreludeSchemas.DOCUMENT.id()).build()
        )
        .build();

    private Documents() {}

    record BooleanDocument(Schema schema, boolean value) implements Document {
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
    }

    private static byte toByteExact(ShapeType source, long value) {
        var converted = (byte) value;
        if (value != converted) {
            throw new ArithmeticException(source + " value is out of range of byte");
        }
        return converted;
    }

    private static short toShortExact(ShapeType source, long value) {
        var converted = (short) value;
        if (value != converted) {
            throw new ArithmeticException(source + " value is out of range of short");
        }
        return converted;
    }

    private static int toIntExact(ShapeType source, long value) {
        var converted = (int) value;
        if (value != converted) {
            throw new ArithmeticException(source + " value is out of range of integer");
        }
        return converted;
    }

    record ByteDocument(Schema schema, byte value) implements Document {
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
    }

    record ShortDocument(Schema schema, short value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.SHORT;
        }

        @Override
        public byte asByte() {
            return toByteExact(type(), value);
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
    }

    record IntegerDocument(Schema schema, int value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.INTEGER;
        }

        @Override
        public byte asByte() {
            return toByteExact(type(), value);
        }

        @Override
        public short asShort() {
            return toShortExact(type(), value);
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
    }

    record LongDocument(Schema schema, long value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.LONG;
        }

        @Override
        public byte asByte() {
            return toByteExact(type(), value);
        }

        @Override
        public short asShort() {
            return toShortExact(type(), value);
        }

        @Override
        public int asInteger() {
            return toIntExact(type(), value);
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
    }

    record FloatDocument(Schema schema, float value) implements Document {

        private static long convertFloatToLong(ShapeType dest, float value) {
            if (!Float.isFinite(value)) {
                throw new ArithmeticException("Value must be finite to convert to " + dest);
            }
            return (long) value;
        }

        @Override
        public ShapeType type() {
            return ShapeType.FLOAT;
        }

        @Override
        public byte asByte() {
            return toByteExact(type(), convertFloatToLong(ShapeType.BYTE, value));
        }

        @Override
        public short asShort() {
            return toShortExact(type(), convertFloatToLong(ShapeType.SHORT, value));
        }

        @Override
        public int asInteger() {
            return toIntExact(type(), convertFloatToLong(ShapeType.INTEGER, value));
        }

        @Override
        public long asLong() {
            return convertFloatToLong(ShapeType.LONG, value);
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
            return asBigDecimal().toBigInteger();
        }

        @Override
        public BigDecimal asBigDecimal() {
            return new BigDecimal(value);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeFloat(schema, value);
        }
    }

    record DoubleDocument(Schema schema, double value) implements Document {

        private static long convertDoubleToLong(ShapeType dest, double value) {
            if (!Double.isFinite(value)) {
                throw new ArithmeticException("Value must be finite to convert to " + dest);
            }
            return (long) value;
        }

        @Override
        public ShapeType type() {
            return ShapeType.DOUBLE;
        }

        @Override
        public byte asByte() {
            return toByteExact(type(), convertDoubleToLong(ShapeType.BYTE, value));
        }

        @Override
        public short asShort() {
            return toShortExact(type(), convertDoubleToLong(ShapeType.SHORT, value));
        }

        @Override
        public int asInteger() {
            return toIntExact(type(), convertDoubleToLong(ShapeType.INTEGER, value));
        }

        @Override
        public long asLong() {
            return convertDoubleToLong(ShapeType.LONG, value);
        }

        @Override
        public float asFloat() {
            if (Double.isFinite(value)) {
                var converted = (float) value;
                if (converted != value) {
                    throw new ArithmeticException("Value of double exceeds float");
                }
                return converted;
            } else if (Double.isNaN(value)) {
                return Float.NaN;
            } else if (value < 0) {
                return Float.NEGATIVE_INFINITY;
            } else {
                return Float.POSITIVE_INFINITY;
            }
        }

        @Override
        public double asDouble() {
            return value;
        }

        @Override
        public BigInteger asBigInteger() {
            return asBigDecimal().toBigInteger();
        }

        @Override
        public BigDecimal asBigDecimal() {
            return new BigDecimal(value);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeDouble(schema, value);
        }
    }

    record BigIntegerDocument(Schema schema, BigInteger value) implements Document {
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
    }

    record BigDecimalDocument(Schema schema, BigDecimal value) implements Document {
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
    }

    record StringDocument(Schema schema, String value) implements Document {
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
    }

    record BlobDocument(Schema schema, byte[] value) implements Document {
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
    }

    record TimestampDocument(Schema schema, Instant value) implements Document {
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
    }

    record ListDocument(Schema schema, List<Document> values) implements Document {
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
                    element.serialize(ser);
                }
            });
        }
    }

    record StringMapDocument(Schema schema, Map<String, Document> members) implements Document {
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
        public Set<String> getMemberNames() {
            return Set.copyOf(members.keySet());
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeMap(schema, members, (members, s) -> {
                var key = schema.member("key");
                for (var entry : members.entrySet()) {
                    s.writeEntry(key, entry.getKey(), entry.getValue(), Document::serialize);
                }
            });
        }
    }

    record StructureDocument(Schema schema, Map<String, Document> members) implements Document, SerializableStruct {
        @Override
        public ShapeType type() {
            return ShapeType.STRUCTURE;
        }

        @Override
        public Document getMember(String memberName) {
            return members.get(memberName);
        }

        @Override
        public Set<String> getMemberNames() {
            return Set.copyOf(members.keySet());
        }

        @Override
        public Map<String, Document> asStringMap() {
            return members;
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            // De-conflict Document and SerializableStruct default implementations.
            Document.super.serialize(serializer);
        }

        // Note that this method is never actually called right now because LazyStructure doesn't delegate to it.
        @Override
        public void serializeContents(ShapeSerializer encoder) {
            encoder.writeStruct(schema, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            for (var entry : members.entrySet()) {
                entry.getValue().serialize(serializer);
            }
        }
    }

    /**
     * A document that wraps a shape and a schema, lazily creating the document only if needed.
     */
    static final class LazyStructure implements Document {

        private final Schema schema;
        private final SerializableStruct struct;
        private volatile transient Document createdDocument;

        LazyStructure(Schema schema, SerializableStruct struct) {
            this.schema = schema;
            this.struct = struct;
        }

        private Document getDocument() {
            var result = createdDocument;
            if (result == null) {
                var parser = new DocumentParser.StructureParser();
                struct.serializeMembers(parser);
                result = new StructureDocument(schema, parser.members());
                createdDocument = result;
            }
            return result;
        }

        @Override
        public ShapeType type() {
            return ShapeType.STRUCTURE;
        }

        @Override
        public Map<String, Document> asStringMap() {
            return getDocument().asStringMap();
        }

        @Override
        public Document getMember(String memberName) {
            if (memberName.equals("__type")) {
                return Document.createString(schema.id().toString());
            } else {
                return getDocument().getMember(memberName);
            }
        }

        @Override
        public Set<String> getMemberNames() {
            var result = new HashSet<>(getDocument().getMemberNames());
            result.add("__type");
            return result;
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
            } else if (obj instanceof LazyStructure) {
                return getDocument().equals(((LazyStructure) obj).getDocument());
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

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            struct.serialize(serializer);
        }
    }
}
