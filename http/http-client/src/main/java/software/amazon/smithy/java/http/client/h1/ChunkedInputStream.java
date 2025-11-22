/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.io.IOException;
import java.io.InputStream;

/**
 * InputStream that reads HTTP/1.1 chunked transfer encoding format.
 *
 * <p>Chunked encoding format (RFC 7230 Section 4.1):
 * <pre>
 *   chunk-size [ chunk-ext ] CRLF
 *   chunk-data CRLF
 *   ...
 *   0 CRLF
 *   [ trailer-part ] CRLF
 * </pre>
 *
 * <p>Example:
 * <pre>
 *   5\r\n
 *   Hello\r\n
 *   6\r\n
 *   World!\r\n
 *   0\r\n
 *   \r\n
 * </pre>
 */
final class ChunkedInputStream extends InputStream {
    // Maximum allowed chunk size, configurable via SMITHY_HTTP_CLIENT_MAX_CHUNK_SIZE system property (in bytes)
    private static final long MAX_CHUNK_SIZE = readMaxChunkSize();
    private static final long DEFAULT_MAX_CHUNK_SIZE = 1024 * 1024; // 10 MB

    private final InputStream delegate;

    private static long readMaxChunkSize() {
        String property = System.getProperty("SMITHY_HTTP_CLIENT_MAX_CHUNK_SIZE");
        if (property == null) {
            return DEFAULT_MAX_CHUNK_SIZE;
        }
        try {
            long size = Long.parseLong(property);
            if (size <= 0) {
                throw new IllegalArgumentException(
                        "SMITHY_HTTP_CLIENT_MAX_CHUNK_SIZE must be positive: " + size);
            }
            return size;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid SMITHY_HTTP_CLIENT_MAX_CHUNK_SIZE: " + property,
                    e);
        }
    }

    // Current chunk state
    private long chunkRemaining = -1; // -1 means need to read chunk size
    private boolean eof = false;
    private boolean closed = false;

    ChunkedInputStream(InputStream delegate) {
        this.delegate = delegate;
    }

    @Override
    public int read() throws IOException {
        if (closed || eof) {
            return -1;
        }

        // Need to read next chunk?
        if (chunkRemaining == -1 || chunkRemaining == 0) {
            if (!readNextChunk()) {
                return -1; // EOF
            }
        }

        // Read one byte from current chunk
        int b = delegate.read();
        if (b != -1) {
            chunkRemaining--;
        } else {
            // Unexpected EOF
            throw new IOException("Unexpected end of stream in chunked encoding");
        }

        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed || eof) {
            return -1;
        }

        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }

        // Need to read next chunk?
        if (chunkRemaining == -1 || chunkRemaining == 0) {
            if (!readNextChunk()) {
                return -1; // EOF
            }
        }

        // Read at most chunkRemaining bytes
        int toRead = (int) Math.min(len, chunkRemaining);
        int n = delegate.read(b, off, toRead);

        if (n > 0) {
            chunkRemaining -= n;
        } else if (n == -1) {
            throw new IOException("Unexpected end of stream in chunked encoding");
        }

        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        if (closed || eof || n <= 0) {
            return 0;
        }

        // Skip by reading into a buffer
        byte[] buffer = new byte[8192];
        long remaining = n;

        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int read = read(buffer, 0, toRead);

            if (read == -1) {
                break;
            }

            remaining -= read;
        }

        return n - remaining;
    }

    @Override
    public int available() throws IOException {
        if (closed || eof) {
            return 0;
        }

        if (chunkRemaining > 0) {
            // We know there's at least chunkRemaining bytes available
            int available = delegate.available();
            return (int) Math.min(available, chunkRemaining);
        }

        return 0;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;

        // Drain remaining chunks to allow connection reuse
        if (!eof) {
            byte[] drain = new byte[8192];
            while (read(drain) != -1) {
                // Discard
            }
        }

        // Don't close delegate - connection may be reused
    }

    /**
     * Read the next chunk header and update state.
     *
     * @return true if there's more data, false if final chunk (size 0)
     * @throws IOException if chunk format is invalid
     */
    private boolean readNextChunk() throws IOException {
        // If we just finished a chunk, consume trailing CRLF
        if (chunkRemaining == 0) {
            readCRLF();
        }

        // Read chunk size line
        String chunkSizeLine = readLine();

        if (chunkSizeLine.isEmpty()) {
            throw new IOException("Empty chunk size line");
        }

        // Parse chunk size (hex), ignore chunk extensions after semicolon
        int semicolon = chunkSizeLine.indexOf(';');
        String sizeStr = semicolon > 0
                ? chunkSizeLine.substring(0, semicolon).trim()
                : chunkSizeLine.trim();

        if (sizeStr.isEmpty()) {
            throw new IOException("Missing chunk size");
        }

        long chunkSize;
        try {
            chunkSize = Long.parseLong(sizeStr, 16);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid chunk size: " + sizeStr, e);
        }

        if (chunkSize < 0) {
            throw new IOException("Negative chunk size: " + chunkSize);
        }

        if (chunkSize > MAX_CHUNK_SIZE) {
            throw new IOException("Chunk size " + chunkSize + " exceeds maximum allowed size of " + MAX_CHUNK_SIZE);
        }

        if (chunkSize == 0) {
            // Final chunk - read optional trailers
            readTrailers();
            eof = true;
            chunkRemaining = 0;
            return false;
        }

        chunkRemaining = chunkSize;
        return true;
    }

    /**
     * Read and discard trailer headers after final chunk.
     */
    private void readTrailers() throws IOException {
        // Trailers are formatted like HTTP headers
        // Read until blank line
        String line;
        while (!(line = readLine()).isEmpty()) {
            // Could parse and expose trailers if needed
            // For now, just consume them

            // Validate it looks like a header (has colon)
            if (!line.contains(":")) {
                throw new IOException("Invalid trailer line: " + line);
            }
        }
    }

    /**
     * Read CRLF and validate it's present.
     */
    private void readCRLF() throws IOException {
        int cr = delegate.read();
        int lf = delegate.read();

        if (cr != '\r' || lf != '\n') {
            throw new IOException(
                    String.format("Expected CRLF, got 0x%02X 0x%02X", cr, lf));
        }
    }

    /**
     * Read a line terminated by CRLF or LF.
     *
     * @return the line without the line terminator
     * @throws IOException if read fails
     */
    private String readLine() throws IOException {
        StringBuilder line = new StringBuilder();
        int b;

        while ((b = delegate.read()) != -1) {
            if (b == '\r') {
                // Expect LF next
                int next = delegate.read();
                if (next == '\n') {
                    break; // Found CRLF
                } else if (next == -1) {
                    throw new IOException("Unexpected EOF after CR");
                } else {
                    // CR not followed by LF - treat CR as data
                    line.append((char) b);
                    line.append((char) next);
                }
            } else if (b == '\n') {
                // LF without CR - accept it
                break;
            } else {
                line.append((char) b);
            }

            // Safety limit to prevent unbounded memory use
            if (line.length() > 8192) {
                throw new IOException("Chunk size line too long (>8192 chars)");
            }
        }

        if (b == -1 && line.isEmpty()) {
            throw new IOException("Unexpected EOF reading chunk size");
        }

        return line.toString();
    }
}
