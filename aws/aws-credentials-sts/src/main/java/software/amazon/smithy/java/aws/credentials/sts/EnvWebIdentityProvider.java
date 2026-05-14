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
 * Resolves credentials via STS AssumeRoleWithWebIdentity using environment variables
 * ({@code AWS_WEB_IDENTITY_TOKEN_FILE} + {@code AWS_ROLE_ARN}).
 */
public final class EnvWebIdentityProvider implements ChainIdentityProvider {

    private static final Set<CredentialFeatureId> FEATURE_IDS = Set.of(
            new CredentialFeatureId("h"),
            new CredentialFeatureId("k"));

    @Override
    public String name() {
        return "EnvWebIdentity";
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.Standard(StandardProvider.WEB_IDENTITY_TOKEN_ENV);
    }

    @Override
    public Set<CredentialFeatureId> featureIds() {
        return FEATURE_IDS;
    }

    @Override
    public void setup(Class<? extends Identity> identityType, ChainSetup setup) {
        if (identityType != AwsCredentialsIdentity.class) {
            return;
        }

        String tokenFile = setup.getenv("AWS_WEB_IDENTITY_TOKEN_FILE");
        if (tokenFile != null) {
            String roleArn = setup.getenv("AWS_ROLE_ARN");
            if (roleArn != null) {
                String sessionName = setup.getenv("AWS_ROLE_SESSION_NAME");
                var wit = new AwsConfigCredentialSource.WebIdentityToken(roleArn, tokenFile, sessionName, null);
                setup.addTerminalResolver(new StsWebIdentityResolver(wit, StsClientFactory.createNoAuth()));
            }
        }
    }
}
