/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class H2PingAckTest {

    @Test
    void pingResponseFrameHasAckFlag() throws IOException {
        // Write a PING frame with ACK=true (as a PING response should be)
        var out = new ByteArrayOutputStream();
        var codec = new H2FrameCodec(
                new ChannelFrameReader(java.nio.channels.Channels.newChannel(new ByteArrayInputStream(new byte[0])),
                        256),
                new ChannelFrameWriter(java.nio.channels.Channels.newChannel(out), 256),
                16384);

        byte[] pingPayload = {1, 2, 3, 4, 5, 6, 7, 8};
        codec.writeFrame(H2Constants.FRAME_TYPE_PING, H2Constants.FLAG_ACK, 0, pingPayload);
        codec.flush();

        // Read it back and verify ACK flag is set
        var readCodec = new H2FrameCodec(
                new ChannelFrameReader(
                        java.nio.channels.Channels.newChannel(new ByteArrayInputStream(out.toByteArray())),
                        256),
                new ChannelFrameWriter(java.nio.channels.Channels.newChannel(new ByteArrayOutputStream()), 256),
                16384);
        int type = readCodec.nextFrame();

        assertEquals(H2Constants.FRAME_TYPE_PING, type);
        assertTrue(readCodec.hasFrameFlag(H2Constants.FLAG_ACK),
                "PING response must have ACK flag set");
    }

    @Test
    void pingRequestFrameDoesNotHaveAckFlag() throws IOException {
        // Write a PING frame without ACK (a PING request)
        var out = new ByteArrayOutputStream();
        var codec = new H2FrameCodec(
                new ChannelFrameReader(java.nio.channels.Channels.newChannel(new ByteArrayInputStream(new byte[0])),
                        256),
                new ChannelFrameWriter(java.nio.channels.Channels.newChannel(out), 256),
                16384);

        byte[] pingPayload = {1, 2, 3, 4, 5, 6, 7, 8};
        codec.writeFrame(H2Constants.FRAME_TYPE_PING, 0, 0, pingPayload);
        codec.flush();

        var readCodec = new H2FrameCodec(
                new ChannelFrameReader(
                        java.nio.channels.Channels.newChannel(new ByteArrayInputStream(out.toByteArray())),
                        256),
                new ChannelFrameWriter(java.nio.channels.Channels.newChannel(new ByteArrayOutputStream()), 256),
                16384);
        int type = readCodec.nextFrame();

        assertEquals(H2Constants.FRAME_TYPE_PING, type);
        assertFalse(readCodec.hasFrameFlag(H2Constants.FLAG_ACK), "PING request must NOT have ACK flag");
    }
}
