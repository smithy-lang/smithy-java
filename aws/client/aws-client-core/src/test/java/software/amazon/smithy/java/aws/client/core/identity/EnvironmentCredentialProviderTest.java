/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.context.Context;

class EnvironmentCredentialProviderTest {
    private static final Map<String, String> ENVIRONMENT = Map.of(
            "AWS_ACCESS_KEY_ID",
            "ENV_AK",
            "AWS_SECRET_ACCESS_KEY",
            "ENV_SK",
            "AWS_PROFILE",
            "work");

    @Test
    void awsProfileDoesNotSuppressTopLevelEnvironmentCredentials() {
        var provider = new EnvironmentCredentialProvider();
        var setup = ChainSetup.builder().env(ENVIRONMENT::get).build();
        setup.setCurrentProvider(provider);

        provider.setup(AwsCredentialsIdentity.class, setup);

        assertEquals(1, setup.resolvers().size());
        var identity = (AwsCredentialsIdentity) setup.resolvers()
                .getFirst()
                .resolver()
                .resolveIdentity(Context.empty())
                .identity();
        assertEquals(
                "ENV_AK",
                identity.accessKeyId());
    }

    @Test
    void explicitProfileSuppressesOnlyTopLevelEnvironmentParticipation() {
        var provider = new EnvironmentCredentialProvider();
        var setup = ChainSetup.builder()
                .env(ENVIRONMENT::get)
                .profileNameOverride("work")
                .build();
        setup.setCurrentProvider(provider);
        setup.setProviders(List.of(provider));

        provider.setup(AwsCredentialsIdentity.class, setup);

        assertTrue(setup.resolvers().isEmpty());
        var sourceResolver = setup.sourceResolver("Environment", AwsCredentialsIdentity.class);
        assertEquals(
                "ENV_AK",
                sourceResolver.resolveIdentity(Context.empty()).identity().accessKeyId());
    }
}
