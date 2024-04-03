/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.StructSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Captures whatever is emitted from a member shape serializer and converts it lazily into a Document.
 *
 * <p>This class captures all the necessary properties of a document, including casting numbers to the requested
 * type and converting between blobs and strings automatically.
 */
final class TypedDocumentMember implements Document {

    private final SdkSchema schema;
    private final Consumer<ShapeSerializer> memberWriter;
    private volatile int computedHashCode;
    private volatile Document equalityValue;

    TypedDocumentMember(SdkSchema schema, Consumer<ShapeSerializer> memberWriter) {
        this.schema = Objects.requireNonNull(schema, "Typed document member schema is null");
        this.memberWriter = Objects.requireNonNull(memberWriter, "Typed document member writer is null");
    }

    @Override
    public String toString() {
        return "TypedDocumentMember{schema=" + schema + ", value=" + getEqualityValue() + '}';
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        memberWriter.accept(encoder);
    }

    @Override
    public ShapeType type() {
        return schema.type();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TypedDocumentMember that = (TypedDocumentMember) o;

        // Ensure the document schema matches.
        if (!schema.equals(that.schema)) {
            return false;
        }

        // Compare equality based on the value of the serialized shapes.
        return getEqualityValue().equals(that.getEqualityValue());
    }

    // Equality of typed documents is based on the serialized value of the document.
    private Document getEqualityValue() {
        var eq = equalityValue;
        if (eq == null) {
            eq = equalityValue = Document.ofValue(this);
        }
        return eq;
    }

    @Override
    public int hashCode() {
        var hash = computedHashCode;
        if (hash == 0) {
            hash = Objects.hash(schema, getEqualityValue());
            computedHashCode = hash;
        }
        return hash;
    }

    @Override
    public boolean asBoolean() {
        var expect = new SpecificShapeSerializer() {
            private boolean value;

            @Override
            public void writeBoolean(SdkSchema schema, boolean value) {
                this.value = value;
            }
        };
        memberWriter.accept(expect);
        return expect.value;
    }

