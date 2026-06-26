/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core;

import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.credentials.chain.IdentityChain;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;

/**
 * A {@link ClientPlugin} that registers the AWS default credential chain on any client that uses an AWS auth scheme
 * (one whose {@link AuthScheme#identityClass()} is {@link AwsCredentialsIdentity}).
 *
 * <p>This plugin is wired into generated AWS clients by codegen. It is a no-op for clients that do not use
 * AWS authentication or that already have an {@link AwsCredentialsIdentity} resolver registered.
 *
 * <p>Users can also add it explicitly:
 * {@snippet lang="java" :
 * MyClient.builder()
 *     .addPlugin(new AwsCredentialChainPlugin())
 *     .build();
 * }
 *
 * <p>To customize the chain (e.g., exclude providers), build the chain manually and register it as an identity
 * resolver directly instead of using this plugin.
 */
public final class AwsCredentialChainPlugin implements ClientPlugin {
    @Override
    public Phase getPluginPhase() {
        // Run after DEFAULTS so the client's region (and any other defaults) are populated on the config before we
        // read them to assemble the chain.
        return Phase.AFTER_DEFAULTS;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        if (needsAwsCredentials(config) && !hasAwsCredentialsResolver(config)) {
            // Pass the client's configured region so credential providers that make a service call (STS, SSO) use
            // the same region as the client. Null when no region is configured, in which case the providers fall
            // back to the environment and profile.
            String region = config.context().get(RegionSetting.REGION);
            var chain = IdentityChain.create(AwsCredentialsIdentity.class, null, region);
            config.addIdentityResolver(chain);
            config.addInterceptor(new InvalidateOnAuthFailureInterceptor(chain));
        }
    }

    private static boolean needsAwsCredentials(ClientConfig.Builder config) {
        for (AuthScheme<?, ?> scheme : config.supportedAuthSchemes()) {
            if (scheme.identityClass() == AwsCredentialsIdentity.class) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAwsCredentialsResolver(ClientConfig.Builder config) {
        for (IdentityResolver<?> resolver : config.identityResolvers()) {
            if (resolver.identityType() == AwsCredentialsIdentity.class) {
                return true;
            }
        }
        return false;
    }
}
