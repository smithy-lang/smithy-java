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
 * Resolves credentials via STS AssumeRoleWithWebIdentity, configured from
 * {@code web_identity_token_file} + {@code role_arn} in the active AWS profile.
 */
public final class ProfileWebIdentityProvider implements ChainIdentityProvider {

    private static final Set<CredentialFeatureId> FEATURE_IDS = Set.of(
            new CredentialFeatureId("q"),
            new CredentialFeatureId("k"));

    @Override
    public String name() {
        return "ProfileWebIdentity";
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.Standard(StandardProvider.PROFILE_WEB_IDENTITY);
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
            if (source instanceof AwsConfigCredentialSource.WebIdentityToken wit) {
                var endpoint = StsEndpointConfig.resolve(wit.region(), setup);
                setup.addTerminalResolver(new StsWebIdentityResolver(wit, StsClientFactory.createNoAuth(endpoint)));
                return;
            }
        }
    }
}
