/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a virtual thread blocked in {@link H2Exchange#writeData} waiting for send-window
 * flow control is woken promptly when the connection closes or the stream errors, instead of
 * hanging until the (potentially 30s) write timeout expires.
 */
class H2ExchangeWritePoisonTest {

    private static final int INITIAL_WINDOW_SIZE = 65535;
    // Long enough that a plain timeout would clearly dominate; poison must beat it.
    private static final long WRITE_TIMEOUT_MS = 30_000;

    private H2Muxer muxer;

    @BeforeEach
    void setUp() {
        var codec = new H2FrameCodec(
                new ChannelFrameReader(Channels.newChannel(new ByteArrayInputStream(new byte[0])), 256),
                new ChannelFrameWriter(Channels.newChannel(new ByteArrayOutputStream()), 256),
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
                    public void releaseConnectionReceiveWindow(int bytes) {}
                },
                codec,
                4096,
                "write-poison-test",
                INITIAL_WINDOW_SIZE);
    }

    @AfterEach
    void tearDown() {
        muxer.close();
    }

    @Test
    void connectionCloseUnblocksBlockedWriter() throws Exception {
        assertWriterUnblockedBy(exchange -> exchange.signalConnectionClosed(new IOException("connection closed")));
    }

    @Test
    void streamErrorUnblocksBlockedWriter() throws Exception {
        assertWriterUnblockedBy(exchange -> exchange.signalStreamError(
                new H2Exception(H2Constants.ERROR_CANCEL, 1, "reset")));
    }

    private void assertWriterUnblockedBy(Consumer<H2Exchange> poison) throws Exception {
        H2Exchange exchange = new H2Exchange(muxer, null, WRITE_TIMEOUT_MS, WRITE_TIMEOUT_MS, INITIAL_WINDOW_SIZE);
        exchange.setStreamId(1);

        // Drain the entire send window so the next write blocks on stream flow control.
        exchange.adjustSendWindow(-INITIAL_WINDOW_SIZE);

        var started = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var thrown = new AtomicReference<Throwable>();

        Thread writer = Thread.startVirtualThread(() -> {
            started.countDown();
            try {
                exchange.writeData(new byte[] {1, 2, 3, 4}, 0, 4, false);
            } catch (Throwable t) {
                thrown.set(t);
            } finally {
                finished.countDown();
            }
        });

        // Ensure the writer has entered writeData (and thus is blocked on the empty window).
        assertTrue(started.await(2, TimeUnit.SECONDS), "writer VT should have started");
        Thread.sleep(100);

        long start = System.nanoTime();
        poison.accept(exchange);

        assertTrue(finished.await(2, TimeUnit.SECONDS),
                "blocked writer should have been unblocked well before the " + WRITE_TIMEOUT_MS + "ms timeout");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        writer.join(1000);
        assertTrue(elapsedMs < 1000,
                "writer should have failed fast after poison, took " + elapsedMs + "ms");
        assertNotNull(thrown.get(), "writer should have thrown after the window was poisoned");
        assertInstanceOf(IOException.class, thrown.get());
    }
}
