/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

import java.util.concurrent.ConcurrentHashMap;
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
        // The default MAX_SIZE is 25 (private constant). Insert 26 keys; the oldest should be
        // evicted when we insert the 26th.
        var seen = new ConcurrentHashMap<S3ExpressIdentityKey, CachingIdentityResolver<AwsCredentialsIdentity>>();
        var cache = new S3ExpressIdentityCache(key -> {
            var r = staticResolver();
            seen.put(key, r);
            return r;
        });

        S3ExpressIdentityKey first = new S3ExpressIdentityKey("bucket-0",
                AwsCredentialsIdentity.create("AKID", "sk"));
        var firstResolver = cache.get(first);

        // Force re-access of key 1..24 so they outrank key 0 in last-access tick. Then insert
        // a 26th key so key 0 (the least recently used) is evicted.
        for (int i = 1; i < 25; i++) {
            cache.get(new S3ExpressIdentityKey("bucket-" + i, AwsCredentialsIdentity.create("AKID", "sk")));
        }
        // Insert the 26th key.
        cache.get(new S3ExpressIdentityKey("bucket-25", AwsCredentialsIdentity.create("AKID", "sk")));

        // Re-fetching key 0 should produce a *new* resolver (the original was evicted and
        // then re-built lazily).
        var refetch = cache.get(first);
        assertThat(refetch, is(not(sameInstance(firstResolver))));
    }

    private static Matcher<Object> not(Matcher<Object> matcher) {
        return Matchers.not(matcher);
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
