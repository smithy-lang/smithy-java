/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A lock-free ByteBuffer allocator with optional pooling to reduce GC pressure.
 *
 * <p>Pools direct ByteBuffers. Each connection should have its own allocator.
 *
 * <p>The pool is split into size classes rather than one mixed stack. This keeps
 * frame-sized upload and download buffers reusing other frame-sized buffers instead
 * of getting displaced by arbitrary historical sizes.
 */
final class ByteAllocator {

    private final SizeClass[] classes;
    private final int maxBufferSize;
    private final int maxPoolableSize;
    private final int defaultBufferSize;
    private H2ConnectionStats stats;

    private static final class SizeClass {
        final int bufferSize;
        final AtomicReferenceArray<ByteBuffer> stack;
        final AtomicInteger top = new AtomicInteger(0);

        SizeClass(int bufferSize, int capacity) {
            this.bufferSize = bufferSize;
            this.stack = new AtomicReferenceArray<>(capacity);
        }

        int capacity() {
            return stack.length();
        }

        ByteBuffer tryBorrow() {
            while (true) {
                int currentTop = top.get();
                if (currentTop == 0) {
                    return null;
                }
                int newTop = currentTop - 1;
                if (top.compareAndSet(currentTop, newTop)) {
                    return stack.getAndSet(newTop, null);
                }
            }
        }

        boolean tryRelease(ByteBuffer buffer) {
            while (true) {
                int currentTop = top.get();
                if (currentTop >= stack.length()) {
                    return false;
                }
                if (top.compareAndSet(currentTop, currentTop + 1)) {
                    stack.set(currentTop, buffer);
                    return true;
                }
            }
        }

        int size() {
            return top.get();
        }

        void clear() {
            int n = top.getAndSet(0);
            for (int i = 0; i < Math.min(n, stack.length()); i++) {
                stack.set(i, null);
            }
        }
    }

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
        if (defaultBufferSize > maxPoolableSize) {
            throw new IllegalArgumentException("defaultBufferSize must be <= maxPoolableSize");
        }
        if (maxPoolableSize <= 0 || maxPoolableSize > maxBufferSize) {
            throw new IllegalArgumentException("maxPoolableSize must be > 0 and <= maxBufferSize");
        }
        this.maxBufferSize = maxBufferSize;
        this.maxPoolableSize = maxPoolableSize;
        this.defaultBufferSize = defaultBufferSize;
        this.classes = buildClasses(maxPoolCount, maxPoolableSize, defaultBufferSize);
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
            int classIndex = findBorrowClassIndex(minSize);
            for (int i = classIndex; i < classes.length; i++) {
                ByteBuffer buffer = classes[i].tryBorrow();
                if (buffer != null) {
                    buffer.clear();
                    if (stats != null) {
                        stats.buffersBorrowed.increment();
                        stats.buffersReused.increment();
                    }
                    return buffer;
                }
            }
        }

        int size;
        if (minSize <= maxPoolableSize) {
            size = classes[findBorrowClassIndex(minSize)].bufferSize;
        } else {
            size = Math.max(minSize, defaultBufferSize);
            if (size > maxBufferSize) {
                size = maxBufferSize;
            }
        }
        if (stats != null) {
            stats.buffersBorrowed.increment();
            stats.buffersAllocated.increment();
        }
        return ByteBuffer.allocateDirect(size);
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
        int classIndex = findReleaseClassIndex(buffer.capacity());
        if (classIndex < 0) {
            if (stats != null) {
                stats.buffersDropped.increment();
            }
            return;
        }

        if (!classes[classIndex].tryRelease(buffer)) {
            if (stats != null) {
                stats.buffersDropped.increment();
            }
        }
    }

    public int size() {
        int size = 0;
        for (SizeClass sizeClass : classes) {
            size += sizeClass.size();
        }
        return size;
    }

    public void clear() {
        for (SizeClass sizeClass : classes) {
            sizeClass.clear();
        }
    }

    private int findBorrowClassIndex(int minSize) {
        int targetSize = Math.max(minSize, defaultBufferSize);
        for (int i = 0; i < classes.length; i++) {
            if (classes[i].bufferSize >= targetSize) {
                return i;
            }
        }
        return classes.length - 1;
    }

    private int findReleaseClassIndex(int capacity) {
        for (int i = 0; i < classes.length; i++) {
            if (classes[i].bufferSize == capacity) {
                return i;
            }
        }
        return -1;
    }

    private static SizeClass[] buildClasses(int maxPoolCount, int maxPoolableSize, int defaultBufferSize) {
        List<Integer> sizes = new ArrayList<>(3);
        sizes.add(defaultBufferSize);
        if (defaultBufferSize < maxPoolableSize) {
            int mediumSize = Math.min(maxPoolableSize, Math.max(defaultBufferSize, 16 * 1024));
            if (mediumSize > sizes.get(sizes.size() - 1)) {
                sizes.add(mediumSize);
            }
            if (maxPoolableSize > sizes.get(sizes.size() - 1)) {
                sizes.add(maxPoolableSize);
            }
        }

        int classCount = sizes.size();
        long totalWeight = 0;
        long[] weights = new long[classCount];
        for (int i = 0; i < classCount; i++) {
            long weight = 1L << i;
            weights[i] = weight;
            totalWeight += weight;
        }

        int[] capacities = new int[classCount];
        int allocated = 0;
        if (maxPoolCount >= classCount) {
            for (int i = 0; i < classCount; i++) {
                capacities[i] = 1;
            }
            allocated = classCount;
        }
        for (int i = 0; i < classCount; i++) {
            int capacity = (int) (((long) (maxPoolCount - allocated) * weights[i]) / totalWeight);
            capacities[i] += capacity;
        }
        allocated = 0;
        for (int capacity : capacities) {
            allocated += capacity;
        }
        for (int i = classCount - 1; allocated < maxPoolCount; i--) {
            capacities[i]++;
            allocated++;
            if (i == 0) {
                i = classCount;
            }
        }

        SizeClass[] classes = new SizeClass[classCount];
        for (int i = 0; i < classCount; i++) {
            classes[i] = new SizeClass(sizes.get(i), capacities[i]);
        }
        return classes;
    }
}
