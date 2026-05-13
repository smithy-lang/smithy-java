/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.identity;

import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.credentials.chain.ChainIdentityProvider;
import software.amazon.smithy.java.aws.credentials.chain.CreateResult;
import software.amazon.smithy.java.aws.credentials.chain.CredentialFeatureId;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.ProviderContext;
import software.amazon.smithy.java.aws.credentials.chain.StandardProvider;

public final class SystemPropertiesCredentialProvider implements ChainIdentityProvider {

    private static final Set<CredentialFeatureId> FEATURE_IDS = Set.of(new CredentialFeatureId("f"));

    @Override
    public String name() {
        return "JavaSystemProperties";
    }

    @Override
    public Set<CredentialFeatureId> featureIds() {
        return FEATURE_IDS;
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.Standard(StandardProvider.JAVA_SYSTEM_PROPERTIES);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends Identity> CreateResult<I> create(Class<I> identityType, ProviderContext context) {
        if (identityType == AwsCredentialsIdentity.class) {
            return (CreateResult<I>) new CreateResult.UnconditionalMatch<>(SystemPropertiesIdentityResolver.INSTANCE);
        }
        return CreateResult.pass();
    }
}
