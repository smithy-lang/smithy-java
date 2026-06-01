/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * InputStream wrapper that preserves optimized bulk operations and releases response lifecycle on EOF or close.
 */
final class ManagedResponseInputStream extends InputStream {
    // Cap for the pre-sized readAllBytes path: a (possibly untrusted) Content-Length above this
    // falls back to the JDK grow-as-you-go path rather than pre-allocating a huge buffer up front.
    private static final int MAX_PRESIZED_LEN = 64 * 1024 * 1024;

    private final InputStream inner;
    private final Runnable onClose;
    private long remaining;

    ManagedResponseInputStream(InputStream inner, long contentLength, Runnable onClose) {
        this.inner = inner;
        this.onClose = onClose;
        this.remaining = contentLength >= 0 ? contentLength : -1;
    }

    @Override
    public int read() throws IOException {
        int b = inner.read();
        if (b == -1) {
            onClose.run();
        } else {
            bytesRead(1);
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = inner.read(b, off, len);
        if (n == -1) {
            onClose.run();
        } else {
            bytesRead(n);
        }
        return n;
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        try {
            long len = remaining;
            if (len >= 0 && len <= MAX_PRESIZED_LEN) {
                return readKnownLength((int) len);
            }
            return inner.readAllBytes();
        } finally {
            onClose.run();
        }
    }

    private byte[] readKnownLength(int len) throws IOException {
        byte[] buf = new byte[len];
        int pos = 0;
        while (pos < len) {
            int n = inner.read(buf, pos, len - pos);
            if (n < 0) {
                return Arrays.copyOf(buf, pos); // stream ended early; trim to what we read
            }
            pos += n;
        }
        return buf;
    }

    @Override
    public byte[] readNBytes(int len) throws IOException {
        byte[] bytes = inner.readNBytes(len);
        if (bytes.length < len) {
            onClose.run();
        }
        bytesRead(bytes.length);
        return bytes;
    }

    @Override
    public int readNBytes(byte[] b, int off, int len) throws IOException {
        int n = inner.readNBytes(b, off, len);
        if (n < len) {
            onClose.run();
        }
        bytesRead(n);
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
        long skipped = inner.skip(n);
        bytesRead(skipped);
        return skipped;
    }

    @Override
    public void skipNBytes(long n) throws IOException {
        try {
            inner.skipNBytes(n);
            bytesRead(n);
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

    private void bytesRead(long n) {
        if (remaining < 0 || n <= 0) {
            return;
        }
        remaining -= n;
        if (remaining <= 0) {
            onClose.run();
        }
    }
}
