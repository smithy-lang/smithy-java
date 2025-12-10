/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * InputStream wrapper that runs a callback when the stream is closed rather than closing the provided delegate.
 *
 * <p>The close callback is invoked at most once, and can be safely closed from any thread.
 */
public final class DelegatedClosingInputStream extends FilterInputStream {
    private final CloseCallback closeCallback;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public DelegatedClosingInputStream(InputStream delegate, CloseCallback closeCallback) {
        super(delegate);
        this.closeCallback = closeCallback;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            closeCallback.close(in);
        }
    }

    public interface CloseCallback {
        void close(InputStream delegate) throws IOException;
    }
}
