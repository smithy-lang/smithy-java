/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.io.ByteBufferUtils;

public class CborSerializerTest {
    private static final CborSettings SETTINGS = CborSettings.defaultSettings();
    private static final DefaultCborSerdeProvider CODEC = new DefaultCborSerdeProvider();

    @Test
    public void birdSerialization() {
        var name = "kestrel";
        var bytes = ByteBuffer.wrap(" li'l guy".getBytes(StandardCharsets.UTF_8)).position(1).asReadOnlyBuffer();
        var timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var flightRange = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        var wingspan = BigDecimal.valueOf(Double.MAX_VALUE).add(BigDecimal.ONE);
        var bird = new CborTestData.BirdBuilder()
                .name(name)
                .bytes(bytes.duplicate())
                .lastSquawkAt(timestamp)
                .flightRange(flightRange)
                .wingspan(wingspan)
                .build();

        var ser = CODEC.serialize(bird, SETTINGS);
        var de = new CborTestData.BirdBuilder().deserialize(CODEC.newDeserializer(ser, SETTINGS)).build();

        assertEquals(name, de.name);
        assertEquals(timestamp, de.lastSquawkAt);
        assertEquals(flightRange, de.flightRange);
        assertEquals(wingspan, de.wingspan);
        assertBuffersEqual(bytes, de.bytes);
        assertEquals("li'l guy", new String(ByteBufferUtils.getBytes(de.bytes)));
    }

    private static void assertBuffersEqual(ByteBuffer expected, ByteBuffer actual) {
        byte[] expectedBytes = ByteBufferUtils.getBytes(expected);
        byte[] actualBytes = ByteBufferUtils.getBytes(actual);
        assertArrayEquals(expectedBytes, actualBytes);
    }
}
