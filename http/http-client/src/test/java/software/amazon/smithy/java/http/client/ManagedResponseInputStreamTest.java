/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link ManagedResponseInputStream#readAllBytes()}, in particular the pre-sized
 * known-Content-Length fast path and its fallbacks, plus the {@code onClose} lifecycle hook.
 */
class ManagedResponseInputStreamTest {

    private static byte[] bytes(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) (i * 31 + 7);
        }
        return b;
    }

    @Test
    void readAllBytesKnownLengthExact() throws IOException {
        byte[] payload = bytes(5000);
        var closed = new AtomicInteger();
        var in = new ManagedResponseInputStream(
                new ByteArrayInputStream(payload),
                payload.length,
                closed::incrementAndGet);

        assertArrayEquals(payload, in.readAllBytes());
        assertTrue(closed.get() >= 1, "onClose must run after readAllBytes");
    }

    @Test
    void readAllBytesKnownLengthDripFed() throws IOException {
        // Stream that returns at most 64 bytes per read — exercises the fill loop in readKnownLength.
        byte[] payload = bytes(4096);
        var in = new ManagedResponseInputStream(
                new ChunkedStream(payload, 64),
                payload.length,
                () -> {});
        assertArrayEquals(payload, in.readAllBytes());
    }

    @Test
    void readAllBytesStreamShorterThanContentLength() throws IOException {
        // Header claims more than the stream delivers: must return exactly what was read.
        byte[] payload = bytes(1000);
        var in = new ManagedResponseInputStream(
                new ByteArrayInputStream(payload),
                4096, // overstated length
                () -> {});
        assertArrayEquals(payload, in.readAllBytes());
    }

    @Test
    void readAllBytesReadsExactlyContentLength() throws IOException {
        // In production the inner stream is a length-bounded FixedLengthResponseInputStream that
        // returns EOF after exactly Content-Length bytes, so readAllBytes must read precisely that
        // many — never peeking past, which on a pooled keep-alive connection would read into the
        // next response. Model that bound: stop the stream at `len` even though more data follows.
        byte[] full = bytes(3000);
        int len = 1000;
        var in = new ManagedResponseInputStream(
                new BoundedStream(full, len),
                len,
                () -> {});
        byte[] expected = new byte[len];
        System.arraycopy(full, 0, expected, 0, len);
        assertArrayEquals(expected, in.readAllBytes());
    }

    @Test
    void readAllBytesUnknownLength() throws IOException {
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        var in = new ManagedResponseInputStream(
                new ByteArrayInputStream(payload),
                -1, // unknown
                () -> {});
        assertArrayEquals(payload, in.readAllBytes());
    }

    @Test
    void readAllBytesEmptyKnownLength() throws IOException {
        var in = new ManagedResponseInputStream(new ByteArrayInputStream(new byte[0]), 0, () -> {});
        assertEquals(0, in.readAllBytes().length);
    }

    // --- Error-terminal routing: a read that throws must run onError, never onClose. ---

    @Test
    void readThrowsRunsOnErrorNotOnClose() throws IOException {
        assertErrorTerminal(in -> in.read());
    }

    @Test
    void readArrayThrowsRunsOnErrorNotOnClose() throws IOException {
        assertErrorTerminal(in -> in.read(new byte[16]));
    }

    @Test
    void readAllBytesThrowsRunsOnErrorNotOnClose() throws IOException {
        // The #3 regression: readAllBytes used finally{onClose} and reported a clean close on a torn read.
        assertErrorTerminal(InputStream::readAllBytes);
    }

    @Test
    void transferToThrowsRunsOnErrorNotOnClose() throws IOException {
        assertErrorTerminal(in -> in.transferTo(OutputStream.nullOutputStream()));
    }

    @Test
    void readNBytesThrowsRunsOnErrorNotOnClose() throws IOException {
        assertErrorTerminal(in -> in.readNBytes(16));
    }

    @Test
    void skipNBytesThrowsRunsOnErrorNotOnClose() throws IOException {
        assertErrorTerminal(in -> in.skipNBytes(16));
    }

    @Test
    void readNBytesArrayThrowsRunsOnErrorNotOnClose() throws IOException {
        assertErrorTerminal(in -> in.readNBytes(new byte[16], 0, 16));
    }

    @Test
    void skipThrowsRunsOnErrorNotOnClose() throws IOException {
        assertErrorTerminal(in -> in.skip(16));
    }

    @Test
    void availableThrowsRunsOnErrorNotOnClose() throws IOException {
        assertErrorTerminal(InputStream::available);
    }

    @Test
    void resetThrowsRunsOnErrorNotOnClose() throws IOException {
        assertErrorTerminal(InputStream::reset);
    }

    @Test
    void closeFailureRunsOnErrorNotOnClose() throws IOException {
        // close() is a terminal too: a failing inner.close() must evict (onError), not report a clean close.
        assertErrorTerminal(InputStream::close);
    }

    /**
     * Drive {@code op} against a stream whose operations throw, and assert the failure ran the error
     * terminal (with the original exception) and NOT the success terminal.
     */
    private static void assertErrorTerminal(ThrowingOp op) throws IOException {
        var closed = new AtomicInteger();
        var errored = new AtomicReference<Throwable>();
        var boom = new IOException("boom");
        var in = new ManagedResponseInputStream(
                new ThrowingStream(boom),
                1024,
                closed::incrementAndGet,
                errored::set);

        var thrown = Assertions.assertThrows(IOException.class, () -> op.run(in));

        assertEquals(boom, thrown, "the original read exception must propagate");
        assertEquals(boom, errored.get(), "onError must run with the read failure");
        assertEquals(0, closed.get(), "onClose (success terminal) must NOT run on a failed read");
    }

    @FunctionalInterface
    private interface ThrowingOp {
        void run(ManagedResponseInputStream in) throws IOException;
    }

    /** InputStream whose every read/skip/available throws the supplied exception. */
    private static final class ThrowingStream extends InputStream {
        private final IOException error;

        ThrowingStream(IOException error) {
            this.error = error;
        }

        @Override
        public int read() throws IOException {
            throw error;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            throw error;
        }

        @Override
        public byte[] readAllBytes() throws IOException {
            throw error;
        }

        @Override
        public byte[] readNBytes(int len) throws IOException {
            throw error;
        }

        @Override
        public int readNBytes(byte[] b, int off, int len) throws IOException {
            throw error;
        }

        @Override
        public long skip(long n) throws IOException {
            throw error;
        }

        @Override
        public void skipNBytes(long n) throws IOException {
            throw error;
        }

        @Override
        public int available() throws IOException {
            throw error;
        }

        @Override
        public void reset() throws IOException {
            throw error;
        }

        @Override
        public void close() throws IOException {
            throw error;
        }
    }

    /**
     * InputStream that returns EOF after {@code limit} bytes even though {@code data} holds more —
     * models the length-bounded FixedLengthResponseInputStream the production code wraps.
     */
    private static final class BoundedStream extends InputStream {
        private final byte[] data;
        private final int limit;
        private int pos;

        BoundedStream(byte[] data, int limit) {
            this.data = data;
            this.limit = limit;
        }

        @Override
        public int read() {
            return pos < limit ? data[pos++] & 0xFF : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (pos >= limit) {
                return -1;
            }
            int n = Math.min(len, limit - pos);
            System.arraycopy(data, pos, b, off, n);
            pos += n;
            return n;
        }
    }

    /** InputStream that serves at most {@code maxChunk} bytes per read call. */
    private static final class ChunkedStream extends InputStream {
        private final byte[] data;
        private final int maxChunk;
        private int pos;

        ChunkedStream(byte[] data, int maxChunk) {
            this.data = data;
            this.maxChunk = maxChunk;
        }

        @Override
        public int read() {
            return pos < data.length ? data[pos++] & 0xFF : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (pos >= data.length) {
                return -1;
            }
            int n = Math.min(Math.min(len, maxChunk), data.length - pos);
            System.arraycopy(data, pos, b, off, n);
            pos += n;
            return n;
        }
    }
}
