/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class CloseReleasingResponseInputStream extends InputStream {
    private final H1Exchange exchange;
    private final InputStream delegate;
    private boolean closed;

    CloseReleasingResponseInputStream(H1Exchange exchange, InputStream delegate) {
        this.exchange = exchange;
        this.delegate = delegate;
    }

    @Override
    public int read() throws IOException {
        if (closed) {
            return -1;
        }
        int result = delegate.read();
        if (result == -1) {
            close();
        }
        return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed) {
            return -1;
        }
        int result = delegate.read(b, off, len);
        if (result == -1) {
            close();
        }
        return result;
    }

    @Override
    public long skip(long n) throws IOException {
        if (closed) {
            return 0;
        }
        return delegate.skip(n);
    }

    @Override
    public int available() throws IOException {
        return closed ? 0 : delegate.available();
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        if (closed) {
            return 0;
        }
        try {
            return delegate.transferTo(out);
        } finally {
            close();
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            exchange.responseBodyClosed();
        }
    }
}
