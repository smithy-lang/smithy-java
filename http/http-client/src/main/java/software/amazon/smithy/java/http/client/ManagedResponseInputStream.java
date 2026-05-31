/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * InputStream wrapper that preserves optimized bulk operations and releases response lifecycle on EOF or close.
 */
final class ManagedResponseInputStream extends InputStream {
    private final InputStream inner;
    private final Runnable onClose;

    ManagedResponseInputStream(InputStream inner, Runnable onClose) {
        this.inner = inner;
        this.onClose = onClose;
    }

    @Override
    public int read() throws IOException {
        int b = inner.read();
        if (b == -1) {
            onClose.run();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = inner.read(b, off, len);
        if (n == -1) {
            onClose.run();
        }
        return n;
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        try {
            return inner.readAllBytes();
        } finally {
            onClose.run();
        }
    }

    @Override
    public byte[] readNBytes(int len) throws IOException {
        byte[] bytes = inner.readNBytes(len);
        if (bytes.length < len) {
            onClose.run();
        }
        return bytes;
    }

    @Override
    public int readNBytes(byte[] b, int off, int len) throws IOException {
        int n = inner.readNBytes(b, off, len);
        if (n < len) {
            onClose.run();
        }
        return n;
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        try {
            return inner.transferTo(out);
        } finally {
            onClose.run();
        }
    }

    @Override
    public long skip(long n) throws IOException {
        return inner.skip(n);
    }

    @Override
    public void skipNBytes(long n) throws IOException {
        try {
            inner.skipNBytes(n);
        } catch (IOException e) {
            onClose.run();
            throw e;
        }
    }

    @Override
    public int available() throws IOException {
        return inner.available();
    }

    @Override
    public boolean markSupported() {
        return inner.markSupported();
    }

    @Override
    public synchronized void mark(int readlimit) {
        inner.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        inner.reset();
    }

    @Override
    public void close() throws IOException {
        try {
            inner.close();
        } finally {
            onClose.run();
        }
    }
}
