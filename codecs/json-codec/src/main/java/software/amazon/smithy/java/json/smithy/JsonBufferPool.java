/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Virtual-thread-friendly buffer pool for JSON serialization.
 *
 * <p>Uses a striped {@link AtomicReferenceArray} sized by available processors.
 * For virtual threads, pooling is skipped entirely (fresh allocation each time)
 * following Netty's proven VT strategy. For platform threads, the striped design
 * provides high hit rates with zero contention.
 *
 * <p>Memory is bounded: at most {@code SLOT_COUNT * maxCacheableSize} bytes regardless
 * of thread count. No {@link ThreadLocal}, no {@code synchronized}, no carrier thread pinning.
 */
final class JsonBufferPool {

    private JsonBufferPool() {}

    private static final int SLOT_COUNT;
    private static final int MASK;
    private static final AtomicReferenceArray<byte[]> SLOTS;

    static final int DEFAULT_SIZE = 8192;
    private static final int MAX_CACHEABLE = 1 << 20; // 1MB
    private static final int MAX_PROBE = 3;

    static {
        // Round up to next power of 2 for fast masking
        int processors = Runtime.getRuntime().availableProcessors();
        int raw = processors * 4;
        SLOT_COUNT = Integer.highestOneBit(raw - 1) << 1;
        MASK = SLOT_COUNT - 1;
        SLOTS = new AtomicReferenceArray<>(SLOT_COUNT);
    }

    /**
     * Acquires a buffer of at least the given size.
     *
     * <p>For virtual threads, always allocates a fresh buffer.
     * For platform threads, probes up to 3 striped slots before falling back to allocation.
     */
    static byte[] acquire(int minSize) {
        if (Thread.currentThread().isVirtual()) {
            return new byte[Math.max(minSize, DEFAULT_SIZE)];
        }

        int base = probe();
        for (int i = 0; i < MAX_PROBE; i++) {
            int idx = (base + i) & MASK;
            byte[] buf = SLOTS.getAndSet(idx, null);
            if (buf != null) {
                if (buf.length >= minSize) {
                    return buf;
                }
                // Too small — put it back and keep looking
                SLOTS.lazySet(idx, buf);
            }
        }

        return new byte[Math.max(minSize, DEFAULT_SIZE)];
    }

    /**
     * Returns a buffer to the pool.
     *
     * <p>Oversized buffers (>1MB) and virtual-thread buffers are dropped for GC.
     */
    static void release(byte[] buf) {
        if (buf.length > MAX_CACHEABLE || Thread.currentThread().isVirtual()) {
            return;
        }

        int base = probe();
        for (int i = 0; i < MAX_PROBE; i++) {
            int idx = (base + i) & MASK;
            if (SLOTS.compareAndSet(idx, null, buf)) {
                return;
            }
        }
        // All probed slots occupied — let GC collect
    }

    /**
     * Computes a slot index from the current thread ID.
     * Mixes bits for better distribution across slots.
     */
    private static int probe() {
        long id = Thread.currentThread().threadId();
        return (int) (id ^ (id >>> 16)) & MASK;
    }
}
