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
 * A store to keep token buckets per scope.
 */
final class TokenBucketStore {
    private final int initialRetryTokens;
    private final Map<String, TokenBucket> scopeToTokenBucket;
    private final ReentrantLock lock = new ReentrantLock();

    private TokenBucketStore(int initialRetryTokens, int maxScopes) {
        this.initialRetryTokens = initialRetryTokens;
        this.scopeToTokenBucket = new LinkedHashMap<>(maxScopes, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TokenBucket> eldest) {
                return size() > maxScopes;
            }
        };
    }

    /**
     * Returns the {@link TokenBucket} for the given scope.
     */
    TokenBucket tokenBucketForScope(String scope) {
        Objects.requireNonNull(scope, "scope");
        lock.lock();
        try {
            TokenBucket bucket = scopeToTokenBucket.get(scope);
            if (bucket == null) {
                bucket = new TokenBucket(initialRetryTokens);
                scopeToTokenBucket.put(scope, bucket);
            }
            return bucket;
        } finally {
            lock.unlock();
        }
    }

    static TokenBucketStore create(int initialRetryTokens, int maxScopes) {
        return new TokenBucketStore(initialRetryTokens, maxScopes);
    }
}
