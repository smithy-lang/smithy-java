/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.credentials.chain.CredentialFeatureId;
import software.amazon.smithy.java.context.Context;

/**
 * Strategy for turning an {@link AwsConfigCredentialSource} into an {@link AwsCredentialsIdentity}.
 *
 * <p>A handler inspects a credential source and either produces a result (success or a typed error)
 * or returns {@code null} to signal that it does not handle this source type. Returning {@code null} lets the
 * enclosing resolver try the next handler in its chain.
 *
 * <p>Handlers are discovered via {@link java.util.ServiceLoader}. Modules that provide handlers register them in
 * {@code META-INF/services/software.amazon.smithy.java.aws.config.AwsConfigCredentialSourceHandler}. The resolver
 * iterates the profile's credential sources in priority order (as defined by {@link AwsProfile#credentialSources()})
 * and, for each source, tries all handlers until one returns non-null.
 *
 * <p>Handlers can also be registered explicitly via
 * {@link AwsProfileCredentialsResolver.Builder#addHandler(AwsConfigCredentialSourceHandler)}, which
 * takes precedence over SPI-discovered handlers.
 *
 * <p>The {@code aws-config} module ships with handlers for {@link AwsConfigCredentialSource.StaticKeys},
 * {@link AwsConfigCredentialSource.SessionKeys}, and {@link AwsConfigCredentialSource.CredentialProcess}.
 * Downstream modules can supply handlers for the remaining source types (SSO, AssumeRole, web identity, login).
 */
public interface AwsConfigCredentialSourceHandler {
    /**
     * Attempt to resolve an identity from a credential source.
     *
     * @param source  the source to resolve.
     * @param context runtime context for resolution.
     * @return the result of resolution, or {@code null} if this handler does not handle {@code source}'s type.
     */
    IdentityResult<AwsCredentialsIdentity> tryResolve(AwsConfigCredentialSource source, ResolutionContext context);

    /**
     * Information passed from the enclosing resolver to each handler invocation.
     *
     * <p>Handlers that walk {@code source_profile} chains can look the referenced profile up via
     * {@link #profileFile()} and invoke a child resolution. Cycle detection is the caller's responsibility
     * (the resolver maintains a visited-set while recursing).
     *
     * @param profileFile Entire merged config file data.
     * @param profileName Profile name to use.
     * @param requestProperties Context properties associated with the request.
     */
    record ResolutionContext(AwsProfileFile profileFile, String profileName, Context requestProperties) {}

    /**
     * The business metric feature ID emitted when this handler successfully resolves credentials.
     * Appended to the User-Agent header per the credential chain precedence SEP.
     *
     * @return the feature ID, or {@code null} if none.
     */
    default Set<CredentialFeatureId> featureIds() {
        return Set.of();
    }
}
