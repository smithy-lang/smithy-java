/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Tests the packed-long short-name fast path in {@link SmithyMemberLookup#lookup}.
 *
 * <p>Names of length 1..7 are matched by packing their bytes into a long together with
 * the length; these tests guard that this packing is an exact identity (no collisions
 * across different lengths or against control-byte inputs) and that longer names still
 * resolve via the FNV fallback.
 */
public class SmithyMemberLookupTest {

    private static SmithyMemberLookup lookupOf(String... memberNames) {
        var builder = Schema.structureBuilder(ShapeId.from("test#S"));
        for (String n : memberNames) {
            builder.putMember(n, PreludeSchemas.STRING);
        }
        Schema schema = builder.build();
        return new SmithyMemberLookup(schema.members(), false);
    }

    private static Schema lookup(SmithyMemberLookup l, String name) {
        byte[] b = name.getBytes(StandardCharsets.UTF_8);
        // Embed the name in a larger buffer at a non-zero offset to catch offset bugs.
        byte[] buf = new byte[b.length + 5];
        System.arraycopy(b, 0, buf, 3, b.length);
        return l.lookup(buf, 3, 3 + b.length, /* expectedNext */ -1);
    }

    @Test
    public void matchesShortUnionDiscriminators() {
        // The DynamoDB AttributeValue union shape: all discriminators are <= 4 bytes.
        var l = lookupOf("S", "N", "B", "SS", "NS", "BS", "M", "L", "NULL", "BOOL");
        for (String name : List.of("S", "N", "B", "SS", "NS", "BS", "M", "L", "NULL", "BOOL")) {
            assertThat(lookup(l, name)).as(name).isNotNull();
            assertThat(lookup(l, name).memberName()).isEqualTo(name);
        }
    }

    @Test
    public void differentLengthSamePrefixDoNotCollide() {
        // "S" and "SS" share a prefix but differ in length: folding length into the
        // packed key must keep them distinct.
        var l = lookupOf("S", "SS");
        assertThat(lookup(l, "S").memberName()).isEqualTo("S");
        assertThat(lookup(l, "SS").memberName()).isEqualTo("SS");
    }

    @Test
    public void unknownShortNameReturnsNull() {
        var l = lookupOf("S", "N");
        assertThat(lookup(l, "X")).isNull();
        assertThat(lookup(l, "NS")).isNull(); // not a member here
    }

    @Test
    public void leadingControlByteDoesNotSpuriouslyMatch() {
        // A 2-byte input {0x00, 'S'} must not match member "S" (1 byte) or any other.
        // Without folding length into the key, naive big-endian packing of {0x00,'S'}
        // equals the packing of {'S'} and would mis-dispatch.
        var l = lookupOf("S", "N");
        byte[] buf = {0x00, (byte) 'S'};
        assertThat(l.lookup(buf, 0, 2, -1)).isNull();
    }

    @Test
    public void boundaryLengthsSevenAndEight() {
        // 7 bytes uses the packed path; 8 bytes falls back to FNV. Both must resolve.
        var l = lookupOf("seven77", "eight888");
        assertThat(lookup(l, "seven77").memberName()).isEqualTo("seven77");
        assertThat(lookup(l, "eight888").memberName()).isEqualTo("eight888");
        assertThat(lookup(l, "seven78")).isNull();
        assertThat(lookup(l, "eight889")).isNull();
    }

    @Test
    public void longNamesUseFnvFallback() {
        var l = lookupOf("TableName", "CapacityUnits", "ReadCapacityUnits");
        assertThat(lookup(l, "TableName").memberName()).isEqualTo("TableName");
        assertThat(lookup(l, "CapacityUnits").memberName()).isEqualTo("CapacityUnits");
        assertThat(lookup(l, "ReadCapacityUnits").memberName()).isEqualTo("ReadCapacityUnits");
        assertThat(lookup(l, "WriteCapacityUnits")).isNull();
    }
}
