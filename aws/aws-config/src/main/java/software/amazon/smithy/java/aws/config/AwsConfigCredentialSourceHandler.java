/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.credentials.chain.CredentialFeatureId;
import software.amazon.smithy.java.context.Context;

/**
 * Strategy for turning an {@link AwsConfigCredentialSource} into an {@link Identity}.
 *
 * <p>A handler inspects a credential source and either produces a result (success or a typed error)
 * or returns {@code null} to signal that it does not handle this source type. Returning {@code null} lets the
 * enclosing resolver try the next handler in its chain.
 *
 * <p>Handlers are parameterized by identity type. For example, an SSO handler that exchanges a token for
 * AWS credentials implements {@code AwsConfigCredentialSourceHandler<AwsCredentialsIdentity>}, while one
 * that returns the token directly for bearer auth implements {@code AwsConfigCredentialSourceHandler<TokenIdentity>}.
 *
 * <p>Handlers are discovered via {@link java.util.ServiceLoader}. Modules that provide handlers register them in
 * {@code META-INF/services/software.amazon.smithy.java.aws.config.AwsConfigCredentialSourceHandler}. The resolver
 * filters handlers by {@link #identityType()} and, for each source, tries matching handlers until one returns non-null.
 *
 * @param <I> the identity type this handler produces.
 */
public interface AwsConfigCredentialSourceHandler<I extends Identity> {
    /**
     * The identity type this handler resolves.
     *
     * @return the identity class (e.g., {@code AwsCredentialsIdentity.class} or {@code TokenIdentity.class}).
     */
    Class<I> identityType();

    /**
     * Attempt to resolve an identity from a credential source.
     *
     * @param source  the source to resolve.
     * @param context runtime context for resolution.
     * @return the result of resolution, or {@code null} if this handler does not handle {@code source}'s type.
     */
    IdentityResult<I> tryResolve(AwsConfigCredentialSource source, ResolutionContext context);

    /**
     * The business metric feature IDs emitted when this handler successfully resolves an identity.
     *
     * @return the feature IDs, or empty if none.
     */
    default Set<CredentialFeatureId> featureIds() {
        return Set.of();
    }

    /**
     * Information passed from the enclosing resolver to each handler invocation.
     *
     * @param profileFile Entire merged config file data.
     * @param profileName Profile name to use.
     * @param requestProperties Context properties associated with the request.
     */
    record ResolutionContext(AwsProfileFile profileFile, String profileName, Context requestProperties) {}
}
