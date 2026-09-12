/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.schema.IdxTrait;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaBuilder;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.Trait;

/**
 * Hand-built schemas and shapes for the Sparrowhawk codec tests.
 *
 * <p>Schemas mirror the reference repository's {@code demo.smithy} model where golden bytes are asserted.
 * All {@code serializeMembers} implementations emit members in memberIndex order (the order the core member
 * sort produces) unless a test intentionally does otherwise.
 */
final class TestShapes {

    private TestShapes() {}

    static Trait idx(int value) {
        return new IdxTrait(value);
    }

    // ===== OptionalStruct: mirrors the reference CodegenOptionalStruct { string(1), timestamp(2): Double } =====
    // Sorted member order: timestamp (T_EIGHT, index 0), string (T_LIST, index 1).

    static final Schema OPTIONAL_STRUCT = Schema.structureBuilder(ShapeId.from("test#OptionalStruct"))
            .putMember("string", PreludeSchemas.STRING, idx(1))
            .putMember("timestamp", PreludeSchemas.DOUBLE, idx(2))
            .build();
    static final Schema OPTIONAL_STRUCT_STRING = OPTIONAL_STRUCT.member("string");
    static final Schema OPTIONAL_STRUCT_TIMESTAMP = OPTIONAL_STRUCT.member("timestamp");

    static final class OptionalStruct implements SerializableStruct {
        final String string;
        final Double timestamp;

        OptionalStruct(String string, Double timestamp) {
            this.string = string;
            this.timestamp = timestamp;
        }

