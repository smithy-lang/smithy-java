/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.compression;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.zip.GZIPOutputStream;

/**
 * An InputStream that compresses data from a source InputStream using GZIP compression.
 * This implementation lazily compress from the source data on-demand as it's read.
 */
final class GzipCompressingInputStream extends InputStream {
    private static final int CHUNK_SIZE = 8192;
    private final InputStream source;
    private final ByteArrayOutputStream bufferStream;
    private final GZIPOutputStream gzipStream;
    private final byte[] chunk = new byte[CHUNK_SIZE];
    private byte[] buffer;
    private int bufferPos;
    private int bufferLimit;
    private boolean sourceExhausted;
    private boolean closed;

    public GzipCompressingInputStream(InputStream source) {
        this.source = source;
        this.bufferStream = new ByteArrayOutputStream();
        this.gzipStream = createGzipOutputStream(bufferStream);
        this.buffer = new byte[0];
        this.bufferPos = 0;
        this.bufferLimit = 0;
        this.sourceExhausted = false;
        this.closed = false;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int result = read(b, 0, 1);
        return result == -1 ? -1 : (b[0] & 0xFF);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }

        if (b == null) {
            throw new NullPointerException("b");
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        // Try to fill the output buffer if it's empty
        while (bufferPos >= bufferLimit) {
            if (!fillBuffer()) {
                return -1; // End of stream
            }
        }

        // Copy available data from buffer
        int available = bufferLimit - bufferPos;
        int toRead = Math.min(available, len);
        System.arraycopy(buffer, bufferPos, b, off, toRead);
        bufferPos += toRead;

        return toRead;
    }

    /**
     * Reads a chunk from the source, compresses it, and fills the internal buffer.
     *
     * @return true if data was added to buffer, false if end of stream reached
     */
    private boolean fillBuffer() throws IOException {
        if (sourceExhausted) {
            return false;
        }

        // Read a chunk from source
        int bytesRead = source.read(chunk);

        if (bytesRead == -1) {
            // Source is exhausted, finish compression
            gzipStream.finish();
            sourceExhausted = true;
        } else {
            // Compress the chunk
            gzipStream.write(chunk, 0, bytesRead);
            gzipStream.flush();
        }

        // Get compressed data from buffer stream
        byte[] compressed = bufferStream.toByteArray();
        if (compressed.length > 0) {
            buffer = compressed;
            bufferPos = 0;
            bufferLimit = compressed.length;
            bufferStream.reset();
            return true;
        }

        if (sourceExhausted) {
            return bufferPos >= bufferLimit;
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            try {
                gzipStream.close();
            } finally {
                source.close();
            }
        }
    }

    @Override
    public int available() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        return bufferLimit - bufferPos;
    }

    /**
     * Utility method to avoid having to throw the checked IOException exception.
     */
    private GZIPOutputStream createGzipOutputStream(OutputStream bufferStream) {
        try {
            return new GZIPOutputStream(bufferStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
