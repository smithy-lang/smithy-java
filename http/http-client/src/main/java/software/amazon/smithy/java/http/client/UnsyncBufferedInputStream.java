/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A buffered input stream like {@link java.io.BufferedInputStream}, but without synchronization.
 *
 * <p>This class exposes its guts for optimal performance. Be responsible, and note the warnings on each method.
 */
public final class UnsyncBufferedInputStream extends InputStream {
    private final InputStream in;
    private final byte[] buf;
    private int pos;
    private int limit;
    private boolean closed;

    public UnsyncBufferedInputStream(InputStream in, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        this.in = in;
        this.buf = new byte[size];
    }

    /**
     * Fills the buffer with data from the underlying stream.
     *
     * @return the number of bytes read, or -1 if EOF
     * @throws IOException if an I/O error occurs
     */
    private int fill() throws IOException {
        pos = 0;
        int n = in.read(buf);
        // Keep limit >= 0 so that "pos >= limit" comparisons work correctly after EOF
        limit = Math.max(n, 0);
        return n;
    }

    @Override
    public int read() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        } else if (pos >= limit && fill() <= 0) {
            return -1;
        } else {
            return buf[pos++] & 0xFF;
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        int n = 0;

        // First, drain the buffer
        int avail = limit - pos;
        if (avail > 0) {
            int toCopy = Math.min(avail, len);
            System.arraycopy(buf, pos, b, off, toCopy);
            pos += toCopy;
            off += toCopy;
            len -= toCopy;
            n += toCopy;
            if (len == 0) {
                return n;
            }
        }

        // If remaining request is large, bypass our buffer to avoid double-copy
        if (len >= buf.length) {
            int direct = in.read(b, off, len);
            if (direct < 0) {
                return n == 0 ? -1 : n;
            }
            return n + direct;
        }

        // For smaller remaining requests, refill buffer and copy
        if (fill() <= 0) {
            return n == 0 ? -1 : n;
        }

        int toCopy = Math.min(limit - pos, len);
        System.arraycopy(buf, pos, b, off, toCopy);
        pos += toCopy;
        return n + toCopy;
    }

    @Override
    public long skip(long n) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        } else if (n <= 0) {
            return 0;
        }

        long remaining = n;

        // First skip what's in the buffer
        int avail = limit - pos;
        if (avail > 0) {
            long skipped = Math.min(avail, remaining);
            pos += (int) skipped;
            remaining -= skipped;
        }

        // Skip in underlying stream only if needed
        if (remaining > 0) {
            long skippedUnderlying = in.skip(remaining);
            if (skippedUnderlying > 0) {
                remaining -= skippedUnderlying;
            }
        }

        return n - remaining;
    }

    @Override
    public int available() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        int avail = limit - pos;
        if (avail < 0) {
            avail = 0;
        }
        return avail + in.available();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            in.close();
        }
    }

    // Optimized transferTo that doesn't allocate a new buffer.
    @Override
    public long transferTo(OutputStream out) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }

        // First drain what's already buffered
        long transferred = 0;
        int buffered = limit - pos;
        if (buffered > 0) {
            out.write(buf, pos, buffered);
            pos = limit;
            transferred = buffered;
        }

        // Then stream the rest using _our_ buffer (super would allocate a buffer)
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            transferred += n;
        }
        return transferred;
    }

    /**
     * Read directly from the underlying stream, bypassing this buffer entirely.
     *
     * <p>This is useful when the caller knows the buffer is empty (e.g., after
     * draining via {@link #consume}) and wants to avoid the buffer fill/check overhead.
     *
     * @param b destination buffer
     * @param off offset in destination
     * @param len maximum bytes to read
     * @return bytes read, or -1 on EOF
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if the buffer is not empty
     */
    public int readDirect(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        if (pos < limit) {
            throw new IllegalStateException("Buffer not empty: " + (limit - pos) + " bytes remaining");
        }
        return in.read(b, off, len);
    }

    /**
     * Returns the internal buffer array.
     *
     * <p><b>WARNING:</b> The caller must not modify the buffer contents.
     * This method is provided for zero-copy read access only.
     *
     * @return the internal buffer array
     */
    public byte[] buffer() {
        return buf;
    }

    /**
     * Returns the current read position in the internal buffer.
     *
     * <p>Valid data in the buffer spans from {@code position()} to {@code limit()}.
     *
     * @return the current read position
     */
    public int position() {
        return pos;
    }

    /**
     * Returns the current limit of valid data in the internal buffer.
     *
     * <p>Valid data in the buffer spans from {@code position()} to {@code limit()}.
     *
     * @return the limit of valid data
     */
    public int limit() {
        return limit;
    }

    /**
     * Returns the number of bytes currently buffered and available for reading.
     *
     * <p>This is equivalent to {@code limit() - position()}.
     *
     * @return the number of buffered bytes available
     */
    public int buffered() {
        return limit - pos;
    }

    /**
     * Advances the internal read position, consuming bytes from the buffer.
     *
     * <p>This is used after directly reading from the buffer via {@link #buffer()}.
     *
     * @param n number of bytes to consume
     * @throws IndexOutOfBoundsException if n > buffered()
     */
    public void consume(int n) {
        if (n < 0 || pos + n > limit) {
            throw new IndexOutOfBoundsException("Cannot consume " + n + " bytes, only " + (limit - pos) + " available");
        }
        pos += n;
    }

    /**
     * Ensures at least {@code n} bytes are available in the buffer.
     *
     * <p>If fewer than {@code n} bytes are currently buffered, this method compacts
     * the buffer (moves remaining data to the front) and reads more data from the
     * underlying stream until at least {@code n} bytes are available or EOF is reached.
     *
     * <p>After this method returns true, the caller can safely read {@code n} bytes
     * directly from {@link #buffer()} starting at {@link #position()}.
     *
     * @param n the minimum number of bytes required
     * @return true if at least n bytes are now available, false if EOF was reached
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if n > buffer size
     */
    public boolean ensure(int n) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        if (n > buf.length) {
            throw new IllegalArgumentException("Cannot ensure " + n + " bytes, buffer size is " + buf.length);
        }
        if (n <= 0) {
            return true;
        }

        int avail = limit - pos;
        if (avail >= n) {
            return true;
        }

        // Compact: move remaining data to front of buffer
        if (pos > 0 && avail > 0) {
            System.arraycopy(buf, pos, buf, 0, avail);
        }
        pos = 0;
        limit = avail;

        // Fill until we have enough or hit EOF
        while (limit < n) {
            int read = in.read(buf, limit, buf.length - limit);
            if (read < 0) {
                return false; // EOF before we could get n bytes
            }
            limit += read;
        }

        return true;
    }

    /**
     * Reads a line terminated by CRLF or LF into the provided buffer.
     *
     * <p>This method is optimized for HTTP header parsing where lines are typically
     * short and fit within a single buffer.
     *
     * @param dest buffer to read line into
     * @param maxLength maximum allowed line length
     * @return the number of bytes written to dest, or -1 if EOF with no data
     * @throws IOException if an I/O error occurs or line exceeds maxLength or dest.length
     */
    public int readLine(byte[] dest, int maxLength) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }

        int destPos = 0;

        for (;;) {
            // Ensure buffer has data
            if (pos >= limit && fill() <= 0) {
                // EOF - return what we have
                return destPos > 0 ? destPos : -1;
            }

            // Scan buffer for line terminator - use locals for hot loop
            int scanStart = pos;
            int maxScan = Math.min(limit, pos + Math.min(maxLength - destPos + 1, dest.length - destPos));
            byte[] localBuf = buf;

            while (pos < maxScan) {
                byte b = localBuf[pos];
                if (b == '\r' || b == '\n') {
                    // Copy scanned bytes to dest
                    int scannedLen = pos - scanStart;
                    if (scannedLen > 0) {
                        System.arraycopy(localBuf, scanStart, dest, destPos, scannedLen);
                        destPos += scannedLen;
                    }
                    pos++;
                    if (b == '\r') {
                        // Check for LF after CR
                        if (pos < limit || fill() > 0) {
                            if (localBuf[pos] == '\n') {
                                pos++;
                            }
                        }
                    }
                    return destPos;
                }
                pos++;
            }

            // Copy scanned bytes to dest
            int scannedLen = pos - scanStart;
            if (scannedLen > 0) {
                System.arraycopy(localBuf, scanStart, dest, destPos, scannedLen);
                destPos += scannedLen;
            }

            // Check if we hit the length limit without finding terminator
            if (destPos > maxLength) {
                throw new IOException("Line exceeds maximum length of " + maxLength);
            }
            if (destPos >= dest.length) {
                throw new IOException("Line exceeds buffer size of " + dest.length);
            }
        }
    }
}
