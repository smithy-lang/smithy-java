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
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.aws.credentials.chain.CredentialFeatureId;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.StandardProvider;
import software.amazon.smithy.java.context.Context;

/**
 * Resolves {@link AwsConfigCredentialSource.SessionKeys} from the active profile.
 * Re-reads from the setup's profile on each resolution to support live reload.
 * Registers as terminal — session keys cannot fail.
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
    public void create(Class<? extends Identity> identityType, ChainSetup setup) {
        if (identityType != AwsCredentialsIdentity.class) {
            return;
        }
        AwsProfile profile = setup.profile();
        if (profile == null) {
            return;
        }
        for (AwsConfigCredentialSource source : profile.credentialSources()) {
            if (source instanceof AwsConfigCredentialSource.SessionKeys s) {
                IdentityResult<AwsCredentialsIdentity> result = IdentityResult.of(
                        AwsCredentialsIdentity.create(
                                s.accessKeyId(),
                                s.secretAccessKey(),
                                s.sessionToken(),
                                null,
                                s.accountId()));
                setup.addTerminalResolver(new IdentityResolver<AwsCredentialsIdentity>() {
                    public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context c) {
                        return result;
                    }

                    public Class<AwsCredentialsIdentity> identityType() {
                        return AwsCredentialsIdentity.class;
                    }
                });
                return;
            }
        }
    }
}
