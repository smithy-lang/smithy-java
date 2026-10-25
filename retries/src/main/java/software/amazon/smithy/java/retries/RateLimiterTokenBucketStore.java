/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.retries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A store that manages {@link RateLimiterTokenBucket} instances per scope with LRU eviction.
 *
 * <p>This class is thread-safe.
 */
final class RateLimiterTokenBucketStore {
    private static final RateLimiterClock DEFAULT_CLOCK = new SystemClock();
    private final Map<String, RateLimiterTokenBucket> scopeToTokenBucket;
    private final RateLimiterClock clock;
    private final ReentrantLock lock = new ReentrantLock();

    RateLimiterTokenBucketStore(RateLimiterClock clock, int maxScopes) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scopeToTokenBucket = new LinkedHashMap<>(maxScopes, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, RateLimiterTokenBucket> eldest) {
                return size() > maxScopes;
            }
        };
    }

    RateLimiterTokenBucketStore(int maxScopes) {
        this(DEFAULT_CLOCK, maxScopes);
    }

    /**
     * Returns the {@link RateLimiterTokenBucket} for the given scope.
     */
    RateLimiterTokenBucket tokenBucketForScope(String scope) {
        Objects.requireNonNull(scope, "scope");
        lock.lock();
        try {
            RateLimiterTokenBucket bucket = scopeToTokenBucket.get(scope);
            if (bucket == null) {
                bucket = new RateLimiterTokenBucket(clock);
                scopeToTokenBucket.put(scope, bucket);
            }
            return bucket;
        } finally {
            lock.unlock();
        }
    }
}
