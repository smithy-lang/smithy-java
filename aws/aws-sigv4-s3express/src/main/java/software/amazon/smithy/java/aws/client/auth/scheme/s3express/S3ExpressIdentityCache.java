/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import software.amazon.smithy.java.auth.api.identity.CachingIdentityResolver;

/**
 * Bucket-keyed cache of S3 Express identity resolvers.
 *
 * <p>Each entry wraps a {@link CachingIdentityResolver} configured to call {@code CreateSession}
 * for that bucket on cache miss and refresh in the background as expiration approaches.
 *
 * <h3>Hot path</h3>
 *
 * <p>{@link #get(S3ExpressIdentityKey)} is one {@link ConcurrentHashMap#get} plus a plain
 * volatile write to update the access tick. No lock. Approximate-LRU semantics: races on the
 * tick write are tolerated (we only need order-of-magnitude accuracy to pick an eviction
 * candidate).
 *
 * <h3>Cold path</h3>
 *
 * <p>On miss we take a {@link ReentrantLock}, re-check the map, and either insert (if under
 * capacity) or scan all entries for the lowest tick value, evict that one, and insert. The scan
 * is O(N) but only runs on insert when the cache is full — eviction does not happen on the
 * read path.
 *
 * <h3>Bounding</h3>
 *
 * <p>{@link #MAX_SIZE} is a small constant (25). The S3 Express SEP describes a recommended
 * upper bound of 100; v2's SDK uses 25 pending real-world data, and we follow that. Not
 * configurable today.
 */
final class S3ExpressIdentityCache implements AutoCloseable {

    private static final int MAX_SIZE = 25;

    private final ConcurrentHashMap<S3ExpressIdentityKey, Entry> entries = new ConcurrentHashMap<>();
    private final ReentrantLock writeLock = new ReentrantLock();
    private final AtomicLong tick = new AtomicLong();
    private final Function<S3ExpressIdentityKey, CachingIdentityResolver<S3ExpressIdentity>> factory;

    S3ExpressIdentityCache(Function<S3ExpressIdentityKey, CachingIdentityResolver<S3ExpressIdentity>> factory) {
        this.factory = factory;
    }

    CachingIdentityResolver<S3ExpressIdentity> get(S3ExpressIdentityKey key) {
        Entry e = entries.get(key);
        if (e != null) {
            e.lastAccess = tick.incrementAndGet();
            return e.resolver;
        }
        return getOrCreate(key);
    }

    private CachingIdentityResolver<S3ExpressIdentity> getOrCreate(S3ExpressIdentityKey key) {
        writeLock.lock();
        try {
            Entry e = entries.get(key);
            if (e != null) {
                e.lastAccess = tick.incrementAndGet();
                return e.resolver;
            }
            if (entries.size() >= MAX_SIZE) {
                evictLeastRecentlyUsed();
            }
            Entry created = new Entry(factory.apply(key), tick.incrementAndGet());
            entries.put(key, created);
            return created.resolver;
        } finally {
            writeLock.unlock();
        }
    }

    private void evictLeastRecentlyUsed() {
        S3ExpressIdentityKey victim = null;
        long minTick = Long.MAX_VALUE;
        for (var entry : entries.entrySet()) {
            long t = entry.getValue().lastAccess;
            if (t < minTick) {
                minTick = t;
                victim = entry.getKey();
            }
        }
        if (victim != null) {
            Entry removed = entries.remove(victim);
            if (removed != null) {
                closeQuietly(removed.resolver);
            }
        }
    }

    void invalidateAll() {
        writeLock.lock();
        try {
            for (var entry : entries.values()) {
                entry.resolver.invalidate();
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() {
        writeLock.lock();
        try {
            for (var entry : entries.values()) {
                closeQuietly(entry.resolver);
            }
            entries.clear();
        } finally {
            writeLock.unlock();
        }
    }

    private static void closeQuietly(AutoCloseable c) {
        try {
            c.close();
        } catch (Exception ignored) {
            // Cache eviction shouldn't fail the call that triggered it.
        }
    }

    private static final class Entry {
        final CachingIdentityResolver<S3ExpressIdentity> resolver;

        // Volatile so concurrent readers see updated values; races on simultaneous writes are
        // benign (we only use this for approximate ordering of eviction).
        volatile long lastAccess;

        Entry(CachingIdentityResolver<S3ExpressIdentity> resolver, long lastAccess) {
            this.resolver = resolver;
            this.lastAccess = lastAccess;
        }
    }
}
