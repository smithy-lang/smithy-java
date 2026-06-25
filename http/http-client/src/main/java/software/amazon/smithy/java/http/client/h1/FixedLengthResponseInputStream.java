/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class FixedLengthResponseInputStream extends InputStream {
    private final H1Exchange exchange;
    private final UnsyncBufferedInputStream delegate;
    private long remaining;
    private boolean closed;

    FixedLengthResponseInputStream(H1Exchange exchange, UnsyncBufferedInputStream delegate, long length) {
        this.exchange = exchange;
        this.delegate = delegate;
        this.remaining = length;
    }

    @Override
    public int read() throws IOException {
        if (closed) {
            return -1;
        }
        if (remaining == 0) {
            complete();
            return -1;
        }

        int b = delegate.read();
        if (b == -1) {
            throw prematureEof();
        }

        remaining--;
        if (remaining == 0) {
            complete();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (closed) {
            return -1;
        }
        if (len == 0) {
            return 0;
        }
        if (remaining == 0) {
            complete();
            return -1;
        }

        int n = delegate.read(b, off, (int) Math.min(len, remaining));
        if (n == -1) {
            throw prematureEof();
        }

        remaining -= n;
        if (remaining == 0) {
            complete();
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        if (closed || remaining == 0 || n <= 0) {
            return 0;
        }

        long skipped = delegate.skip(Math.min(n, remaining));
        remaining -= skipped;
        if (remaining == 0) {
            complete();
        }
        return skipped;
    }

    @Override
    public int available() throws IOException {
        if (closed || remaining == 0) {
            return 0;
        }
        return (int) Math.min(delegate.available(), remaining);
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        if (closed || remaining == 0) {
            return 0;
        }

        long transferred = 0;
        while (remaining > 0) {
            int buffered = delegate.buffered();
            if (buffered > 0) {
                int n = (int) Math.min(buffered, remaining);
                out.write(delegate.buffer(), delegate.position(), n);
                delegate.consume(n);
                remaining -= n;
                transferred += n;
                if (remaining == 0) {
                    complete();
                }
                continue;
            }

            int n = delegate.readDirect(delegate.buffer(), 0, (int) Math.min(delegate.buffer().length, remaining));
            if (n == -1) {
                throw prematureEof();
            }
            out.write(delegate.buffer(), 0, n);
            remaining -= n;
            transferred += n;
            if (remaining == 0) {
                complete();
            }
        }
        return transferred;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        try {
            if (remaining > 0) {
                delegate.discard(remaining);
                remaining = 0;
            }
        } finally {
            complete();
        }
    }

    private void complete() throws IOException {
        if (!closed) {
            closed = true;
            exchange.responseBodyClosed();
        }
    }

    private IOException prematureEof() {
        return new IOException("Premature EOF: expected " + remaining
                + " more bytes based on Content-Length");
    }
}
