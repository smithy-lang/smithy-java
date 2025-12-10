/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Input stream for reading response body from DATA frames.
 *
 * <p>This implementation reads directly from the exchange's buffer,
 * which is filled by the connection's reader thread. This avoids
 * per-frame allocations and reduces copying.
 */
final class H2DataInputStream extends InputStream {

    private static final int TRANSFER_BUFFER_SIZE = 16384;
    private final H2Exchange exchange;
    private boolean closed = false;
    private byte[] singleBuff;

    H2DataInputStream(H2Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public int read() throws IOException {
        if (closed) {
            return -1;
        }
        if (singleBuff == null) {
            singleBuff = new byte[1];
        }
        int n = exchange.readFromBuffer(singleBuff, 0, 1);
        return n == 1 ? (singleBuff[0] & 0xFF) : -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed) {
            return -1;
        } else if (len == 0) {
            return 0;
        }

        return exchange.readFromBuffer(b, off, len);
    }

    @Override
    public int available() {
        if (closed) {
            return 0;
        }
        return exchange.availableInBuffer();
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        if (closed) {
            return 0;
        }

        // Borrow buffer from pool instead of allocating
        byte[] buffer = exchange.borrowBuffer(TRANSFER_BUFFER_SIZE);
        try {
            long transferred = 0;
            int read;
            while ((read = exchange.readFromBuffer(buffer, 0, buffer.length)) >= 0) {
                out.write(buffer, 0, read);
                transferred += read;
            }
            return transferred;
        } finally {
            exchange.returnBuffer(buffer);
        }
    }
}
