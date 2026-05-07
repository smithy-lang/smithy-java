/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.smithy.java.context.Context;

/** * An {@link IdentityResolver} that caches the result of a delegate resolver and refreshes it asynchronously in the
 * background before expiration.
 *
 * <p>Behavior:
 * <ul>
 *   <li>On first call (cold start), blocks until the delegate returns a result.</li>
 *   <li>On subsequent calls, returns the cached identity immediately. A background task refreshes the identity when
 *       it enters the prefetch window ({@code expiration - prefetchBuffer}).</li>
 *   <li>If the background refresh fails and {@link Builder#allowExpiredCredentials(boolean)} is {@code true}
 *       (static stability), the expired cached value continues to be returned and refresh is retried after a
 *       jittered 5-10 minute delay.</li>
 *   <li>If the background refresh fails and {@code allowExpiredCredentials} is {@code false} (default), the next
 *       caller that finds the cache expired will block for one synchronous retry.</li>
 *   <li>If the delegate returns an identity with no expiration, it is cached indefinitely until
 *       {@link #invalidate()} is called.</li>
 * </ul>
 *
 * <p>This class is thread-safe. At most one refresh runs at a time (enforced by an {@link AtomicBoolean}).
 * Callers never block except on cold start.
 *
 * @param <I> the identity type.
 */
