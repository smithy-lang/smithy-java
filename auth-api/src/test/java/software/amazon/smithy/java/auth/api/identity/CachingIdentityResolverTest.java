/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;

class CachingIdentityResolverTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private CachingIdentityResolver<TestIdentity> resolver;

    @AfterEach
    void tearDown() {
        if (resolver != null) {
            resolver.close();
        }
        executor.shutdownNow();
    }

    @Test
    void coldStartBlocksAndCachesResult() {
        var delegate = new QueueResolver(identity("cached", BASE.plusSeconds(3600)));
        resolver = resolver(delegate, new MutableClock(BASE), true);

        assertEquals("cached", resolve().identity().value());
        assertEquals("cached", resolve().identity().value());
        assertEquals(1, delegate.calls.get());
    }

    @Test
    void failedColdStartReturnsSourceErrorAndRetriesOnNextCall() {
        var delegate = new QueueResolver(
                error("source unavailable"),
                identity("fresh", BASE.plusSeconds(3600)));
        resolver = resolver(delegate, new MutableClock(BASE), true);

        IdentityResult<TestIdentity> failed = resolve();
        assertNull(failed.identity());
        assertEquals("source unavailable", failed.error());

        assertEquals("fresh", resolve().identity().value());
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void concurrentColdStartUsesOneSourceCall() throws Exception {
        var delegate = new BlockingResolver(identity("initial", BASE.plusSeconds(3600)), true);
        resolver = resolver(delegate, new MutableClock(BASE), true);
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            List<Future<IdentityResult<TestIdentity>>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(callers.submit(this::resolve));
            }

            assertTrue(delegate.started.await(5, TimeUnit.SECONDS));
            assertEquals(1, delegate.calls.get());
            delegate.release.countDown();

            for (var result : results) {
                assertEquals("initial", result.get(5, TimeUnit.SECONDS).identity().value());
            }
            assertEquals(1, delegate.calls.get());
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void interruptedColdStartWaiterReturnsAnError() throws Exception {
        var delegate = new BlockingResolver(identity("initial", BASE.plusSeconds(3600)), true);
        resolver = resolver(delegate, new MutableClock(BASE), true);
        ExecutorService sourceCaller = Executors.newSingleThreadExecutor();
        var waitingResult = new AtomicReference<IdentityResult<TestIdentity>>();
        var interruptRestored = new AtomicBoolean();

        try {
            Future<IdentityResult<TestIdentity>> sourceResult = sourceCaller.submit(this::resolve);
            assertTrue(delegate.started.await(5, TimeUnit.SECONDS));

            Thread waiter = new Thread(() -> {
                waitingResult.set(resolve());
                interruptRestored.set(Thread.currentThread().isInterrupted());
            });
            waiter.start();
            await(() -> waiter.getState() == Thread.State.WAITING);

            waiter.interrupt();
            waiter.join(TimeUnit.SECONDS.toMillis(5));

            assertFalse(waiter.isAlive());
            assertNull(waitingResult.get().identity());
            assertTrue(waitingResult.get().error().contains("Interrupted waiting for credential refresh"));
            assertTrue(interruptRestored.get());

            delegate.release.countDown();
            assertEquals("initial", sourceResult.get(5, TimeUnit.SECONDS).identity().value());
        } finally {
            delegate.release.countDown();
            sourceCaller.shutdownNow();
        }
    }

    @Test
    void unexpectedRefreshFailureDoesNotStrandFutureCallers() {
        var delegate = new QueueResolver(
                identity("failed", BASE.plusSeconds(3600)),
                identity("fresh", BASE.plusSeconds(3600)));
        resolver = resolver(delegate, new FailOnceClock(BASE, 2), true);

        assertThrows(IllegalStateException.class, this::resolve);

        assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> assertEquals("fresh", resolve().identity().value()));
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void advisoryRefreshDoesNotBlockCallers() throws Exception {
        var clock = new MutableClock(BASE);
        var initial = identity("initial", BASE.plus(Duration.ofHours(2)));
        var refreshed = identity("refreshed", BASE.plus(Duration.ofHours(3)));
        var delegate = new BlockingRefreshResolver(initial, refreshed);
        resolver = resolver(delegate, clock, true);

        assertEquals("initial", resolve().identity().value());
        clock.advance(Duration.ofMinutes(61));

        assertEquals("initial", resolve().identity().value());
        assertTrue(delegate.refreshStarted.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < 10; i++) {
            assertEquals("initial", resolve().identity().value());
        }
        assertEquals(2, delegate.calls.get());

        delegate.releaseRefresh.countDown();
        await(() -> "refreshed".equals(resolve().identity().value()));
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void advisoryRefreshFailureUsesCachedCredentialsAndBacksOff() throws Exception {
        var clock = new MutableClock(BASE);
        var delegate = new QueueResolver(
                identity("cached", BASE.plus(Duration.ofHours(2))),
                error("source unavailable"),
                identity("fresh", BASE.plus(Duration.ofHours(3))));
        resolver = resolver(delegate, clock, true);

        assertEquals("cached", resolve().identity().value());
        clock.advance(Duration.ofMinutes(61));
        assertEquals("cached", resolve().identity().value());
        await(() -> delegate.calls.get() == 2);
        assertEquals(2, delegate.calls.get());

        clock.advance(Duration.ofSeconds(299));
        assertEquals("cached", resolve().identity().value());
        assertEquals(2, delegate.calls.get());

        clock.advance(Duration.ofSeconds(1));
        await(() -> "fresh".equals(resolve().identity().value()));
        assertEquals(3, delegate.calls.get());
    }

    @Test
    void invalidationWaitsForInFlightAdvisoryRefresh() throws Exception {
        var clock = new MutableClock(BASE);
        var initial = identity("initial", BASE.plus(Duration.ofHours(2)));
        var refreshed = identity("refreshed", BASE.plus(Duration.ofHours(3)));
        var delegate = new BlockingRefreshResolver(initial, refreshed);
        resolver = resolver(delegate, clock, true);

        assertEquals("initial", resolve().identity().value());
        clock.advance(Duration.ofMinutes(61));

        ExecutorService callers = Executors.newSingleThreadExecutor();
        try {
            assertEquals("initial", resolve().identity().value());
            assertTrue(delegate.refreshStarted.await(5, TimeUnit.SECONDS));

            resolver.invalidate(initial);
            var invalidatedCaller = callers.submit(this::resolve);
            assertTrue(!invalidatedCaller.isDone());
            assertEquals(2, delegate.calls.get());

            delegate.releaseRefresh.countDown();
            assertEquals("refreshed", invalidatedCaller.get(5, TimeUnit.SECONDS).identity().value());
            assertEquals(2, delegate.calls.get());
            assertEquals("refreshed", resolve().identity().value());
            assertEquals(2, delegate.calls.get());
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void invalidationDuringRefreshIsPreservedWhenSourceReturnsSameIdentity() throws Exception {
        var clock = new MutableClock(BASE);
        var initial = identity("same", BASE.plus(Duration.ofHours(2)));
        var refreshed = identity("same", BASE.plus(Duration.ofHours(3)));
        var delegate = new BlockingRefreshResolver(initial, refreshed);
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .clock(clock)
                .allowExpiredCredentials(true)
                .proactiveRefresh(false)
                .identityMatcher((cached, rejected) -> cached.value().equals(rejected.value()))
                .build();

        assertEquals("same", resolve().identity().value());
        clock.advance(Duration.ofMinutes(61));
        assertEquals("same", resolve().identity().value());
        assertTrue(delegate.refreshStarted.await(5, TimeUnit.SECONDS));

        resolver.invalidate(initial);
        delegate.releaseRefresh.countDown();

        assertEquals("same", resolve().identity().value());
        assertEquals("same", resolve().identity().value());
        assertEquals(3, delegate.calls.get());
    }

    @Test
    void mandatoryRefreshBlocksConcurrentCallersOnOneSourceCall() throws Exception {
        var clock = new MutableClock(BASE);
        var initial = identity("initial", BASE.plusSeconds(3600));
        var refreshed = identity("refreshed", BASE.plusSeconds(7200));
        var delegate = new BlockingRefreshResolver(initial, refreshed);
        resolver = resolver(delegate, clock, true);
        assertNotNull(resolve().identity());
        clock.advance(Duration.ofSeconds(3590));

        ExecutorService callers = Executors.newFixedThreadPool(6);
        try {
            List<Future<IdentityResult<TestIdentity>>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                results.add(callers.submit(this::resolve));
            }
            assertTrue(delegate.refreshStarted.await(5, TimeUnit.SECONDS));
            assertEquals(2, delegate.calls.get());
            for (var result : results) {
                assertTrue(!result.isDone());
            }

            delegate.releaseRefresh.countDown();
            for (var result : results) {
                assertEquals("refreshed", result.get(5, TimeUnit.SECONDS).identity().value());
            }
            assertEquals(2, delegate.calls.get());
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void failedRefreshUsesCachedCredentialsAndBacksOff() {
        var clock = new MutableClock(BASE);
        var delegate = new QueueResolver(
                identity("cached", BASE.plusSeconds(60)),
                error("source unavailable"),
                identity("fresh", BASE.plusSeconds(7200)));
        resolver = resolver(delegate, clock, true);

        assertEquals("cached", resolve().identity().value());
        clock.advance(Duration.ofSeconds(61));
        assertEquals("cached", resolve().identity().value());
        assertEquals(2, delegate.calls.get());

        clock.advance(Duration.ofSeconds(299));
        assertEquals("cached", resolve().identity().value());
        assertEquals(2, delegate.calls.get());

        clock.advance(Duration.ofSeconds(1));
        assertEquals("fresh", resolve().identity().value());
        assertEquals(3, delegate.calls.get());
    }

    @Test
    void invalidationPreservesCachedCredentialsAndBackoff() {
        var clock = new MutableClock(BASE);
        var cached = identity("cached", BASE.plusSeconds(60));
        var delegate = new QueueResolver(
                cached,
                error("source unavailable"),
                identity("fresh", BASE.plusSeconds(7200)));
        resolver = resolver(delegate, clock, true);

        resolve();
        clock.advance(Duration.ofSeconds(61));
        resolve();
        resolver.invalidate(cached);

        assertEquals("cached", resolve().identity().value());
        assertEquals(2, delegate.calls.get());
        clock.advance(Duration.ofMinutes(5));
        assertEquals("fresh", resolve().identity().value());
        assertEquals(3, delegate.calls.get());
    }

    @Test
    void matchingInvalidationForcesMandatoryRefresh() {
        var clock = new MutableClock(BASE);
        var initial = identity("initial", BASE.plusSeconds(3600));
        var delegate = new QueueResolver(initial, identity("fresh", BASE.plusSeconds(7200)));
        resolver = resolver(delegate, clock, true);

        assertEquals("initial", resolve().identity().value());
        resolver.invalidate(new TestIdentity("other", initial.expirationTime()));
        assertEquals("initial", resolve().identity().value());
        assertEquals(1, delegate.calls.get());

        resolver.invalidate(initial);
        assertEquals("fresh", resolve().identity().value());
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void staleSourceCredentialsAreRefreshFailures() {
        var clock = new MutableClock(BASE);
        var delegate = new QueueResolver(
                identity("cached", BASE.plusSeconds(60)),
                identity("already-expired", BASE.minusSeconds(1)));
        resolver = resolver(delegate, clock, true);

        resolve();
        clock.advance(Duration.ofSeconds(61));

        assertEquals("cached", resolve().identity().value());
        assertEquals(2, delegate.calls.get());
        assertEquals("cached", resolve().identity().value());
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void staleCredentialsOnColdStartReturnAnError() {
        var clock = new MutableClock(BASE);
        var delegate = new QueueResolver(identity("expired", BASE));
        resolver = resolver(delegate, clock, true);

        IdentityResult<TestIdentity> result = resolve();

        assertNull(result.identity());
        assertTrue(result.error().contains("already expired"));
    }

    @Test
    void strictModeDoesNotReturnExpiredCredentialsDuringBackoff() {
        var clock = new MutableClock(BASE);
        var delegate = new QueueResolver(
                identity("cached", BASE.plusSeconds(60)),
                error("source unavailable"));
        resolver = resolver(delegate, clock, false);

        resolve();
        clock.advance(Duration.ofSeconds(61));
        IdentityResult<TestIdentity> failed = resolve();
        assertNull(failed.identity());
        assertEquals("source unavailable", failed.error());

        IdentityResult<TestIdentity> rateLimited = resolve();
        assertNull(rateLimited.identity());
        assertEquals("source unavailable", rateLimited.error());
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void strictModeRefreshFailureMessageDoesNotClaimExpiredCredentialsAreUsed() {
        assertEquals(
                "Credential refresh failed: source unavailable. "
                        + "The SDK will not use the expired cached credentials. "
                        + "A refresh of these credentials will be attempted again after 300 seconds.",
                CachingIdentityResolver.refreshFailureMessage(
                        "source unavailable",
                        Duration.ofMinutes(5),
                        false));
    }

    @Test
    void advisoryNonRecoverableFailureIsRaisedOnNextResolutionWithoutBackoff() throws Exception {
        var clock = new MutableClock(BASE);
        var delegate = new QueueResolver(
                identity("cached", BASE.plus(Duration.ofHours(2))),
                new NonRecoverableIdentityException("reauthenticate"),
                new NonRecoverableIdentityException("reauthenticate"),
                identity("fresh", BASE.plus(Duration.ofHours(3))));
        resolver = resolver(delegate, clock, true);

        resolve();
        clock.advance(Duration.ofMinutes(61));
        assertEquals("cached", resolve().identity().value());
        await(() -> delegate.calls.get() == 2);

        var error = assertThrows(NonRecoverableIdentityException.class, this::resolve);
        assertEquals("reauthenticate", error.getMessage());
        assertEquals(3, delegate.calls.get());

        assertEquals("fresh", resolve().identity().value());
        assertEquals(4, delegate.calls.get());
    }

    @Test
    void nonRecoverableFailureIsNotBackedOff() {
        var clock = new MutableClock(BASE);
        var delegate = new QueueResolver(
                identity("cached", BASE.plusSeconds(60)),
                new NonRecoverableIdentityException("reauthenticate"),
                identity("fresh", BASE.plusSeconds(7200)));
        resolver = resolver(delegate, clock, true);

        resolve();
        clock.advance(Duration.ofSeconds(61));
        var error = assertThrows(NonRecoverableIdentityException.class, this::resolve);
        assertEquals("reauthenticate", error.getMessage());

        assertEquals("fresh", resolve().identity().value());
        assertEquals(3, delegate.calls.get());
    }

    @Test
    void computesAdvisoryWindowFromCredentialLifetime() {
        assertEquals(
                Duration.ofMinutes(5),
                CachingIdentityResolver.defaultAdvisoryRefreshWindow(Duration.ofMinutes(20)));
        assertEquals(
                Duration.ofMinutes(15),
                CachingIdentityResolver.defaultAdvisoryRefreshWindow(Duration.ofMinutes(21)));
        assertEquals(
                Duration.ofMinutes(15),
                CachingIdentityResolver.defaultAdvisoryRefreshWindow(Duration.ofMinutes(89)));
        assertEquals(
                Duration.ofMinutes(60),
                CachingIdentityResolver.defaultAdvisoryRefreshWindow(Duration.ofMinutes(90)));
    }

    @Test
    void configuredAdvisoryWindowOverridesLifetimeDefault() throws Exception {
        var clock = new MutableClock(BASE);
        var delegate = new QueueResolver(
                identity("initial", BASE.plus(Duration.ofHours(1))),
                identity("fresh", BASE.plus(Duration.ofHours(2))));
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .clock(clock)
                .allowExpiredCredentials(true)
                .prefetchBuffer(Duration.ofMinutes(10))
                .proactiveRefresh(false)
                .build();

        assertEquals("initial", resolve().identity().value());
        clock.advance(Duration.ofMinutes(49));
        assertEquals("initial", resolve().identity().value());
        assertEquals(1, delegate.calls.get());

        clock.advance(Duration.ofMinutes(1));
        assertEquals("initial", resolve().identity().value());
        await(() -> "fresh".equals(resolve().identity().value()));
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void waitsForMandatoryWindowWhenAdvisoryWindowEqualsLifetime() {
        var clock = new MutableClock(BASE);
        var delegate = new QueueResolver(
                identity("initial", BASE.plusSeconds(60)),
                identity("fresh", BASE.plusSeconds(3600)));
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .clock(clock)
                .allowExpiredCredentials(true)
                .prefetchBuffer(Duration.ofSeconds(60))
                .mandatoryRefreshWindow(Duration.ofSeconds(30))
                .proactiveRefresh(false)
                .build();

        assertEquals("initial", resolve().identity().value());
        assertEquals("initial", resolve().identity().value());
        assertEquals(1, delegate.calls.get());

        clock.advance(Duration.ofSeconds(29));
        assertEquals("initial", resolve().identity().value());
        assertEquals(1, delegate.calls.get());

        clock.advance(Duration.ofSeconds(1));
        assertEquals("fresh", resolve().identity().value());
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void proactiveRefreshWaitsForMandatoryWindowWhenAdvisoryWindowIsAlreadyOpen() throws Exception {
        var calls = new AtomicInteger();
        var refreshed = new CountDownLatch(1);
        IdentityResolver<TestIdentity> delegate = new IdentityResolver<>() {
            @Override
            public IdentityResult<TestIdentity> resolveIdentity(Context requestProperties) {
                if (calls.incrementAndGet() == 1) {
                    return IdentityResult.of(identity("initial", Instant.now().plusMillis(500)));
                }
                refreshed.countDown();
                return IdentityResult.of(identity("refreshed", Instant.now().plusSeconds(3600)));
            }

            @Override
            public Class<TestIdentity> identityType() {
                return TestIdentity.class;
            }
        };
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .clock(Clock.systemUTC())
                .allowExpiredCredentials(true)
                .prefetchBuffer(Duration.ofSeconds(1))
                .mandatoryRefreshWindow(Duration.ofMillis(100))
                .build();

        assertEquals("initial", resolve().identity().value());
        assertTrue(!refreshed.await(100, TimeUnit.MILLISECONDS));
        assertTrue(refreshed.await(5, TimeUnit.SECONDS));
        assertEquals(2, calls.get());
        assertEquals("refreshed", resolve().identity().value());
    }

    @Test
    void mandatoryWindowIsClampedToComputedAdvisoryWindow() {
        var clock = new MutableClock(BASE);
        var delegate = new QueueResolver(
                identity("initial", BASE.plus(Duration.ofMinutes(20))),
                identity("fresh", BASE.plus(Duration.ofHours(1))));
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .clock(clock)
                .allowExpiredCredentials(true)
                .mandatoryRefreshWindow(Duration.ofMinutes(10))
                .proactiveRefresh(false)
                .build();

        assertEquals("initial", resolve().identity().value());
        clock.advance(Duration.ofMinutes(10));
        assertEquals("initial", resolve().identity().value());
        assertEquals(1, delegate.calls.get());

        clock.advance(Duration.ofMinutes(5));
        assertEquals("fresh", resolve().identity().value());
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void mandatoryWindowIsClampedToConfiguredAdvisoryWindow() {
        var clock = new MutableClock(BASE);
        var delegate = new QueueResolver(
                identity("initial", BASE.plus(Duration.ofMinutes(10))),
                identity("fresh", BASE.plus(Duration.ofHours(1))));
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .clock(clock)
                .allowExpiredCredentials(true)
                .prefetchBuffer(Duration.ofSeconds(30))
                .proactiveRefresh(false)
                .build();

        assertEquals("initial", resolve().identity().value());
        clock.advance(Duration.ofMinutes(9).plusSeconds(29));
        assertEquals("initial", resolve().identity().value());
        assertEquals(1, delegate.calls.get());

        clock.advance(Duration.ofSeconds(1));
        assertEquals("fresh", resolve().identity().value());
        assertEquals(2, delegate.calls.get());
    }

    @Test
    void proactiveRefreshRetriesAfterBackoff() throws Exception {
        Instant now = Instant.now();
        var delegate = new QueueResolver(
                identity("initial", now.plusMillis(300)),
                error("source unavailable"),
                identity("fresh", now.plusSeconds(3600)));
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .clock(Clock.systemUTC())
                .allowExpiredCredentials(true)
                .prefetchBuffer(Duration.ofMillis(200))
                .mandatoryRefreshWindow(Duration.ofMillis(50))
                .refreshBackoff(Duration.ofMillis(50), Duration.ofMillis(50))
                .build();

        assertEquals("initial", resolve().identity().value());
        await(() -> delegate.calls.get() >= 3);
        assertEquals("fresh", resolve().identity().value());
    }

    @Test
    void proactiveRefreshPreservesResolutionContext() throws Exception {
        var property = Context.<String>key("refresh property");
        var refreshedProperty = new AtomicReference<String>();
        var refreshed = new CountDownLatch(1);
        var calls = new AtomicInteger();
        Instant now = Instant.now();
        IdentityResolver<TestIdentity> delegate = new IdentityResolver<>() {
            @Override
            public IdentityResult<TestIdentity> resolveIdentity(Context requestProperties) {
                if (calls.incrementAndGet() == 1) {
                    return IdentityResult.of(identity("initial", now.plusMillis(300)));
                }
                refreshedProperty.set(requestProperties.get(property));
                refreshed.countDown();
                return IdentityResult.of(identity("refreshed", now.plusSeconds(3600)));
            }

            @Override
            public Class<TestIdentity> identityType() {
                return TestIdentity.class;
            }
        };
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .clock(Clock.systemUTC())
                .prefetchBuffer(Duration.ofMillis(200))
                .mandatoryRefreshWindow(Duration.ofMillis(50))
                .build();
        var properties = Context.create();
        properties.put(property, "expected");

        assertEquals("initial", resolver.resolveIdentity(properties).identity().value());
        assertTrue(refreshed.await(5, TimeUnit.SECONDS));
        assertEquals("expected", refreshedProperty.get());
    }

    @Test
    void nonExpiringIdentityIsCachedUntilInvalidated() {
        var clock = new MutableClock(BASE);
        var first = identity("first", null);
        var delegate = new QueueResolver(first, identity("second", null));
        resolver = resolver(delegate, clock, true);

        assertEquals("first", resolve().identity().value());
        clock.advance(Duration.ofDays(30));
        assertEquals("first", resolve().identity().value());
        resolver.invalidate(first);
        assertEquals("second", resolve().identity().value());
    }

    @Test
    void doesNotCloseCallerSuppliedDelegateByDefault() {
        var delegate = new CloseableResolver();
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .build();

        resolver.close();

        assertFalse(delegate.closed.get());
    }

    @Test
    void closesOwnedDelegateWhenConfigured() {
        var delegate = new CloseableResolver();
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .closeDelegate(true)
                .build();

        resolver.close();

        assertTrue(delegate.closed.get());
    }

    private CachingIdentityResolver<TestIdentity> resolver(
            IdentityResolver<TestIdentity> delegate,
            Clock clock,
            boolean allowExpired
    ) {
        return CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .clock(clock)
                .allowExpiredCredentials(allowExpired)
                .proactiveRefresh(false)
                .refreshBackoff(Duration.ofMinutes(5), Duration.ofMinutes(5))
                .build();
    }

    private IdentityResult<TestIdentity> resolve() {
        return resolver.resolveIdentity(Context.empty());
    }

    private static TestIdentity identity(String value, Instant expiration) {
        return new TestIdentity(value, expiration);
    }

    private static IdentityResult<TestIdentity> error(String message) {
        return IdentityResult.ofError(CachingIdentityResolverTest.class, message);
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "condition was not satisfied before timeout");
    }

    private record TestIdentity(String value, Instant expirationTime) implements Identity {}

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant now) {
            this.now = new AtomicReference<>(now);
        }

        void advance(Duration duration) {
            now.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    private static final class FailOnceClock extends Clock {
        private final Instant now;
        private final int failureCall;
        private final AtomicInteger calls = new AtomicInteger();

        FailOnceClock(Instant now, int failureCall) {
            this.now = now;
            this.failureCall = failureCall;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            if (calls.incrementAndGet() == failureCall) {
                throw new IllegalStateException("clock failure");
            }
            return now;
        }
    }

    private static final class QueueResolver implements IdentityResolver<TestIdentity> {
        private final Deque<Object> responses = new ArrayDeque<>();
        private final AtomicInteger calls = new AtomicInteger();

        QueueResolver(Object... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        @SuppressWarnings("unchecked")
        public synchronized IdentityResult<TestIdentity> resolveIdentity(Context requestProperties) {
            calls.incrementAndGet();
            Object response = responses.removeFirst();
            if (response instanceof RuntimeException error) {
                throw error;
            }
            if (response instanceof IdentityResult<?> result) {
                return (IdentityResult<TestIdentity>) result;
            }
            return IdentityResult.of((TestIdentity) response);
        }

        @Override
        public Class<TestIdentity> identityType() {
            return TestIdentity.class;
        }
    }

    private static final class CloseableResolver implements IdentityResolver<TestIdentity>, AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public IdentityResult<TestIdentity> resolveIdentity(Context requestProperties) {
            return IdentityResult.of(identity("identity", null));
        }

        @Override
        public Class<TestIdentity> identityType() {
            return TestIdentity.class;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    private static final class BlockingResolver implements IdentityResolver<TestIdentity> {
        private final TestIdentity identity;
        private final boolean block;
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        BlockingResolver(TestIdentity identity, boolean block) {
            this.identity = identity;
            this.block = block;
        }

        @Override
        public IdentityResult<TestIdentity> resolveIdentity(Context requestProperties) {
            calls.incrementAndGet();
            started.countDown();
            if (block) {
                awaitLatch(release);
            }
            return IdentityResult.of(identity);
        }

        @Override
        public Class<TestIdentity> identityType() {
            return TestIdentity.class;
        }
    }

    private static final class BlockingRefreshResolver implements IdentityResolver<TestIdentity> {
        private final TestIdentity initial;
        private final TestIdentity refreshed;
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch refreshStarted = new CountDownLatch(1);
        private final CountDownLatch releaseRefresh = new CountDownLatch(1);

        BlockingRefreshResolver(TestIdentity initial, TestIdentity refreshed) {
            this.initial = initial;
            this.refreshed = refreshed;
        }

        @Override
        public IdentityResult<TestIdentity> resolveIdentity(Context requestProperties) {
            if (calls.incrementAndGet() == 1) {
                return IdentityResult.of(initial);
            }
            refreshStarted.countDown();
            awaitLatch(releaseRefresh);
            return IdentityResult.of(refreshed);
        }

        @Override
        public Class<TestIdentity> identityType() {
            return TestIdentity.class;
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(error);
        }
    }
}
