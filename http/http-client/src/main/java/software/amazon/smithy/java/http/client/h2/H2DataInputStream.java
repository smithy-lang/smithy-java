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
 * <p>Uses batch dequeuing to pull multiple data chunks from the exchange
 * in a single lock acquisition. Chunks are ByteBuffers from the pool.
 *
 * <p>Also provides a {@link #channel()} for zero-copy ByteBuffer reads.
 */
final class H2DataInputStream extends InputStream {
    private static final int BATCH_SIZE = 32;

    private final H2Exchange exchange;
    private final Consumer<ByteBuffer> bufferReturner;
    private final DataChunk[] localBatch = new DataChunk[BATCH_SIZE];
    private int batchIndex = 0;
    private int batchCount = 0;

    private ByteBuffer current;
    private boolean eof = false;
    private boolean closed = false;
    private final byte[] singleBuff = new byte[1];

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
        if (current != null) {
            bufferReturner.accept(current);
            current = null;
        }

        if (batchIndex >= batchCount) {
            int drained = exchange.drainChunks(localBatch, BATCH_SIZE);
            if (drained < 0) {
                eof = true;
                return false;
            }
            batchIndex = 0;
            batchCount = drained;
        }

        DataChunk chunk = localBatch[batchIndex];
        localBatch[batchIndex] = null;
        batchIndex++;

        current = chunk.data();
        return true;
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

        if (current != null) {
            bufferReturner.accept(current);
            current = null;
        }

        while (batchIndex < batchCount) {
            bufferReturner.accept(localBatch[batchIndex].data());
            localBatch[batchIndex] = null;
            batchIndex++;
        }
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        if (closed || eof) {
            return 0;
        }

        long transferred = 0;

        if (current != null && current.hasRemaining()) {
            int remaining = current.remaining();
            out.write(current.array(), current.arrayOffset() + current.position(), remaining);
            current.position(current.limit());
            transferred += remaining;
            exchange.onDataConsumed(remaining);
        }

        while (pullNextChunk()) {
            int remaining = current.remaining();
            out.write(current.array(), current.arrayOffset() + current.position(), remaining);
            current.position(current.limit());
            transferred += remaining;
            exchange.onDataConsumed(remaining);
        }

        return transferred;
    }
}
