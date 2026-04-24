/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Blocking {@link InputStream} that pulls {@link ByteBuf} chunks from a queue fed by
 * a Netty inbound handler. Used for streaming HTTP response bodies to the caller VT.
 *
 * <p>Releases each {@link ByteBuf} after its bytes are read. Caller MUST close the stream
 * to release any remaining buffers on the queue.
 */
final class ResponseBodyInputStream extends InputStream {

    static final ByteBuf EOS_MARKER = Unpooled.EMPTY_BUFFER;

    private final LinkedBlockingQueue<ByteBuf> queue;
    private final AtomicReference<Throwable> error;
    private final Runnable onClose;
    private ByteBuf current;
    private boolean done;

    ResponseBodyInputStream(LinkedBlockingQueue<ByteBuf> queue, AtomicReference<Throwable> error, Runnable onClose) {
        this.queue = queue;
        this.error = error;
        this.onClose = onClose;
    }

    private boolean ensure() throws IOException {
        while (current == null || !current.isReadable()) {
            if (current != null) {
                current.release();
                current = null;
            }
            if (done) {
                return false;
            }
            ByteBuf next;
            try {
                next = queue.poll(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted reading response body", e);
            }
            if (next == null) {
                throw new IOException("Timed out reading response body");
            }
            if (next == EOS_MARKER) {
                done = true;
                Throwable t = error.get();
                if (t != null) {
                    throw new IOException("Response stream failed", t);
                }
                return false;
            }
            current = next;
        }
        return true;
    }

    @Override
    public int read() throws IOException {
        if (!ensure()) {
            return -1;
        }
        return current.readByte() & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (!ensure()) {
            return -1;
        }
        int n = Math.min(len, current.readableBytes());
        current.readBytes(b, off, n);
        return n;
    }

    @Override
    public int available() {
        return current == null ? 0 : current.readableBytes();
    }

    @Override
    public void close() {
        if (current != null) {
            current.release();
            current = null;
        }
        while (!done) {
            ByteBuf next = queue.poll();
            if (next == null) {
                break;
            }
            if (next == EOS_MARKER) {
                done = true;
            } else {
                next.release();
            }
        }
        done = true;
        if (onClose != null) {
            onClose.run();
        }
    }
}
