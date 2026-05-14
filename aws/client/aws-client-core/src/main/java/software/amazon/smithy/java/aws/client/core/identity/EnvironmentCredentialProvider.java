/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.identity;

import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.Identity;
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
        if (identityType == AwsCredentialsIdentity.class) {
            setup.addTerminalResolver(EnvironmentVariableIdentityResolver.INSTANCE);
        }
    }
}
