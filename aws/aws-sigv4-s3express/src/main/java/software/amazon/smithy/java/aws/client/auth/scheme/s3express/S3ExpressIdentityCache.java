/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import software.amazon.smithy.java.auth.api.identity.CachingIdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;

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
 * <h3>Idle eviction</h3>
 *
 * <p>The S3 Express SEP requires that cached session credentials for buckets that are no longer
 * accessed be reaped, to bound their lifetime. A periodic background sweep removes (and closes)
 * any entry that has not been accessed by a request for longer than {@link #idleTimeoutMillis}
 * (default 5 minutes). Closing an entry stops its {@link CachingIdentityResolver}'s background
 * {@code CreateSession} refresh loop, so an unused bucket stops re-minting credentials.
 *
 * <h3>Bounding</h3>
 *
 * <p>{@link #MAX_SIZE} is a small constant (100), the recommended upper bound from the S3 Express
 * SEP. Not configurable today.
 */
final class S3ExpressIdentityCache implements AutoCloseable {

    private static final int MAX_SIZE = 100;
    private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(5);

    private final ConcurrentHashMap<S3ExpressIdentityKey, Entry> entries = new ConcurrentHashMap<>();
    private final ReentrantLock writeLock = new ReentrantLock();
    private final AtomicLong tick = new AtomicLong();
    private final Function<S3ExpressIdentityKey, CachingIdentityResolver<AwsCredentialsIdentity>> factory;
    private final Clock clock;
    private final long idleTimeoutMillis;
    private final ScheduledFuture<?> sweepTask;

    S3ExpressIdentityCache(Function<S3ExpressIdentityKey, CachingIdentityResolver<AwsCredentialsIdentity>> factory) {
        this(factory, null, DEFAULT_IDLE_TIMEOUT, Clock.systemUTC());
    }

    /**
     * @param factory       builds the per-bucket resolver on cache miss.
     * @param sweepExecutor executor for the periodic idle-eviction sweep, or {@code null} to disable
     *                      the background sweep (callers may still invoke {@link #evictIdle()} directly).
     * @param idleTimeout   how long an entry may go unaccessed before it is eligible for eviction.
     * @param clock         clock used for idle tracking (injectable for tests).
     */
    S3ExpressIdentityCache(
            Function<S3ExpressIdentityKey, CachingIdentityResolver<AwsCredentialsIdentity>> factory,
            ScheduledExecutorService sweepExecutor,
            Duration idleTimeout,
            Clock clock
    ) {
        this.factory = factory;
        this.clock = clock;
        this.idleTimeoutMillis = idleTimeout.toMillis();
        if (sweepExecutor != null) {
            long sweepMillis = idleTimeoutMillis;
            this.sweepTask = sweepExecutor.scheduleWithFixedDelay(
                    this::evictIdle,
                    sweepMillis,
                    sweepMillis,
                    TimeUnit.MILLISECONDS);
        } else {
            this.sweepTask = null;
        }
    }

    CachingIdentityResolver<AwsCredentialsIdentity> get(S3ExpressIdentityKey key) {
        Entry e = entries.get(key);
        if (e != null) {
            touch(e);
            return e.resolver;
        }
        return getOrCreate(key);
    }

    private void touch(Entry e) {
        e.lastAccessTick = tick.incrementAndGet();
        e.lastAccessMillis = clock.millis();
    }

    private CachingIdentityResolver<AwsCredentialsIdentity> getOrCreate(S3ExpressIdentityKey key) {
        writeLock.lock();
        try {
            Entry e = entries.get(key);
            if (e != null) {
                touch(e);
                return e.resolver;
            }
            if (entries.size() >= MAX_SIZE) {
                evictLeastRecentlyUsed();
            }
            Entry created = new Entry(factory.apply(key), tick.incrementAndGet(), clock.millis());
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
            long t = entry.getValue().lastAccessTick;
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

    /**
     * Evict (and close) every entry that has not been accessed within the idle timeout. Invoked
     * periodically by the background sweep; also callable directly for testing.
     */
    void evictIdle() {
        long now = clock.millis();
        writeLock.lock();
        try {
            var it = entries.entrySet().iterator();
            while (it.hasNext()) {
                Entry entry = it.next().getValue();
                if (now - entry.lastAccessMillis > idleTimeoutMillis) {
                    it.remove();
                    closeQuietly(entry.resolver);
                }
            }
        } finally {
            writeLock.unlock();
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
        if (sweepTask != null) {
            sweepTask.cancel(false);
        }
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
        final CachingIdentityResolver<AwsCredentialsIdentity> resolver;

        // Volatile so concurrent readers see updated values; races on simultaneous writes are
        // benign (we only use these for approximate ordering of eviction).
        //
        // lastAccessTick is a strictly monotonic counter used to pick the LRU victim on overflow.
        // lastAccessMillis is wall-clock time used to detect idle entries for proactive eviction.
        volatile long lastAccessTick;
        volatile long lastAccessMillis;

        Entry(CachingIdentityResolver<AwsCredentialsIdentity> resolver, long lastAccessTick, long lastAccessMillis) {
            this.resolver = resolver;
            this.lastAccessTick = lastAccessTick;
            this.lastAccessMillis = lastAccessMillis;
        }
    }
}
