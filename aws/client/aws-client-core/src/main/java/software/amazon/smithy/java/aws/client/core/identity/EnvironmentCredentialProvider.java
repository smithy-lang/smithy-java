/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.identity;

import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsResolver;
import software.amazon.smithy.java.aws.credentials.chain.AwsCredentialProvider;
import software.amazon.smithy.java.aws.credentials.chain.BuiltinProvider;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.ProviderContext;

/**
 * Registers {@link EnvironmentVariableIdentityResolver} in the credential chain's
 * {@link BuiltinProvider#ENVIRONMENT} slot.
 */
public final class EnvironmentCredentialProvider implements AwsCredentialProvider {
    @Override
    public String name() {
        return "Environment";
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.Builtin(BuiltinProvider.ENVIRONMENT);
    }

    @Override
    public AwsCredentialsResolver create(ProviderContext context) {
        return EnvironmentVariableIdentityResolver.INSTANCE;
    }
}
