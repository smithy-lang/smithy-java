/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A bounded cache that has a FIFO eviction policy when the cache is full.
 * <p>Adapted from JavaV2's {@code software.amazon.awssdk.auth.signer.internal.FifoCache}.
 */
final class SigningCache extends LinkedHashMap<String, SigningKey> {
    private final int maxSize;
    private final ReentrantReadWriteLock.ReadLock readLock;
    private final ReentrantReadWriteLock.WriteLock writeLock;

    public SigningCache(int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize " + maxSize + " must be at least 1");
        }
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        readLock = lock.readLock();
        writeLock = lock.writeLock();
        this.maxSize = maxSize;
    }

    /**
     * {@inheritDoc}
     *
     * Returns true if the size of this map exceeds the maximum.
     */
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, SigningKey> eldest) {
        return size() > maxSize;
    }

    /**
     * Adds an entry to the cache, evicting the earliest entry if necessary.
     */
    @Override
    public SigningKey put(String key, SigningKey value) {
        writeLock.lock();
        try {
            return super.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    /** Returns the value of the given key; or null of no such entry exists. */
    public SigningKey get(String key) {
        readLock.lock();
        try {
            return super.get(key);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Returns the current size of the cache.
     */
    public int size() {
        readLock.lock();
        try {
            return super.size();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public String toString() {
        readLock.lock();
        try {
            return super.toString();
        } finally {
            readLock.unlock();
        }
    }
}
