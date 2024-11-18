/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.cbor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.io.ByteBufferUtils;

public class CborSerializerTest {
    private static final Rpcv2CborCodec.Settings SETTINGS = new Rpcv2CborCodec.Settings();
    private static final DefaultCborSerdeProvider CODEC = new DefaultCborSerdeProvider();

    @Test
    public void birdSerialization() {
        var name = "kestrel";
        var bytes = ByteBuffer.wrap(" li'l guy".getBytes(StandardCharsets.UTF_8)).position(1).asReadOnlyBuffer();
        var bird = new CborTestData.BirdBuilder()
            .name(name)
            .bytes(bytes.duplicate())
            .build();

        var ser = CODEC.serialize(bird, SETTINGS);
        var de = new CborTestData.BirdBuilder().deserialize(CODEC.newDeserializer(ser, SETTINGS)).build();

        assertEquals(name, de.name);
        assertBuffersEqual(bytes, de.bytes);
        assertEquals("li'l guy", new String(ByteBufferUtils.getBytes(de.bytes)));
    }

    private static void assertBuffersEqual(ByteBuffer expected, ByteBuffer actual) {
        byte[] expectedBytes = ByteBufferUtils.getBytes(expected);
        byte[] actualBytes = ByteBufferUtils.getBytes(actual);
        assertArrayEquals(expectedBytes, actualBytes);
    }
}