        @Override
        public Schema schema() {
            return OPTIONAL_STRUCT;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (timestamp != null) {
                serializer.writeDouble(OPTIONAL_STRUCT_TIMESTAMP, timestamp);
            }
            if (string != null) {
                serializer.writeString(OPTIONAL_STRUCT_STRING, string);
            }
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    static final class OptionalStructBuilder implements ShapeBuilder<OptionalStruct> {
        String string;
        Double timestamp;

        @Override
        public Schema schema() {
            return OPTIONAL_STRUCT;
        }

        @Override
        public ShapeBuilder<OptionalStruct> deserialize(ShapeDeserializer de) {
            de.readStruct(OPTIONAL_STRUCT, this, (b, member, d) -> {
                switch (member.memberName()) {
                    case "string" -> b.string = d.readString(member);
                    case "timestamp" -> b.timestamp = d.readDouble(member);
                    default -> throw new IllegalArgumentException(member.memberName());
                }
            });
            return this;
        }

        @Override
        public OptionalStruct build() {
            return new OptionalStruct(string, timestamp);
        }
    }

    // ===== DemoInput / NestedStructure: mirrors the reference demo.smithy model =====
    // DemoInput: str(1) String, f(2) float, d(3) double, i(4) int, bytes(5) blob, nested(6).
    // Sorted member order: i(0), f(1), d(2), str(3), bytes(4), nested(5).

    static final Schema VARINT_LIST = Schema.listBuilder(ShapeId.from("test#VarintList"))
            .putMember("member", PreludeSchemas.INTEGER)
            .build();
    static final Schema VARINT_LIST_MEMBER = VARINT_LIST.member("member");

    static final Schema NESTED_STRUCTURE = Schema.structureBuilder(ShapeId.from("test#NestedStructure"))
            .putMember("innerStr", PreludeSchemas.STRING, idx(1))
            .putMember("list", VARINT_LIST, idx(2))
            .build();
    static final Schema NESTED_INNER_STR = NESTED_STRUCTURE.member("innerStr");
    static final Schema NESTED_LIST = NESTED_STRUCTURE.member("list");

    static final Schema DEMO_INPUT = Schema.structureBuilder(ShapeId.from("test#DemoInput"))
            .putMember("str", PreludeSchemas.STRING, idx(1))
            .putMember("f", PreludeSchemas.FLOAT, idx(2))
            .putMember("d", PreludeSchemas.DOUBLE, idx(3))
            .putMember("i", PreludeSchemas.INTEGER, idx(4))
            .putMember("bytes", PreludeSchemas.BLOB, idx(5))
            .putMember("nested", NESTED_STRUCTURE, idx(6))
            .build();
    static final Schema DEMO_STR = DEMO_INPUT.member("str");
    static final Schema DEMO_F = DEMO_INPUT.member("f");
    static final Schema DEMO_D = DEMO_INPUT.member("d");
    static final Schema DEMO_I = DEMO_INPUT.member("i");
    static final Schema DEMO_BYTES = DEMO_INPUT.member("bytes");
    static final Schema DEMO_NESTED = DEMO_INPUT.member("nested");

    static final class Nested implements SerializableStruct {
        final String innerStr;
        final List<Integer> list;

        Nested(String innerStr, List<Integer> list) {
            this.innerStr = innerStr;
            this.list = list;
        }

        @Override
        public Schema schema() {
            return NESTED_STRUCTURE;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(NESTED_INNER_STR, innerStr);
            serializer.writeList(NESTED_LIST, list, list.size(), (l, s) -> {
                for (var v : l) {
                    s.writeInteger(VARINT_LIST_MEMBER, v);
                }
            });
        }

        @Override
        public long presenceBits() {
            return 0x3;
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    /** How a test struct reports presence, to exercise the serializer's different paths. */
    enum PresenceMode {
        /** Override presenceBits (the codegen fast path). */
        FAST,
        /** Leave presence unknown (the incremental path). */
        UNKNOWN
    }

    static final class DemoInput implements SerializableStruct {
        final String str;
        final float f;
        final double d;
        final int i;
        final ByteBuffer bytes;
        final Nested nested;
        final PresenceMode mode;

        DemoInput(String str, float f, double d, int i, ByteBuffer bytes, Nested nested, PresenceMode mode) {
            this.str = str;
            this.f = f;
            this.d = d;
            this.i = i;
            this.bytes = bytes;
            this.nested = nested;
            this.mode = mode;
        }

        @Override
        public Schema schema() {
            return DEMO_INPUT;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger(DEMO_I, i);
            serializer.writeFloat(DEMO_F, f);
            serializer.writeDouble(DEMO_D, d);
            serializer.writeString(DEMO_STR, str);
            serializer.writeBlob(DEMO_BYTES, bytes);
            if (nested != null) {
                serializer.writeStruct(DEMO_NESTED, nested);
            }
        }

        @Override
        public long presenceBits() {
            if (mode == PresenceMode.UNKNOWN) {
                return SerializableStruct.PRESENCE_UNKNOWN;
            }
            return 0x1F | (nested != null ? 0x20 : 0);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    /** Same shape as DemoInput but dispatches members in reverse order (exercises the unordered path). */
    static final class ReversedDemoInput implements SerializableStruct {
        final DemoInput delegate;

        ReversedDemoInput(DemoInput delegate) {
            this.delegate = delegate;
        }

        @Override
        public Schema schema() {
            return DEMO_INPUT;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (delegate.nested != null) {
                serializer.writeStruct(DEMO_NESTED, delegate.nested);
            }
            serializer.writeBlob(DEMO_BYTES, delegate.bytes);
            serializer.writeString(DEMO_STR, delegate.str);
            serializer.writeDouble(DEMO_D, delegate.d);
            serializer.writeFloat(DEMO_F, delegate.f);
            serializer.writeInteger(DEMO_I, delegate.i);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    /** Claims members are present that serializeMembers never writes. */
    static final class LyingDemoInput implements SerializableStruct {
        @Override
        public Schema schema() {
            return DEMO_INPUT;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger(DEMO_I, 1);
        }

        @Override
        public long presenceBits() {
            return 0x9; // claims i and str
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    static final class DemoInputBuilder implements ShapeBuilder<DemoInput> {
        String str;
        float f;
        double d;
        int i;
        ByteBuffer bytes;
        Nested nested;

        @Override
        public Schema schema() {
            return DEMO_INPUT;
        }

        @Override
        public ShapeBuilder<DemoInput> deserialize(ShapeDeserializer de) {
            de.readStruct(DEMO_INPUT, this, (b, member, d0) -> {
                switch (member.memberName()) {
                    case "str" -> b.str = d0.readString(member);
                    case "f" -> b.f = d0.readFloat(member);
                    case "d" -> b.d = d0.readDouble(member);
                    case "i" -> b.i = d0.readInteger(member);
                    case "bytes" -> b.bytes = d0.readBlob(member);
                    case "nested" -> {
                        String[] inner = new String[1];
                        List<Integer> list = new ArrayList<>();
                        d0.readStruct(DEMO_NESTED, b, (b2, m2, d2) -> {
                            switch (m2.memberName()) {
                                case "innerStr" -> inner[0] = d2.readString(m2);
                                case "list" -> d2.readList(m2, list, (l, d3) -> {
                                    l.add(d3.readInteger(VARINT_LIST_MEMBER));
                                });
                                default -> throw new IllegalArgumentException(m2.memberName());
                            }
                        });
                        b.nested = new Nested(inner[0], list);
                    }
                    default -> throw new IllegalArgumentException(member.memberName());
                }
            });
            return this;
        }

        @Override
        public DemoInput build() {
            return new DemoInput(str, f, d, i, bytes, nested, PresenceMode.FAST);
        }
    }

    // ===== Scalars: one member of each scalar type =====
    // Sorted order: aBool..aLong (varints by idx), aFloat, aDouble+aTimestamp, aString/aBlob/aBigInt/aBigDecimal.

    static final Schema SCALARS = Schema.structureBuilder(ShapeId.from("test#Scalars"))
            .putMember("aBool", PreludeSchemas.BOOLEAN, idx(1))
            .putMember("aByte", PreludeSchemas.BYTE, idx(2))
            .putMember("aShort", PreludeSchemas.SHORT, idx(3))
            .putMember("anInt", PreludeSchemas.INTEGER, idx(4))
            .putMember("aLong", PreludeSchemas.LONG, idx(5))
            .putMember("aFloat", PreludeSchemas.FLOAT, idx(6))
            .putMember("aDouble", PreludeSchemas.DOUBLE, idx(7))
            .putMember("aTimestamp", PreludeSchemas.TIMESTAMP, idx(8))
            .putMember("aString", PreludeSchemas.STRING, idx(9))
            .putMember("aBlob", PreludeSchemas.BLOB, idx(10))
            .putMember("aBigInt", PreludeSchemas.BIG_INTEGER, idx(11))
            .putMember("aBigDecimal", PreludeSchemas.BIG_DECIMAL, idx(12))
            .build();

    static class Scalars implements SerializableStruct {
        Boolean aBool;
        Byte aByte;
        Short aShort;
        Integer anInt;
        Long aLong;
        Float aFloat;
        Double aDouble;
        Instant aTimestamp;
        String aString;
        ByteBuffer aBlob;
        BigInteger aBigInt;
        BigDecimal aBigDecimal;

        @Override
        public Schema schema() {
            return SCALARS;
        }

        @Override
        public void serializeMembers(ShapeSerializer s) {
            if (aBool != null) {
                s.writeBoolean(SCALARS.member("aBool"), aBool);
            }
            if (aByte != null) {
                s.writeByte(SCALARS.member("aByte"), aByte);
            }
            if (aShort != null) {
                s.writeShort(SCALARS.member("aShort"), aShort);
            }
            if (anInt != null) {
                s.writeInteger(SCALARS.member("anInt"), anInt);
            }
            if (aLong != null) {
                s.writeLong(SCALARS.member("aLong"), aLong);
            }
            if (aFloat != null) {
                s.writeFloat(SCALARS.member("aFloat"), aFloat);
            }
            if (aDouble != null) {
                s.writeDouble(SCALARS.member("aDouble"), aDouble);
            }
            if (aTimestamp != null) {
                s.writeTimestamp(SCALARS.member("aTimestamp"), aTimestamp);
            }
            if (aString != null) {
                s.writeString(SCALARS.member("aString"), aString);
            }
            if (aBlob != null) {
                s.writeBlob(SCALARS.member("aBlob"), aBlob);
            }
            if (aBigInt != null) {
                s.writeBigInteger(SCALARS.member("aBigInt"), aBigInt);
            }
            if (aBigDecimal != null) {
                s.writeBigDecimal(SCALARS.member("aBigDecimal"), aBigDecimal);
            }
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    static final class ScalarsBuilder implements ShapeBuilder<Scalars> {
        final Scalars s = new Scalars();

        @Override
        public Schema schema() {
            return SCALARS;
        }

        @Override
        public ShapeBuilder<Scalars> deserialize(ShapeDeserializer de) {
            de.readStruct(SCALARS, s, (b, member, d) -> {
                switch (member.memberName()) {
                    case "aBool" -> b.aBool = d.readBoolean(member);
                    case "aByte" -> b.aByte = d.readByte(member);
                    case "aShort" -> b.aShort = d.readShort(member);
                    case "anInt" -> b.anInt = d.readInteger(member);
                    case "aLong" -> b.aLong = d.readLong(member);
                    case "aFloat" -> b.aFloat = d.readFloat(member);
                    case "aDouble" -> b.aDouble = d.readDouble(member);
                    case "aTimestamp" -> b.aTimestamp = d.readTimestamp(member);
                    case "aString" -> b.aString = d.readString(member);
                    case "aBlob" -> b.aBlob = d.readBlob(member);
                    case "aBigInt" -> b.aBigInt = d.readBigInteger(member);
                    case "aBigDecimal" -> b.aBigDecimal = d.readBigDecimal(member);
                    default -> throw new IllegalArgumentException(member.memberName());
                }
            });
            return this;
        }

        @Override
        public Scalars build() {
            return s;
        }
    }

    // ===== ScalarsV2: Scalars plus appended members (schema evolution) =====

    static final Schema SCALARS_V2 = Schema.structureBuilder(ShapeId.from("test#Scalars"))
            .putMember("aBool", PreludeSchemas.BOOLEAN, idx(1))
            .putMember("aByte", PreludeSchemas.BYTE, idx(2))
            .putMember("aShort", PreludeSchemas.SHORT, idx(3))
            .putMember("anInt", PreludeSchemas.INTEGER, idx(4))
            .putMember("aLong", PreludeSchemas.LONG, idx(5))
            .putMember("aFloat", PreludeSchemas.FLOAT, idx(6))
            .putMember("aDouble", PreludeSchemas.DOUBLE, idx(7))
            .putMember("aTimestamp", PreludeSchemas.TIMESTAMP, idx(8))
            .putMember("aString", PreludeSchemas.STRING, idx(9))
            .putMember("aBlob", PreludeSchemas.BLOB, idx(10))
            .putMember("aBigInt", PreludeSchemas.BIG_INTEGER, idx(11))
            .putMember("aBigDecimal", PreludeSchemas.BIG_DECIMAL, idx(12))
            .putMember("extraString", PreludeSchemas.STRING, idx(13))
            .putMember("extraInt", PreludeSchemas.INTEGER, idx(14))
            .build();

    static final class ScalarsV2 extends Scalars {
        String extraString;
        Integer extraInt;

        @Override
        public Schema schema() {
            return SCALARS_V2;
        }

        @Override
        public void serializeMembers(ShapeSerializer s) {
            if (aBool != null) {
                s.writeBoolean(SCALARS_V2.member("aBool"), aBool);
            }
            if (anInt != null) {
                s.writeInteger(SCALARS_V2.member("anInt"), anInt);
            }
            if (extraInt != null) {
                s.writeInteger(SCALARS_V2.member("extraInt"), extraInt);
            }
            if (aDouble != null) {
                s.writeDouble(SCALARS_V2.member("aDouble"), aDouble);
            }
            if (aString != null) {
                s.writeString(SCALARS_V2.member("aString"), aString);
            }
            if (extraString != null) {
                s.writeString(SCALARS_V2.member("extraString"), extraString);
            }
        }
    }

    // ===== Collections =====

    static final Schema STRING_LIST = Schema.listBuilder(ShapeId.from("test#StringList"))
            .putMember("member", PreludeSchemas.STRING)
            .build();
    static final Schema DOUBLE_LIST = Schema.listBuilder(ShapeId.from("test#DoubleList"))
            .putMember("member", PreludeSchemas.DOUBLE)
            .build();
    static final Schema STRUCT_LIST = Schema.listBuilder(ShapeId.from("test#StructList"))
            .putMember("member", OPTIONAL_STRUCT)
            .build();
    static final Schema SPARSE_STRING_LIST =
            Schema.listBuilder(ShapeId.from("test#SparseStringList"), new SparseTrait())
                    .putMember("member", PreludeSchemas.STRING)
                    .build();
    static final Schema SPARSE_BLOB_LIST = Schema.listBuilder(ShapeId.from("test#SparseBlobList"), new SparseTrait())
            .putMember("member", PreludeSchemas.BLOB)
            .build();
    static final Schema STRING_MAP = Schema.mapBuilder(ShapeId.from("test#StringMap"))
            .putMember("key", PreludeSchemas.STRING)
            .putMember("value", PreludeSchemas.STRING)
            .build();
    static final Schema INT_MAP = Schema.mapBuilder(ShapeId.from("test#IntMap"))
            .putMember("key", PreludeSchemas.STRING)
            .putMember("value", PreludeSchemas.INTEGER)
            .build();
    static final Schema STRUCT_MAP = Schema.mapBuilder(ShapeId.from("test#StructMap"))
            .putMember("key", PreludeSchemas.STRING)
            .putMember("value", OPTIONAL_STRUCT)
            .build();
    static final Schema SPARSE_INT_MAP = Schema.mapBuilder(ShapeId.from("test#SparseIntMap"), new SparseTrait())
            .putMember("key", PreludeSchemas.STRING)
            .putMember("value", PreludeSchemas.INTEGER)
            .build();

    static final Schema COLLECTIONS = Schema.structureBuilder(ShapeId.from("test#Collections"))
            .putMember("strings", STRING_LIST, idx(1))
            .putMember("ints", VARINT_LIST, idx(2))
            .putMember("doubles", DOUBLE_LIST, idx(3))
            .putMember("structs", STRUCT_LIST, idx(4))
            .putMember("sparseStrings", SPARSE_STRING_LIST, idx(5))
            .putMember("stringMap", STRING_MAP, idx(6))
            .putMember("intMap", INT_MAP, idx(7))
            .putMember("structMap", STRUCT_MAP, idx(8))
            .putMember("sparseIntMap", SPARSE_INT_MAP, idx(9))
            .build();

    static final class Collections implements SerializableStruct {
        List<String> strings;
        List<Integer> ints;
        List<Double> doubles;
        List<OptionalStruct> structs;
        List<String> sparseStrings;
        Map<String, String> stringMap;
        Map<String, Integer> intMap;
        Map<String, OptionalStruct> structMap;
        Map<String, Integer> sparseIntMap;

        @Override
        public Schema schema() {
            return COLLECTIONS;
        }

        @Override
        public void serializeMembers(ShapeSerializer s) {
            if (strings != null) {
                s.writeList(COLLECTIONS.member("strings"), strings, strings.size(), (l, ser) -> {
                    for (var v : l) {
                        ser.writeString(STRING_LIST.member("member"), v);
                    }
                });
            }
            if (ints != null) {
                s.writeList(COLLECTIONS.member("ints"), ints, ints.size(), (l, ser) -> {
                    for (var v : l) {
                        ser.writeInteger(VARINT_LIST_MEMBER, v);
                    }
                });
            }
            if (doubles != null) {
                s.writeList(COLLECTIONS.member("doubles"), doubles, doubles.size(), (l, ser) -> {
                    for (var v : l) {
                        ser.writeDouble(DOUBLE_LIST.member("member"), v);
                    }
                });
            }
            if (structs != null) {
                s.writeList(COLLECTIONS.member("structs"), structs, structs.size(), (l, ser) -> {
                    for (var v : l) {
                        ser.writeStruct(STRUCT_LIST.member("member"), v);
                    }
                });
            }
            if (sparseStrings != null) {
                s.writeList(COLLECTIONS.member("sparseStrings"), sparseStrings, sparseStrings.size(), (l, ser) -> {
                    for (var v : l) {
                        if (v == null) {
                            ser.writeNull(SPARSE_STRING_LIST.member("member"));
                        } else {
                            ser.writeString(SPARSE_STRING_LIST.member("member"), v);
                        }
                    }
                });
            }
            if (stringMap != null) {
                s.writeMap(COLLECTIONS.member("stringMap"), stringMap, stringMap.size(), (m, ser) -> {
                    for (var e : m.entrySet()) {
                        ser.writeEntry(STRING_MAP.member("key"), e.getKey(), e.getValue(), (v, vs) -> {
                            vs.writeString(STRING_MAP.member("value"), v);
                        });
                    }
                });
            }
            if (intMap != null) {
                s.writeMap(COLLECTIONS.member("intMap"), intMap, intMap.size(), (m, ser) -> {
                    for (var e : m.entrySet()) {
                        ser.writeEntry(INT_MAP.member("key"), e.getKey(), e.getValue(), (v, vs) -> {
                            vs.writeInteger(INT_MAP.member("value"), v);
                        });
                    }
                });
            }
            if (structMap != null) {
                s.writeMap(COLLECTIONS.member("structMap"), structMap, structMap.size(), (m, ser) -> {
                    for (var e : m.entrySet()) {
                        ser.writeEntry(STRUCT_MAP.member("key"), e.getKey(), e.getValue(), (v, vs) -> {
                            vs.writeStruct(STRUCT_MAP.member("value"), v);
                        });
                    }
                });
            }
            if (sparseIntMap != null) {
                s.writeMap(COLLECTIONS.member("sparseIntMap"), sparseIntMap, sparseIntMap.size(), (m, ser) -> {
                    for (var e : m.entrySet()) {
                        ser.writeEntry(SPARSE_INT_MAP.member("key"), e.getKey(), e.getValue(), (v, vs) -> {
                            if (v == null) {
                                vs.writeNull(SPARSE_INT_MAP.member("value"));
                            } else {
                                vs.writeInteger(SPARSE_INT_MAP.member("value"), v);
                            }
                        });
                    }
                });
            }
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    static final class CollectionsBuilder implements ShapeBuilder<Collections> {
        final Collections c = new Collections();

        @Override
        public Schema schema() {
            return COLLECTIONS;
        }

        @Override
        public ShapeBuilder<Collections> deserialize(ShapeDeserializer de) {
            de.readStruct(COLLECTIONS, c, (b, member, d) -> {
                switch (member.memberName()) {
                    case "strings" -> {
                        b.strings = new ArrayList<>();
                        d.readList(member, b.strings, (l, dd) -> l.add(dd.readString(STRING_LIST.member("member"))));
                    }
                    case "ints" -> {
                        b.ints = new ArrayList<>();
                        d.readList(member, b.ints, (l, dd) -> l.add(dd.readInteger(VARINT_LIST_MEMBER)));
                    }
                    case "doubles" -> {
                        b.doubles = new ArrayList<>();
                        d.readList(member, b.doubles, (l, dd) -> l.add(dd.readDouble(DOUBLE_LIST.member("member"))));
                    }
                    case "structs" -> {
                        b.structs = new ArrayList<>();
                        d.readList(member, b.structs, (l, dd) -> {
                            var builder = new OptionalStructBuilder();
                            builder.deserialize(dd);
                            l.add(builder.build());
                        });
                    }
                    case "sparseStrings" -> {
                        b.sparseStrings = new ArrayList<>();
                        d.readList(member, b.sparseStrings, (l, dd) -> {
                            if (dd.isNull()) {
                                l.add(dd.readNull());
                            } else {
                                l.add(dd.readString(SPARSE_STRING_LIST.member("member")));
                            }
                        });
                    }
                    case "stringMap" -> {
                        b.stringMap = new LinkedHashMap<>();
                        d.readStringMap(member, b.stringMap, (m, k, dd) -> {
                            m.put(k, dd.readString(STRING_MAP.member("value")));
                        });
                    }
                    case "intMap" -> {
                        b.intMap = new LinkedHashMap<>();
                        d.readStringMap(member, b.intMap, (m, k, dd) -> {
                            m.put(k, dd.readInteger(INT_MAP.member("value")));
                        });
                    }
                    case "structMap" -> {
                        b.structMap = new LinkedHashMap<>();
                        d.readStringMap(member, b.structMap, (m, k, dd) -> {
                            var builder = new OptionalStructBuilder();
                            builder.deserialize(dd);
                            m.put(k, builder.build());
                        });
                    }
                    case "sparseIntMap" -> {
                        b.sparseIntMap = new LinkedHashMap<>();
                        d.readStringMap(member, b.sparseIntMap, (m, k, dd) -> {
                            if (dd.isNull()) {
                                m.put(k, dd.readNull());
                            } else {
                                m.put(k, dd.readInteger(SPARSE_INT_MAP.member("value")));
                            }
                        });
                    }
                    default -> throw new IllegalArgumentException(member.memberName());
                }
            });
            return this;
        }

        @Override
        public Collections build() {
            return c;
        }
    }

    // ===== BigStruct: 70 varint members and 70 string members (continuation groups) =====

    static final Schema BIG_STRUCT;
    static {
        SchemaBuilder b = Schema.structureBuilder(ShapeId.from("test#BigStruct"));
        for (int j = 1; j <= 70; j++) {
            b.putMember("i" + j, PreludeSchemas.INTEGER, idx(j));
        }
        for (int j = 1; j <= 70; j++) {
            b.putMember("s" + j, PreludeSchemas.STRING, idx(70 + j));
        }
        BIG_STRUCT = b.build();
    }

    static final class BigStruct implements SerializableStruct {
        final Integer[] ints = new Integer[70];
        final String[] strings = new String[70];

        @Override
        public Schema schema() {
            return BIG_STRUCT;
        }

        @Override
        public void serializeMembers(ShapeSerializer s) {
            for (int j = 0; j < 70; j++) {
                if (ints[j] != null) {
                    s.writeInteger(BIG_STRUCT.member("i" + (j + 1)), ints[j]);
                }
            }
            for (int j = 0; j < 70; j++) {
                if (strings[j] != null) {
                    s.writeString(BIG_STRUCT.member("s" + (j + 1)), strings[j]);
                }
            }
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    static final class BigStructBuilder implements ShapeBuilder<BigStruct> {
        final BigStruct s = new BigStruct();

        @Override
        public Schema schema() {
            return BIG_STRUCT;
        }

        @Override
        public ShapeBuilder<BigStruct> deserialize(ShapeDeserializer de) {
            de.readStruct(BIG_STRUCT, s, (b, member, d) -> {
                String name = member.memberName();
                int j = Integer.parseInt(name.substring(1)) - 1;
                if (name.charAt(0) == 'i') {
                    b.ints[j] = d.readInteger(member);
                } else {
                    b.strings[j] = d.readString(member);
                }
            });
            return this;
        }

        @Override
        public BigStruct build() {
            return s;
        }
    }

    // ===== Union =====

    static final Schema UNION = Schema.unionBuilder(ShapeId.from("test#Union"))
            .putMember("num", PreludeSchemas.INTEGER, idx(1))
            .putMember("txt", PreludeSchemas.STRING, idx(2))
            .build();

    static final Schema UNION_V2 = Schema.unionBuilder(ShapeId.from("test#Union"))
            .putMember("num", PreludeSchemas.INTEGER, idx(1))
            .putMember("txt", PreludeSchemas.STRING, idx(2))
            .putMember("extra", PreludeSchemas.STRING, idx(3))
            .build();

    static final class UnionValue implements SerializableStruct {
        final Schema schema;
        final String memberName;
        final Object value;

        UnionValue(Schema schema, String memberName, Object value) {
            this.schema = schema;
            this.memberName = memberName;
            this.value = value;
        }

        @Override
        public Schema schema() {
            return schema;
        }

        @Override
        public void serializeMembers(ShapeSerializer s) {
            var member = schema.member(memberName);
            if (value instanceof Integer i) {
                s.writeInteger(member, i);
            } else {
                s.writeString(member, (String) value);
            }
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    // ===== MapHolder / nested maps: maps whose values contain further maps =====
    // Regression shape: an outer map's key spans must not swallow inner maps' keys.

    static final Schema MAP_HOLDER = Schema.structureBuilder(ShapeId.from("test#MapHolder"))
            .putMember("m", STRING_MAP, idx(1))
            .putMember("tag", PreludeSchemas.STRING, idx(2))
            .build();

    static final Schema HOLDER_MAP = Schema.mapBuilder(ShapeId.from("test#HolderMap"))
            .putMember("key", PreludeSchemas.STRING)
            .putMember("value", MAP_HOLDER)
            .build();

    static final Schema NESTED_MAPS = Schema.structureBuilder(ShapeId.from("test#NestedMaps"))
            .putMember("holders", HOLDER_MAP, idx(1))
            .build();

    record Holder(Map<String, String> m, String tag) {}

    static final class NestedMaps implements SerializableStruct {
        final Map<String, Holder> holders;

        NestedMaps(Map<String, Holder> holders) {
            this.holders = holders;
        }

        @Override
        public Schema schema() {
            return NESTED_MAPS;
        }

        @Override
        public void serializeMembers(ShapeSerializer s) {
            s.writeMap(NESTED_MAPS.member("holders"), holders, holders.size(), (m, ser) -> {
                for (var e : m.entrySet()) {
                    ser.writeEntry(HOLDER_MAP.member("key"), e.getKey(), e.getValue(), (h, vs) -> {
                        vs.writeStruct(HOLDER_MAP.member("value"), new SerializableStruct() {
                            @Override
                            public Schema schema() {
                                return MAP_HOLDER;
                            }

                            @Override
                            public void serializeMembers(ShapeSerializer s2) {
                                if (h.m() != null) {
                                    s2.writeMap(MAP_HOLDER.member("m"), h.m(), h.m().size(), (im, is) -> {
                                        for (var ie : im.entrySet()) {
                                            is.writeEntry(STRING_MAP.member("key"),
                                                    ie.getKey(),
                                                    ie.getValue(),
                                                    (v, ivs) -> ivs.writeString(STRING_MAP.member("value"), v));
                                        }
                                    });
                                }
                                if (h.tag() != null) {
                                    s2.writeString(MAP_HOLDER.member("tag"), h.tag());
                                }
                            }

                            @Override
                            public <T> T getMemberValue(Schema member) {
                                return null;
                            }
                        });
                    });
                }
            });
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    static final class NestedMapsBuilder implements ShapeBuilder<NestedMaps> {
        final Map<String, Holder> holders = new LinkedHashMap<>();

        @Override
        public Schema schema() {
            return NESTED_MAPS;
        }

        @Override
        public ShapeBuilder<NestedMaps> deserialize(ShapeDeserializer de) {
            de.readStruct(NESTED_MAPS, holders, (state, member, d) -> {
                d.readStringMap(member, state, (m, k, dd) -> {
                    Map<String, String> inner = new LinkedHashMap<>();
                    String[] tag = new String[1];
                    dd.readStruct(HOLDER_MAP.member("value"), inner, (st, m2, d2) -> {
                        switch (m2.memberName()) {
                            case "m" -> d2.readStringMap(m2, st, (mm, kk, d3) -> {
                                mm.put(kk, d3.readString(STRING_MAP.member("value")));
                            });
                            case "tag" -> tag[0] = d2.readString(m2);
                            default -> throw new IllegalArgumentException(m2.memberName());
                        }
                    });
                    m.put(k, new Holder(inner, tag[0]));
                });
            });
            return this;
        }

        @Override
        public NestedMaps build() {
            return new NestedMaps(holders);
        }
    }

    // ===== OneOf: a struct serializing exactly one member, with configurable presence reporting =====
    // Used to check the inline single-leaf-member path against the incremental path byte for byte.

    static final Schema WIDE_STRINGS;
    static {
        SchemaBuilder b = Schema.structureBuilder(ShapeId.from("test#WideStrings"));
        for (int j = 1; j <= 63; j++) {
            b.putMember("s" + j, PreludeSchemas.STRING, idx(j));
        }
        WIDE_STRINGS = b.build();
    }

    static final class OneOf implements SerializableStruct {
        final Schema schema;
        final String memberName;
        final Object value;
        final boolean fast;

        OneOf(Schema schema, String memberName, Object value, boolean fast) {
            this.schema = schema;
            this.memberName = memberName;
            this.value = value;
            this.fast = fast;
        }

        @Override
        public Schema schema() {
            return schema;
        }

        @Override
        public void serializeMembers(ShapeSerializer s) {
            var member = schema.member(memberName);
            if (value instanceof Integer i) {
                s.writeInteger(member, i);
            } else if (value instanceof Long l) {
                s.writeLong(member, l);
            } else if (value instanceof Double d) {
                s.writeDouble(member, d);
            } else if (value instanceof Float f) {
                s.writeFloat(member, f);
            } else if (value instanceof ByteBuffer b) {
                s.writeBlob(member, b);
            } else {
                s.writeString(member, (String) value);
            }
        }

        @Override
        public long presenceBits() {
            return fast ? 1L << schema.member(memberName).memberIndex() : SerializableStruct.PRESENCE_UNKNOWN;
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    // ===== A shape that serializes a bare top-level list =====

    static final class TopLevelSparseBlobList implements SerializableShape {
        final List<ByteBuffer> values;

        TopLevelSparseBlobList(List<ByteBuffer> values) {
            this.values = values;
        }

        @Override
        public void serialize(ShapeSerializer encoder) {
            encoder.writeList(SPARSE_BLOB_LIST, values, values.size(), (l, s) -> {
                for (var v : l) {
                    if (v == null) {
                        s.writeNull(SPARSE_BLOB_LIST.member("member"));
                    } else {
                        s.writeBlob(SPARSE_BLOB_LIST.member("member"), v);
                    }
                }
            });
        }
    }

    // ===== Longs: a single-long struct for varint edge encoding =====

    static final Schema LONGS = Schema.structureBuilder(ShapeId.from("test#Longs"))
            .putMember("aLong", PreludeSchemas.LONG, idx(1))
            .build();

    static final class Longs implements SerializableStruct {
        final long value;

        Longs(long value) {
            this.value = value;
        }

        @Override
        public Schema schema() {
            return LONGS;
        }

        @Override
        public void serializeMembers(ShapeSerializer s) {
            s.writeLong(LONGS.member("aLong"), value);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }
}
