/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream for writing request body as DATA frames.
 */
final class H2DataOutputStream extends OutputStream {
    private final H2Exchange exchange;
    private final byte[] buffer;
    private int pos = 0;
    private boolean closed = false;

    H2DataOutputStream(H2Exchange exchange, int bufferSize) {
        this.exchange = exchange;
        this.buffer = new byte[Math.max(bufferSize, 1)];
    }

    @Override
    public void write(int b) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        buffer[pos++] = (byte) b;
        if (pos >= buffer.length) {
            flush();
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (closed)
            throw new IOException("Stream closed");
        while (len > 0) {
            int space = buffer.length - pos;
            int toCopy = Math.min(space, len);
            System.arraycopy(b, off, buffer, pos, toCopy);
            pos += toCopy;
            off += toCopy;
            len -= toCopy;
            if (pos >= buffer.length) {
                flush();
            }
        }
    }

    @Override
    public void flush() throws IOException {
        if (pos > 0) {
            exchange.writeData(buffer, 0, pos, false);
            pos = 0;
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        // Flush remaining data with END_STREAM
        if (pos > 0) {
            exchange.writeData(buffer, 0, pos, true);
            pos = 0;
        } else {
            exchange.sendEndStream();
        }
    }
}
