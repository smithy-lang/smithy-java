/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;
import software.amazon.smithy.java.context.Context;

/**
 * An {@link IdentityResolver} that caches identities and refreshes them using advisory and mandatory refresh
 * windows.
 *
 * <p>Only one refresh can run at a time. Advisory refreshes run in the background while callers return cached
 * identities. Mandatory refreshes block callers and share the result of the in-flight refresh. When static stability
 * is enabled, failed refreshes retain and return cached identities, including expired identities, and rate-limit
 * subsequent attempts with a jittered backoff.
 *
 * @param <I> identity type.
 */
public final class CachingIdentityResolver<I extends Identity> implements IdentityResolver<I>, AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(CachingIdentityResolver.class.getName());
    private static final Duration DEFAULT_MANDATORY_WINDOW = Duration.ofMinutes(1);
    private static final Duration DEFAULT_BACKOFF_MIN = Duration.ofMinutes(5);
    private static final Duration DEFAULT_BACKOFF_MAX = Duration.ofMinutes(10);

    private final IdentityResolver<I> delegate;
    private final Duration configuredAdvisoryWindow;
    private final Duration mandatoryRefreshWindow;
    private final boolean allowExpiredCredentials;
    private final Duration refreshBackoffMin;
    private final Duration refreshBackoffMax;
    private final Clock clock;
    private final ScheduledExecutorService executor;
    private final boolean ownsExecutor;
    private final boolean closeDelegate;
    private final boolean proactiveRefresh;
    private final BiPredicate<I, I> identityMatcher;
    private final ReentrantLock lock = new ReentrantLock();

    private CachedValue<I> cached;
    private CompletableFuture<RefreshOutcome<I>> inFlight;
    private ScheduledFuture<?> scheduledRefresh;
    private Instant nextRefreshAllowedAt;
    private Instant nextRefreshAfterSuccessAt;
    private RefreshOutcome<I> lastFailure;
    private boolean refreshRequired;
    private boolean closed;

    private CachingIdentityResolver(Builder<I> builder) {
        this.delegate = Objects.requireNonNull(builder.delegate, "delegate");
        this.configuredAdvisoryWindow = builder.advisoryRefreshWindow;
        this.mandatoryRefreshWindow = builder.mandatoryRefreshWindow;
        this.allowExpiredCredentials = builder.allowExpiredCredentials;
        this.refreshBackoffMin = builder.refreshBackoffMin;
        this.refreshBackoffMax = builder.refreshBackoffMax;
        this.clock = builder.clock;
        this.closeDelegate = builder.closeDelegate;
        this.proactiveRefresh = builder.proactiveRefresh;
        this.identityMatcher = builder.identityMatcher;

        if (builder.executor == null) {
            this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "smithy-identity-cache-refresh");
                thread.setDaemon(true);
                return thread;
            });
            this.ownsExecutor = true;
        } else {
            this.executor = builder.executor;
            this.ownsExecutor = false;
        }
    }

    /**
     * Creates a caching resolver builder.
     *
     * @param delegate resolver that obtains identities from the source.
     * @param <I> identity type.
     * @return builder.
     */
    public static <I extends Identity> Builder<I> builder(IdentityResolver<I> delegate) {
        return new Builder<>(delegate);
    }

    @Override
    public IdentityResult<I> resolveIdentity(Context requestProperties) {
        CompletableFuture<RefreshOutcome<I>> refresh;
        boolean performRefresh = false;
        IdentityResult<I> advisoryResult = null;

        lock.lock();
        try {
            Instant now = clock.instant();
            if (cached == null) {
                refresh = inFlight;
                if (refresh == null) {
                    refresh = new CompletableFuture<>();
                    inFlight = refresh;
                    performRefresh = true;
                }
            } else if (!refreshNeeded(cached, now)) {
                return cached.result;
            } else if (refreshRateLimited(now)) {
                return resolveOutcome(fallbackOutcome(cached, lastFailure, now));
            } else if (!mandatoryRefreshNeeded(cached, now)) {
                refresh = inFlight;
                if (refresh != null) {
                    return cached.result;
                }
                refresh = new CompletableFuture<>();
                inFlight = refresh;
                performRefresh = true;
                advisoryResult = cached.result;
            } else {
                refresh = inFlight;
                if (refresh == null) {
                    refresh = new CompletableFuture<>();
                    inFlight = refresh;
                    performRefresh = true;
                }
            }
        } finally {
            lock.unlock();
        }

        if (performRefresh) {
            if (advisoryResult != null) {
                executeAdvisoryRefresh(requestProperties, refresh, advisoryResult);
                return advisoryResult;
            }
            executeRefresh(requestProperties, refresh);
        }
        return awaitRefresh(refresh);
    }

    @Override
    public Class<I> identityType() {
        return delegate.identityType();
    }

    @Override
    public void invalidate(I rejectedIdentity) {
        // Invalidation must not make the request path wait for refresh lifecycle state.
        if (!lock.tryLock()) {
            return;
        }
        try {
            // An active refresh supersedes this rejection and will determine the next cached identity.
            if (inFlight != null) {
                return;
            }
            if (cached == null || !identityMatcher.test(cached.identity, rejectedIdentity)) {
                return;
            }
            refreshRequired = true;
            nextRefreshAfterSuccessAt = null;
            cancelScheduledRefreshLocked();
            if (nextRefreshAllowedAt != null && clock.instant().isBefore(nextRefreshAllowedAt)) {
                scheduleRefreshLocked(nextRefreshAllowedAt);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            cancelScheduledRefreshLocked();
        } finally {
            lock.unlock();
        }
        if (closeDelegate && delegate instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception error) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to close identity resolver", error);
            }
        }
        if (ownsExecutor) {
            executor.shutdownNow();
        }
    }

    private void executeRefresh(Context requestProperties, CompletableFuture<RefreshOutcome<I>> refresh) {
        try {
            RefreshAttempt<I> attempt = callCredentialSource(requestProperties);
            RefreshOutcome<I> callerOutcome;

            lock.lock();
            try {
                Instant now = clock.instant();
                if (attempt.identity != null && isFresh(attempt.identity, now)) {
                    cached = createCachedValue(attempt.identity, now, requestProperties);
                    nextRefreshAllowedAt = null;
                    lastFailure = null;
                    refreshRequired = false;
                    nextRefreshAfterSuccessAt = firstRefreshAt(cached, now);
                    scheduleRefreshLocked(nextRefreshAfterSuccessAt);
                    callerOutcome = RefreshOutcome.result(cached.result);
                } else {
                    RefreshOutcome<I> failure = attempt.failureOutcome(this);
                    nextRefreshAfterSuccessAt = null;
                    lastFailure = failure;
                    if (attempt.nonRecoverable) {
                        // Non-recoverable failures are not backed off; cached state forces the next call to retry.
                        nextRefreshAllowedAt = null;
                        refreshRequired = cached != null;
                        cancelScheduledRefreshLocked();
                        callerOutcome = failure;
                    } else if (cached == null) {
                        nextRefreshAllowedAt = null;
                        cancelScheduledRefreshLocked();
                        callerOutcome = failure;
                    } else {
                        Duration backoff = refreshBackoff();
                        nextRefreshAllowedAt = now.plus(backoff);
                        boolean useCachedCredentials = allowExpiredCredentials || !isExpired(cached, now);
                        logRefreshFailure(attempt.failureDescription(), backoff, useCachedCredentials);
                        callerOutcome = fallbackOutcome(cached, failure, now);
                        scheduleRefreshLocked(nextRefreshAllowedAt);
                    }
                }
                if (inFlight == refresh) {
                    inFlight = null;
                }
            } finally {
                lock.unlock();
            }

            refresh.complete(callerOutcome);
        } catch (RuntimeException | Error failure) {
            failRefresh(refresh, failure);
            throw failure;
        }
    }

    private void failRefresh(CompletableFuture<RefreshOutcome<I>> refresh, Throwable failure) {
        lock.lock();
        try {
            if (inFlight == refresh) {
                inFlight = null;
            }
        } finally {
            lock.unlock();
        }
        refresh.completeExceptionally(failure);
    }

    private void executeAdvisoryRefresh(
            Context requestProperties,
            CompletableFuture<RefreshOutcome<I>> refresh,
            IdentityResult<I> cachedResult
    ) {
        Context refreshProperties = Context.unmodifiableCopy(requestProperties);
        try {
            executor.execute(() -> executeRefresh(refreshProperties, refresh));
        } catch (RejectedExecutionException error) {
            lock.lock();
            try {
                if (inFlight == refresh) {
                    inFlight = null;
                }
            } finally {
                lock.unlock();
            }
            refresh.complete(RefreshOutcome.result(cachedResult));
        }
    }

    private RefreshAttempt<I> callCredentialSource(Context requestProperties) {
        try {
            IdentityResult<I> result = delegate.resolveIdentity(requestProperties);
            if (result == null) {
                return RefreshAttempt.resultFailure(
                        IdentityResult.ofError(getClass(), "Credential source returned no result"));
            }
            if (result.identity() != null) {
                return RefreshAttempt.success(result.identity());
            }
            return RefreshAttempt.resultFailure(result);
        } catch (NonRecoverableIdentityException error) {
            return RefreshAttempt.nonRecoverable(error);
        } catch (RuntimeException error) {
            return RefreshAttempt.exceptionFailure(error);
        }
    }

    private CachedValue<I> createCachedValue(I identity, Instant obtainedAt, Context requestProperties) {
        Context refreshProperties = Context.unmodifiableCopy(requestProperties);
        Instant expiration = identity.expirationTime();
        if (expiration == null) {
            return new CachedValue<>(identity, null, null, refreshProperties);
        }

        Duration lifetime = Duration.between(obtainedAt, expiration);
        Duration advisoryWindow = configuredAdvisoryWindow == null
                ? defaultAdvisoryRefreshWindow(lifetime)
                : configuredAdvisoryWindow;
        Instant advisoryAt = expiration.minus(advisoryWindow);
        Duration effectiveMandatoryWindow = mandatoryRefreshWindow.compareTo(advisoryWindow) > 0
                ? advisoryWindow
                : mandatoryRefreshWindow;
        return new CachedValue<>(
                identity,
                advisoryAt,
                expiration.minus(effectiveMandatoryWindow),
                refreshProperties);
    }

    private boolean refreshNeeded(CachedValue<I> value, Instant now) {
        boolean advisoryRefreshNeeded = reached(now, value.advisoryRefreshAt)
                && (nextRefreshAfterSuccessAt == null || reached(now, nextRefreshAfterSuccessAt));
        return refreshRequired
                || advisoryRefreshNeeded
                || reached(now, value.mandatoryRefreshAt)
                || isExpired(value, now);
    }

    private boolean mandatoryRefreshNeeded(CachedValue<I> value, Instant now) {
        return refreshRequired || reached(now, value.mandatoryRefreshAt) || isExpired(value, now);
    }

    private boolean refreshRateLimited(Instant now) {
        return nextRefreshAllowedAt != null && now.isBefore(nextRefreshAllowedAt);
    }

    private RefreshOutcome<I> fallbackOutcome(
            CachedValue<I> value,
            RefreshOutcome<I> failure,
            Instant now
    ) {
        if (allowExpiredCredentials || !isExpired(value, now)) {
            return RefreshOutcome.result(value.result);
        }
        if (failure != null) {
            return failure;
        }
        return RefreshOutcome.result(IdentityResult.ofError(
                getClass(),
                "Credentials are expired and credential refresh is rate limited"));
    }

    private IdentityResult<I> resolveOutcome(RefreshOutcome<I> outcome) {
        if (outcome.exception != null) {
            throw outcome.exception;
        }
        return outcome.result;
    }

    private IdentityResult<I> awaitRefresh(CompletableFuture<RefreshOutcome<I>> refresh) {
        try {
            return resolveOutcome(refresh.get());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return IdentityResult.ofError(getClass(), "Interrupted waiting for credential refresh");
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error executionError) {
                throw executionError;
            }
            throw new IllegalStateException("Unexpected credential refresh failure", cause);
        }
    }

    private void scheduleRefreshLocked(Instant refreshAt) {
        cancelScheduledRefreshLocked();
        if (!proactiveRefresh || closed || cached == null || refreshAt == null) {
            return;
        }

        long delayMillis = Math.max(0, Duration.between(clock.instant(), refreshAt).toMillis());
        try {
            scheduledRefresh = executor.schedule(this::runScheduledRefresh, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            scheduledRefresh = null;
        }
    }

    private void runScheduledRefresh() {
        CompletableFuture<RefreshOutcome<I>> refresh = null;
        Context refreshProperties = null;
        lock.lock();
        try {
            scheduledRefresh = null;
            if (closed || cached == null || inFlight != null) {
                return;
            }

            Instant now = clock.instant();
            if (refreshRateLimited(now)) {
                scheduleRefreshLocked(nextRefreshAllowedAt);
                return;
            }
            if (!refreshNeeded(cached, now)) {
                scheduleRefreshLocked(firstRefreshAt(cached, now));
                return;
            }

            refresh = new CompletableFuture<>();
            inFlight = refresh;
            refreshProperties = cached.refreshProperties;
        } finally {
            lock.unlock();
        }

        if (refresh != null) {
            executeRefresh(refreshProperties, refresh);
        }
    }

    private Instant firstRefreshAt(CachedValue<I> value, Instant now) {
        if (value.advisoryRefreshAt != null && now.isBefore(value.advisoryRefreshAt)) {
            return value.advisoryRefreshAt;
        }
        if (value.mandatoryRefreshAt != null && now.isBefore(value.mandatoryRefreshAt)) {
            return value.mandatoryRefreshAt;
        }
        return null;
    }

    private void cancelScheduledRefreshLocked() {
        if (scheduledRefresh != null) {
            scheduledRefresh.cancel(false);
            scheduledRefresh = null;
        }
    }

    private Duration refreshBackoff() {
        long minMillis = refreshBackoffMin.toMillis();
        long maxMillis = refreshBackoffMax.toMillis();
        if (minMillis == maxMillis) {
            return refreshBackoffMin;
        }
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1));
    }

    private void logRefreshFailure(String error, Duration backoff, boolean useCachedCredentials) {
        LOGGER.log(
                System.Logger.Level.WARNING,
                refreshFailureMessage(error, backoff, useCachedCredentials));
    }

    static String refreshFailureMessage(String error, Duration backoff, boolean useCachedCredentials) {
        String cachedCredentialsMessage = useCachedCredentials
                ? "The SDK will continue using cached credentials."
                : "The SDK will not use the expired cached credentials.";
        return "Credential refresh failed: " + error + ". " + cachedCredentialsMessage
                + " A refresh of these credentials will be attempted again after "
                + backoff.toSeconds() + " seconds.";
    }

    private boolean isFresh(I identity, Instant now) {
        Instant expiration = identity.expirationTime();
        return expiration == null || expiration.isAfter(now);
    }

    private static boolean isExpired(CachedValue<?> value, Instant now) {
        Instant expiration = value.identity.expirationTime();
        return expiration != null && !now.isBefore(expiration);
    }

    private static boolean reached(Instant now, Instant threshold) {
        return threshold != null && !now.isBefore(threshold);
    }

    static Duration defaultAdvisoryRefreshWindow(Duration lifetime) {
        if (lifetime.compareTo(Duration.ofMinutes(20)) <= 0) {
            return Duration.ofMinutes(5);
        }
        if (lifetime.compareTo(Duration.ofMinutes(90)) < 0) {
            return Duration.ofMinutes(15);
        }
        return Duration.ofMinutes(60);
    }

    private static final class CachedValue<I extends Identity> {
        final I identity;
        final IdentityResult<I> result;
        final Instant advisoryRefreshAt;
        final Instant mandatoryRefreshAt;
        final Context refreshProperties;

        CachedValue(
                I identity,
                Instant advisoryRefreshAt,
                Instant mandatoryRefreshAt,
                Context refreshProperties
        ) {
            this.identity = identity;
            this.result = IdentityResult.of(identity);
            this.advisoryRefreshAt = advisoryRefreshAt;
            this.mandatoryRefreshAt = mandatoryRefreshAt;
            this.refreshProperties = refreshProperties;
        }
    }

    private static final class RefreshOutcome<I extends Identity> {
        final IdentityResult<I> result;
        final RuntimeException exception;

        private RefreshOutcome(IdentityResult<I> result, RuntimeException exception) {
            this.result = result;
            this.exception = exception;
        }

        static <I extends Identity> RefreshOutcome<I> result(IdentityResult<I> result) {
            return new RefreshOutcome<>(Objects.requireNonNull(result), null);
        }

        static <I extends Identity> RefreshOutcome<I> exception(RuntimeException exception) {
            return new RefreshOutcome<>(null, Objects.requireNonNull(exception));
        }
    }

    private static final class RefreshAttempt<I extends Identity> {
        final I identity;
        final IdentityResult<I> failureResult;
        final RuntimeException failureException;
        final boolean nonRecoverable;

        private RefreshAttempt(
                I identity,
                IdentityResult<I> failureResult,
                RuntimeException failureException,
                boolean nonRecoverable
        ) {
            this.identity = identity;
            this.failureResult = failureResult;
            this.failureException = failureException;
            this.nonRecoverable = nonRecoverable;
        }

        static <I extends Identity> RefreshAttempt<I> success(I identity) {
            return new RefreshAttempt<>(Objects.requireNonNull(identity), null, null, false);
        }

        static <I extends Identity> RefreshAttempt<I> resultFailure(IdentityResult<I> result) {
            return new RefreshAttempt<>(null, Objects.requireNonNull(result), null, false);
        }

        static <I extends Identity> RefreshAttempt<I> exceptionFailure(RuntimeException exception) {
            return new RefreshAttempt<>(null, null, Objects.requireNonNull(exception), false);
        }

        static <I extends Identity> RefreshAttempt<I> nonRecoverable(NonRecoverableIdentityException exception) {
            return new RefreshAttempt<>(null, null, exception, true);
        }

        RefreshOutcome<I> failureOutcome(CachingIdentityResolver<I> owner) {
            if (identity != null) {
                return RefreshOutcome.result(IdentityResult.ofError(
                        owner.getClass(),
                        "Credential source returned credentials that are already expired"));
            }
            if (failureException != null) {
                return RefreshOutcome.exception(failureException);
            }
            return RefreshOutcome.result(failureResult);
        }

        String failureDescription() {
            if (identity != null) {
                return "credential source returned credentials that are already expired";
            }
            if (failureException != null) {
                return failureException.toString();
            }
            return failureResult.error();
        }
    }

    /** Builder for {@link CachingIdentityResolver}. */
    public static final class Builder<I extends Identity> {
        private final IdentityResolver<I> delegate;
        private Duration advisoryRefreshWindow;
        private Duration mandatoryRefreshWindow = DEFAULT_MANDATORY_WINDOW;
        private boolean allowExpiredCredentials;
        private Duration refreshBackoffMin = DEFAULT_BACKOFF_MIN;
        private Duration refreshBackoffMax = DEFAULT_BACKOFF_MAX;
        private Clock clock = Clock.systemUTC();
        private ScheduledExecutorService executor;
        private boolean closeDelegate;
        private boolean proactiveRefresh = true;
        private BiPredicate<I, I> identityMatcher = Objects::equals;

        private Builder(IdentityResolver<I> delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        /**
         * Sets an explicit advisory refresh window. By default the window is derived from credential lifetime.
         */
        public Builder<I> prefetchBuffer(Duration advisoryRefreshWindow) {
            this.advisoryRefreshWindow = requirePositive(advisoryRefreshWindow, "advisoryRefreshWindow");
            return this;
        }

        /** Sets the mandatory blocking refresh window. Default: 1 minute. */
        public Builder<I> mandatoryRefreshWindow(Duration mandatoryRefreshWindow) {
            this.mandatoryRefreshWindow = requirePositive(mandatoryRefreshWindow, "mandatoryRefreshWindow");
            return this;
        }

        /** Enables returning cached expired identities after refresh failure. */
        public Builder<I> allowExpiredCredentials(boolean allowExpiredCredentials) {
            this.allowExpiredCredentials = allowExpiredCredentials;
            return this;
        }

        /**
         * Sets the base stale refresh delay. The actual delay is uniformly selected from this value to twice this
         * value. The default is 5-10 minutes.
         */
        public Builder<I> staleRefreshDelay(Duration staleRefreshDelay) {
            this.refreshBackoffMin = requirePositive(staleRefreshDelay, "staleRefreshDelay");
            this.refreshBackoffMax = staleRefreshDelay.multipliedBy(2);
            return this;
        }

        /** Sets the clock used for refresh decisions. */
        public Builder<I> clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /** Sets the executor used for proactive and advisory refresh work. */
        public Builder<I> executor(ScheduledExecutorService executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        /**
         * Sets whether closing the cache also closes an {@link AutoCloseable} delegate. Disabled by default.
         *
         * <p>Enable this only when the cache owns the delegate rather than wrapping a caller-supplied resolver.
         */
        public Builder<I> closeDelegate(boolean closeDelegate) {
            this.closeDelegate = closeDelegate;
            return this;
        }

        /** Enables or disables proactive scheduled refresh. Enabled by default. */
        public Builder<I> proactiveRefresh(boolean proactiveRefresh) {
            this.proactiveRefresh = proactiveRefresh;
            return this;
        }

        /** Sets how an invalidated identity is matched against the currently cached identity. */
        public Builder<I> identityMatcher(BiPredicate<I, I> identityMatcher) {
            this.identityMatcher = Objects.requireNonNull(identityMatcher, "identityMatcher");
            return this;
        }

        Builder<I> refreshBackoff(Duration minimum, Duration maximum) {
            this.refreshBackoffMin = requirePositive(minimum, "refreshBackoffMin");
            this.refreshBackoffMax = requirePositive(maximum, "refreshBackoffMax");
            return this;
        }

        /** Builds the caching resolver. */
        public CachingIdentityResolver<I> build() {
            if (refreshBackoffMin.compareTo(refreshBackoffMax) > 0) {
                throw new IllegalArgumentException("refreshBackoffMin must not exceed refreshBackoffMax");
            }
            return new CachingIdentityResolver<>(this);
        }

        private static Duration requirePositive(Duration duration, String name) {
            Objects.requireNonNull(duration, name);
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return duration;
        }
    }
}
