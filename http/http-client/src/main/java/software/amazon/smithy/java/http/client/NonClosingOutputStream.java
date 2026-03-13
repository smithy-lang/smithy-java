/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.OutputStream;

/**
 * OutputStream wrapper that prevents closing the underlying stream.
 *
 * <p>Used for HTTP/1.1 request bodies where we don't want to close the socket when the request body is done.
 */
public final class NonClosingOutputStream extends OutputStream {
    private final OutputStream delegate;
    private boolean closed = false;

    public NonClosingOutputStream(OutputStream delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        delegate.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        if (!closed) {
            delegate.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            delegate.flush();
        }
    }
}
