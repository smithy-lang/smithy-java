/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.client.UnsyncBufferedInputStream;
import software.amazon.smithy.java.http.client.UnsyncBufferedOutputStream;

class H2MuxerStreamReleaseTest {

    private H2Muxer muxer;

    @BeforeEach
    void setUp() {
        var codec = new H2FrameCodec(
                new UnsyncBufferedInputStream(new ByteArrayInputStream(new byte[0]), 256),
                new UnsyncBufferedOutputStream(new ByteArrayOutputStream(), 256),
                16384);
        muxer = new H2Muxer(
                new H2Muxer.ConnectionCallback() {
                    @Override
                    public boolean isAcceptingStreams() {
                        return true;
                    }

                    @Override
                    public int getRemoteMaxHeaderListSize() {
                        return Integer.MAX_VALUE;
                    }
                },
                codec,
                4096,
                "test-writer",
                65535);
    }

    @AfterEach
    void tearDown() {
        muxer.close();
    }

    @Test
    void releaseStreamInvokesCallback() {
        var callCount = new AtomicInteger(0);
        muxer.setStreamReleaseCallback(callCount::incrementAndGet);

        // Register a fake stream so releaseStream has something to remove
        var exchange = new H2Exchange(muxer, null, 5000, 5000, 65535);
        int streamId = muxer.allocateAndRegisterStream(exchange);

        muxer.releaseStream(streamId);

        assertEquals(1, callCount.get(), "Callback should be invoked on stream release");
    }

    @Test
    void releaseStreamSlotInvokesCallback() {
        var callCount = new AtomicInteger(0);
        muxer.setStreamReleaseCallback(callCount::incrementAndGet);

        muxer.releaseStreamSlot();

        assertEquals(1, callCount.get(), "Callback should be invoked on stream slot release");
    }

    @Test
    void releaseStreamDoesNotFailWithoutCallback() {
        // No callback set — should not throw
        var exchange = new H2Exchange(muxer, null, 5000, 5000, 65535);
        int streamId = muxer.allocateAndRegisterStream(exchange);

        muxer.releaseStream(streamId); // should not throw
    }
}
