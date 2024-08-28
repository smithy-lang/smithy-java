/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.aws.http;

import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.runtime.aws.http.auth.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.runtime.aws.http.auth.identity.EnvironmentVariableIdentityResolver;
import software.amazon.smithy.java.runtime.aws.http.auth.identity.SystemPropertiesIdentityResolver;
import software.amazon.smithy.java.runtime.client.core.ClientConfig;
import software.amazon.smithy.java.runtime.client.core.ClientPlugin;
import software.amazon.smithy.java.runtime.client.core.annotations.Configuration;

/**
 * TODO: Docs
 */
public final class AwsBasePlugin implements ClientPlugin {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(AwsBasePlugin.class);
    // TODO: Add remaining default identity resolvers in chain
    // see: https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html
    private static final IdentityResolver<AwsCredentialsIdentity> DEFAULT_RESOLVER_CHAIN = IdentityResolver.chain(
        List.of(new SystemPropertiesIdentityResolver(), new EnvironmentVariableIdentityResolver())
    );
    private String region;

    @Configuration
    public void region(String region) {
        this.region = region;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        LOGGER.trace("Applying AWS configuration properties.");
        config.putConfig(
            AwsConfigurationProperties.REGION,
            Objects.requireNonNull(region, "Region property must be set")
        );
        config.addIdentityResolver(DEFAULT_RESOLVER_CHAIN);
    }
}
