/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import java.util.List;
import software.amazon.smithy.java.auth.api.identity.CachingIdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.credentials.chain.AwsCredentialProvider;
import software.amazon.smithy.java.aws.credentials.chain.BuiltinProvider;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.ProviderContext;

/**
 * Registers {@link AwsProfileCredentialsResolver} in the credential chain's
 * {@link BuiltinProvider#SHARED_CONFIG} slot.
 */
public final class ProfileCredentialProvider implements AwsCredentialProvider {
    @Override
    public String name() {
        return "SharedConfig";
    }

    @Override
    public List<String> aliases() {
        return List.of("SharedCredentials");
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.Builtin(BuiltinProvider.SHARED_CONFIG);
    }

    @Override
    public IdentityResolver<AwsCredentialsIdentity> create(ProviderContext context) {
        AwsProfileCredentialsResolver resolver = AwsProfileCredentialsResolver.builder().build();
        // Share the loaded profile file with other providers via context.
        context.properties().put(AwsProfileFile.CONTEXT_KEY, resolver.profileFile());
        return CachingIdentityResolver.<AwsCredentialsIdentity>builder(resolver)
                .executor(context.executor())
                .build();
    }
}
