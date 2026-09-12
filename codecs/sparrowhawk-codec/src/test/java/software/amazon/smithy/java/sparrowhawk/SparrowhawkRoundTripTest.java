/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

public class SparrowhawkRoundTripTest {

    private static final Codec CODEC = SparrowhawkCodec.get();

    private static <T extends SerializableShape> T roundTrip(SerializableStruct value, ShapeBuilder<T> builder) {
        return CODEC.deserializeShape(CODEC.serialize(value), builder);
    }

    private static byte[] bytes(ByteBuffer bb) {
        byte[] b = new byte[bb.remaining()];
        bb.duplicate().get(b);
        return b;
    }

    @Test
    public void allScalars() {
        var s = new TestShapes.Scalars();
        s.aBool = true;
        s.aByte = (byte) -7;
        s.aShort = (short) 1234;
        s.anInt = Integer.MIN_VALUE;
        s.aLong = Long.MAX_VALUE;
        s.aFloat = 3.7f;
        s.aDouble = 1.5d;
        s.aTimestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        s.aString = "kestrel ❤ unicode";
        s.aBlob = ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5});
        s.aBigInt = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN);
        s.aBigDecimal = new BigDecimal("12345.6789");

        var de = roundTrip(s, new TestShapes.ScalarsBuilder());
        assertEquals(true, de.aBool);
        assertEquals((byte) -7, de.aByte);
        assertEquals((short) 1234, de.aShort);
        assertEquals(Integer.MIN_VALUE, de.anInt);
        assertEquals(Long.MAX_VALUE, de.aLong);
        assertEquals(3.7f, de.aFloat);
        assertEquals(1.5d, de.aDouble);
        assertEquals(s.aTimestamp, de.aTimestamp);
        assertEquals("kestrel ❤ unicode", de.aString);
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, bytes(de.aBlob));
        assertEquals(s.aBigInt, de.aBigInt);
        assertEquals(s.aBigDecimal, de.aBigDecimal);
    }

    @Test
    public void scalarEdgeValues() {
        var s = new TestShapes.Scalars();
        s.anInt = 0;
        s.aLong = Long.MIN_VALUE;
        s.aDouble = -0.0d;
        s.aString = "";
        s.aBigInt = BigInteger.valueOf(-1);
        s.aBigDecimal = new BigDecimal("-0.0001");

        var de = roundTrip(s, new TestShapes.ScalarsBuilder());
        assertEquals(0, de.anInt);
        assertEquals(Long.MIN_VALUE, de.aLong);
        assertEquals(Double.doubleToLongBits(-0.0d), Double.doubleToLongBits(de.aDouble));
        assertEquals("", de.aString);
        assertNull(de.aBool);
        assertNull(de.aBlob);
        assertEquals(BigInteger.valueOf(-1), de.aBigInt);
        assertEquals(new BigDecimal("-0.0001"), de.aBigDecimal);
    }

    @Test
    public void collections() {
        var c = new TestShapes.Collections();
        c.strings = List.of("a", "b", "see");
        c.ints = List.of(0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE);
        c.doubles = List.of(1.5d, -2.25d);
        c.structs = List.of(
                new TestShapes.OptionalStruct("first", 1.0d),
                new TestShapes.OptionalStruct(null, 2.0d));
        c.stringMap = new LinkedHashMap<>();
        c.stringMap.put("k1", "v1");
        c.stringMap.put("k2", "v2");
        c.intMap = new LinkedHashMap<>();
        c.intMap.put("a", 1);
        c.intMap.put("b", -1);
        c.structMap = new LinkedHashMap<>();
        c.structMap.put("x", new TestShapes.OptionalStruct("inner", 9.0d));

        var de = roundTrip(c, new TestShapes.CollectionsBuilder());
        assertEquals(List.of("a", "b", "see"), de.strings);
        assertEquals(List.of(0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE), de.ints);
        assertEquals(List.of(1.5d, -2.25d), de.doubles);
        assertEquals(2, de.structs.size());
        assertEquals("first", de.structs.get(0).string);
        assertEquals(1.0d, de.structs.get(0).timestamp);
        assertNull(de.structs.get(1).string);
        assertEquals(2.0d, de.structs.get(1).timestamp);
        assertEquals(c.stringMap, de.stringMap);
        assertEquals(c.intMap, de.intMap);
        assertEquals("inner", de.structMap.get("x").string);
    }

    @Test
    public void emptyCollections() {
        var c = new TestShapes.Collections();
        c.strings = List.of();
        c.stringMap = new LinkedHashMap<>();

        var de = roundTrip(c, new TestShapes.CollectionsBuilder());
        assertEquals(List.of(), de.strings);
        assertEquals(Map.of(), de.stringMap);
        assertNull(de.ints);
    }

    @Test
    public void sparseCollections() {
        var c = new TestShapes.Collections();
        var list = new ArrayList<String>();
        list.add("one");
        list.add(null);
        list.add("three");
        list.add("");
        c.sparseStrings = list;
        var map = new LinkedHashMap<String, Integer>();
        map.put("a", 1);
        map.put("b", null);
        map.put("c", -3);
        c.sparseIntMap = map;

        var de = roundTrip(c, new TestShapes.CollectionsBuilder());
        assertEquals(list, de.sparseStrings);
        assertEquals(map, de.sparseIntMap);
    }

    @Test
    public void nullInDenseListThrows() {
        SerializableShape shape = encoder -> encoder.writeList(
                TestShapes.STRING_LIST,
                (Object) null,
                1,
                (state, s) -> s.writeNull(TestShapes.STRING_LIST.member("member")));
        assertThrows(SerializationException.class, () -> CODEC.serialize(shape));
    }

    @Test
    public void bigStructContinuationGroups() {
        var s = new TestShapes.BigStruct();
        // Fields spanning both the first and continuation groups of both sections, with gaps.
        for (int j = 0; j < 70; j += 3) {
            s.ints[j] = j * 1001 - 35;
            s.strings[j] = "value-" + j;
        }
        s.ints[60] = 60;
        s.ints[61] = 61;
        s.ints[69] = 69;
        s.strings[69] = "last";

        var de = roundTrip(s, new TestShapes.BigStructBuilder());
        assertArrayEquals(s.ints, de.ints);
        assertArrayEquals(s.strings, de.strings);
    }

    @Test
    public void bigStructOnlyContinuationGroup() {
        // Only fields in the second 61-field group are present: the first group emits no section header.
        var s = new TestShapes.BigStruct();
        s.ints[65] = 42;
        s.strings[66] = "hi";

        var de = roundTrip(s, new TestShapes.BigStructBuilder());
        assertArrayEquals(s.ints, de.ints);
        assertArrayEquals(s.strings, de.strings);
    }

    @Test
    public void mapsNestedInMapValues() {
        // Regression: an outer map's key block must not swallow inner maps' keys (the inner spans
        // interleave with the outer's in the shared key scratch).
        var holders = new LinkedHashMap<String, TestShapes.Holder>();
        var m1 = new LinkedHashMap<String, String>();
        m1.put("ik1", "iv1");
        m1.put("ik2", "iv2");
        holders.put("outer-one", new TestShapes.Holder(m1, "first"));
        var m2 = new LinkedHashMap<String, String>();
        m2.put("another-inner-key", "another-inner-value");
        holders.put("outer-two", new TestShapes.Holder(m2, "second"));
        holders.put("outer-three", new TestShapes.Holder(new LinkedHashMap<>(), null));

        var de = roundTrip(new TestShapes.NestedMaps(holders), new TestShapes.NestedMapsBuilder());
        assertEquals(m1, de.holders.get("outer-one").m());
        assertEquals("first", de.holders.get("outer-one").tag());
        assertEquals(m2, de.holders.get("outer-two").m());
        assertEquals("second", de.holders.get("outer-two").tag());
        assertEquals(Map.of(), de.holders.get("outer-three").m());
    }

    @Test
    public void unionRoundTrip() {
        var value = new TestShapes.UnionValue(TestShapes.UNION, "num", 42);
        var read = new Object[2];
        var deser = CODEC.createDeserializer(bytes(CODEC.serialize(value)));
        deser.readStruct(TestShapes.UNION, read, (state, member, d) -> {
            state[0] = member.memberName();
            state[1] = member.memberName().equals("num") ? d.readInteger(member) : d.readString(member);
        });
        assertEquals("num", read[0]);
        assertEquals(42, read[1]);
    }

    @Test
    public void unknownUnionMemberReported() {
        var value = new TestShapes.UnionValue(TestShapes.UNION_V2, "extra", "future");
        var unknown = new String[1];
        var deser = CODEC.createDeserializer(bytes(CODEC.serialize(value)));
        deser.readStruct(TestShapes.UNION,
                unknown,
                new ShapeDeserializer.StructMemberConsumer<String[]>() {
                    @Override
                    public void accept(
                            String[] state,
                            Schema member,
                            ShapeDeserializer d
                    ) {
                        throw new AssertionError("expected only an unknown member");
                    }

                    @Override
                    public void unknownMember(String[] state, String memberName) {
                        state[0] = memberName;
                    }
                });
        assertEquals("1", unknown[0]);
    }

    @Test
    public void schemaEvolutionNewFieldsSkipped() {
        // A newer writer adds appended members; an older reader must skip them without desyncing.
        var v2 = new TestShapes.ScalarsV2();
        v2.aBool = true;
        v2.anInt = 99;
        v2.aDouble = 2.5d;
        v2.aString = "known";
        v2.extraString = "newer-string";
        v2.extraInt = 777;

        var de = CODEC.deserializeShape(CODEC.serialize(v2), new TestShapes.ScalarsBuilder());
        assertEquals(true, de.aBool);
        assertEquals(99, de.anInt);
        assertEquals(2.5d, de.aDouble);
        assertEquals("known", de.aString);
    }

    @Test
    public void schemaEvolutionOldPayloadNewReader() {
        // An older writer's payload read against the newer schema: appended members are simply absent.
        var v1 = new TestShapes.Scalars();
        v1.anInt = 7;
        v1.aString = "old";
        var payload = bytes(CODEC.serialize(v1));

        var seen = new LinkedHashMap<String, Object>();
        var deser = CODEC.createDeserializer(payload);
        deser.readStruct(TestShapes.SCALARS_V2, seen, (state, member, d) -> {
            switch (member.memberName()) {
                case "anInt" -> state.put("anInt", d.readInteger(member));
                case "aString" -> state.put("aString", d.readString(member));
                default -> throw new AssertionError("unexpected member: " + member.memberName());
            }
        });
        assertEquals(Map.of("anInt", 7, "aString", "old"), seen);
    }

    @Test
    public void fastPathOutOfOrderWithPresenceBitsThrows() {
        // A struct that provides presenceBits must dispatch in memberIndex order.
        var struct = new SerializableStruct() {
            @Override
            public Schema schema() {
                return TestShapes.DEMO_INPUT;
            }

            @Override
            public void serializeMembers(ShapeSerializer s) {
                s.writeFloat(TestShapes.DEMO_F, 1f);
                s.writeInteger(TestShapes.DEMO_I, 1);
            }

            @Override
            public long presenceBits() {
                return 0x3;
            }

            @Override
            public <T> T getMemberValue(Schema member) {
                return null;
            }
        };
        assertThrows(SerializationException.class, () -> CODEC.serialize(struct));
    }

    @Test
    public void byteBufferDeserialization() {
        var s = new TestShapes.OptionalStruct("hello", 4.25d);
        ByteBuffer payload = CODEC.serialize(s);
        var builder = new TestShapes.OptionalStructBuilder();
        builder.deserialize(CODEC.createDeserializer(payload));
        var de = builder.build();
        assertEquals("hello", de.string);
        assertEquals(4.25d, de.timestamp);
    }

    private static byte[] serializeList(List<String> values, int sizeHint) {
        SerializableShape shape = encoder -> encoder.writeList(
                TestShapes.STRING_LIST,
                values,
                sizeHint,
                (state, s) -> {
                    for (var v : state) {
                        s.writeString(TestShapes.STRING_LIST.member("member"), v);
                    }
                });
        return bytes(CODEC.serialize(shape));
    }

    private static byte[] serializeMap(Map<String, String> values, int sizeHint) {
        SerializableShape shape = encoder -> encoder.writeMap(
                TestShapes.STRING_MAP,
                values,
                sizeHint,
                (state, ms) -> state.forEach((k, v) -> ms.writeEntry(
                        TestShapes.STRING_MAP.member("key"),
                        k,
                        v,
                        (value, s) -> s.writeString(TestShapes.STRING_MAP.member("value"), value))));
        return bytes(CODEC.serialize(shape));
    }

    @Test
    public void unknownSizeListMatchesKnownSize() {
        // A known size writes the count-prefixed header forward; unknown backfills. Same bytes either
        // way, including a count large enough to need a multi-byte header.
        var few = List.of("a", "bb", "ccc");
        assertArrayEquals(serializeList(few, few.size()), serializeList(few, -1));
        var many = new ArrayList<String>();
        for (int i = 0; i < 40; i++) {
            many.add("value-" + i);
        }
        assertArrayEquals(serializeList(many, many.size()), serializeList(many, -1));
    }

    @Test
    public void unknownSizeMapMatchesKnownSize() {
        // Size 1 takes the fully-forward singleton path; it must produce the exact bytes of the general
        // (key scratch + map-head gap) encoding. Sizes 0 and 3 cover the empty short-circuit and the
        // general path.
        var one = new LinkedHashMap<String, String>();
        one.put("only-key", "only-value");
        assertArrayEquals(serializeMap(one, -1), serializeMap(one, 1));

        assertArrayEquals(serializeMap(Map.of(), -1), serializeMap(Map.of(), 0));

        var three = new LinkedHashMap<String, String>();
        three.put("k1", "v1");
        three.put("k2", "v2");
        three.put("k3", "v3");
        assertArrayEquals(serializeMap(three, -1), serializeMap(three, 3));
    }

    @Test
    public void listSizeMismatchThrows() {
        assertThrows(SerializationException.class, () -> serializeList(List.of("a", "b"), 3));
    }

    @Test
    public void singletonMapSizeMismatchThrows() {
        var two = new LinkedHashMap<String, String>();
        two.put("k1", "v1");
        two.put("k2", "v2");
        assertThrows(SerializationException.class, () -> serializeMap(two, 1));
    }

    @Test
    public void largeBlobsRoundTrip() {
        // Blobs at or above the extern threshold are copied from the caller's buffer at compaction time
        // instead of being staged; sizes straddle the threshold and the one-byte prefix limit.
        for (int size : new int[] {63, 64, 65, 200, 5000, 70000}) {
            byte[] data = new byte[size];
            for (int i = 0; i < size; i++) {
                data[i] = (byte) (i * 31);
            }
            var s = new TestShapes.Scalars();
            s.aString = "with-blob-" + size;
            s.aBlob = ByteBuffer.wrap(data);
            var de = roundTrip(s, new TestShapes.ScalarsBuilder());
            assertArrayEquals(data, bytes(de.aBlob));
            assertEquals("with-blob-" + size, de.aString);
        }
    }

    @Test
    public void largeBlobWithNonZeroPositionRoundTrip() {
        // The extern gap must respect the buffer's position/remaining window, not its whole capacity.
        byte[] backing = new byte[300];
        for (int i = 0; i < backing.length; i++) {
            backing[i] = (byte) i;
        }
        ByteBuffer window = ByteBuffer.wrap(backing, 100, 128).slice();
        var s = new TestShapes.Scalars();
        s.aBlob = window.duplicate();
        var de = roundTrip(s, new TestShapes.ScalarsBuilder());
        assertArrayEquals(bytes(window), bytes(de.aBlob));
    }

    @Test
    public void largeSparseBlobListRoundTrip() {
        // A large blob inside a sparse element combines a wrapper gap with an extern gap.
        byte[] big = new byte[500];
        Arrays.fill(big, (byte) 0x5A);
        var values = new ArrayList<ByteBuffer>();
        values.add(ByteBuffer.wrap(big));
        values.add(null);
        values.add(ByteBuffer.wrap(new byte[] {1, 2, 3}));
        var payload = bytes(CODEC.serialize(new TestShapes.TopLevelSparseBlobList(values)));

        var read = new ArrayList<ByteBuffer>();
        var deser = CODEC.createDeserializer(payload);
        deser.readList(TestShapes.SPARSE_BLOB_LIST, read, (state, d) -> {
            if (d.isNull()) {
                state.add(d.readNull());
            } else {
                state.add(d.readBlob(TestShapes.SPARSE_BLOB_LIST.member("member")));
            }
        });
        assertEquals(3, read.size());
        assertArrayEquals(big, bytes(read.get(0)));
        assertNull(read.get(1));
        assertArrayEquals(new byte[] {1, 2, 3}, bytes(read.get(2)));
    }

    @Test
    public void repeatedUseOfPooledSerializerWithLargeBlobs() {
        // Extern gap references must be released on reset; repeated use must stay stable.
        byte[] data = new byte[256];
        for (int i = 0; i < 64; i++) {
            data[i % data.length] = (byte) i;
            var s = new TestShapes.Scalars();
            s.aBlob = ByteBuffer.wrap(data.clone());
            var de = roundTrip(s, new TestShapes.ScalarsBuilder());
            assertArrayEquals(s.aBlob.array(), bytes(de.aBlob));
        }
    }

    @Test
    public void repeatedUseOfPooledSerializer() {
        // Exercise pooled-state reuse: many serializations in a row must be stable.
        for (int i = 0; i < 512; i++) {
            var s = new TestShapes.OptionalStruct("iteration-" + i, (double) i);
            var de = roundTrip(s, new TestShapes.OptionalStructBuilder());
            assertEquals("iteration-" + i, de.string);
            assertEquals((double) i, de.timestamp);
        }
        assertTrue(true);
    }
}
