/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Output stream for writing request body as DATA frames.
 *
 * <p>Uses pooled ByteBuffers from the muxer's ByteAllocator to reduce GC pressure.
 */
final class H2DataOutputStream extends OutputStream {
    private final H2Exchange exchange;
    private final H2Muxer muxer;
    private ByteBuffer buffer;
    private boolean closed = false;

    H2DataOutputStream(H2Exchange exchange, H2Muxer muxer, int bufferSize) {
        this.exchange = exchange;
        this.muxer = muxer;
        this.buffer = bufferSize > 0 ? muxer.borrowBuffer(bufferSize) : null;
    }

    @Override
    public void write(int b) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        } else if (buffer == null) {
            throw new IOException("Cannot write body: END_STREAM already sent with headers");
        }

        buffer.put((byte) b);
        if (!buffer.hasRemaining()) {
            flush();
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (closed) {
            throw new IOException("Stream closed");
        } else if (len == 0) {
            return;
        } else if (buffer == null) {
            throw new IOException("Cannot write body: END_STREAM already sent with headers");
        }

        // Fast path: large write — flush buffer, then write directly
        if (len >= buffer.capacity()) {
            flush();
            exchange.writeData(b, off, len, false);
            return;
        }

        while (len > 0) {
            int space = buffer.remaining();
            int toCopy = Math.min(space, len);
            buffer.put(b, off, toCopy);
            off += toCopy;
            len -= toCopy;
            if (!buffer.hasRemaining()) {
                flush();
            }
        }
    }

    @Override
    public void flush() throws IOException {
        if (buffer != null && buffer.position() > 0) {
            buffer.flip();
            exchange.writeData(buffer, false);
            buffer.clear();
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        try {
            if (buffer != null && buffer.position() > 0) {
                buffer.flip();
                exchange.writeData(buffer, true);
            } else {
                exchange.sendEndStream();
            }
        } finally {
            if (buffer != null) {
                buffer.clear();
                muxer.returnBuffer(buffer);
                buffer = null;
            }
        }
    }
}
