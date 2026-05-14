/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain.config;

import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.ChainIdentityProvider;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.StandardProvider;

/**
 * Claims the {@link software.amazon.smithy.java.aws.credentials.chain.StandardProvider#SHARED_CONFIG}
 * slot. Parses the AWS config/credentials files and stores the result on the {@link ChainSetup}
 * for downstream providers. Does not register any resolver.
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
    public void create(Class<? extends Identity> identityType, ChainSetup setup) {
        AwsProfileFile profileFile = AwsProfileFile.loadSilently();
        if (profileFile != null) {
            setup.setProfileFile(profileFile);
            String name = setup.profileNameOverride();
            if (name == null) {
                setup.setProfile(profileFile.activeProfile());
            } else {
                setup.setProfile(profileFile.profile(name));
            }
        }
    }
}
