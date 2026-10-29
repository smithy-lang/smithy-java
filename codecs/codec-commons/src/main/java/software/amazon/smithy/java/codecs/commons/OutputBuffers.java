/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import software.amazon.smithy.java.io.datastream.ByteArrayRecycler;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Pools output arrays in power-of-two sizes from 4 KB to 1 MB.
 *
 * <p>Codecs use {@link #shared()} by default.
 * Use {@link #create()} for a pool owned by one codec.
 */
public final class OutputBuffers {

    private static final int MIN_SHIFT = 12;
    private static final int MAX_SHIFT = 20;
    private static final int CLASS_BUDGET = 4 << 20; // bytes of pooled arrays per size class

    private static final class Shared {
        static final OutputBuffers INSTANCE = new OutputBuffers();
    }

    private final ArrayPool[] pools = new ArrayPool[MAX_SHIFT - MIN_SHIFT + 1];
    private final ByteArrayRecycler recycler = this::release;

    private OutputBuffers() {
        int maxSlots = StripedPool.logicalSlots(4, Runtime.getRuntime().availableProcessors());
        for (int shift = MIN_SHIFT; shift <= MAX_SHIFT; shift++) {
            int size = 1 << shift;
            int slots = Math.min(maxSlots, Math.max(1, CLASS_BUDGET / size));
            pools[shift - MIN_SHIFT] = new ArrayPool(size, slots);
        }
    }

    int slotsFor(int capacity) {
        return pools[shiftFor(capacity) - MIN_SHIFT].logicalSlotCount();
    }

    /**
     * @return the JVM-wide default pool
     */
    public static OutputBuffers shared() {
        return Shared.INSTANCE;
    }

    /**
     * @return a new pool
     */
    public static OutputBuffers create() {
        return new OutputBuffers();
    }

    /**
     * Acquires an array from the smallest pooled size that meets {@code minCapacity}.
     * Sizes above 1 MB receive an exact-size allocation.
     *
     * @param minCapacity minimum array length
     */
    @SmithyInternalApi
    public byte[] acquire(int minCapacity) {
        int shift = shiftFor(minCapacity);
        if (shift > MAX_SHIFT) {
            return new byte[minCapacity];
        }
        return pools[shift - MIN_SHIFT].acquire(null);
    }

    /**
     * Returns an array to the pool. Arrays outside the pooled sizes are discarded.
     *
     * @param array array from {@link #acquire}
     */
    @SmithyInternalApi
    public void release(byte[] array) {
        int n = array.length;
        if (n < (1 << MIN_SHIFT) || n > (1 << MAX_SHIFT) || Integer.bitCount(n) != 1) {
            return;
        }
        pools[Integer.numberOfTrailingZeros(n) - MIN_SHIFT].release(array);
    }

    /**
     * Replaces the array with a larger array and recycles the original.
     *
     * @param used bytes to copy
     * @param minCapacity minimum replacement length
     * @return the replacement containing the first {@code used} bytes
     */
    @SmithyInternalApi
    public byte[] grow(byte[] array, int used, int minCapacity) {
        byte[] bigger = acquire(Math.max(minCapacity, array.length * 2));
        System.arraycopy(array, 0, bigger, 0, used);
        release(array);
        return bigger;
    }

    /**
     * @return a thread-safe recycler that returns arrays to this pool
     */
    @SmithyInternalApi
    public ByteArrayRecycler recycler() {
        return recycler;
    }

    /**
     * Removes all idle arrays from the pool.
     * The pool can accept arrays again, including arrays already acquired.
     */
    public void clear() {
        for (ArrayPool pool : pools) {
            pool.clear();
        }
    }

    /**
     * The hint decreases by a quarter, but stays at least as large as the latest output.
     * The result cannot exceed 1 MB.
     */
    @SmithyInternalApi
    public static int nextSizeHint(int previousHint, int used) {
        int decayed = previousHint - (previousHint >>> 2);
        return Math.clamp(used, decayed, 1 << MAX_SHIFT);
    }

    private static int shiftFor(int minCapacity) {
        if (minCapacity <= (1 << MIN_SHIFT)) {
            return MIN_SHIFT;
        }
        return 32 - Integer.numberOfLeadingZeros(minCapacity - 1);
    }

    private static final class ArrayPool extends StripedPool<byte[], Void> {
        private final int size;

        ArrayPool(int size, int slots) {
            super(slots);
            this.size = size;
        }

        @Override
        protected byte[] create(Void context) {
            return new byte[size];
        }

        @Override
        protected boolean canPool(byte[] item) {
            return item.length == size;
        }

        @Override
        protected boolean reset(byte[] item, Void context) {
            return true;
        }
    }
}
