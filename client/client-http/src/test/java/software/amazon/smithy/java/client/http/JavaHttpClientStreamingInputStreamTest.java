/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Tests for the overridden {@link InputStream} fast paths on
 * {@link JavaHttpClientStreamingInputStream} — {@code transferTo}, {@code readAllBytes},
 * {@code readNBytes}, {@code available}, and {@code skip} — plus the supporting helpers on
 * {@link JavaHttpClientStreamingDataStream}. Tests drive the stream directly using
 * synthetic subscriber callbacks ({@code enqueueBatch}/{@code complete}/{@code fail})
 * rather than going over the wire.
 */
class JavaHttpClientStreamingInputStreamTest {

    /** No-op subscription; tests don't rely on request(n)/cancel() semantics. */
    private static final Flow.Subscription NO_OP_SUBSCRIPTION = new Flow.Subscription() {
        @Override
        public void request(long n) {}

        @Override
        public void cancel() {}
    };

    private static JavaHttpClientStreamingDataStream newStream(long contentLength) {
        JavaHttpClientStreamingDataStream stream = new JavaHttpClientStreamingDataStream(
                "application/octet-stream",
                contentLength);
        stream.setSubscription(NO_OP_SUBSCRIPTION);
        return stream;
    }

    private static byte[] bytesOfLength(int n) {
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) (i & 0xFF);
        }
        return out;
    }

    /** Split {@code data} into consecutive chunks of the given sizes and enqueue each as its own batch. */
    private static void feedChunks(JavaHttpClientStreamingDataStream stream, byte[] data, int... chunkSizes) {
        int offset = 0;
        for (int size : chunkSizes) {
            byte[] chunk = new byte[size];
            System.arraycopy(data, offset, chunk, 0, size);
            stream.enqueueBatch(List.of(ByteBuffer.wrap(chunk)));
            offset += size;
        }
        assertEquals(data.length, offset, "chunkSizes must sum to data.length");
    }

    // ---- transferTo ----

    @Test
    void transferToWritesAllBytesAcrossMultipleChunks() throws IOException {
        byte[] payload = bytesOfLength(4096 + 1234);
        JavaHttpClientStreamingDataStream stream = newStream(payload.length);
        feedChunks(stream, payload, 1024, 512, 2048, 512 + 1234);
        stream.complete();

        try (InputStream in = stream.asInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            long transferred = in.transferTo(out);
            assertEquals(payload.length, transferred);
            assertArrayEquals(payload, out.toByteArray());
        }
    }

    @Test
    void transferToReturnsZeroForEmptyStream() throws IOException {
        JavaHttpClientStreamingDataStream stream = newStream(0);
        stream.complete();

        try (InputStream in = stream.asInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            assertEquals(0L, in.transferTo(out));
            assertEquals(0, out.size());
        }
    }

    @Test
    void transferToWorksWithReadOnlyChunksLargerThanScratch() throws IOException {
        // Force the non-array-backed (read-only view) path by wrapping an already-read-only buffer
        // in an even more restrictive view. `ByteBuffer.wrap(...).asReadOnlyBuffer()` returns
        // something whose hasArray() is false, which is exactly what the JDK subscriber delivers.
        byte[] payload = bytesOfLength(64 * 1024 + 7); // larger than TRANSFER_SCRATCH_SIZE (16 KiB)
        JavaHttpClientStreamingDataStream stream = newStream(payload.length);
        // enqueueBatch wraps each buffer in asReadOnlyBuffer(), so we hit the scratch path.
        stream.enqueueBatch(List.of(ByteBuffer.wrap(payload)));
        stream.complete();

        try (InputStream in = stream.asInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            long transferred = in.transferTo(out);
            assertEquals(payload.length, transferred);
            assertArrayEquals(payload, out.toByteArray());
        }
    }

    @Test
    void transferToPropagatesOutputStreamIOException() {
        JavaHttpClientStreamingDataStream stream = newStream(10);
        feedChunks(stream, bytesOfLength(10), 10);
        stream.complete();

        InputStream in = stream.asInputStream();
        OutputStream failing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("boom");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException("boom");
            }
        };
        IOException e = assertThrows(IOException.class, () -> in.transferTo(failing));
        assertEquals("boom", e.getMessage());
    }

    @Test
    void transferToDoesNotHoldLockDuringSlowWrites() throws Exception {
        // Verify that a slow consumer does not prevent the producer from enqueuing more data.
        // If transferTo held the internal lock while writing to `out`, enqueueBatch would block
        // until writeNextChunkTo finished; we assert that the producer thread completes while
        // the consumer is still inside its (slow) write call.
        byte[] chunk1 = bytesOfLength(512);
        byte[] chunk2 = bytesOfLength(512);
        JavaHttpClientStreamingDataStream stream = newStream(chunk1.length + chunk2.length);
        stream.enqueueBatch(List.of(ByteBuffer.wrap(chunk1)));

        CountDownLatch consumerInsideWrite = new CountDownLatch(1);
        CountDownLatch producerDone = new CountDownLatch(1);
        AtomicLong consumerWroteBytes = new AtomicLong();

        InputStream in = stream.asInputStream();
        OutputStream slow = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                write(new byte[] {(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                consumerInsideWrite.countDown();
                // Stall until the producer confirms it finished enqueuing more data.
                try {
                    assertTrue(producerDone.await(5, TimeUnit.SECONDS), "producer should not be blocked");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
                consumerWroteBytes.addAndGet(len);
            }
        };

        Thread consumer = new Thread(() -> {
            try {
                in.transferTo(slow);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }, "consumer");
        consumer.start();

        assertTrue(consumerInsideWrite.await(5, TimeUnit.SECONDS), "consumer should reach slow write");

        // Now push another chunk and complete. If the consumer held the stream lock, these would
        // block until the slow write returned; the latch below would never fire in time.
        stream.enqueueBatch(List.of(ByteBuffer.wrap(chunk2)));
        stream.complete();
        producerDone.countDown();

        consumer.join(5_000);
        assertFalse(consumer.isAlive(), "consumer should finish");
        assertThat(consumerWroteBytes.get(), greaterThanOrEqualTo((long) chunk1.length));
    }

    // ---- readAllBytes ----

    @Test
    void readAllBytesWithKnownContentLengthReturnsExactArray() throws IOException {
        byte[] payload = bytesOfLength(8192);
        JavaHttpClientStreamingDataStream stream = newStream(payload.length);
        feedChunks(stream, payload, 1000, 3000, 4192);
        stream.complete();

        try (InputStream in = stream.asInputStream()) {
            assertArrayEquals(payload, in.readAllBytes());
        }
    }

    @Test
    void readAllBytesWithUnknownContentLengthFallsBackToDefault() throws IOException {
        byte[] payload = bytesOfLength(5000);
        JavaHttpClientStreamingDataStream stream = newStream(-1L);
        feedChunks(stream, payload, 2500, 2500);
        stream.complete();

        try (InputStream in = stream.asInputStream()) {
            assertArrayEquals(payload, in.readAllBytes());
        }
    }

    @Test
    void readAllBytesAfterPartialReadReturnsRemainder() throws IOException {
        byte[] payload = bytesOfLength(1024);
        JavaHttpClientStreamingDataStream stream = newStream(payload.length);
        feedChunks(stream, payload, 256, 768);
        stream.complete();

        try (InputStream in = stream.asInputStream()) {
            byte[] first = new byte[200];
            int n = in.read(first);
            assertEquals(200, n);

            byte[] rest = in.readAllBytes();
            assertEquals(payload.length - 200, rest.length);
            byte[] combined = new byte[payload.length];
            System.arraycopy(first, 0, combined, 0, 200);
            System.arraycopy(rest, 0, combined, 200, rest.length);
            assertArrayEquals(payload, combined);
        }
    }

    @Test
    void readAllBytesShortCircuitsWhenAlreadyAtAdvertisedLength() throws IOException {
        byte[] payload = bytesOfLength(16);
        JavaHttpClientStreamingDataStream stream = newStream(payload.length);
        feedChunks(stream, payload, 16);
        stream.complete();

        try (InputStream in = stream.asInputStream()) {
            assertArrayEquals(payload, in.readAllBytes());
            // Next call sees zero remaining against the known length → empty array, no blocking.
            assertArrayEquals(new byte[0], in.readAllBytes());
        }
    }

    @Test
    void readAllBytesReturnsShortArrayWhenServerClosesEarly() throws IOException {
        // Advertise 1000 bytes, deliver only 400, then end the stream.
        JavaHttpClientStreamingDataStream stream = newStream(1000);
        stream.enqueueBatch(List.of(ByteBuffer.wrap(bytesOfLength(400))));
        stream.complete();

        try (InputStream in = stream.asInputStream()) {
            byte[] result = in.readAllBytes();
            assertEquals(400, result.length);
            assertArrayEquals(bytesOfLength(400), result);
        }
    }

    // ---- readNBytes ----

    @Test
    void readNBytesReturnsExactlyRequestedWhenAvailable() throws IOException {
        byte[] payload = bytesOfLength(4096);
        JavaHttpClientStreamingDataStream stream = newStream(payload.length);
        feedChunks(stream, payload, 1024, 1024, 2048);
        stream.complete();

        try (InputStream in = stream.asInputStream()) {
            byte[] head = in.readNBytes(1500);
            assertEquals(1500, head.length);
            byte[] expected = new byte[1500];
            System.arraycopy(payload, 0, expected, 0, 1500);
            assertArrayEquals(expected, head);
        }
    }

    @Test
    void readNBytesCapsAtContentLengthWithoutBlocking() throws IOException {
        byte[] payload = bytesOfLength(100);
        JavaHttpClientStreamingDataStream stream = newStream(payload.length);
        feedChunks(stream, payload, 100);
        stream.complete();

        try (InputStream in = stream.asInputStream()) {
            // Request more than the advertised length; implementation should cap at contentLength
            // and return a 100-byte array rather than block waiting for bytes that will never come.
            byte[] result = in.readNBytes(10_000);
            assertEquals(100, result.length);
            assertArrayEquals(payload, result);
        }
    }

    @Test
    void readNBytesWithZeroReturnsEmpty() throws IOException {
        JavaHttpClientStreamingDataStream stream = newStream(10);
        feedChunks(stream, bytesOfLength(10), 10);
        stream.complete();

        try (InputStream in = stream.asInputStream()) {
            assertArrayEquals(new byte[0], in.readNBytes(0));
        }
    }

    @Test
    void readNBytesRejectsNegativeLength() {
        JavaHttpClientStreamingDataStream stream = newStream(0);
        stream.complete();
        try (InputStream in = stream.asInputStream()) {
            assertThrows(IllegalArgumentException.class, () -> in.readNBytes(-1));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    // ---- available ----

    @Test
    void availableReflectsQueuedBytesBeforeAndAfterReads() throws IOException {
        JavaHttpClientStreamingDataStream stream = newStream(-1L);
        stream.enqueueBatch(List.of(ByteBuffer.wrap(bytesOfLength(100)), ByteBuffer.wrap(bytesOfLength(50))));

        try (InputStream in = stream.asInputStream()) {
            assertEquals(150, in.available());
            byte[] scratch = new byte[80];
            int n = in.read(scratch);
            assertEquals(80, n);
            assertEquals(70, in.available());
        }
    }

    @Test
    void availableReturnsZeroForEmptyCompletedStream() throws IOException {
        JavaHttpClientStreamingDataStream stream = newStream(0);
        stream.complete();

        try (InputStream in = stream.asInputStream()) {
            assertEquals(0, in.available());
        }
    }

    // ---- skip ----

    @Test
    void skipAdvancesPositionWithoutReturningBytes() throws IOException {
        byte[] payload = bytesOfLength(1000);
        JavaHttpClientStreamingDataStream stream = newStream(payload.length);
        feedChunks(stream, payload, 400, 600);
        stream.complete();

        try (InputStream in = stream.asInputStream()) {
            long skipped = in.skip(250);
            assertEquals(250L, skipped);
            byte[] rest = in.readAllBytes();
            assertEquals(750, rest.length);
            byte[] expected = new byte[750];
            System.arraycopy(payload, 250, expected, 0, 750);
            assertArrayEquals(expected, rest);
        }
    }

    @Test
    void skipSpansMultipleChunks() throws IOException {
        byte[] payload = bytesOfLength(3000);
        JavaHttpClientStreamingDataStream stream = newStream(payload.length);
        feedChunks(stream, payload, 500, 500, 500, 500, 500, 500);
        stream.complete();

        try (InputStream in = stream.asInputStream()) {
            long skipped = in.skip(1250);
            assertEquals(1250L, skipped);
            byte[] rest = in.readAllBytes();
            assertEquals(payload.length - 1250, rest.length);
            byte[] expected = new byte[rest.length];
            System.arraycopy(payload, 1250, expected, 0, rest.length);
            assertArrayEquals(expected, rest);
        }
    }

    @Test
    void skipReturnsZeroAtEndOfStream() throws IOException {
        byte[] payload = bytesOfLength(10);
        JavaHttpClientStreamingDataStream stream = newStream(payload.length);
        feedChunks(stream, payload, 10);
        stream.complete();

        try (InputStream in = stream.asInputStream()) {
            assertEquals(10L, in.skip(10));
            assertEquals(0L, in.skip(100));
        }
    }

    @Test
    void skipOfZeroOrNegativeReturnsZero() throws IOException {
        JavaHttpClientStreamingDataStream stream = newStream(10);
        feedChunks(stream, bytesOfLength(10), 10);
        stream.complete();

        try (InputStream in = stream.asInputStream()) {
            assertEquals(0L, in.skip(0));
            assertEquals(0L, in.skip(-5));
        }
    }

    // ---- error propagation ----

    @Test
    void transferToPropagatesStreamFailure() {
        JavaHttpClientStreamingDataStream stream = newStream(-1L);
        RuntimeException cause = new RuntimeException("upstream broke");
        stream.fail(cause);

        InputStream in = stream.asInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IOException e = assertThrows(IOException.class, () -> in.transferTo(out));
        assertEquals(cause, e.getCause());
    }

    @Test
    void readAllBytesPropagatesStreamFailure() {
        JavaHttpClientStreamingDataStream stream = newStream(500);
        // Deliver some data, then fail mid-stream.
        stream.enqueueBatch(List.of(ByteBuffer.wrap(bytesOfLength(100))));
        RuntimeException cause = new RuntimeException("upstream broke");
        stream.fail(cause);

        InputStream in = stream.asInputStream();
        IOException e = assertThrows(IOException.class, in::readAllBytes);
        assertEquals(cause, e.getCause());
    }

    // ---- cross-thread integration ----

    @Test
    void transferToBlocksUntilProducerCompletes() throws Exception {
        byte[] part1 = bytesOfLength(1024);
        byte[] part2 = bytesOfLength(2048);
        JavaHttpClientStreamingDataStream stream = newStream(part1.length + part2.length);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AtomicBoolean done = new AtomicBoolean();
        InputStream in = stream.asInputStream();
        Thread consumer = new Thread(() -> {
            try {
                in.transferTo(out);
                done.set(true);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }, "consumer");
        consumer.start();

        // Producer feeds in two pieces and then completes.
        stream.enqueueBatch(List.of(ByteBuffer.wrap(part1)));
        Thread.sleep(50); // let consumer drain
        stream.enqueueBatch(List.of(ByteBuffer.wrap(part2)));
        stream.complete();

        consumer.join(5_000);
        assertTrue(done.get(), "consumer should have finished");

        byte[] expected = new byte[part1.length + part2.length];
        System.arraycopy(part1, 0, expected, 0, part1.length);
        System.arraycopy(part2, 0, expected, part1.length, part2.length);
        assertArrayEquals(expected, out.toByteArray());
    }
}
