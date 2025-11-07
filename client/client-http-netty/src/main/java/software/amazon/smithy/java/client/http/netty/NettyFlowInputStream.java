/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * An {@link InputStream} implementation backed up by a {@link NettyDataStream}.
 */
class NettyFlowInputStream extends InputStream {
    private final BlockingQueue<ByteBuffer> queue = new LinkedBlockingQueue<>();
    private ByteBuffer currentBuffer;
    private volatile boolean completed = false;
    private volatile Throwable error = null;
    private Flow.Subscription subscription;

    NettyFlowInputStream(NettyDataStream nettyDataStream) {
        nettyDataStream.subscribe(new ReadSubscriber());
    }

    @Override
    public int read() throws IOException {
        if (currentBuffer == null || !currentBuffer.hasRemaining()) {
            if (!fetchNextBuffer()) {
                return -1; // End of stream
            }
        }
        return currentBuffer.get() & 0xFF;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        Objects.requireNonNull(buf, "b");
        if (off < 0 || len < 0 || len > buf.length - off) {
            throw new IndexOutOfBoundsException("off < 0 || len < 0 || len > b.length - off");
        }
        if (len == 0) {
            return 0;
        }

        if (currentBuffer == null || !currentBuffer.hasRemaining()) {
            if (!fetchNextBuffer()) {
                // End of stream
                return -1;
            }
        }

        var bytesToRead = Math.min(len, currentBuffer.remaining());
        currentBuffer.get(buf, off, bytesToRead);
        return bytesToRead;
    }

    @Override
    public void close() throws IOException {
        if (subscription != null && !completed) {
            subscription.cancel();
        }
        super.close();
    }

    private boolean fetchNextBuffer() throws IOException {
        if (error != null) {
            throw new IOException("Subscription is in error state", error);
        }

        try {
            while (true) {
                currentBuffer = queue.poll(100, TimeUnit.MILLISECONDS);
                if (currentBuffer != null) {
                    return true;
                }

                // Check if we're done
                if (completed && queue.isEmpty()) {
                    return false;
                }

                // Check for errors again
                if (error != null) {
                    throw new IOException("Subscription entered error state", error);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading", e);
        }
    }

    class ReadSubscriber implements Flow.Subscriber<ByteBuffer> {

        @Override
        public void onSubscribe(Flow.Subscription s) {
            subscription = s;
            s.request(1); // Request first item
        }

        @Override
        public void onNext(ByteBuffer item) {
            try {
                queue.put(item);
                // Request next item
                subscription.request(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                onError(e);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onComplete() {
            completed = true;
        }
    }
}
