/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import software.amazon.smithy.java.auth.api.identity.CachingIdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.utils.SmithyInternalApi;

/** Applies the AWS credential refresh policy to credential-source resolvers. */
@SmithyInternalApi
public final class AwsCredentialCaching {

    private AwsCredentialCaching() {}

    /** Wraps an AWS-managed credential source with caching and static stability. */
    public static IdentityResolver<AwsCredentialsIdentity> staticallyStable(
            IdentityResolver<AwsCredentialsIdentity> delegate,
            ScheduledExecutorService executor
    ) {
        return builder(delegate, executor)
                .allowExpiredCredentials(true)
                .build();
    }

    /** Wraps an opaque credential source with caching but without expired-credential fallback. */
    public static IdentityResolver<AwsCredentialsIdentity> cachingOnly(
            IdentityResolver<AwsCredentialsIdentity> delegate,
            ScheduledExecutorService executor
    ) {
        return builder(delegate, executor)
                .allowExpiredCredentials(false)
                .build();
    }

    private static CachingIdentityResolver.Builder<AwsCredentialsIdentity> builder(
            IdentityResolver<AwsCredentialsIdentity> delegate,
            ScheduledExecutorService executor
    ) {
        var builder = CachingIdentityResolver.builder(Objects.requireNonNull(delegate, "delegate"))
                .closeDelegate(true)
                .identityMatcher((cached, rejected) -> Objects.equals(
                        cached.accessKeyId(),
                        rejected.accessKeyId()));
        if (executor != null) {
            builder.executor(executor);
        }
        return builder;
    }
}
