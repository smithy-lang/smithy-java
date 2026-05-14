/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.aws.credentials.chain.ChainIdentityProvider;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.aws.credentials.chain.CredentialFeatureId;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.StandardProvider;

/**
 * Resolves credentials by assuming an IAM role via STS, configured from
 * {@code role_arn} + {@code source_profile} or {@code credential_source}
 * in the active AWS profile.
 *
 * <p>Handles recursive source_profile chains with cycle detection.
 */
public final class ProfileAssumeRoleProvider implements ChainIdentityProvider {

    private static final Set<CredentialFeatureId> FEATURE_IDS = Set.of(
            new CredentialFeatureId("o"),
            new CredentialFeatureId("i"));

    @Override
    public String name() {
        return "ProfileAssumeRole";
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.Standard(StandardProvider.PROFILE_ASSUME_ROLE);
    }

    @Override
    public Set<CredentialFeatureId> featureIds() {
        return FEATURE_IDS;
    }

    @Override
    public void setup(Class<? extends Identity> identityType, ChainSetup setup) {
        if (identityType != AwsCredentialsIdentity.class || setup.profile() == null) {
            return;
        }

        for (AwsConfigCredentialSource source : setup.profile().credentialSources()) {
            if (source instanceof AwsConfigCredentialSource.AssumeRole ar) {
                setup.addTerminalResolver(new StsAssumeRoleResolver(ar, setup.profileFile()));
                return;
            }
        }
    }
}
