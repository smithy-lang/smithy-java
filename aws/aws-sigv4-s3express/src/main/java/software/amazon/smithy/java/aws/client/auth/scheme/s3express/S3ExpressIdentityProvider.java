/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import java.util.Objects;
import software.amazon.smithy.java.auth.api.identity.CachingIdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.context.Context;

/**
 * Resolves {@link S3ExpressIdentity} for the in-flight request.
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
public final class S3ExpressIdentityProvider implements IdentityResolver<S3ExpressIdentity> {

    private final IdentityResolver<AwsCredentialsIdentity> baseIdentityProvider;
    private final S3ExpressIdentityCache cache;

    /**
     * Build a provider for the given base credential resolver and {@code CreateSession} callback.
     *
     * @param baseIdentityProvider resolves the AWS credentials used to authorize CreateSession.
     * @param createSession        bridge to the user's S3 client's CreateSession operation.
     */
    public S3ExpressIdentityProvider(
            IdentityResolver<AwsCredentialsIdentity> baseIdentityProvider,
            CreateSessionCallback createSession
    ) {
        this.baseIdentityProvider = Objects.requireNonNull(baseIdentityProvider, "baseIdentityProvider");
        Objects.requireNonNull(createSession, "createSession");
        this.cache = new S3ExpressIdentityCache(key -> buildPerBucketResolver(key, createSession));
    }

    private static CachingIdentityResolver<S3ExpressIdentity> buildPerBucketResolver(
            S3ExpressIdentityKey key,
            CreateSessionCallback createSession
    ) {
        // Each per-bucket entry has its own delegate that closes over the bucket + base
        // identity captured at insertion time. The CachingIdentityResolver around it does the
        // refresh-ahead work using its own scheduled executor.
        IdentityResolver<S3ExpressIdentity> delegate = new IdentityResolver<>() {
            @Override
            public IdentityResult<S3ExpressIdentity> resolveIdentity(Context requestProperties) {
                S3ExpressIdentity identity = createSession.createSession(key.bucket(), key.identity());
                return IdentityResult.of(identity);
            }

            @Override
            public Class<S3ExpressIdentity> identityType() {
                return S3ExpressIdentity.class;
            }
        };
        return CachingIdentityResolver.builder(delegate).build();
    }

    @Override
    public IdentityResult<S3ExpressIdentity> resolveIdentity(Context requestProperties) {
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
            return IdentityResult.ofError(
                    getClass(),
                    "Could not resolve base AWS credentials for S3 Express CreateSession");
        }

        var perBucket = cache.get(new S3ExpressIdentityKey(bucket, base));
        var result = perBucket.resolveIdentity(requestProperties);

        // Emit the S3_EXPRESS_BUCKET business metric on successful resolve. Aligns with how
        // the credential chain providers tag FEATURE_IDS on the call context after a hit.
        if (result.identity() != null) {
            var ids = requestProperties.get(CallContext.FEATURE_IDS);
            if (ids != null) {
                ids.add(S3ExpressFeatureId.S3_EXPRESS_BUCKET);
            }
        }
        return result;
    }

    @Override
    public Class<S3ExpressIdentity> identityType() {
        return S3ExpressIdentity.class;
    }

    @Override
    public void invalidate() {
        // Per-bucket resolvers manage their own validity; punting on global invalidation for now.
        // If/when an auth-failure interceptor needs to invalidate, we can extend this to clear
        // the bucket entry for the call's specific (bucket, identity) key.
    }
}
