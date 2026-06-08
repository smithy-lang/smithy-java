/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * InputStream wrapper that preserves optimized bulk operations and releases response lifecycle on EOF or close.
 */
final class ManagedResponseInputStream extends InputStream {
    // Cap for the pre-sized readAllBytes path: a (possibly untrusted) Content-Length above this
    // falls back to the JDK grow-as-you-go path rather than pre-allocating a huge buffer up front.
    private static final int MAX_PRESIZED_LEN = 64 * 1024 * 1024;

    private final InputStream inner;
    private final Runnable onClose;
    private final Consumer<Throwable> onError;
    private long remaining;

    ManagedResponseInputStream(InputStream inner, long contentLength, Runnable onClose) {
        this(inner, contentLength, onClose, ignored -> {});
    }

    ManagedResponseInputStream(InputStream inner, long contentLength, Runnable onClose, Consumer<Throwable> onError) {
        this.inner = inner;
        this.onClose = onClose;
        this.onError = onError;
        this.remaining = contentLength >= 0 ? contentLength : -1;
    }

    /** Run the error terminal for a read that threw, then rethrow. */
    private <T extends Throwable> T failed(T e) {
        onError.accept(e);
        return e;
    }

    @Override
    public int read() throws IOException {
        int b;
        try {
            b = inner.read();
        } catch (IOException e) {
            throw failed(e);
        }
        if (b == -1) {
            onClose.run();
        } else {
            bytesRead(1);
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n;
        try {
            n = inner.read(b, off, len);
        } catch (IOException e) {
            throw failed(e);
        }
        if (n == -1) {
            onClose.run();
        } else {
            bytesRead(n);
        }
        return n;
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        byte[] result;
        try {
            long len = remaining;
            result = (len >= 0 && len <= MAX_PRESIZED_LEN) ? readKnownLength((int) len) : inner.readAllBytes();
        } catch (IOException e) {
            // A failed (e.g. interrupted) read must NOT fire the success terminal — that would report a
            // clean completion (onRequestEnd(null)) for a torn read and pool a broken connection.
            throw failed(e);
        }
        onClose.run();
        return result;
    }

    // Caller (readAllBytes) routes a thrown read through failed(); no terminal here.
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
        byte[] bytes;
        try {
            bytes = inner.readNBytes(len);
        } catch (IOException e) {
            throw failed(e);
        }
        if (bytes.length < len) {
            onClose.run();
        }
        bytesRead(bytes.length);
        return bytes;
    }

    @Override
    public int readNBytes(byte[] b, int off, int len) throws IOException {
        int n;
        try {
            n = inner.readNBytes(b, off, len);
        } catch (IOException e) {
            throw failed(e);
        }
        if (n < len) {
            onClose.run();
        }
        bytesRead(n);
        return n;
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        long n;
        try {
            n = inner.transferTo(out);
        } catch (IOException e) {
            throw failed(e);
        }
        onClose.run();
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped;
        try {
            skipped = inner.skip(n);
        } catch (IOException e) {
            throw failed(e);
        }
        bytesRead(skipped);
        return skipped;
    }

    @Override
    public void skipNBytes(long n) throws IOException {
        try {
            inner.skipNBytes(n);
            bytesRead(n);
        } catch (IOException e) {
            throw failed(e);
        }
    }

    @Override
    public int available() throws IOException {
        try {
            return inner.available();
        } catch (IOException e) {
            throw failed(e);
        }
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
        try {
            inner.reset();
        } catch (IOException e) {
            throw failed(e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            inner.close();
        } catch (IOException e) {
            // A close that errors leaves the connection suspect — route to the error terminal (evict)
            // rather than reporting a clean close. One-shot latches make this a no-op if the body was
            // already fully read and a terminal ran.
            throw failed(e);
        }
        onClose.run();
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
