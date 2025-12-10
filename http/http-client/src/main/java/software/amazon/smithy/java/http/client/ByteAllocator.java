/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A lock-free byte array allocator with optional pooling to reduce GC pressure.
 *
 * <p>Designed for use by a single HTTP/2 connection. Each connection should have
 * its own allocator to minimize contention.
 *
 * <p>Implementation details:
 * <ul>
 *   <li>Bounded, array-backed LIFO stack (no queue nodes).</li>
 *   <li>Single AtomicInteger "top" index, used as the size and stack pointer.</li>
 *   <li>Best-effort: under races we may drop or miss a buffer instead of pooling
 *       it, which is fine for a GC-reducing pool.</li>
 * </ul>
 *
 * <p>The allocator has a configurable maximum count and poolable size. Buffers larger
 * than {@code maxPoolableSize} are never pooled. Requests larger than
 * {@code maxBufferSize} are rejected.
 *
 * <p>Thread-safe: multiple threads can borrow and release buffers concurrently.
 */
public final class ByteAllocator {

    // LIFO stack of pooled buffers: [0, top) are valid entries.
    private final AtomicReferenceArray<byte[]> stack;
    private final AtomicInteger top = new AtomicInteger(0);

    private final int capacity;
    private final int maxBufferSize;
    private final int maxPoolableSize;
    private final int defaultBufferSize;

    /**
     * Create a byte allocator with pooling.
     *
     * @param maxPoolCount maximum number of buffers to keep in pool
     * @param maxBufferSize hard limit on buffer size (throws if exceeded)
     * @param maxPoolableSize buffers larger than this are not pooled (but still allowed)
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
     * Borrow a buffer from the pool, or allocate a new one.
     *
     * <p>If a pooled buffer is available and large enough, it's returned.
     * Otherwise, a new buffer is allocated with at least {@code minSize} bytes.
     *
     * <p><b>Important:</b> The returned buffer may be larger than {@code minSize}.
     * Callers must track the actual data length separately and not rely on
     * {@code buffer.length} to determine data boundaries.
     *
     * @param minSize minimum buffer size needed
     * @return a buffer of at least minSize bytes (may be larger)
     * @throws IllegalArgumentException if minSize exceeds maxBufferSize
     */
    public byte[] borrow(int minSize) {
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
                    // Pool empty.
                    break;
                }

                int newTop = currentTop - 1;
                if (top.compareAndSet(currentTop, newTop)) {
                    // getAndSet is a single atomic op: we both read the slot and clear it.
                    byte[] buffer = stack.getAndSet(newTop, null);
                    if (buffer != null && buffer.length >= minSize) {
                        return buffer;
                    }
                    // Null (race) or too small: treat as a miss and fall through to allocation.
                    break;
                }
                // Lost the race, retry.
            }
        }

        int size = Math.max(minSize, defaultBufferSize);
        if (size > maxBufferSize) {
            size = maxBufferSize;
        }
        return new byte[size];
    }

    /**
     * Return a buffer to the pool for reuse.
     *
     * <p>If the pool is full or the buffer is larger than maxPoolableSize, it's discarded.
     *
     * @param buffer the buffer to return (may be null, which is ignored)
     */
    public void release(byte[] buffer) {
        if (buffer == null) {
            return;
        }
        if (buffer.length > maxPoolableSize) {
            // Don't pool very large buffers; let GC handle them.
            return;
        }

        while (true) {
            int currentTop = top.get();
            if (currentTop >= capacity) {
                // Pool is full; drop the buffer.
                return;
            }

            if (top.compareAndSet(currentTop, currentTop + 1)) {
                // We now "own" this slot; publish buffer with a volatile write.
                stack.set(currentTop, buffer);
                return;
            }
            // Lost the race, retry (or eventually see pool as full and drop).
        }
    }

    /**
     * Get the current number of buffers in the pool.
     *
     * @return current pool size (approximate under contention)
     */
    public int size() {
        return top.get();
    }

    /**
     * Clear all buffers from the pool.
     *
     * <p>Best-effort, not strictly atomic wrt concurrent borrows/releases,
     * but good enough for typical "connection shutdown" usage.
     */
    public void clear() {
        int n = top.getAndSet(0);
        int limit = Math.min(n, capacity);
        for (int i = 0; i < limit; i++) {
            stack.set(i, null);
        }
    }
}
