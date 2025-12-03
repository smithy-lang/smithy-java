/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;

/**
 * InputStream that reads exactly a specified number of bytes.
 *
 * <p>Used for HTTP responses with Content-Length. Note that this does not close the delegate InputStream on close.
 */
public final class BoundedInputStream extends InputStream {
    private final InputStream delegate;
    private long remaining;
    private boolean closed;

    public BoundedInputStream(InputStream delegate, long length) {
        this.delegate = delegate;
        this.remaining = length;
    }

    @Override
    public int read() throws IOException {
        if (closed || remaining <= 0) {
            return -1;
        }

        int b = delegate.read();
        if (b != -1) {
            remaining--;
        } else if (remaining > 0) {
            // Server closed connection before sending all Content-Length bytes
            throw new IOException("Premature EOF: expected " + remaining
                    + " more bytes based on Content-Length");
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed || remaining <= 0) {
            return -1;
        }

        int toRead = (int) Math.min(len, remaining);
        int n = delegate.read(b, off, toRead);

        if (n > 0) {
            remaining -= n;
        } else if (n == -1 && remaining > 0) {
            // Server closed connection before sending all Content-Length bytes
            throw new IOException("Premature EOF: expected " + remaining
                    + " more bytes based on Content-Length");
        }

        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        if (closed || remaining <= 0) {
            return 0;
        }

        long toSkip = Math.min(n, remaining);
        long skipped = delegate.skip(toSkip);

        if (skipped > 0) {
            remaining -= skipped;
        }

        return skipped;
    }

    @Override
    public int available() throws IOException {
        if (closed || remaining <= 0) {
            return 0;
        }

        int available = delegate.available();
        return (int) Math.min(available, remaining);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        // Drain remaining bytes so connection can be reused
        if (remaining > 0) {
            byte[] drain = new byte[(int) Math.min(8192, remaining)];
            while (remaining > 0) {
                int toRead = (int) Math.min(drain.length, remaining);
                int n = delegate.read(drain, 0, toRead);
                if (n == -1) {
                    throw new IOException("Premature EOF while draining response body: expected "
                            + remaining + " more bytes based on Content-Length");
                }
                remaining -= n;
            }
        }
        // Don't close delegate - connection may be reused
    }
}
