/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;

class CachingIdentityResolverTest {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private CachingIdentityResolver<?> resolver;

    @AfterEach
    void tearDown() {
        if (resolver != null) {
            resolver.close();
        }
        executor.shutdownNow();
    }

    @Test
    void coldStartBlocksAndCachesResult() {
        var identity = new TestIdentity("cached", Instant.now().plusSeconds(3600));
        var delegate = new CountingResolver(identity);
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .build();

        IdentityResult<TestIdentity> result = resolve();
        assertNotNull(result.identity());
        assertEquals("cached", result.identity().value);
        assertEquals(1, delegate.callCount.get());

        // Second call returns cached — no delegate invocation.
        IdentityResult<TestIdentity> result2 = resolve();
        assertEquals("cached", result2.identity().value);
        assertEquals(1, delegate.callCount.get());
    }

    @Test
    void backgroundRefreshHappensBeforeExpiry() throws InterruptedException {
        Instant now = Instant.now();
        // Credentials expire in 2 seconds, prefetch buffer is 1 second → refresh at T+1s.
        var delegate = new CountingResolver(expiringIdentity(now.plusSeconds(2)));
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .prefetchBuffer(Duration.ofSeconds(1))
                .build();

        // Cold start.
        resolve();
        assertEquals(1, delegate.callCount.get());

        // Wait for background refresh to fire (should happen ~1s from now).
        Thread.sleep(1500);
        assertTrue(delegate.callCount.get() >= 2, "Expected background refresh, got " + delegate.callCount.get());
    }

    @Test
    void nonExpiringIdentityIsCachedIndefinitely() throws InterruptedException {
        var delegate = new CountingResolver(new TestIdentity("permanent", null));
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .build();

        resolve();
        Thread.sleep(200);
        resolve();
        assertEquals(1, delegate.callCount.get());
    }

    @Test
    void allowExpiredCredentialsReturnsStaleOnFailure() {
        AtomicInteger calls = new AtomicInteger(0);
        IdentityResolver<TestIdentity> delegate = new IdentityResolver<>() {
            @Override
            public IdentityResult<TestIdentity> resolveIdentity(Context ctx) {
                if (calls.incrementAndGet() == 1) {
                    return IdentityResult.of(expiringIdentity(Instant.now().minusSeconds(10)));
                }
                throw new RuntimeException("refresh failed");
            }

            @Override
            public Class<TestIdentity> identityType() {
                return TestIdentity.class;
            }
        };

        // Use a fixed clock that's past expiration so the cache is immediately stale.
        Clock pastClock = Clock.fixed(Instant.now().plusSeconds(60), ZoneId.of("UTC"));
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .allowExpiredCredentials(true)
                .clock(pastClock)
                .prefetchBuffer(Duration.ofSeconds(1))
                .build();

        // Cold start succeeds (returns expired identity).
        IdentityResult<TestIdentity> result = resolve();
        assertNotNull(result.identity());

        // Trigger refresh — it fails, but we still get the stale value.
        IdentityResult<TestIdentity> result2 = resolve();
        assertNotNull(result2.identity());
    }

    @Test
    void strictModeReturnsErrorWhenExpiredAndRefreshFails() {
        Instant expiration = Instant.now().plusMillis(50); // Expires very soon.
        var identity = new TestIdentity("expiring", expiration);
        AtomicInteger calls = new AtomicInteger(0);
        IdentityResolver<TestIdentity> delegate = new IdentityResolver<>() {
            @Override
            public IdentityResult<TestIdentity> resolveIdentity(Context ctx) {
                if (calls.incrementAndGet() == 1) {
                    return IdentityResult.of(identity);
                }
                return IdentityResult.ofError(getClass(), "no creds");
            }

            @Override
            public Class<TestIdentity> identityType() {
                return TestIdentity.class;
            }
        };

        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .allowExpiredCredentials(false)
                .prefetchBuffer(Duration.ofMillis(10))
                .build();

        // Cold start succeeds.
        IdentityResult<TestIdentity> first = resolve();
        assertNotNull(first.identity());

        // Wait for expiration.
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Now expired + strict → blocks for retry → delegate returns error.
        IdentityResult<TestIdentity> result = resolve();
        assertNull(result.identity());
        assertNotNull(result.error());
    }

    @Test
    void invalidateForcesNextCallToRefresh() {
        var delegate = new CountingResolver(expiringIdentity(Instant.now().plusSeconds(3600)));
        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .build();

        resolve();
        assertEquals(1, delegate.callCount.get());

        resolver.invalidate();
        resolve();
        assertEquals(2, delegate.callCount.get());
    }

    @Test
    void concurrentColdStartOnlyCallsDelegateOnce() throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        var delegate = new IdentityResolver<TestIdentity>() {
            final AtomicInteger callCount = new AtomicInteger(0);

            @Override
            public IdentityResult<TestIdentity> resolveIdentity(Context ctx) {
                callCount.incrementAndGet();
                try {
                    Thread.sleep(100); // Simulate slow first fetch.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return IdentityResult.of(new TestIdentity("shared", Instant.now().plusSeconds(3600)));
            }

            @Override
            public Class<TestIdentity> identityType() {
                return TestIdentity.class;
            }
        };

        resolver = CachingIdentityResolver.builder(delegate)
                .executor(executor)
                .build();

        int threadCount = 10;
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicReference<String> firstValue = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    return;
                }
                @SuppressWarnings("unchecked")
                var r = (CachingIdentityResolver<TestIdentity>) resolver;
                IdentityResult<TestIdentity> result = r.resolveIdentity(Context.empty());
                firstValue.compareAndSet(null, result.identity().value);
                done.countDown();
            }).start();
        }

        startGate.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(1, delegate.callCount.get());
        assertEquals("shared", firstValue.get());
    }

    @SuppressWarnings("unchecked")
    private IdentityResult<TestIdentity> resolve() {
        return ((CachingIdentityResolver<TestIdentity>) resolver).resolveIdentity(Context.empty());
    }

    private static TestIdentity expiringIdentity(Instant expiration) {
        return new TestIdentity("id-" + System.nanoTime(), expiration);
    }

    record TestIdentity(String value, Instant expirationTime) implements Identity {}

    static class CountingResolver implements IdentityResolver<TestIdentity> {
        final AtomicInteger callCount = new AtomicInteger(0);
        private final TestIdentity identity;

        CountingResolver(TestIdentity identity) {
            this.identity = identity;
        }

        @Override
        public IdentityResult<TestIdentity> resolveIdentity(Context ctx) {
            callCount.incrementAndGet();
            return IdentityResult.of(identity);
        }

        @Override
        public Class<TestIdentity> identityType() {
            return TestIdentity.class;
        }
    }
}