    @Override
    public byte asByte() {
        var expect = new SpecificShapeSerializer() {
            private byte value;

            @Override
            public void writeByte(SdkSchema schema, byte value) {
                this.value = value;
            }

            @Override
            public void writeShort(SdkSchema schema, short value) {
                this.value = (byte) value;
            }

            @Override
            public void writeInteger(SdkSchema schema, int value) {
                this.value = (byte) value;
            }

            @Override
            public void writeLong(SdkSchema schema, long value) {
                this.value = (byte) value;
            }

            @Override
            public void writeFloat(SdkSchema schema, float value) {
                this.value = (byte) value;
            }

            @Override
            public void writeDouble(SdkSchema schema, double value) {
                this.value = (byte) value;
            }

            @Override
            public void writeBigInteger(SdkSchema schema, BigInteger value) {
                this.value = value.byteValueExact();
            }

            @Override
            public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
                this.value = value.byteValueExact();
            }
        };
        memberWriter.accept(expect);
        return expect.value;
    }

    @Override
    public short asShort() {
        var expect = new SpecificShapeSerializer() {
            private short value;

            @Override
            public void writeByte(SdkSchema schema, byte value) {
                this.value = value;
            }

            @Override
            public void writeShort(SdkSchema schema, short value) {
                this.value = value;
            }

            @Override
            public void writeInteger(SdkSchema schema, int value) {
                this.value = (short) value;
            }

            @Override
            public void writeLong(SdkSchema schema, long value) {
                this.value = (short) value;
            }

            @Override
            public void writeFloat(SdkSchema schema, float value) {
                this.value = (short) value;
            }

            @Override
            public void writeDouble(SdkSchema schema, double value) {
                this.value = (short) value;
            }

            @Override
            public void writeBigInteger(SdkSchema schema, BigInteger value) {
                this.value = value.shortValueExact();
            }

            @Override
            public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
                this.value = value.shortValueExact();
            }
        };
        memberWriter.accept(expect);
        return expect.value;
    }

    @Override
    public int asInteger() {
        var expect = new SpecificShapeSerializer() {
            private int value;

            @Override
            public void writeByte(SdkSchema schema, byte value) {
                this.value = value;
            }

            @Override
            public void writeShort(SdkSchema schema, short value) {
                this.value = value;
            }

            @Override
            public void writeInteger(SdkSchema schema, int value) {
                this.value = value;
            }

            @Override
            public void writeLong(SdkSchema schema, long value) {
                this.value = (int) value;
            }

            @Override
            public void writeFloat(SdkSchema schema, float value) {
                this.value = (int) value;
            }

            @Override
            public void writeDouble(SdkSchema schema, double value) {
                this.value = (int) value;
            }

            @Override
            public void writeBigInteger(SdkSchema schema, BigInteger value) {
                this.value = value.intValueExact();
            }

            @Override
            public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
                this.value = value.intValueExact();
            }
        };
        memberWriter.accept(expect);
        return expect.value;
    }

    @Override
    public long asLong() {
        var expect = new SpecificShapeSerializer() {
            private long value;

            @Override
            public void writeByte(SdkSchema schema, byte value) {
                this.value = value;
            }

            @Override
            public void writeShort(SdkSchema schema, short value) {
                this.value = value;
            }

            @Override
            public void writeInteger(SdkSchema schema, int value) {
                this.value = value;
            }

            @Override
            public void writeLong(SdkSchema schema, long value) {
                this.value = value;
            }

            @Override
            public void writeFloat(SdkSchema schema, float value) {
                this.value = (long) value;
            }

            @Override
            public void writeDouble(SdkSchema schema, double value) {
                this.value = (long) value;
            }

            @Override
            public void writeBigInteger(SdkSchema schema, BigInteger value) {
                this.value = value.longValueExact();
            }

            @Override
            public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
                this.value = value.longValueExact();
            }
        };
        memberWriter.accept(expect);
        return expect.value;
    }

    @Override
    public float asFloat() {
        var expect = new SpecificShapeSerializer() {
            private float value;

            @Override
            public void writeByte(SdkSchema schema, byte value) {
                this.value = value;
            }

            @Override
            public void writeShort(SdkSchema schema, short value) {
                this.value = value;
            }

            @Override
            public void writeInteger(SdkSchema schema, int value) {
                this.value = value;
            }

            @Override
            public void writeLong(SdkSchema schema, long value) {
                this.value = value;
            }

            @Override
            public void writeFloat(SdkSchema schema, float value) {
                this.value = value;
            }

            @Override
            public void writeDouble(SdkSchema schema, double value) {
                this.value = (float) value;
            }

            @Override
            public void writeBigInteger(SdkSchema schema, BigInteger value) {
                this.value = value.floatValue();
            }

            @Override
            public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
                this.value = value.floatValue();
            }
        };
        memberWriter.accept(expect);
        return expect.value;
    }

    @Override
    public double asDouble() {
        var expect = new SpecificShapeSerializer() {
            private double value;

            @Override
            public void writeByte(SdkSchema schema, byte value) {
                this.value = value;
            }

            @Override
            public void writeShort(SdkSchema schema, short value) {
                this.value = value;
            }

            @Override
            public void writeInteger(SdkSchema schema, int value) {
                this.value = value;
            }

            @Override
            public void writeLong(SdkSchema schema, long value) {
                this.value = value;
            }

            @Override
            public void writeFloat(SdkSchema schema, float value) {
                this.value = value;
            }

            @Override
            public void writeDouble(SdkSchema schema, double value) {
                this.value = value;
            }

            @Override
            public void writeBigInteger(SdkSchema schema, BigInteger value) {
                this.value = value.doubleValue();
            }

            @Override
            public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
                this.value = value.doubleValue();
            }
        };
        memberWriter.accept(expect);
        return expect.value;
    }

    @Override
    public BigInteger asBigInteger() {
        var expect = new SpecificShapeSerializer() {
            private BigInteger value;

            @Override
            public void writeByte(SdkSchema schema, byte value) {
                this.value = BigInteger.valueOf(value);
            }

            @Override
            public void writeShort(SdkSchema schema, short value) {
                this.value = BigInteger.valueOf(value);
            }

            @Override
            public void writeInteger(SdkSchema schema, int value) {
                this.value = BigInteger.valueOf(value);
            }

            @Override
            public void writeLong(SdkSchema schema, long value) {
                this.value = BigInteger.valueOf(value);
            }

            @Override
            public void writeFloat(SdkSchema schema, float value) {
                this.value = BigInteger.valueOf((long) value);
            }

            @Override
            public void writeDouble(SdkSchema schema, double value) {
                this.value = BigInteger.valueOf((long) value);
            }

            @Override
            public void writeBigInteger(SdkSchema schema, BigInteger value) {
                this.value = value;
            }

            @Override
            public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
                this.value = value.toBigInteger();
            }
        };
        memberWriter.accept(expect);
        return expect.value;
    }

    @Override
    public BigDecimal asBigDecimal() {
        var expect = new SpecificShapeSerializer() {
            private BigDecimal value;

            @Override
            public void writeByte(SdkSchema schema, byte value) {
                this.value = new BigDecimal(value);
            }

            @Override
            public void writeShort(SdkSchema schema, short value) {
                this.value = new BigDecimal(value);
            }

            @Override
            public void writeInteger(SdkSchema schema, int value) {
                this.value = BigDecimal.valueOf(value);
            }

            @Override
            public void writeLong(SdkSchema schema, long value) {
                this.value = BigDecimal.valueOf(value);
            }

            @Override
            public void writeFloat(SdkSchema schema, float value) {
                this.value = new BigDecimal(value);
            }

            @Override
            public void writeDouble(SdkSchema schema, double value) {
                this.value = new BigDecimal(value);
            }

            @Override
            public void writeBigInteger(SdkSchema schema, BigInteger value) {
                this.value = new BigDecimal(value);
            }

            @Override
            public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
                this.value = value;
            }
        };
        memberWriter.accept(expect);
        return expect.value;
    }

    @Override
    public String asString() {
        var expect = new SpecificShapeSerializer() {
            private String value;

            @Override
            public void writeString(SdkSchema schema, String value) {
                this.value = value;
            }

            @Override
            public void writeBlob(SdkSchema schema, byte[] value) {
                this.value = new String(value, StandardCharsets.UTF_8);
            }
        };
        memberWriter.accept(expect);
        return expect.value;
    }

    @Override
    public byte[] asBlob() {
        var expect = new SpecificShapeSerializer() {
            private byte[] value;

            @Override
            public void writeString(SdkSchema schema, String value) {
                this.value = value.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public void writeBlob(SdkSchema schema, byte[] value) {
                this.value = value;
            }
        };
        memberWriter.accept(expect);
        return expect.value;
    }

    @Override
    public Instant asTimestamp() {
        var expect = new SpecificShapeSerializer() {
            private Instant value;

            @Override
            public void writeTimestamp(SdkSchema schema, Instant value) {
                this.value = value;
            }
        };
        memberWriter.accept(expect);
        return expect.value;
    }


    @Override
    public List<Document> asList() {
        var expect = new SpecificShapeSerializer() {
            private final List<Document> values = new ArrayList<>();

            @Override
            public void beginList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
                values.add(new TypedDocumentMember(schema.documentMember("member"), consumer));
            }
        };
        memberWriter.accept(expect);
        return expect.values;
    }

    @Override
    public Map<Document, Document> asMap() {
        var expect = new SpecificShapeSerializer() {
            private final Map<Document, Document> values = new LinkedHashMap<>();

            @Override
            public void beginMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
                var valueMember = schema.documentMember("value");
                consumer.accept(new MapSerializer() {
                    @Override
                    public void entry(String key, Consumer<ShapeSerializer> valueSerializer) {
                        var keyMember = schema.documentMember("key", PreludeSchemas.STRING);
                        values.put(
                            new TypedDocumentMember(keyMember, ser -> ser.writeString(keyMember, key)),
                            new TypedDocumentMember(valueMember, valueSerializer)
                        );
                    }

                    @Override
                    public void entry(int key, Consumer<ShapeSerializer> valueSerializer) {
                        var keyMember = schema.documentMember("key", PreludeSchemas.INTEGER);
                        values.put(
                            new TypedDocumentMember(keyMember, ser -> ser.writeInteger(keyMember, key)),
                            new TypedDocumentMember(valueMember, valueSerializer)
                        );
                    }

                    @Override
                    public void entry(long key, Consumer<ShapeSerializer> valueSerializer) {
                        var keyMember = schema.documentMember("key", PreludeSchemas.LONG);
                        values.put(
                            new TypedDocumentMember(keyMember, ser -> ser.writeLong(keyMember, key)),
                            new TypedDocumentMember(valueMember, valueSerializer)
                        );
                    }
                });
            }
        };
        memberWriter.accept(expect);
        return expect.values;
    }

    @Override
    public Document getMember(String memberName) {
        var expect = new SpecificShapeSerializer() {
            private Document result;

            @Override
            public void beginMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
                consumer.accept(new MapSerializer() {
                    @Override
                    public void entry(String key, Consumer<ShapeSerializer> valueSerializer) {
                        if (key.equals(memberName)) {
                            var memberSchema = schema.documentMember("member");
                            result = new TypedDocumentMember(memberSchema, valueSerializer);
                        }
                    }

                    @Override
                    public void entry(int key, Consumer<ShapeSerializer> valueSerializer) {}

                    @Override
                    public void entry(long key, Consumer<ShapeSerializer> valueSerializer) {}
                });
            }

            @Override
            public StructSerializer beginStruct(SdkSchema schema) {
                return new StructSerializer() {
                    @Override
                    public void endStruct() {}

                    @Override
                    public void member(SdkSchema member, Consumer<ShapeSerializer> memberWriter) {
                        if (member.memberName().equals(memberName)) {
                            result = new TypedDocumentMember(member, memberWriter);
                        }
                    }
                };
            }
        };
        memberWriter.accept(expect);
        return expect.result;
    }
}
