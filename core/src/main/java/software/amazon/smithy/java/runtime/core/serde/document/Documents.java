/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class Documents {

    private Documents() {}

    record BooleanDocument(boolean value) implements Document {
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
            serializer.writeBoolean(PreludeSchemas.BOOLEAN, value);
        }
    }

    record ByteDocument(byte value) implements Document {
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
            serializer.writeByte(PreludeSchemas.BYTE, value);
        }
    }

    record ShortDocument(short value) implements Document {
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
            serializer.writeShort(PreludeSchemas.SHORT, value);
        }
    }

    record IntegerDocument(int value) implements Document {
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
            serializer.writeInteger(PreludeSchemas.INTEGER, value);
        }
    }

    record LongDocument(long value) implements Document {
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
            serializer.writeLong(PreludeSchemas.LONG, value);
        }
    }

    record FloatDocument(float value) implements Document {
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
            serializer.writeFloat(PreludeSchemas.FLOAT, value);
        }
    }

    record DoubleDocument(double value) implements Document {
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
            serializer.writeDouble(PreludeSchemas.DOUBLE, value);
        }
    }

    record BigIntegerDocument(BigInteger value) implements Document {
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
            serializer.writeBigInteger(PreludeSchemas.BIG_INTEGER, value);
        }
    }

    record BigDecimalDocument(BigDecimal value) implements Document {
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
            serializer.writeBigDecimal(PreludeSchemas.BIG_DECIMAL, value);
        }
    }

    record StringDocument(String value) implements Document {
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
            serializer.writeString(PreludeSchemas.STRING, value);
        }
    }

    record BlobDocument(byte[] value) implements Document {
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
            serializer.writeBlob(PreludeSchemas.BLOB, value);
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

    record TimestampDocument(Instant value) implements Document {
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
            serializer.writeTimestamp(PreludeSchemas.TIMESTAMP, value);
        }
    }

    record ListDocument(List<Document> values) implements Document {
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
            serializer.beginList(PreludeSchemas.DOCUMENT, ser -> {
                for (var element : values) {
                    element.serializeContents(ser);
                }
            });
        }
    }

    record MapDocument(Map<Document, Document> members) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.MAP;
        }

        @Override
        public Map<Document, Document> asMap() {
            return members;
        }

        @Override
        public Document getMember(String memberName) {
            // Attempt to access a string member from the map.
            for (var entry : members.entrySet()) {
                if (entry.getKey().type() == ShapeType.STRING && entry.getKey().asString().equals(memberName)) {
                    return entry.getValue();
                }
            }
            return null;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.beginMap(PreludeSchemas.DOCUMENT, s -> {
                for (var entry : members.entrySet()) {
                    Document key = entry.getKey();
                    Consumer<ShapeSerializer> valueSer = ser -> entry.getValue().serializeContents(ser);
                    switch (key.type()) {
                        case STRING, ENUM -> s.entry(entry.getKey().asString(), valueSer);
                        case INTEGER, INT_ENUM -> s.entry(entry.getKey().asInteger(), valueSer);
                        case LONG -> s.entry(entry.getKey().asLong(), valueSer);
                        default -> throw new SdkSerdeException("Unexpected document map key: " + key);
                    }
                }
            });
        }
    }

    record StructureDocument(Map<String, Document> members) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.STRUCTURE;
        }

        @Override
        public Document getMember(String memberName) {
            return members.get(memberName);
        }

        @Override
        public Map<Document, Document> asMap() {
            Map<Document, Document> map = new LinkedHashMap<>();
            for (var entry : members.entrySet()) {
                map.put(Document.of(entry.getKey()), entry.getValue());
            }
            return map;
        }

        @Override
        public void serializeContents(ShapeSerializer encoder) {
            var structSerializer = encoder.beginStruct(PreludeSchemas.DOCUMENT);
            for (var entry : members.entrySet()) {
                structSerializer.member(syntheticMember(entry.getKey()), entry.getValue()::serializeContents);
            }
            structSerializer.endStruct();
        }
    }

    /**
     * Create a synthetic SdkSchema member so that serde consumers don't need to special case documents.
     *
     * @param name  Name of the member.
     * @return the created member schema.
     */
    private static SdkSchema syntheticMember(String name) {
        return SdkSchema.memberBuilder(name, PreludeSchemas.DOCUMENT)
            .id(PreludeSchemas.DOCUMENT.id())
            .build();
    }
}
