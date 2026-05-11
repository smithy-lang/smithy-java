/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.identity;

import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.credentials.chain.AwsCredentialProvider;
import software.amazon.smithy.java.aws.credentials.chain.BuiltinProvider;
import software.amazon.smithy.java.aws.credentials.chain.CredentialFeatureId;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.ProviderContext;

/**
 * Registers {@link SystemPropertiesIdentityResolver} in the credential chain's
 * {@link BuiltinProvider#JAVA_SYSTEM_PROPERTIES} slot.
 */
public final class SystemPropertiesCredentialProvider implements AwsCredentialProvider {

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
        return new OrderingConstraint.Builtin(BuiltinProvider.JAVA_SYSTEM_PROPERTIES);
    }

    @Override
    public IdentityResolver<AwsCredentialsIdentity> create(ProviderContext context) {
        return SystemPropertiesIdentityResolver.INSTANCE;
    }
}
