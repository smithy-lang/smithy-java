/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import smithy.java.codegen.types.test.model.SparrowhawkDemoInput;
import smithy.java.codegen.types.test.model.SparrowhawkNestedStructure;
import software.amazon.smithy.java.sparrowhawk.SparrowhawkCodec;

/**
 * End-to-end lockstep check: code generated from a model carrying {@code smithy.protocols#idx} traits must
 * produce payloads byte-identical to the reference Sparrowhawk implementation. This exercises the real
 * generated {@code serializeMembers} dispatch order, {@code presenceBits()}, and builder member switches
 * against the runtime member sort.
 *
 * <p>The golden hex strings were produced by running the reference smithy-sparrowhawk-java implementation
 * over its demo.smithy model with identical values.
 */
public class SparrowhawkGeneratedSerdeTest {

    private static final String FULL_HEX =
            "2a0213a8c2831115cdcc6c4017000000000000f83f7151737472696e67206669656c6420302066616c7365"
                    + "150102030405553115686f77647957010503d0ffffff1ff0ffffff1f";

    private static final String MINIMAL_HEX = "61130115000000001700000000000000003115686f77647901";

    private static byte[] bytes(ByteBuffer bb) {
        byte[] b = new byte[bb.remaining()];
        bb.duplicate().get(b);
        return b;
    }

    @Test
    void generatedCodeMatchesReferenceGolden() {
        var input = SparrowhawkDemoInput.builder()
                .str("string field 0 false")
                .f(3.7f)
                .d(1.5d)
                .i(9182741)
                .bytes(ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5}))
                .nested(SparrowhawkNestedStructure.builder()
                        .innerStr("howdy")
                        .list(List.of(0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE))
                        .build())
                .build();

        byte[] actual = bytes(SparrowhawkCodec.get().serialize(input));
        assertArrayEquals(
                HexFormat.of().parseHex(FULL_HEX),
                actual,
                () -> "got: " + HexFormat.of().formatHex(actual));
    }

    @Test
    void generatedCodeMinimalMatchesReferenceGolden() {
        var input = SparrowhawkDemoInput.builder()
                .str("howdy")
                .bytes(ByteBuffer.wrap(new byte[0]))
                .build();

        byte[] actual = bytes(SparrowhawkCodec.get().serialize(input));
        assertArrayEquals(
                HexFormat.of().parseHex(MINIMAL_HEX),
                actual,
                () -> "got: " + HexFormat.of().formatHex(actual));
    }

    @Test
    void generatedCodeRoundTrips() {
        var input = SparrowhawkDemoInput.builder()
                .str("round trip")
                .f(-1.25f)
                .d(Double.MAX_VALUE)
                .i(-42)
                .bytes(ByteBuffer.wrap(new byte[] {9, 8, 7}))
                .nested(SparrowhawkNestedStructure.builder()
                        .innerStr("nested")
                        .list(List.of(5, -5))
                        .build())
                .build();

        var codec = SparrowhawkCodec.get();
        var output = codec.deserializeShape(codec.serialize(input), SparrowhawkDemoInput.builder());
        assertEquals(input, output);
    }

    @Test
    void generatedCodeRoundTripsAbsentOptional() {
        var input = SparrowhawkDemoInput.builder()
                .str("no nested")
                .bytes(ByteBuffer.wrap(new byte[0]))
                .build();

        var codec = SparrowhawkCodec.get();
        var output = codec.deserializeShape(codec.serialize(input), SparrowhawkDemoInput.builder());
        assertEquals(input, output);
        assertNull(output.getNested());
    }
}
