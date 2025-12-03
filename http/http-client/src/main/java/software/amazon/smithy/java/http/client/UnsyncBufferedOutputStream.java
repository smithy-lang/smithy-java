/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A buffered output stream like {@link java.io.BufferedOutputStream}, but without synchronization.
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

        // If data won't fit in remaining buffer, flush first before copying to the buffer.
        if (len > buf.length - pos) {
            flushBuffer();
        }

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
    // we intentionally use the deprecated getBytes(int,int,byte[],int) since it's perfect for copying ascii.
    @SuppressWarnings("deprecation")
    public void writeAscii(String s) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        int len = s.length();
        if (len == 0) {
            return;
        }

        int stringPosition = 0;
        int bufLen = buf.length;

        // Work through the string bytes in chunks of bytes that can fit into the buffer.
        while (stringPosition < len) {
            int available = bufLen - pos;
            if (available == 0) {
                // We filled up the buffer, so flush and then continue to copy the next chunk
                flushBuffer();
                available = bufLen;
            }

            // Copy as many characters as will fit (or remaining string length, whichever is smaller)
            int toCopy = Math.min(available, len - stringPosition);
            s.getBytes(stringPosition, stringPosition + toCopy, buf, pos);
            pos += toCopy;
            stringPosition += toCopy;
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
