/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

final class Pool<T> {
    private final Supplier<T> supplier;
    private final int maxSize;
    private final ConcurrentLinkedQueue<T> pool;

    Pool(int maxItems, Supplier<T> supplier) {
        this.supplier = supplier;
        this.maxSize = maxItems;
        this.pool = new ConcurrentLinkedQueue<>();
    }

    public T get() {
        T cached = pool.poll();
        return cached != null ? cached : supplier.get();
    }

    public void release(T obj) {
        if (pool.size() < maxSize) {
            pool.offer(obj);
        }
    }
}
