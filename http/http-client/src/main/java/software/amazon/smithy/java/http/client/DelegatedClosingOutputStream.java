/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OutputStream wrapper that runs a callback when the stream is closed rather than closing the delegate.
 *
 * <p>The close callback is invoked at most once, and can be safely closed from any thread.
 */
public final class DelegatedClosingOutputStream extends OutputStream {
    private final OutputStream out;
    private final CloseCallback closeCallback;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DelegatedClosingOutputStream(OutputStream delegate, CloseCallback closeCallback) {
        this.out = delegate;
        this.closeCallback = closeCallback;
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            closeCallback.close(out);
        }
    }

    public interface CloseCallback {
        void close(OutputStream delegate) throws IOException;
    }
}
