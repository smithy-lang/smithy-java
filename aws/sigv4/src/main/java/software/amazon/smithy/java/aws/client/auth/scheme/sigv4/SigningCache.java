/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;

/**
 * A bounded cache for {@link SigningKey}s that has a FIFO eviction policy when the cache is full.
 */
final class SigningCache {
    private final LinkedHashMap<CacheKey, SigningKey> fifoStore;
    private final StampedLock lock = new StampedLock();

    SigningCache(int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize " + maxSize + " must be at least 1");
        }
        this.fifoStore = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, SigningKey> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * Adds an entry to the cache, evicting the earliest entry if necessary.
     */
    void put(CacheKey key, SigningKey value) {
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
    SigningKey get(CacheKey key) {
        long stamp = lock.readLock();
        try {
            return fifoStore.get(key);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    static final class CacheKey {
        private final String secretKey;
        private final String regionName;
        private final String serviceName;
        private final int cachedHashCode;

        CacheKey(String secretKey, String regionName, String serviceName) {
            this.secretKey = secretKey;
            this.regionName = regionName;
            this.serviceName = serviceName;
            cachedHashCode = computeHashCode(secretKey, regionName, serviceName);
        }

        private static int computeHashCode(String secretKey, String regionName, String serviceName) {
            int result = 1;
            result = 31 * result + secretKey.hashCode();
            result = 31 * result + regionName.hashCode();
            result = 31 * result + serviceName.hashCode();
            return result;
        }

        @Override
        public int hashCode() {
            return cachedHashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj instanceof CacheKey c) {
                return cachedHashCode == c.cachedHashCode
                        && secretKey.equals(c.secretKey)
                        && regionName.equals(c.regionName)
                        && serviceName.equals(c.serviceName);
            }
            return false;
        }
    }
}
