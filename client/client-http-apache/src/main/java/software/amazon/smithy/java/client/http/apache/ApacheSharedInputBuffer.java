/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.apache;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.hc.core5.http.impl.nio.ExpandableBuffer;
import org.apache.hc.core5.http.nio.CapacityChannel;

final class ApacheSharedInputBuffer extends ExpandableBuffer {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final int initialBufferSize;
    private final AtomicInteger capacityIncrement = new AtomicInteger();
    private volatile CapacityChannel capacityChannel;
    private volatile boolean endStream;
    private volatile boolean aborted;

    ApacheSharedInputBuffer(int initialBufferSize) {
        super(initialBufferSize);
        this.initialBufferSize = initialBufferSize;
    }

    int fill(ByteBuffer src) {
        lock.lock();
        try {
            setInputMode();
            ensureAdjustedCapacity(buffer().position() + src.remaining());
            buffer().put(src);
            int remaining = buffer().remaining();
            condition.signalAll();
            return remaining;
        } finally {
            lock.unlock();
        }
    }

    void updateCapacity(CapacityChannel capacityChannel) throws IOException {
        lock.lock();
        try {
            this.capacityChannel = capacityChannel;
            setInputMode();
            if (buffer().position() == 0) {
                capacityChannel.update(initialBufferSize);
            }
        } finally {
            lock.unlock();
        }
    }

    int availableBytes() {
        lock.lock();
        try {
            return super.length();
        } finally {
            lock.unlock();
        }
    }

    int read() throws IOException {
        lock.lock();
        try {
            setOutputMode();
            awaitInput();
            ensureNotAborted();
            if (!buffer().hasRemaining() && endStream) {
                return -1;
            }
            int b = buffer().get() & 0xff;
            capacityIncrement.incrementAndGet();
            if (!buffer().hasRemaining()) {
                incrementCapacity();
            }
            return b;
        } finally {
            lock.unlock();
        }
    }

    int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        lock.lock();
        try {
            setOutputMode();
            awaitInput();
            ensureNotAborted();
            if (!buffer().hasRemaining() && endStream) {
                return -1;
            }
            int chunk = Math.min(buffer().remaining(), len);
            buffer().get(b, off, chunk);
            capacityIncrement.addAndGet(chunk);
            if (!buffer().hasRemaining()) {
                incrementCapacity();
            }
            return chunk;
        } finally {
            lock.unlock();
        }
    }

    void markEndStream() {
        lock.lock();
        try {
            endStream = true;
            capacityChannel = null;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void abort() {
        lock.lock();
        try {
            endStream = true;
            aborted = true;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void incrementCapacity() throws IOException {
        if (capacityChannel != null) {
            int increment = capacityIncrement.getAndSet(0);
            if (increment > 0) {
                capacityChannel.update(increment);
            }
        }
    }

    private void awaitInput() throws InterruptedIOException {
        if (!buffer().hasRemaining()) {
            setInputMode();
            while (buffer().position() == 0 && !endStream && !aborted) {
                try {
                    condition.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException(ex.getMessage());
                }
            }
            setOutputMode();
        }
    }

    private void ensureNotAborted() throws InterruptedIOException {
        if (aborted) {
            throw new InterruptedIOException("Operation aborted");
        }
    }
}
