/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import java.util.concurrent.atomic.AtomicReferenceArray;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * A striped lock-free object pool for reusing serializer/deserializer instances across requests.
 *
 * <p>Each concrete pool should be declared as a {@code private static final} anonymous subclass.
 * The JIT devirtualizes callback methods via monomorphic inline caching at each callsite.
 *
 * <p>Pool slots are probed starting from a Fibonacci-hashed thread-ID with cache-line-sized
 * strides to avoid false sharing under contention. Both platform and virtual threads use the
 * pool -- actual concurrency is bounded by carrier thread count, and the pool is sized at
 * 4x available processors to absorb probe collisions.
 *
 * @param <T> the pooled type
 * @param <C> context type passed to {@link #acquire} and forwarded to {@link #create}/{@link #reset}
 */
@SmithyInternalApi
public abstract class StripedPool<T, C> {

    // 64-byte cache line / 4-byte compressed oop = 16 refs per line
    private static final int STRIDE_SHIFT = 4;
    private static final int MAX_PROBE = 4;
    private final int slotMask;
    private final AtomicReferenceArray<T> pool;

    protected StripedPool() {
        this(4);
    }

    protected StripedPool(int multiplier) {
        int raw = Runtime.getRuntime().availableProcessors() * multiplier;
        int logicalSlots = Integer.highestOneBit(raw - 1) << 1;
        this.slotMask = logicalSlots - 1;
        this.pool = new AtomicReferenceArray<>(logicalSlots << STRIDE_SHIFT);
    }

    /**
     * Create a fresh instance when the pool is empty.
     */
    protected abstract T create(C context);

    /**
     * Always-run cleanup on release, before the canPool check.
     * Use for clearing references that must not outlive the request (e.g., OutputStream sink).
     */
    protected void cleanup(T item) {}

    /**
     * Whether this item is eligible for pooling. Called after {@link #cleanup}.
     * Return false to discard (e.g., internal buffer is null).
     */
    protected abstract boolean canPool(T item);

    /**
     * Pre-pool preparation, called only when {@link #canPool} returned true.
     * Use for downsizing oversized buffers to bound memory.
     */
    protected void prepareForPool(T item) {}

    /**
     * Reinitialize a pooled item for reuse. Return true to accept the item.
     * Return false if the item is unsuitable for this particular acquire (e.g., settings mismatch).
     * A rejected item is put back into its pool slot and probing continues.
     */
    protected abstract boolean reset(T item, C context);

    public final T acquire(C context) {
        AtomicReferenceArray<T> p = this.pool;
        int mask = this.slotMask;
        long id = Thread.currentThread().threadId();
        int base = (int) (id * 0x9E3779B97F4A7C15L >>> 32) & mask;
        for (int i = 0; i < MAX_PROBE; i++) {
            int idx = ((base + i) & mask) << STRIDE_SHIFT;
            T s = p.getPlain(idx);
            if (s != null && p.compareAndExchangeAcquire(idx, s, null) == s) {
                if (reset(s, context)) {
                    return s;
                }
                p.weakCompareAndSetRelease(idx, null, s);
            }
        }
        return create(context);
    }

    public final void release(T item) {
        cleanup(item);
        if (!canPool(item)) {
            return;
        }
        prepareForPool(item);
        AtomicReferenceArray<T> p = this.pool;
        int mask = this.slotMask;
        long id = Thread.currentThread().threadId();
        int base = (int) (id * 0x9E3779B97F4A7C15L >>> 32) & mask;
        for (int i = 0; i < MAX_PROBE; i++) {
            int idx = ((base + i) & mask) << STRIDE_SHIFT;
            if (p.getPlain(idx) == null && p.weakCompareAndSetRelease(idx, null, item)) {
                return;
            }
        }
    }
}
