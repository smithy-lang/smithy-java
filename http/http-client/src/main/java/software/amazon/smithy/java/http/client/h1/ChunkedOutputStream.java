/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * OutputStream that writes HTTP/1.1 chunked transfer encoding format (RFC 7230 Section 4.1).
 *
 * <p>This stream does not close the delegate on close, allowing the underlying socket to be reused
 * for subsequent HTTP/1.1 requests. The socket lifecycle is managed by {@link H1Connection}.
 */
final class ChunkedOutputStream extends OutputStream {
    private final OutputStream delegate;
    private final byte[] buffer;
    private int bufferPos = 0;
    private boolean closed = false;

    // Default chunk size: 8KB
    private static final int DEFAULT_CHUNK_SIZE = 8192;

    /**
     * Create a ChunkedOutputStream with default chunk size (8KB).
     */
    ChunkedOutputStream(OutputStream delegate) {
        this(delegate, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Create a ChunkedOutputStream with specified chunk size.
     *
     * @param delegate underlying stream to write chunks to
     * @param chunkSize maximum size of each chunk in bytes (must be > 0)
     */
    ChunkedOutputStream(OutputStream delegate, int chunkSize) {
        if (delegate == null) {
            throw new NullPointerException("delegate");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive: " + chunkSize);
        }

        this.delegate = delegate;
        this.buffer = new byte[chunkSize];
    }

    @Override
    public void write(int b) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }

        buffer[bufferPos++] = (byte) b;

        if (bufferPos >= buffer.length) {
            flushChunk();
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }

        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return;
        }

        int remaining = len;
        int offset = off;

        while (remaining > 0) {
            int available = buffer.length - bufferPos;
            int toCopy = Math.min(remaining, available);

            System.arraycopy(b, offset, buffer, bufferPos, toCopy);
            bufferPos += toCopy;
            offset += toCopy;
            remaining -= toCopy;

            if (bufferPos >= buffer.length) {
                flushChunk();
            }
        }
    }

    @Override
    public void flush() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }

        // Flush any buffered data as a chunk
        if (bufferPos > 0) {
            flushChunk();
        }

        // Flush underlying stream
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;

        // Flush any remaining buffered data
        if (bufferPos > 0) {
            flushChunk();
        }

        // Write final 0-sized chunk
        writeFinalChunk();
        // Flush underlying stream, and don't close delegate on failure since the connection may be reused
        delegate.flush();
    }

    /**
     * Flush the current buffer as a chunk.
     */
    private void flushChunk() throws IOException {
        if (bufferPos == 0) {
            return;
        }

        writeChunk(buffer, 0, bufferPos);
        bufferPos = 0;
    }

    /**
     * Write a chunk with the given data.
     *
     * <p>Format:
     *   {size-in-hex}\r\n
     *   {data}\r\n
     */
    private void writeChunk(byte[] data, int off, int len) throws IOException {
        // Write chunk size in hexadecimal
        String hexSize = Integer.toHexString(len);
        delegate.write(hexSize.getBytes(StandardCharsets.US_ASCII));
        delegate.write('\r');
        delegate.write('\n');

        // Write chunk data
        delegate.write(data, off, len);

        // Write trailing CRLF
        delegate.write('\r');
        delegate.write('\n');
    }

    /**
     * Write the final 0-sized chunk.
     *
     * <p>Format:
     *   0\r\n
     *   \r\n
     */
    private void writeFinalChunk() throws IOException {
        // Write "0\r\n\r\n"
        delegate.write('0');
        delegate.write('\r');
        delegate.write('\n');
        delegate.write('\r');
        delegate.write('\n');
    }
}
