/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;

/**
 * A bounded cache for {@link SigningKey}s that has a FIFO eviction policy when the cache is full.
 */
final class SigningCache {
    private final LinkedHashMap<String, SigningKey> fifoStore;
    private final StampedLock lock = new StampedLock();

    SigningCache(int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize " + maxSize + " must be at least 1");
        }
        this.fifoStore = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SigningKey> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * Adds an entry to the cache, evicting the earliest entry if necessary.
     */
    void put(String key, SigningKey value) {
        long stamp = lock.writeLock();
        try {
            fifoStore.put(key, value);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * @return Signing key if it exists in the store, otherwise {@code null}.
     */
    SigningKey get(String key) {
        long stamp = lock.readLock();
        try {
            return fifoStore.get(key);
        } finally {
            lock.unlockRead(stamp);
        }
    }
}
