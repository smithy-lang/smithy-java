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

        // If caller wants something large, bypass our buffer
        if (len >= buf.length) {
            int direct = in.read(b, off, len);
            if (direct < 0) {
                return n == 0 ? -1 : n;
            }
            return n + direct;
        }

        // Otherwise, refill and copy from buffer
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
