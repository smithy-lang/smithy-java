/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.smithy.java.auth.api.identity.CachingIdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;

/**
 * Resolves S3 Express session credentials for the in-flight request.
 *
 * <p>On each call:
 *
 * <ol>
 *     <li>Read the target bucket from {@link S3ExpressContext#BUCKET} on the request context
 *         (set by an upstream interceptor).</li>
 *     <li>Resolve the base {@link AwsCredentialsIdentity} from the underlying provider.</li>
 *     <li>Look up (or build, on miss) a per-bucket {@link CachingIdentityResolver} keyed on
 *         {@code (bucket, base identity)}.</li>
 *     <li>Delegate to that resolver, which calls {@code CreateSession} on cold start and
 *         refreshes asynchronously near expiration.</li>
 * </ol>
 *
 * <p>Confused-deputy is prevented by including the base credentials' access key / secret key /
 * session token in the cache key — different base identities never share a cached session.
 */
final class S3ExpressIdentityProvider implements IdentityResolver<AwsCredentialsIdentity> {

    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(5);
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
    private static final ScheduledExecutorService REFRESH_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "smithy-s3express-session-refresh-" + THREAD_COUNTER.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    private final IdentityResolver<AwsCredentialsIdentity> baseIdentityProvider;
    private final S3ExpressIdentityCache cache;

    /**
     * Build a provider for the given base credential resolver and {@code CreateSession} callback.
     *
     * @param baseIdentityProvider resolves the AWS credentials used to authorize CreateSession.
     * @param createSession        bridge to the user's S3 client's CreateSession operation.
     */
    S3ExpressIdentityProvider(
            IdentityResolver<AwsCredentialsIdentity> baseIdentityProvider,
            CreateSessionCallback createSession
    ) {
        this.baseIdentityProvider = Objects.requireNonNull(baseIdentityProvider, "baseIdentityProvider");
        Objects.requireNonNull(createSession, "createSession");
        // Reuse the shared refresh executor to also drive periodic idle-entry eviction.
        this.cache = new S3ExpressIdentityCache(
                key -> buildPerBucketResolver(key, createSession),
                REFRESH_EXECUTOR,
                IDLE_TIMEOUT,
                Clock.systemUTC());
    }

    private CachingIdentityResolver<AwsCredentialsIdentity> buildPerBucketResolver(
            S3ExpressIdentityKey key,
            CreateSessionCallback createSession
    ) {
        // Each per-bucket entry has its own delegate that closes over the bucket + base
        // identity captured at insertion time. The CachingIdentityResolver around it does the
        // refresh-ahead work using its own scheduled executor.
        IdentityResolver<AwsCredentialsIdentity> delegate = new IdentityResolver<>() {
            @Override
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
                AwsCredentialsIdentity identity = createSession.createSession(key.bucket(), key.identity());
                return IdentityResult.of(identity);
            }

            @Override
            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }
        };
        return CachingIdentityResolver.builder(delegate)
                .executor(REFRESH_EXECUTOR)
                .build();
    }

    @Override
    public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
        String bucket = requestProperties.get(S3ExpressContext.BUCKET);
        if (bucket == null) {
            return IdentityResult.ofError(
                    getClass(),
                    "S3 Express identity requested but no bucket was set in the request context. "
                            + "An interceptor must publish S3ExpressContext.BUCKET before auth runs.");
        }

        IdentityResult<AwsCredentialsIdentity> baseResult = baseIdentityProvider.resolveIdentity(requestProperties);
        AwsCredentialsIdentity base = baseResult.identity();
        if (base == null) {
            String baseError = baseResult.error();
            Class<?> resolver = baseResult.resolver() == null ? getClass() : baseResult.resolver();
            return IdentityResult.ofError(
                    resolver,
                    baseError == null
                            ? "Could not resolve base AWS credentials for S3 Express CreateSession"
                            : "Could not resolve base AWS credentials for S3 Express CreateSession: " + baseError);
        }

        var perBucket = cache.get(new S3ExpressIdentityKey(bucket, base));
        return perBucket.resolveIdentity(requestProperties);
    }

    @Override
    public Class<AwsCredentialsIdentity> identityType() {
        return AwsCredentialsIdentity.class;
    }

    @Override
    public void invalidate() {
        cache.invalidateAll();
    }
}
