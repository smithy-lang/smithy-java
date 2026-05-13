/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain.config;

import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.ChainIdentityProvider;
import software.amazon.smithy.java.aws.credentials.chain.CreateResult;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.ProviderContext;
import software.amazon.smithy.java.aws.credentials.chain.StandardProvider;

/**
 * Claims the {@link StandardProvider#SHARED_CONFIG} slot. Parses the AWS config/credentials
 * files and stores the result on the {@link ProviderContext} for downstream providers.
 * Returns {@code null} — it does not itself resolve credentials.
 */
public final class SharedConfigProvider implements ChainIdentityProvider {
    @Override
    public String name() {
        return "SharedConfig";
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.Standard(StandardProvider.SHARED_CONFIG);
    }

    @Override
    public <I extends Identity> CreateResult<I> create(Class<I> identityType, ProviderContext context) {
        AwsProfileFile profileFile = AwsProfileFile.loadSilently();
        if (profileFile != null) {
            context.setProfileFile(profileFile);
            String name = context.profileNameOverride();
            if (name == null) {
                context.setProfile(profileFile.activeProfile());
            } else {
                context.setProfile(profileFile.profile(name));
            }
        }
        return CreateResult.pass();
    }
}
