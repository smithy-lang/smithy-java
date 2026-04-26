/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.function.Consumer;

/**
 * Input stream for reading response body from DATA frames.
 *
 * <p>Reads directly from per-stream pooled DATA-frame buffers owned by the exchange.
 * Chunks are borrowed {@link ByteBuffer}s that are returned to the pool when retired.
 *
 * <p>Also provides a {@link #channel()} for zero-copy ByteBuffer reads.
 */
final class H2DataInputStream extends InputStream {
    private static final int BATCH_SIZE = 128;

    private final H2Exchange exchange;
    private final Consumer<ByteBuffer> bufferReturner;
    private final DataChunk[] localBatch = new DataChunk[BATCH_SIZE];

    private DataChunk currentChunk;
    private ByteBuffer current;
    private boolean eof = false;
    private boolean closed = false;
    private final byte[] singleBuff = new byte[1];
    private final byte[] transferBuffer = new byte[65536];

    H2DataInputStream(H2Exchange exchange, Consumer<ByteBuffer> bufferReturner) {
        this.exchange = exchange;
        this.bufferReturner = bufferReturner;
    }

    /**
     * Get a zero-copy readable channel backed by this stream's data chunks.
     * Reads transfer ByteBuffer data directly without byte[] intermediaries.
     */
    ReadableByteChannel channel() {
        return new ReadableByteChannel() {
            @Override
            public int read(ByteBuffer dst) throws IOException {
                return readChannel(dst);
            }

            @Override
            public boolean isOpen() {
                return !closed && !eof;
            }

            @Override
            public void close() throws IOException {
                H2DataInputStream.this.close();
            }
        };
    }

    /**
     * Zero-copy read into a ByteBuffer. Transfers data directly from pooled
     * chunk buffers into the destination without going through byte[].
     */
    int readChannel(ByteBuffer dst) throws IOException {
        if (closed || eof) {
            return -1;
        }
        if (!dst.hasRemaining()) {
            return 0;
        }

        if (current == null || !current.hasRemaining()) {
            if (!pullNextChunk()) {
                return -1;
            }
        }

        int toCopy = Math.min(current.remaining(), dst.remaining());
        int oldLimit = current.limit();
        current.limit(current.position() + toCopy);
        dst.put(current);
        current.limit(oldLimit);

        exchange.onDataConsumed(toCopy);
        return toCopy;
    }

    @Override
    public int read() throws IOException {
        if (closed || eof) {
            return -1;
        }
        int n = read(singleBuff, 0, 1);
        return n == 1 ? (singleBuff[0] & 0xFF) : -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (closed || eof) {
            return -1;
        } else if (len == 0) {
            return 0;
        }

        if (current == null || !current.hasRemaining()) {
            if (!pullNextChunk()) {
                return -1;
            }
        }

        int toCopy = Math.min(current.remaining(), len);
        current.get(b, off, toCopy);
        exchange.onDataConsumed(toCopy);
        return toCopy;
    }

    private boolean pullNextChunk() throws IOException {
        if (currentChunk != null) {
            releaseCurrentChunk();
        }

        currentChunk = exchange.awaitNextChunk();
        if (currentChunk == null) {
            eof = true;
            return false;
        }
        current = currentChunk.data();
        return true;
    }

    private void releaseCurrentChunk() {
        exchange.releaseDataCredit(currentChunk.flowControlBytes());
        bufferReturner.accept(currentChunk.data());
        currentChunk = null;
        current = null;
    }

    @Override
    public int available() {
        if (closed || eof || current == null) {
            return 0;
        }
        return current.remaining();
    }

    @Override
    public long skip(long n) throws IOException {
        if (closed || eof || n <= 0) {
            return 0;
        }

        long skipped = 0;

        if (current != null && current.hasRemaining()) {
            int toSkip = (int) Math.min(current.remaining(), n);
            current.position(current.position() + toSkip);
            exchange.onDataConsumed(toSkip);
            skipped += toSkip;
            n -= toSkip;
        }

        while (n > 0 && pullNextChunk()) {
            int toSkip = (int) Math.min(current.remaining(), n);
            current.position(current.position() + toSkip);
            exchange.onDataConsumed(toSkip);
            skipped += toSkip;
            n -= toSkip;
        }

        return skipped;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        if (currentChunk != null) {
            releaseCurrentChunk();
        }
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        if (closed || eof) {
            return 0;
        }

        long transferred = 0;

        if (current != null && current.hasRemaining()) {
            transferred += writeCurrentTo(out);
        }

        while (true) {
            int drained = exchange.awaitChunks(localBatch, BATCH_SIZE);
            if (drained < 0) {
                eof = true;
                return transferred;
            }

            for (int i = 0; i < drained; i++) {
                currentChunk = localBatch[i];
                localBatch[i] = null;
                current = currentChunk.data();
                transferred += writeCurrentTo(out);
                releaseCurrentChunk();
            }
        }
    }

    private int writeCurrentTo(OutputStream out) throws IOException {
        int remaining = current.remaining();
        if (current.hasArray()) {
            out.write(current.array(), current.arrayOffset() + current.position(), remaining);
            current.position(current.limit());
            exchange.onDataConsumed(remaining);
            return remaining;
        }

        int written = 0;
        while (current.hasRemaining()) {
            int toCopy = Math.min(current.remaining(), transferBuffer.length);
            current.get(transferBuffer, 0, toCopy);
            out.write(transferBuffer, 0, toCopy);
            written += toCopy;
            exchange.onDataConsumed(toCopy);
        }
        return written;
    }
}
