/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain.config;

import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.aws.config.AwsProfile;
import software.amazon.smithy.java.aws.credentials.chain.ChainIdentityProvider;
import software.amazon.smithy.java.aws.credentials.chain.CreateResult;
import software.amazon.smithy.java.aws.credentials.chain.CredentialFeatureId;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.ProviderContext;
import software.amazon.smithy.java.aws.credentials.chain.StandardProvider;
import software.amazon.smithy.java.context.Context;

/**
 * Resolves {@link AwsConfigCredentialSource.SessionKeys} from the active profile.
 * Re-reads the profile on each call to support live reload after invalidation.
 */
public final class SessionKeysHandler implements ChainIdentityProvider {

    private static final Set<CredentialFeatureId> FEATURE_IDS = Set.of(new CredentialFeatureId("n"));
    private static final IdentityResult<AwsCredentialsIdentity> NO_PROFILE =
            IdentityResult.ofError(SessionKeysHandler.class, "No active profile");
    private static final IdentityResult<AwsCredentialsIdentity> NOT_FOUND =
            IdentityResult.ofError(SessionKeysHandler.class, "No session keys in profile");

    @Override
    public String name() {
        return "SessionKeys";
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.Standard(StandardProvider.PROFILE_SESSION_KEYS);
    }

    @Override
    public Set<CredentialFeatureId> featureIds() {
        return FEATURE_IDS;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends Identity> CreateResult<I> create(Class<I> identityType, ProviderContext context) {
        if (identityType != AwsCredentialsIdentity.class) {
            return CreateResult.pass();
        }

        AwsProfile profile = context.profile();
        if (profile == null) {
            return CreateResult.pass();
        }

        for (AwsConfigCredentialSource source : profile.credentialSources()) {
            if (source instanceof AwsConfigCredentialSource.SessionKeys) {
                return (CreateResult<I>) new CreateResult.UnconditionalMatch<>(new Resolver(context));
            }
        }

        return CreateResult.pass();
    }

    private record Resolver(ProviderContext context) implements IdentityResolver<AwsCredentialsIdentity> {
        @Override
        public Class<AwsCredentialsIdentity> identityType() {
            return AwsCredentialsIdentity.class;
        }

        @Override
        public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
            AwsProfile profile = context.profile();
            if (profile == null) {
                return NO_PROFILE;
            }

            for (AwsConfigCredentialSource source : profile.credentialSources()) {
                if (source instanceof AwsConfigCredentialSource.SessionKeys(String accessKeyId, String secretAccessKey, String sessionToken, String accountId)) {
                    return IdentityResult.of(AwsCredentialsIdentity.create(
                            accessKeyId,
                            secretAccessKey,
                            sessionToken,
                            null,
                            accountId));
                }
            }

            return NOT_FOUND;
        }
    }
}
