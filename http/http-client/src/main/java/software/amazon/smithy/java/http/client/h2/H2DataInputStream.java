/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * Input stream for reading response body from DATA frames.
 *
 * <p>This implementation uses batch dequeuing to pull multiple data chunks from the exchange
 * in a single lock acquisition, reducing lock contention. The InputStream manages its own
 * buffer state (currentBuffer, readPosition) and a local batch of chunks.
 *
 * <p>Buffer lifecycle:
 * <ol>
 *   <li>Connection borrows buffer from muxer pool, fills from socket, enqueues chunk to exchange</li>
 *   <li>InputStream drains chunks in batches when local batch is exhausted</li>
 *   <li>InputStream returns exhausted buffer to pool via consumer</li>
 * </ol>
 */
final class H2DataInputStream extends InputStream {
    /**
     * Number of chunks to pull in a single batch. This reduces lock acquisitions by 8x for large responses.
     */
    private static final int BATCH_SIZE = 8;

    private final H2Exchange exchange;
    private final Consumer<byte[]> bufferReturner;
    private final DataChunk[] localBatch = new DataChunk[BATCH_SIZE];
    private int batchIndex = 0;
    private int batchCount = 0;

    // Current buffer state
    private byte[] currentBuffer;
    private int currentLength;
    private int readPosition;
    private boolean eof = false;
    private boolean closed = false;
    private final byte[] singleBuff = new byte[1];

    H2DataInputStream(H2Exchange exchange, Consumer<byte[]> bufferReturner) {
        this.exchange = exchange;
        this.bufferReturner = bufferReturner;
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

        // Ensure we have data
        if (currentBuffer == null || readPosition >= currentLength) {
            if (!pullNextChunk()) {
                return -1; // EOF
            }
        }

        // Copy from current buffer
        int available = currentLength - readPosition;
        int toCopy = Math.min(available, len);
        System.arraycopy(currentBuffer, readPosition, b, off, toCopy);
        readPosition += toCopy;

        // Notify exchange of bytes consumed (for flow control)
        exchange.onDataConsumed(toCopy);

        return toCopy;
    }

    /**
     * Pull the next data chunk, using batch dequeuing to reduce lock contention.
     *
     * <p>Chunks are pulled from a local batch first (no lock). When the local batch
     * is exhausted, we drain multiple chunks from the exchange in a single lock
     * acquisition.
     *
     * @return true if a chunk was pulled, false if EOF
     */
    private boolean pullNextChunk() throws IOException {
        // Return previous buffer to pool
        if (currentBuffer != null) {
            bufferReturner.accept(currentBuffer);
            currentBuffer = null;
            currentLength = 0;
        }

        // Try local batch first (no lock needed)
        if (batchIndex >= batchCount) {
            // Local batch empty - drain more chunks from exchange (one lock acquisition)
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

        currentBuffer = chunk.data();
        currentLength = chunk.length();
        readPosition = 0;

        return true;
    }

    @Override
    public int available() {
        if (closed || eof) {
            return 0;
        } else if (currentBuffer == null) {
            return 0;
        }
        return currentLength - readPosition;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        // Return current buffer to pool
        if (currentBuffer != null) {
            bufferReturner.accept(currentBuffer);
            currentBuffer = null;
        }

        // Return any remaining batched buffers to pool
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

        // First, transfer any remaining data in current buffer
        if (currentBuffer != null && readPosition < currentLength) {
            int remaining = currentLength - readPosition;
            out.write(currentBuffer, readPosition, remaining);
            transferred += remaining;
            exchange.onDataConsumed(remaining);
            readPosition = currentLength;
        }

        // Pull and write chunks directly - no intermediate buffer, no double copy
        while (pullNextChunk()) {
            out.write(currentBuffer, 0, currentLength);
            transferred += currentLength;
            exchange.onDataConsumed(currentLength);
            readPosition = currentLength;
        }

        return transferred;
    }
}
