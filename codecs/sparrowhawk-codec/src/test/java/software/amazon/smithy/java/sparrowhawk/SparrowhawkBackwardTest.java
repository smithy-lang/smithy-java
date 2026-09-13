/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

/**
 * Correctness tests for the experimental backward writer. Shapes here dispatch members in DESCENDING
 * memberIndex order and iterate lists in reverse, matching what the {@code reverseMemberSerialization}
 * codegen setting generates. Non-map payloads must be byte-identical to the forward serializer; map
 * payloads reverse entry order and are compared by decoded value plus byte-equality against a
 * forward-serialized reversed-entry map.
 */
public class SparrowhawkBackwardTest {

    private static final Codec FORWARD = SparrowhawkCodec.get();
    private static final Codec BACKWARD = SparrowhawkCodec.backward();

    private static byte[] serialize(Codec codec, SerializableShape shape) {
        var b = codec.serialize(shape);
        byte[] out = new byte[b.remaining()];
        b.duplicate().get(out);
        return out;
    }

    /** Descending TestShapes.Nested: list first, reverse-iterated. */
    private static final class DescNested implements SerializableStruct {
        final TestShapes.Nested delegate;

        DescNested(TestShapes.Nested delegate) {
            this.delegate = delegate;
        }

        @Override
        public Schema schema() {
            return TestShapes.NESTED_STRUCTURE;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeList(TestShapes.NESTED_LIST, delegate.list, delegate.list.size(), (l, s) -> {
                for (int i = l.size() - 1; i >= 0; i--) {
                    s.writeInteger(TestShapes.VARINT_LIST_MEMBER, l.get(i));
                }
            });
            serializer.writeString(TestShapes.NESTED_INNER_STR, delegate.innerStr);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    /** Descending TestShapes.DemoInput. */
    private static final class DescDemoInput implements SerializableStruct {
        final TestShapes.DemoInput delegate;

        DescDemoInput(TestShapes.DemoInput delegate) {
            this.delegate = delegate;
        }

        @Override
        public Schema schema() {
            return TestShapes.DEMO_INPUT;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (delegate.nested != null) {
                serializer.writeStruct(TestShapes.DEMO_NESTED, new DescNested(delegate.nested));
            }
            serializer.writeBlob(TestShapes.DEMO_BYTES, delegate.bytes);
            serializer.writeString(TestShapes.DEMO_STR, delegate.str);
            serializer.writeDouble(TestShapes.DEMO_D, delegate.d);
            serializer.writeFloat(TestShapes.DEMO_F, delegate.f);
            serializer.writeInteger(TestShapes.DEMO_I, delegate.i);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    private static TestShapes.DemoInput fullDemo() {
        return new TestShapes.DemoInput(
                "string field 0 false",
                3.7f,
                1.5d,
                9182741,
                ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5}),
                new TestShapes.Nested("howdy", List.of(0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE)),
                TestShapes.PresenceMode.FAST);
    }

    @Test
    public void fullDemoGoldenBytes() {
        // Reference-minted golden: nested struct, varint list, blob, all scalar sections.
        assertArrayEquals(
                HexFormat.of()
                        .parseHex(
                                "2a0213a8c2831115cdcc6c4017000000000000f83f7151737472696e67206669656c6420302066616c7365"
                                        + "150102030405553115686f77647957010503d0ffffff1ff0ffffff1f"),
                serialize(BACKWARD, new DescDemoInput(fullDemo())));
    }

    @Test
    public void minimalDemoGoldenBytes() {
        var demo = new TestShapes.DemoInput(
                "howdy",
                0f,
                0d,
                0,
                ByteBuffer.wrap(new byte[0]),
                null,
                TestShapes.PresenceMode.FAST);
        assertArrayEquals(
                HexFormat.of().parseHex("61130115000000001700000000000000003115686f77647901"),
                serialize(BACKWARD, new DescDemoInput(demo)));
    }

    @Test
    public void optionalStructGoldenBytes() {
        SerializableStruct desc = new SerializableStruct() {
            @Override
            public Schema schema() {
                return TestShapes.OPTIONAL_STRUCT;
            }

            @Override
            public void serializeMembers(ShapeSerializer s) {
                s.writeString(TestShapes.OPTIONAL_STRUCT_STRING, "howdy");
                s.writeDouble(TestShapes.OPTIONAL_STRUCT_TIMESTAMP, 123.456d);
            }

            @Override
            public <T> T getMemberValue(Schema member) {
                return null;
            }
        };
        assertArrayEquals(
                HexFormat.of().parseHex("411777be9f1a2fdd5e401115686f776479"),
                serialize(BACKWARD, desc));
    }

    @Test
    public void emptyStructGoldenBytes() {
        SerializableStruct empty = new SerializableStruct() {
            @Override
            public Schema schema() {
                return TestShapes.OPTIONAL_STRUCT;
            }

            @Override
            public void serializeMembers(ShapeSerializer s) {}

            @Override
            public <T> T getMemberValue(Schema member) {
                return null;
            }
        };
        assertArrayEquals(new byte[] {0x01}, serialize(BACKWARD, empty));
    }

    @Test
    public void unionMatchesForward() {
        // Single-member dispatch is trivially descending, so union fixtures work with both codecs.
        for (var value : new TestShapes.UnionValue[] {
                new TestShapes.UnionValue(TestShapes.UNION, "num", 42),
                new TestShapes.UnionValue(TestShapes.UNION, "num", Integer.MIN_VALUE),
                new TestShapes.UnionValue(TestShapes.UNION, "txt", "howdy"),
                new TestShapes.UnionValue(TestShapes.UNION, "txt", "x".repeat(200)),
        }) {
            assertArrayEquals(serialize(FORWARD, value), serialize(BACKWARD, value));
        }
    }

    @Test
    public void longEdgeValuesGoldenBytes() {
        // Nine-byte varint form (0x00 + raw LE) through the exact-width prepend path.
        assertArrayEquals(
                HexFormat.of().parseHex("291300ffffffffffffffff"),
                serialize(BACKWARD, new TestShapes.Longs(Long.MIN_VALUE)));
        assertArrayEquals(
                HexFormat.of().parseHex("291300feffffffffffffff"),
                serialize(BACKWARD, new TestShapes.Longs(Long.MAX_VALUE)));
    }

    @Test
    public void sparseBlobListGoldenBytes() {
        // Reference golden for ["hi", null]; reverse iteration writes the null first so the wire order
        // matches the forward serializer's.
        SerializableShape shape = encoder -> encoder.writeList(
                TestShapes.SPARSE_BLOB_LIST,
                null,
                2,
                (state, s) -> {
                    s.writeNull(TestShapes.SPARSE_BLOB_LIST.member("member"));
                    s.writeBlob(
                            TestShapes.SPARSE_BLOB_LIST.member("member"),
                            ByteBuffer.wrap("hi".getBytes(StandardCharsets.UTF_8)));
                });
        assertArrayEquals(HexFormat.of().parseHex("23111009686901"), serialize(BACKWARD, shape));
    }

    @Test
    public void bigStructContinuationGroupsMatchForward() {
        // Fields across both 61-field groups of two sections; descending dispatch must produce the
        // forward serializer's exact bytes (headers, continuation offsets, values).
        var s = new TestShapes.BigStruct();
        for (int j = 0; j < 70; j += 3) {
            s.ints[j] = j * 1001 - 35;
            s.strings[j] = "value-" + j;
        }
        s.ints[61] = 61;
        s.strings[69] = "last";

        SerializableStruct desc = new SerializableStruct() {
            @Override
            public Schema schema() {
                return s.schema();
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                for (int i = 69; i >= 0; i--) {
                    if (s.strings[i] != null) {
                        serializer.writeString(s.schema().member("s" + (i + 1)), s.strings[i]);
                    }
                }
                for (int i = 69; i >= 0; i--) {
                    if (s.ints[i] != null) {
                        serializer.writeInteger(s.schema().member("i" + (i + 1)), s.ints[i]);
                    }
                }
            }

            @Override
            public <T> T getMemberValue(Schema member) {
                return null;
            }
        };
        assertArrayEquals(serialize(FORWARD, s), serialize(BACKWARD, desc));
    }

    @Test
    public void mapEntriesReversedButDecodeEqual() {
        var map = new LinkedHashMap<String, String>();
        map.put("alpha", "one");
        map.put("beta", "two");
        map.put("gamma", "three");

        byte[] backward = serialize(BACKWARD, mapShape(map));

        // Byte-identical to the forward serializer writing the entries in reversed order.
        var reversed = new LinkedHashMap<String, String>();
        var entries = new ArrayList<>(map.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            reversed.put(entries.get(i).getKey(), entries.get(i).getValue());
        }
        assertArrayEquals(serialize(FORWARD, mapShape(reversed)), backward);

        // And decodes to the same map.
        var read = new LinkedHashMap<String, String>();
        var deser = BACKWARD.createDeserializer(backward);
        deser.readStringMap(TestShapes.STRING_MAP,
                read,
                (state, key, d) -> state.put(key, d.readString(TestShapes.STRING_MAP.member("value"))));
        assertEquals(map, read);
    }

    @Test
    public void nestedMapsInMapValuesDecodeEqual() {
        // Nested maps exercise the backward key-scratch stack: inner maps must consume exactly their own
        // key block. Uses hand-written descending Holder wrappers.
        var inner1 = new LinkedHashMap<String, String>();
        inner1.put("ik1", "iv1");
        inner1.put("ik2", "iv2");
        var inner2 = new LinkedHashMap<String, String>();
        inner2.put("another-inner-key", "another-inner-value");

        var holders = new LinkedHashMap<String, SerializableStruct>();
        holders.put("outer-one", descHolder(inner1, "first"));
        holders.put("outer-two", descHolder(inner2, "second"));
        holders.put("outer-three", descHolder(new LinkedHashMap<>(), null));

        SerializableShape shape = encoder -> encoder.writeMap(
                TestShapes.NESTED_MAPS.member("holders"),
                holders,
                holders.size(),
                (state, ms) -> state.forEach((k, v) -> ms.writeEntry(
                        TestShapes.HOLDER_MAP.member("key"),
                        k,
                        v,
                        (holder, s) -> s.writeStruct(TestShapes.HOLDER_MAP.member("value"), holder))));
        byte[] payload = serialize(BACKWARD, shape);

        var read = new LinkedHashMap<String, Map<String, String>>();
        var tags = new LinkedHashMap<String, String>();
        var deser = BACKWARD.createDeserializer(payload);
        deser.readStringMap(TestShapes.HOLDER_MAP, read, (state, key, d) -> {
            var m = new LinkedHashMap<String, String>();
            var tag = new String[1];
            d.readStruct(TestShapes.MAP_HOLDER, m, (mm, member, dd) -> {
                if (member.memberName().equals("m")) {
                    dd.readStringMap(member,
                            mm,
                            (mmm, k2, d2) -> mmm.put(k2, d2.readString(TestShapes.STRING_MAP.member("value"))));
                } else {
                    tag[0] = dd.readString(member);
                }
            });
            state.put(key, m);
            tags.put(key, tag[0]);
        });
        assertEquals(inner1, read.get("outer-one"));
        assertEquals("first", tags.get("outer-one"));
        assertEquals(inner2, read.get("outer-two"));
        assertEquals("second", tags.get("outer-two"));
        assertEquals(Map.of(), read.get("outer-three"));
    }

    @Test
    public void largeBlobHoleRoundTrip() {
        for (int size : new int[] {63, 64, 200, 5000}) {
            byte[] data = new byte[size];
            for (int i = 0; i < size; i++) {
                data[i] = (byte) (i * 31);
            }
            SerializableStruct shape = new SerializableStruct() {
                @Override
                public Schema schema() {
                    return TestShapes.SCALARS;
                }

                @Override
                public void serializeMembers(ShapeSerializer s) {
                    s.writeBlob(TestShapes.SCALARS.member("aBlob"), ByteBuffer.wrap(data));
                    s.writeString(TestShapes.SCALARS.member("aString"), "with-blob");
                }

                @Override
                public <T> T getMemberValue(Schema member) {
                    return null;
                }
            };
            byte[] payload = serialize(BACKWARD, shape);
            var de = BACKWARD.deserializeShape(payload, new TestShapes.ScalarsBuilder());
            byte[] readBack = new byte[de.aBlob.remaining()];
            de.aBlob.duplicate().get(readBack);
            assertArrayEquals(data, readBack);
            assertEquals("with-blob", de.aString);
        }
    }

    @Test
    public void ascendingDispatchThrows() {
        // Forward-ordered fixtures are incompatible with the backward writer by design.
        assertThrows(
                SerializationException.class,
                () -> BACKWARD.serialize(fullDemo()));
    }

    @Test
    public void pooledReuseStable() {
        for (int i = 0; i < 256; i++) {
            var demo = fullDemo();
            assertArrayEquals(serialize(FORWARD, demo), serialize(BACKWARD, new DescDemoInput(demo)));
        }
    }

    private static SerializableStruct descHolder(Map<String, String> m, String tag) {
        return new SerializableStruct() {
            @Override
            public Schema schema() {
                return TestShapes.MAP_HOLDER;
            }

            @Override
            public void serializeMembers(ShapeSerializer s) {
                // HOLDER members: m (map), tag (string) — emit descending.
                var tagMember = TestShapes.MAP_HOLDER.member("tag");
                var mapMember = TestShapes.MAP_HOLDER.member("m");
                if (tagMember.memberIndex() > mapMember.memberIndex()) {
                    if (tag != null) {
                        s.writeString(tagMember, tag);
                    }
                    writeM(s, mapMember);
                } else {
                    writeM(s, mapMember);
                    if (tag != null) {
                        s.writeString(tagMember, tag);
                    }
                }
            }

            private void writeM(ShapeSerializer s, Schema mapMember) {
                s.writeMap(mapMember,
                        m,
                        m.size(),
                        (state, ms) -> state.forEach((k, v) -> ms.writeEntry(
                                TestShapes.STRING_MAP.member("key"),
                                k,
                                v,
                                (value, vs) -> vs.writeString(TestShapes.STRING_MAP.member("value"), value))));
            }

            @Override
            public <T> T getMemberValue(Schema member) {
                return null;
            }
        };
    }

    private static SerializableShape mapShape(Map<String, String> values) {
        return encoder -> encoder.writeMap(
                TestShapes.STRING_MAP,
                values,
                values.size(),
                (state, ms) -> state.forEach((k, v) -> ms.writeEntry(
                        TestShapes.STRING_MAP.member("key"),
                        k,
                        v,
                        (value, s) -> s.writeString(TestShapes.STRING_MAP.member("value"), value))));
    }
}
