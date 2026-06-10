/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.identity.CachingIdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;

class S3ExpressIdentityCacheTest {

    @Test
    void getReturnsSameResolverAcrossCalls() {
        var factoryCalls = new AtomicInteger();
        var cache = new S3ExpressIdentityCache(key -> {
            factoryCalls.incrementAndGet();
            return staticResolver();
        });

        var key = new S3ExpressIdentityKey("bucket", AwsCredentialsIdentity.create("AKID", "sk"));

        var first = cache.get(key);
        var second = cache.get(key);

        assertThat(second, is(sameInstance(first)));
        assertThat(factoryCalls.get(), is(1));
    }

    @Test
    void differentKeysGetDifferentResolvers() {
        var cache = new S3ExpressIdentityCache(key -> staticResolver());
        var k1 = new S3ExpressIdentityKey("bucket-a", AwsCredentialsIdentity.create("AKID", "sk"));
        var k2 = new S3ExpressIdentityKey("bucket-b", AwsCredentialsIdentity.create("AKID", "sk"));
        assertThat(cache.get(k1), is(not(sameInstance(cache.get(k2)))));
    }

    @Test
    void evictsLeastRecentlyUsedWhenFull() {
        // The default MAX_SIZE is 100 (private constant). Insert MAX_SIZE + 1 keys; the oldest
        // should be evicted when we insert the overflowing one.
        int maxSize = 100;
        var cache = new S3ExpressIdentityCache(key -> staticResolver());

        S3ExpressIdentityKey first = new S3ExpressIdentityKey("bucket-0",
                AwsCredentialsIdentity.create("AKID", "sk"));
        var firstResolver = cache.get(first);

        // Re-access keys 1..maxSize-1 so they outrank key 0 in last-access tick. Then insert
        // one more key so key 0 (the least recently used) is evicted.
        for (int i = 1; i < maxSize; i++) {
            cache.get(new S3ExpressIdentityKey("bucket-" + i, AwsCredentialsIdentity.create("AKID", "sk")));
        }
        // Insert the overflowing key.
        cache.get(new S3ExpressIdentityKey("bucket-" + maxSize, AwsCredentialsIdentity.create("AKID", "sk")));

        // Re-fetching key 0 should produce a *new* resolver (the original was evicted and
        // then re-built lazily).
        var refetch = cache.get(first);
        assertThat(refetch, is(not(sameInstance(firstResolver))));
    }

    @Test
    void evictsEntriesIdleBeyondTimeout() {
        var clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
        var factoryCalls = new AtomicInteger();
        // No sweep executor; we drive evictIdle() directly with a controlled clock.
        var cache = new S3ExpressIdentityCache(
                key -> {
                    factoryCalls.incrementAndGet();
                    return staticResolver();
                },
                null,
                Duration.ofMinutes(5),
                clock);

        var key = new S3ExpressIdentityKey("bucket", AwsCredentialsIdentity.create("AKID", "sk"));
        var resolver = cache.get(key);
        assertThat(factoryCalls.get(), is(1));

        // Within the idle window: not evicted, same resolver returned.
        clock.advance(Duration.ofMinutes(4));
        cache.evictIdle();
        assertThat(cache.get(key), is(sameInstance(resolver)));

        // Advance past the timeout with no access, then sweep.
        clock.advance(Duration.ofMinutes(6));
        cache.evictIdle();

        // Entry was evicted; next get() rebuilds a new resolver via the factory.
        assertThat(cache.get(key), is(not(sameInstance(resolver))));
        assertThat(factoryCalls.get(), is(2));
    }

    @Test
    void accessResetsIdleTimer() {
        var clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
        var cache = new S3ExpressIdentityCache(
                key -> staticResolver(),
                null,
                Duration.ofMinutes(5),
                clock);

        var key = new S3ExpressIdentityKey("bucket", AwsCredentialsIdentity.create("AKID", "sk"));
        var resolver = cache.get(key);

        // Keep accessing just under the timeout; the entry should survive each sweep.
        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofMinutes(4));
            cache.get(key);
            cache.evictIdle();
        }
        assertThat(cache.get(key), is(sameInstance(resolver)));
    }

    private static Matcher<Object> not(Matcher<Object> matcher) {
        return Matchers.not(matcher);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static CachingIdentityResolver<AwsCredentialsIdentity> staticResolver() {
        IdentityResolver<AwsCredentialsIdentity> delegate = new IdentityResolver<>() {
            @Override
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
                return IdentityResult.of(AwsCredentialsIdentity.create("AKID", "secret", "session", null));
            }

            @Override
            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }
        };
        return CachingIdentityResolver.builder(delegate).build();
    }
}
