/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.identity;

import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.credentials.chain.ChainIdentityProvider;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.aws.credentials.chain.CredentialFeatureId;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.StandardProvider;

public final class EnvironmentCredentialProvider implements ChainIdentityProvider {

    private static final Set<CredentialFeatureId> FEATURE_IDS = Set.of(new CredentialFeatureId("g"));

    @Override
    public String name() {
        return "Environment";
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT);
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

        // Read the environment once, through the setup's lookup. If credentials are present, register a static
        // resolver over the snapshot we just read rather than a resolver that reads the environment again later:
        // the source has already been loaded, and this keeps the read consistent with the assembly-time decision.
        String accessKey = setup.getenv(EnvironmentVariableIdentityResolver.ACCESS_KEY_PROPERTY);
        String secretKey = setup.getenv(EnvironmentVariableIdentityResolver.SECRET_KEY_PROPERTY);
        if (accessKey == null || secretKey == null) {
            return;
        }

        String sessionToken = setup.getenv(EnvironmentVariableIdentityResolver.SESSION_TOKEN_PROPERTY);
        String accountId = setup.getenv(EnvironmentVariableIdentityResolver.ACCOUNT_ID_PROPERTY);
        var identity = AwsCredentialsIdentity.create(accessKey, secretKey, sessionToken, null, accountId);
        setup.addTerminalResolver(IdentityResolver.of(identity));
    }
}
