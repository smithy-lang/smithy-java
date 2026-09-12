/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.serde.SerializationException;

/**
 * Byte-compatibility tests. Every expected hex string in this file was produced by running the reference
 * Sparrowhawk implementation (smithy-sparrowhawk-java) against the same values and schemas.
 */
public class SparrowhawkGoldenTest {

    private static byte[] serialize(SerializableShape shape) {
        var buf = SparrowhawkCodec.get().serialize(shape);
        byte[] result = new byte[buf.remaining()];
        buf.duplicate().get(result);
        return result;
    }

    private static void assertGolden(String expectedHex, SerializableShape shape) {
        byte[] actual = serialize(shape);
        assertArrayEquals(
                HexFormat.of().parseHex(expectedHex),
                actual,
                () -> "got: " + HexFormat.of().formatHex(actual));
    }

    @Test
    public void optionalStructGolden() {
        // Reference CodegenOptionalStruct { string: "howdy", timestamp: 123.456 }.
        assertGolden(
                "411777be9f1a2fdd5e401115686f776479",
                new TestShapes.OptionalStruct("howdy", 123.456d));
    }

    @Test
    public void emptyStructGolden() {
        // Canonical empty struct: a zero-length byte list.
        assertGolden("01", new TestShapes.OptionalStruct(null, null));
    }

    private static TestShapes.DemoInput fullDemo(TestShapes.PresenceMode mode) {
        var nested = new TestShapes.Nested(
                "howdy",
                List.of(0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE));
        return new TestShapes.DemoInput(
                "string field 0 false",
                3.7f,
                1.5d,
                9182741,
                ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5}),
                nested,
                mode);
    }

    private static final String FULL_DEMO_HEX =
            "2a0213a8c2831115cdcc6c4017000000000000f83f7151737472696e67206669656c6420302066616c7365"
                    + "150102030405553115686f77647957010503d0ffffff1ff0ffffff1f";

    @Test
    public void demoInputFullGoldenFastPath() {
        assertGolden(FULL_DEMO_HEX, fullDemo(TestShapes.PresenceMode.FAST));
    }

    @Test
    public void demoInputFullGoldenIncrementalPath() {
        assertGolden(FULL_DEMO_HEX, fullDemo(TestShapes.PresenceMode.UNKNOWN));
    }

    @Test
    public void demoInputFullGoldenUnorderedPath() {
        assertGolden(FULL_DEMO_HEX, new TestShapes.ReversedDemoInput(fullDemo(TestShapes.PresenceMode.FAST)));
    }

    @Test
    public void demoInputMinimalGolden() {
        var demo = new TestShapes.DemoInput(
                "howdy",
                0f,
                0d,
                0,
                ByteBuffer.wrap(new byte[0]),
                null,
                TestShapes.PresenceMode.FAST);
        assertGolden("61130115000000001700000000000000003115686f77647901", demo);
    }

    @Test
    public void sparseBlobListGolden() {
        // Reference writeSparseBlobList(["hi", null]): a present element wraps as a byte-list containing
        // the single-field marker byte 0x10 (the reference's exact quirk) plus the value; null is 0x01.
        var values = new ArrayList<ByteBuffer>();
        values.add(ByteBuffer.wrap("hi".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        values.add(null);
        assertGolden("23111009686901", new TestShapes.TopLevelSparseBlobList(values));
    }

    @Test
    public void longEdgeValuesGolden() {
        // zigzag(Long.MIN_VALUE) = 0xFFFFFFFFFFFFFFFF and zigzag(Long.MAX_VALUE) = 0xFFFFFFFFFFFFFFFE,
        // both taking the 9-byte varint form (0x00 + raw LE), as produced by the reference writeVarL.
        assertGolden("291300ffffffffffffffff", new TestShapes.Longs(Long.MIN_VALUE));
        assertGolden("291300feffffffffffffff", new TestShapes.Longs(Long.MAX_VALUE));
    }

    @Test
    public void inlineSingleMemberPathMatchesIncrementalPath() {
        // The inline single-leaf-member fast path must produce byte-identical output to the incremental
        // path, across every section type, the >63-byte shift case, and a continuation-group member.
        record Case(Schema schema, String member, Object value) {}
        var cases = new Case[] {
                new Case(TestShapes.UNION, "num", 42),
                new Case(TestShapes.UNION, "num", Integer.MIN_VALUE),
                new Case(TestShapes.UNION, "txt", "short"),
                new Case(TestShapes.UNION, "txt", ""),
                new Case(TestShapes.UNION, "txt", "x".repeat(200)), // content > 63 bytes: backfill must shift
                new Case(TestShapes.OPTIONAL_STRUCT, "timestamp", 123.456d),
                new Case(TestShapes.OPTIONAL_STRUCT, "string", "howdy"),
                new Case(TestShapes.LONGS, "aLong", Long.MIN_VALUE),
                new Case(TestShapes.WIDE_STRINGS, "s62", "in the continuation group"),
                new Case(TestShapes.WIDE_STRINGS, "s63", "y".repeat(500)),
        };
        for (var c : cases) {
            byte[] inline = serialize(new TestShapes.OneOf(c.schema(), c.member(), c.value(), true));
            byte[] incremental = serialize(new TestShapes.OneOf(c.schema(), c.member(), c.value(), false));
            assertArrayEquals(
                    incremental,
                    inline,
                    () -> c.member() + ": inline=" + HexFormat.of().formatHex(inline)
                            + " incremental=" + HexFormat.of().formatHex(incremental));
        }
    }

    @Test
    public void optionalStructInlineGolden() {
        // The single-member inline path against a reference-derived payload: OptionalStruct with only the
        // timestamp present is the golden payload's eight-byte section alone.
        assertGolden(
                "251777be9f1a2fdd5e40",
                new TestShapes.OneOf(TestShapes.OPTIONAL_STRUCT, "timestamp", 123.456d, true));
    }

    @Test
    public void presenceBitsMismatchThrows() {
        assertThrows(
                SerializationException.class,
                () -> SparrowhawkCodec.get().serialize(new TestShapes.LyingDemoInput()));
    }
}
