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
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
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
            serializer.writeList(PreludeSchemas.DOCUMENT, ser -> {
                for (var element : values) {
                    element.serializeContents(ser);
                }
            });
        }
    }

    record MapDocument(Map<Document, Document> members) implements Document {

        private static final SdkSchema STR_KEY = PreludeSchemas.DOCUMENT.getOrCreateDocumentMember(
            "key",
            PreludeSchemas.STRING
        );
        private static final SdkSchema INT_KEY = PreludeSchemas.DOCUMENT.getOrCreateDocumentMember(
            "key",
            PreludeSchemas.INTEGER
        );
        private static final SdkSchema LONG_KEY = PreludeSchemas.DOCUMENT.getOrCreateDocumentMember(
            "key",
            PreludeSchemas.LONG
        );

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
            serializer.writeMap(PreludeSchemas.DOCUMENT, s -> {
                for (var entry : members.entrySet()) {
                    Document key = entry.getKey();
                    Consumer<ShapeSerializer> valueSer = ser -> entry.getValue().serializeContents(ser);
                    switch (key.type()) {
                        case STRING, ENUM -> s.writeEntry(STR_KEY, entry.getKey().asString(), valueSer);
                        case INTEGER, INT_ENUM -> s.writeEntry(INT_KEY, entry.getKey().asInteger(), valueSer);
                        case LONG -> s.writeEntry(LONG_KEY, entry.getKey().asLong(), valueSer);
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
            encoder.writeStruct(PreludeSchemas.DOCUMENT, structSerializer -> {
                var rewriter = new StructMemberRewriter(structSerializer);
                for (var entry : members.entrySet()) {
                    rewriter.memberName = entry.getKey();
                    entry.getValue().serializeContents(rewriter);
                }
            });
        }
    }

    // Rewrites document members to use the right member name.
    private static final class StructMemberRewriter implements ShapeSerializer {

        private final ShapeSerializer structWriter;
        private String memberName;

        StructMemberRewriter(ShapeSerializer structWriter) {
            this.structWriter = structWriter;
        }

        @Override
        public void writeStruct(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
            structWriter.writeStruct(syntheticMember(memberName, schema), consumer);
        }

        @Override
        public void writeEnum(SdkSchema schema, Consumer<ShapeSerializer> consumer) {

        }

        @Override
        public void writeList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
            structWriter.writeList(syntheticMember(memberName, schema), consumer);
        }

        @Override
        public void writeMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
            structWriter.writeMap(syntheticMember(memberName, schema), consumer);
        }

        @Override
        public void writeBoolean(SdkSchema schema, boolean value) {
            structWriter.writeBoolean(syntheticMember(memberName, schema), value);
        }

        @Override
        public void writeByte(SdkSchema schema, byte value) {
            structWriter.writeByte(syntheticMember(memberName, schema), value);
        }

        @Override
        public void writeShort(SdkSchema schema, short value) {
            structWriter.writeShort(syntheticMember(memberName, schema), value);
        }

        @Override
        public void writeInteger(SdkSchema schema, int value) {
            structWriter.writeInteger(syntheticMember(memberName, schema), value);
        }

        @Override
        public void writeLong(SdkSchema schema, long value) {
            structWriter.writeLong(syntheticMember(memberName, schema), value);
        }

        @Override
        public void writeFloat(SdkSchema schema, float value) {
            structWriter.writeFloat(syntheticMember(memberName, schema), value);
        }

        @Override
        public void writeDouble(SdkSchema schema, double value) {
            structWriter.writeDouble(syntheticMember(memberName, schema), value);
        }

        @Override
        public void writeBigInteger(SdkSchema schema, BigInteger value) {
            structWriter.writeBigInteger(syntheticMember(memberName, schema), value);
        }

        @Override
        public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
            structWriter.writeBigDecimal(syntheticMember(memberName, schema), value);
        }

        @Override
        public void writeString(SdkSchema schema, String value) {
            structWriter.writeString(syntheticMember(memberName, schema), value);
        }

        @Override
        public void writeBlob(SdkSchema schema, byte[] value) {
            structWriter.writeBlob(syntheticMember(memberName, schema), value);
        }

        @Override
        public void writeTimestamp(SdkSchema schema, Instant value) {
            structWriter.writeTimestamp(syntheticMember(memberName, schema), value);
        }

        @Override
        public void writeDocument(SdkSchema schema, Document value) {
            structWriter.writeDocument(syntheticMember(memberName, schema), value);
        }

        @Override
        public void writeNull(SdkSchema schema) {
            structWriter.writeNull(syntheticMember(memberName, schema));
        }

        private static SdkSchema syntheticMember(String name, SdkSchema target) {
            return SdkSchema.memberBuilder(-1, name, target).id(PreludeSchemas.DOCUMENT.id()).build();
        }
    }
}
