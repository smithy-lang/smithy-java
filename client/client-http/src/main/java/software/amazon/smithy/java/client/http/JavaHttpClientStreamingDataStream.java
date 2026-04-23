/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * A streaming {@link DataStream} that receives {@link ByteBuffer} chunks from a
 * {@link java.net.http.HttpResponse.BodySubscriber} and delivers their bytes to a
 * caller thread via {@link InputStream}.
 *
 * <p>Uses a single {@link ReentrantLock} + {@link Condition} plus an internal
 * {@link ArrayDeque} to coordinate producer and consumer. Compared to a plain
 * {@link java.util.concurrent.LinkedBlockingQueue}, this:
 * <ul>
 *   <li>Takes ONE lock per {@code onNext(List)} batch (instead of one per buffer).</li>
 *   <li>Lets the consumer drain multiple buffers from the deque under a single lock
 *       acquisition rather than re-locking for each.</li>
 *   <li>Avoids LBQ's per-node allocation; ArrayDeque uses a small backing array.</li>
 * </ul>
 *
 * <p>Threading model: producer is the JDK HttpClient's subscriber callback thread
 * (from the {@code Executor} configured on the {@code HttpClient}). Consumer is the
 * caller thread that calls {@link InputStream#read}. Either may invoke {@link #close}
 * concurrently.
 */
final class JavaHttpClientStreamingDataStream implements DataStream {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition ready = lock.newCondition();
    // Deque of ByteBuffer chunks awaiting consumption. Guarded by lock.
    private final Deque<ByteBuffer> chunks = new ArrayDeque<>();

    private final String contentType;
    private final long contentLength;

    // Producer state (all guarded by lock except where noted):
    private Throwable failure;
    private boolean endOfStream;

    // Subscription is written once from onSubscribe, read from close().
    private volatile Flow.Subscription subscription;

    // Consumer state (set only from consumer thread):
    private volatile boolean consumed;
    private volatile boolean closed;

    JavaHttpClientStreamingDataStream(String contentType, long contentLength) {
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public boolean isReplayable() {
        return false;
    }

    @Override
    public boolean isAvailable() {
        return !consumed;
    }

    @Override
    public InputStream asInputStream() {
        if (consumed) {
            throw new IllegalStateException("Response body is not replayable and has already been consumed");
        }
        consumed = true;
        return new JavaHttpClientStreamingInputStream(this);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        var current = subscription;
        if (current != null) {
            current.cancel();
        }
        lock.lock();
        try {
            endOfStream = true;
            chunks.clear();
            ready.signal();
        } finally {
            lock.unlock();
        }
    }

    // ---- Producer API (called from subscriber callbacks) ----

    void setSubscription(Flow.Subscription subscription) {
        this.subscription = subscription;
        if (closed) {
            subscription.cancel();
        }
    }

    /**
     * Append a batch of buffers in one lock acquisition.
     */
    void enqueueBatch(List<ByteBuffer> batch) {
        lock.lock();
        try {
            if (endOfStream) {
                return;
            }
            for (ByteBuffer buffer : batch) {
                if (buffer.hasRemaining()) {
                    chunks.add(buffer.asReadOnlyBuffer());
                }
            }
            ready.signal();
        } finally {
            lock.unlock();
        }
    }

    void fail(Throwable throwable) {
        lock.lock();
        try {
            if (endOfStream) {
                return;
            }
            failure = throwable;
            endOfStream = true;
            ready.signal();
        } finally {
            lock.unlock();
        }
    }

    void complete() {
        lock.lock();
        try {
            if (endOfStream) {
                return;
            }
            endOfStream = true;
            ready.signal();
        } finally {
            lock.unlock();
        }
    }

    // ---- Consumer API (called from InputStream.read) ----

    /**
     * Return a non-blocking snapshot of the number of bytes currently buffered and ready to be
     * consumed without blocking. Used to back {@link InputStream#available()}.
     */
    int availableBytes() {
        lock.lock();
        try {
            long sum = 0;
            for (ByteBuffer chunk : chunks) {
                sum += chunk.remaining();
                if (sum >= Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
            return (int) sum;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Advance the read position by up to {@code n} bytes, blocking once for data like
     * {@link #readInto} if the deque is empty. Returns the number of bytes actually skipped,
     * which may be less than {@code n}, including 0 at end-of-stream.
     */
    long skipBytes(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }
        lock.lock();
        try {
            while (chunks.isEmpty() && !endOfStream) {
                try {
                    ready.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for response body data", e);
                }
            }
            long skipped = 0;
            while (skipped < n) {
                ByteBuffer head = chunks.peek();
                if (head == null) {
                    break;
                }
                int remaining = head.remaining();
                if (remaining == 0) {
                    chunks.poll();
                    continue;
                }
                long toSkip = Math.min((long) remaining, n - skipped);
                head.position(head.position() + (int) toSkip);
                skipped += toSkip;
                if (!head.hasRemaining()) {
                    chunks.poll();
                }
            }
            if (skipped == 0 && failure != null) {
                throw new IOException("Failed receiving response body", failure);
            }
            return skipped;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sentinel returned by {@link #writeNextChunkTo} to request that the caller allocate a
     * scratch buffer. Using a sentinel avoids allocating a scratch buffer for streams whose
     * chunks are array-backed and can be written directly.
     */
    static final int SCRATCH_NEEDED = -2;

    /**
     * Write the next queued chunk (if any) to {@code out}. Blocks until a chunk is available,
     * the stream ends, or an error is signalled. Writes are performed <em>without</em> the
     * internal lock held so a slow {@link OutputStream} does not block the producer.
     *
     * <p>If the next chunk is array-backed ({@link ByteBuffer#hasArray()}), its bytes are
     * written directly from the backing array (zero intermediate copy). Otherwise, if
     * {@code scratch} is non-null, the chunk is drained into {@code scratch} before writing;
     * if {@code scratch} is null, {@link #SCRATCH_NEEDED} is returned so the caller can
     * allocate one on demand. This lets callers avoid a scratch allocation entirely when
     * every chunk is array-backed.
     *
     * @return bytes written from the popped chunk, {@code -1} at end-of-stream, or
     *         {@link #SCRATCH_NEEDED} when a scratch buffer is required and none was supplied.
     * @throws IOException if the producer signalled a failure, or {@code out.write} throws.
     */
    int writeNextChunkTo(OutputStream out, byte[] scratch) throws IOException {
        ByteBuffer chunk;
        lock.lock();
        try {
            while (true) {
                // Skip drained chunks to find the first with bytes.
                ByteBuffer head = chunks.peek();
                while (head != null && !head.hasRemaining()) {
                    chunks.poll();
                    head = chunks.peek();
                }
                if (head != null) {
                    // If not array-backed and caller has no scratch buffer, punt back to them
                    // without dequeuing so they can allocate once and retry.
                    if (!head.hasArray() && scratch == null) {
                        return SCRATCH_NEEDED;
                    }
                    chunk = chunks.poll();
                    break;
                }
                if (endOfStream) {
                    if (failure != null) {
                        throw new IOException("Failed receiving response body", failure);
                    }
                    return -1;
                }
                try {
                    ready.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for response body data", e);
                }
            }
        } finally {
            lock.unlock();
        }

        // Write outside the lock so a slow OutputStream does not stall the producer.
        int written;
        if (chunk.hasArray()) {
            int position = chunk.position();
            int remaining = chunk.remaining();
            out.write(chunk.array(), chunk.arrayOffset() + position, remaining);
            chunk.position(position + remaining);
            written = remaining;
        } else {
            int remaining = chunk.remaining();
            int toWrite = Math.min(remaining, scratch.length);
            chunk.get(scratch, 0, toWrite);
            out.write(scratch, 0, toWrite);
            written = toWrite;
            // If the chunk still has bytes (larger than scratch), push it back for the next call.
            if (chunk.hasRemaining()) {
                lock.lock();
                try {
                    chunks.addFirst(chunk);
                } finally {
                    lock.unlock();
                }
            }
        }
        return written;
    }

    /**
     * Read up to {@code len} bytes into {@code b}, blocking until data is available,
     * the stream ends, or an error is signalled. Drains as many queued chunks as fit
     * in {@code b} in a single lock acquisition.
     *
     * @return number of bytes read, or -1 on end-of-stream
     */
    int readInto(byte[] b, int off, int len) throws IOException {
        lock.lock();
        try {
            // Wait for something to consume.
            while (chunks.isEmpty() && !endOfStream) {
                try {
                    ready.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for response body data", e);
                }
            }

            // Drain up to len bytes from the head of the chunk deque.
            int total = 0;
            while (total < len) {
                ByteBuffer head = chunks.peek();
                if (head == null) {
                    break;
                }
                int remaining = head.remaining();
                if (remaining == 0) {
                    chunks.poll();
                    continue;
                }
                int toCopy = Math.min(remaining, len - total);
                head.get(b, off + total, toCopy);
                total += toCopy;
                if (!head.hasRemaining()) {
                    chunks.poll();
                }
            }

            if (total > 0) {
                return total;
            }
            // total == 0 implies chunks was empty AND endOfStream is true.
            if (failure != null) {
                throw new IOException("Failed receiving response body", failure);
            }
            return -1;
        } finally {
            lock.unlock();
        }
    }
}
