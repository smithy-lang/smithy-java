/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A buffered output stream without synchronization.
 */
public final class UnsyncBufferedOutputStream extends OutputStream {
    private final OutputStream out;
    private final byte[] buf;
    private int pos;
    private boolean closed;

    /**
     * Creates a buffered output stream with the specified buffer size.
     *
     * @param out the underlying output stream
     * @param size the buffer size
     */
    public UnsyncBufferedOutputStream(OutputStream out, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        this.out = out;
        this.buf = new byte[size];
    }

    /**
     * Flushes the internal buffer to the underlying stream.
     *
     * @throws IOException if an I/O error occurs
     */
    private void flushBuffer() throws IOException {
        if (pos > 0) {
            out.write(buf, 0, pos);
            pos = 0;
        }
    }

    @Override
    public void write(int b) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        if (pos >= buf.length) {
            flushBuffer();
        }
        buf[pos++] = (byte) b;
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        } else if (len >= buf.length) {
            // If data is larger than buffer, flush and write directly
            flushBuffer();
            out.write(b, off, len);
            return;
        }

        // If data won't fit in remaining buffer, flush first
        if (len > buf.length - pos) {
            flushBuffer();
        }

        // Copy to buffer
        System.arraycopy(b, off, buf, pos, len);
        pos += len;
    }

    /**
     * Writes an ASCII string directly to the buffer.
     * Each character is cast to a byte (assumes ASCII/Latin-1 input).
     *
     * @param s the string to write
     * @throws IOException if an I/O error occurs
     */
    public void writeAscii(String s) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        int len = s.length();
        if (len == 0) {
            return;
        }

        int i = 0;
        byte[] localBuf = buf;
        int bufLen = localBuf.length;

        while (i < len) {
            int available = bufLen - pos;
            if (available == 0) {
                flushBuffer();
                available = bufLen;
            }

            // Copy as many chars as fit in buffer
            int toCopy = Math.min(available, len - i);
            int end = i + toCopy;
            int p = pos;
            while (i < end) {
                localBuf[p++] = (byte) s.charAt(i++);
            }
            pos = p;
        }
    }

    @Override
    public void flush() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        flushBuffer();
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            try {
                flushBuffer();
            } finally {
                closed = true;
                out.close();
            }
        }
    }
}