public final class CachingIdentityResolver<I extends Identity> implements IdentityResolver<I>, AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(CachingIdentityResolver.class.getName());

    private final IdentityResolver<I> delegate;
    private final Duration prefetchBuffer;
    private final boolean allowExpiredCredentials;
    private final Duration staleRefreshDelay;
    private final Clock clock;
    private final ScheduledExecutorService executor;
    private final boolean ownsExecutor;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile CountDownLatch coldStartLatch = new CountDownLatch(1);

    private volatile CachedValue<I> cached;
    private volatile ScheduledFuture<?> scheduledRefresh;

    private CachingIdentityResolver(Builder<I> builder) {
        this.delegate = Objects.requireNonNull(builder.delegate, "delegate");
        this.prefetchBuffer = builder.prefetchBuffer;
        this.allowExpiredCredentials = builder.allowExpiredCredentials;
        this.staleRefreshDelay = builder.staleRefreshDelay;
        this.clock = builder.clock;

        if (builder.executor != null) {
            this.executor = builder.executor;
            this.ownsExecutor = false;
        } else {
            this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "smithy-identity-cache-refresh");
                t.setDaemon(true);
                return t;
            });
            this.ownsExecutor = true;
        }
    }

    /**
     * Create a builder.
     *
     * @param delegate the underlying resolver to cache.
     * @param <I>      identity type.
     * @return a new builder.
     */
    public static <I extends Identity> Builder<I> builder(IdentityResolver<I> delegate) {
        return new Builder<>(delegate);
    }

    @Override
    public IdentityResult<I> resolveIdentity(Context requestProperties) {
        CachedValue<I> current = cached;

        // Cold start: first caller triggers refresh, others wait.
        if (current == null) {
            return coldStart(requestProperties);
        }

        // Cache is fresh — return immediately.
        if (!isInPrefetchWindow(current) && !isExpired(current)) {
            return current.result;
        }

        // Cache is in prefetch window or expired. Kick off async refresh if not already running.
        triggerAsyncRefresh(requestProperties);

        // If expired and strict mode, we can't return stale — block for the refresh.
        if (isExpired(current) && !allowExpiredCredentials) {
            return blockForRefresh(current, requestProperties);
        }

        return current.result;
    }

    @Override
    public Class<I> identityType() {
        return delegate.identityType();
    }

    @Override
    public void invalidate() {
        cached = null;
        coldStartLatch = new CountDownLatch(1);
        cancelScheduledRefresh();
    }

    @Override
    public void close() {
        cancelScheduledRefresh();
        if (ownsExecutor) {
            executor.shutdownNow();
        }
    }

    private IdentityResult<I> coldStart(Context requestProperties) {
        if (refreshing.compareAndSet(false, true)) {
            try {
                return doRefresh(requestProperties);
            } finally {
                refreshing.set(false);
                coldStartLatch.countDown();
            }
        }

        // Another thread is doing the cold start — wait for it.
        try {
            coldStartLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return IdentityResult.ofError(getClass(), "Interrupted waiting for initial credential resolution");
        }

        CachedValue<I> result = cached;
        return result != null ? result.result : IdentityResult.ofError(getClass(), "Failed to resolve credentials");
    }

    private void triggerAsyncRefresh(Context requestProperties) {
        if (refreshing.compareAndSet(false, true)) {
            executor.submit(() -> {
                try {
                    doRefresh(requestProperties);
                } finally {
                    refreshing.set(false);
                }
            });
        }
    }

    private IdentityResult<I> blockForRefresh(CachedValue<I> current, Context requestProperties) {
        // Strict mode: cache is expired. Try one synchronous refresh.
        if (refreshing.compareAndSet(false, true)) {
            try {
                IdentityResult<I> result = doRefresh(requestProperties);
                // If doRefresh returned the stale cached value (shouldn't in strict mode), check again.
                CachedValue<I> latest = cached;
                if (latest != current) {
                    return latest.result;
                }
                return result;
            } finally {
                refreshing.set(false);
            }
        }

        // Another thread is refreshing — wait briefly then check.
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        CachedValue<I> latest = cached;
        if (latest != current && latest != null && !isExpired(latest)) {
            return latest.result;
        }

        // Still expired — return error.
        return IdentityResult.ofError(getClass(), "Credentials are expired and refresh failed");
    }

    private IdentityResult<I> doRefresh(Context requestProperties) {
        CachedValue<I> current = cached;

        // Stale delay: don't hammer the source (only in static stability mode).
        if (allowExpiredCredentials && current != null
                && current.nextRefreshAfter != null
                && clock.instant().isBefore(current.nextRefreshAfter)) {
            return current.result;
        }

        IdentityResult<I> result;
        try {
            result = delegate.resolveIdentity(requestProperties);
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Credential refresh failed", e);
            if (current != null && allowExpiredCredentials) {
                current.nextRefreshAfter = clock.instant().plus(jitteredStaleDelay());
                return current.result;
            }
            throw e;
        }

        if (result.identity() != null) {
            CachedValue<I> newCached = new CachedValue<>(result.identity());
            cached = newCached;
            scheduleNextRefresh(newCached, requestProperties);
            return newCached.result;
        }

        // Delegate returned an error.
        if (current != null && allowExpiredCredentials) {
            current.nextRefreshAfter = clock.instant().plus(jitteredStaleDelay());
            return current.result;
        }

        return result;
    }

    private void scheduleNextRefresh(CachedValue<I> value, Context requestProperties) {
        cancelScheduledRefresh();
        Instant expiration = value.identity.expirationTime();
        if (expiration == null) {
            return;
        }

        Instant refreshAt = expiration.minus(prefetchBuffer);
        long delayMillis = Duration.between(clock.instant(), refreshAt).toMillis();
        if (delayMillis <= 0) {
            // Already in prefetch window; refresh was just done.
            return;
        }

        scheduledRefresh = executor.schedule(() -> {
            if (refreshing.compareAndSet(false, true)) {
                try {
                    doRefresh(requestProperties);
                } finally {
                    refreshing.set(false);
                }
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void cancelScheduledRefresh() {
        ScheduledFuture<?> f = scheduledRefresh;
        if (f != null) {
            f.cancel(false);
            scheduledRefresh = null;
        }
    }

    private boolean isInPrefetchWindow(CachedValue<I> value) {
        Instant expiration = value.identity.expirationTime();
        return expiration != null && clock.instant().isAfter(expiration.minus(prefetchBuffer));
    }

    private boolean isExpired(CachedValue<I> value) {
        Instant exp = value.identity.expirationTime();
        return exp != null && clock.instant().isAfter(exp);
    }

    private Duration jitteredStaleDelay() {
        long baseMillis = staleRefreshDelay.toMillis();
        long jitter = (long) (Math.random() * baseMillis);
        return Duration.ofMillis(baseMillis + jitter);
    }

    private static final class CachedValue<I extends Identity> {
        final I identity;
        final IdentityResult<I> result;
        volatile Instant nextRefreshAfter;

        CachedValue(I identity) {
            this.identity = identity;
            this.result = IdentityResult.of(identity);
        }
    }

    /**
     * Builder for {@link CachingIdentityResolver}.
     */
    public static final class Builder<I extends Identity> {
        private final IdentityResolver<I> delegate;
        private Duration prefetchBuffer = Duration.ofMinutes(5);
        private boolean allowExpiredCredentials = false;
        private Duration staleRefreshDelay = Duration.ofMinutes(5);
        private Clock clock = Clock.systemUTC();
        private ScheduledExecutorService executor;

        private Builder(IdentityResolver<I> delegate) {
            this.delegate = delegate;
        }

        /**
         * How far before expiration to trigger a background refresh. Default: 5 minutes.
         */
        public Builder<I> prefetchBuffer(Duration prefetchBuffer) {
            this.prefetchBuffer = Objects.requireNonNull(prefetchBuffer);
            return this;
        }

        /**
         * When {@code true}, expired credentials are returned instead of failing. Enables
         * AWS Static Stability behavior. Default: {@code false}.
         */
        public Builder<I> allowExpiredCredentials(boolean allowExpiredCredentials) {
            this.allowExpiredCredentials = allowExpiredCredentials;
            return this;
        }

        /**
         * Base delay before retrying refresh when credentials are expired and refresh failed.
         * Actual delay is jittered up to 2x this value. Default: 5 minutes.
         */
        public Builder<I> staleRefreshDelay(Duration staleRefreshDelay) {
            this.staleRefreshDelay = Objects.requireNonNull(staleRefreshDelay);
            return this;
        }

        /**
         * Clock for time comparisons. Default: {@link Clock#systemUTC()}.
         */
        public Builder<I> clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock);
            return this;
        }

        /**
         * Executor for background refresh tasks. If not set, a single daemon thread is created
         * and owned by this resolver (shut down on {@link CachingIdentityResolver#close()}).
         */
        public Builder<I> executor(ScheduledExecutorService executor) {
            this.executor = Objects.requireNonNull(executor);
            return this;
        }

        public CachingIdentityResolver<I> build() {
            return new CachingIdentityResolver<>(this);
        }
    }
}
