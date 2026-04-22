/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A lock-free ByteBuffer allocator with optional pooling to reduce GC pressure.
 *
 * <p>Pools heap-backed ByteBuffers. Each connection should have its own allocator.
 *
 * <p>Implementation: bounded LIFO stack with AtomicInteger top pointer.
 * Best-effort under contention — may drop buffers rather than block.
 */
final class ByteAllocator {

    private final AtomicReferenceArray<ByteBuffer> stack;
    private final AtomicInteger top = new AtomicInteger(0);

    private final int capacity;
    private final int maxBufferSize;
    private final int maxPoolableSize;
    private final int defaultBufferSize;
    private H2ConnectionStats stats;

    void setStats(H2ConnectionStats stats) {
        this.stats = stats;
    }

    /**
     * @param maxPoolCount maximum buffers to keep in pool
     * @param maxBufferSize hard limit on buffer size
     * @param maxPoolableSize buffers larger than this are not pooled
     * @param defaultBufferSize default size for new buffers when pool is empty
     */
    public ByteAllocator(int maxPoolCount, int maxBufferSize, int maxPoolableSize, int defaultBufferSize) {
        if (maxPoolCount <= 0) {
            throw new IllegalArgumentException("maxPoolCount must be > 0");
        }
        if (defaultBufferSize <= 0) {
            throw new IllegalArgumentException("defaultBufferSize must be > 0");
        }
        if (maxPoolableSize <= 0 || maxPoolableSize > maxBufferSize) {
            throw new IllegalArgumentException("maxPoolableSize must be > 0 and <= maxBufferSize");
        }
        this.capacity = maxPoolCount;
        this.maxBufferSize = maxBufferSize;
        this.maxPoolableSize = maxPoolableSize;
        this.defaultBufferSize = defaultBufferSize;
        this.stack = new AtomicReferenceArray<>(maxPoolCount);
    }

    /**
     * Borrow a ByteBuffer from the pool, or allocate a new one.
     *
     * <p>Returned buffer is in write mode (position=0, limit=capacity).
     * The capacity may be larger than minSize.
     *
     * @param minSize minimum buffer capacity needed
     * @return a ByteBuffer with at least minSize capacity, cleared and ready for writing
     */
    public ByteBuffer borrow(int minSize) {
        if (minSize <= 0) {
            throw new IllegalArgumentException("minSize must be > 0");
        }
        if (minSize > maxBufferSize) {
            throw new IllegalArgumentException(
                    "Requested buffer size " + minSize + " exceeds maximum " + maxBufferSize);
        }

        if (minSize <= maxPoolableSize) {
            while (true) {
                int currentTop = top.get();
                if (currentTop == 0) {
                    break;
                }
                int newTop = currentTop - 1;
                if (top.compareAndSet(currentTop, newTop)) {
                    ByteBuffer buffer = stack.getAndSet(newTop, null);
                    if (buffer != null && buffer.capacity() >= minSize) {
                        buffer.clear();
                        if (stats != null) {
                            stats.buffersBorrowed.increment();
                            stats.buffersReused.increment();
                        }
                        return buffer;
                    }
                    break;
                }
            }
        }

        int size = Math.max(minSize, defaultBufferSize);
        if (size > maxBufferSize) {
            size = maxBufferSize;
        }
        if (stats != null) {
            stats.buffersBorrowed.increment();
            stats.buffersAllocated.increment();
        }
        return ByteBuffer.allocate(size);
    }

    /**
     * Return a ByteBuffer to the pool for reuse.
     *
     * @param buffer the buffer to return (may be null)
     */
    public void release(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        if (buffer.capacity() > maxPoolableSize) {
            if (stats != null) {
                stats.buffersDropped.increment();
            }
            return;
        }

        while (true) {
            int currentTop = top.get();
            if (currentTop >= capacity) {
                if (stats != null) {
                    stats.buffersDropped.increment();
                }
                return;
            }
            if (top.compareAndSet(currentTop, currentTop + 1)) {
                stack.set(currentTop, buffer);
                return;
            }
        }
    }

    public int size() {
        return top.get();
    }

    public void clear() {
        int n = top.getAndSet(0);
        int limit = Math.min(n, capacity);
        for (int i = 0; i < limit; i++) {
            stack.set(i, null);
        }
    }
}
