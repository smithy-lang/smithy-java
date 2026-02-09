/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;
import software.amazon.smithy.java.http.client.UnsyncBufferedInputStream;

/**
 * InputStream that reads HTTP/1.1 chunked transfer encoding format (RFC 7230 Section 4.1).
 *
 * <p>ChunkedInputStream intentionally doesn't close the delegate stream because it's a view over one response on a
 * potentially long-lived socket. The socket lifecycle is managed by H1Connection, which is managed by the pool.
 */
final class ChunkedInputStream extends InputStream {
    private static final long MAX_CHUNK_SIZE = readMaxChunkSize();
    private static final long DEFAULT_MAX_CHUNK_SIZE = 1024 * 1024; // 1 MB
    private static final int MAX_LINE_LENGTH = 8192;

    private final UnsyncBufferedInputStream delegate;
    private long chunkRemaining = -1; // -1 means need to read chunk size
    private boolean eof;
    private boolean closed;
    private final byte[] lineBuffer = new byte[MAX_LINE_LENGTH];
    private HttpHeaders trailers; // Trailer headers parsed from final chunk (RFC 7230 Section 4.1.2)

    ChunkedInputStream(UnsyncBufferedInputStream delegate) {
        this.delegate = delegate;
    }

    private static long readMaxChunkSize() {
        String property = System.getProperty("SMITHY_HTTP_CLIENT_MAX_CHUNK_SIZE");
        if (property == null) {
            return DEFAULT_MAX_CHUNK_SIZE;
        }
        try {
            long size = Long.parseLong(property);
            if (size <= 0) {
                throw new IllegalArgumentException("SMITHY_HTTP_CLIENT_MAX_CHUNK_SIZE must be positive: " + size);
            }
            return size;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid SMITHY_HTTP_CLIENT_MAX_CHUNK_SIZE: " + property, e);
        }
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
            throw new IOException("Unexpected end of stream in chunked encoding");
        }

        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed || eof) {
            return -1;
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
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

        byte[] buffer = new byte[8192];
        long remaining = n;

        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int bytesRead = read(buffer, 0, toRead);
            if (bytesRead == -1) {
                break;
            }
            remaining -= bytesRead;
        }

        return n - remaining;
    }

    @Override
    public int available() throws IOException {
        if (closed || eof) {
            return 0;
        }

        if (chunkRemaining > 0) {
            // We know up to chunkRemaining bytes remain in this chunk; cap by delegate.available().
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

        // Drain remaining chunks to allow connection reuse (before setting closed flag)
        if (!eof) {
            transferTo(OutputStream.nullOutputStream());
        }

        closed = true;
        // Note: we don't close the delegate since the connection may be reused
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

        // Read chunk size line directly into buffer
        int lineLen = delegate.readLine(lineBuffer, MAX_LINE_LENGTH);

        long chunkSize = getChunkSize(lineLen);

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

    private long getChunkSize(int lineLen) throws IOException {
        if (lineLen <= 0) {
            throw new IOException("Empty chunk size line");
        }

        // Find end of hex size (stop at semicolon for chunk extensions, or end of line)
        int sizeEnd = lineLen;
        for (int i = 0; i < lineLen; i++) {
            byte b = lineBuffer[i];
            if (b == ';' || b == ' ') {
                sizeEnd = i;
                break;
            }
        }

        if (sizeEnd == 0) {
            throw new IOException("Missing chunk size");
        }

        // Parse hex directly from bytes
        long chunkSize = parseHex(lineBuffer, 0, sizeEnd);
        if (chunkSize < 0) {
            throw new IOException("Negative chunk size: " + chunkSize);
        }
        return chunkSize;
    }

    private static long parseHex(byte[] buf, int start, int end) throws IOException {
        long value = 0;
        for (int i = start; i < end; i++) {
            byte b = buf[i];
            int digit;
            if (b >= '0' && b <= '9') {
                digit = b - '0';
            } else if (b >= 'a' && b <= 'f') {
                digit = 10 + (b - 'a');
            } else if (b >= 'A' && b <= 'F') {
                digit = 10 + (b - 'A');
            } else {
                throw new IOException("Invalid hex character in chunk size: " + (char) b);
            }
            // Check for overflow before shifting (top 4 bits must be clear)
            if ((value & 0xF000_0000_0000_0000L) != 0) {
                throw new IOException("HTTP/1.1 chunk size overflow");
            }
            value = (value << 4) | digit;
        }
        return value;
    }

    /**
     * Read and parse trailer headers after final chunk (RFC 7230 Section 4.1.2).
     *
     * <p>Trailers are formatted like HTTP headers and are read until a blank line.
     * Parsed trailers are stored and can be retrieved via {@link #getTrailers()}.
     */
    private void readTrailers() throws IOException {
        ModifiableHttpHeaders parsedTrailers = HttpHeaders.ofModifiable();
        int len;
        while ((len = delegate.readLine(lineBuffer, MAX_LINE_LENGTH)) > 0) {
            String name = H1Utils.parseHeaderLine(lineBuffer, len, parsedTrailers);
            if (name == null) {
                throw new IOException("Invalid trailer line: "
                        + new String(lineBuffer, 0, len, StandardCharsets.US_ASCII));
            }
        }

        // Only store if we actually got trailers
        if (!parsedTrailers.isEmpty()) {
            this.trailers = parsedTrailers;
        }
    }

    /**
     * Get trailer headers parsed from the chunked stream.
     *
     * <p>Trailers are only available after the stream has been fully read (EOF reached).
     * Before EOF, this method returns null.
     *
     * @return trailer headers, or null if no trailers were received or stream not fully read
     */
    HttpHeaders getTrailers() {
        return trailers;
    }

    private void readCRLF() throws IOException {
        int cr = delegate.read();
        int lf = delegate.read();
        if (cr == -1 || lf == -1) {
            throw new IOException("Unexpected end of stream: expected CRLF after chunk data");
        }
        if (cr != '\r' || lf != '\n') {
            throw new IOException(String.format("Expected CRLF after chunk data, got 0x%02X 0x%02X", cr, lf));
        }
    }
}
