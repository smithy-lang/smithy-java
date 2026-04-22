/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class H2ReceiveFlowControlTest {

    private static final int INITIAL_WINDOW_SIZE = 8;

    private H2Muxer muxer;
    private AtomicInteger connectionCreditReleased;

    @BeforeEach
    void setUp() {
        connectionCreditReleased = new AtomicInteger();
        var codec = new H2FrameCodec(
                new ChannelFrameReader(java.nio.channels.Channels.newChannel(new ByteArrayInputStream(new byte[0])),
                        256),
                new ChannelFrameWriter(java.nio.channels.Channels.newChannel(new ByteArrayOutputStream()), 256),
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

                    @Override
                    public void releaseConnectionReceiveWindow(int bytes) {
                        connectionCreditReleased.addAndGet(bytes);
                    }
                },
                codec,
                4096,
                "receive-flow-test",
                INITIAL_WINDOW_SIZE);
    }

    @AfterEach
    void tearDown() {
        muxer.close();
    }

    @Test
    void enqueueDoesNotReleaseReceiveWindowCredit() {
        H2Exchange exchange = exchange();

        exchange.enqueueData(ByteBuffer.wrap(new byte[] {1, 2}), false, false, 4);

        assertEquals(0, connectionCreditReleased.get());
    }

    @Test
    void closingDataInputStreamReleasesHeldChunkCredit() throws Exception {
        H2Exchange exchange = exchange();
        exchange.deliverHeaders(List.of(":status", "200"), false);
        exchange.enqueueData(ByteBuffer.wrap(new byte[] {1, 2}), false, false, 4);

        H2DataInputStream input = new H2DataInputStream(exchange, _buffer -> {});
        assertEquals(2, input.read(new byte[2]));
        assertEquals(0, connectionCreditReleased.get());

        input.close();

        assertEquals(4, connectionCreditReleased.get());
    }

    @Test
    void closingExchangeReleasesQueuedChunkCredit() {
        H2Exchange exchange = exchange();
        exchange.enqueueData(ByteBuffer.wrap(new byte[] {1, 2}), false, false, 4);

        exchange.close();

        assertEquals(4, connectionCreditReleased.get());
    }

    private H2Exchange exchange() {
        H2Exchange exchange = new H2Exchange(muxer, null, 5000, 5000, INITIAL_WINDOW_SIZE);
        exchange.setStreamId(1);
        return exchange;
    }
}
