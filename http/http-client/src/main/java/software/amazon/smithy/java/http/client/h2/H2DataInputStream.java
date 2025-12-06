/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.io.InputStream;

/**
 * Input stream for reading response body from DATA frames.
 *
 * <p>This implementation reads directly from the exchange's buffer,
 * which is filled by the connection's reader thread. This avoids
 * per-frame allocations and reduces copying.
 */
final class H2DataInputStream extends InputStream {
    private final H2Exchange exchange;
    private boolean closed = false;

    H2DataInputStream(H2Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public int read() throws IOException {
        if (closed) {
            return -1;
        }
        byte[] buf = new byte[1];
        int n = exchange.readFromBuffer(buf, 0, 1);
        return n == 1 ? (buf[0] & 0xFF) : -1;
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
}
