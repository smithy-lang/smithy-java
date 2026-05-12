/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import software.amazon.smithy.java.auth.api.identity.CachingIdentityResolver;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.credentials.chain.BuiltinProvider;
import software.amazon.smithy.java.aws.credentials.chain.ChainIdentityProvider;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.ProviderContext;

/**
 * Registers {@link ProfileIdentityResolver} in the credential chain's
 * {@link BuiltinProvider#SHARED_CONFIG} slot.
 */
public final class ProfileCredentialProvider implements ChainIdentityProvider {
    @Override
    public String name() {
        return "SharedConfig";
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.Builtin(BuiltinProvider.SHARED_CONFIG);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends Identity> IdentityResolver<I> create(Class<I> identityType, ProviderContext context) {
        if (identityType != AwsCredentialsIdentity.class) {
            return null;
        }
        var resolver = ProfileIdentityResolver.builder(AwsCredentialsIdentity.class).build();
        context.properties().put(AwsProfileFile.CONTEXT_KEY, resolver.profileFile());
        return (IdentityResolver<I>) CachingIdentityResolver.builder(resolver).executor(context.executor()).build();
    }
}
