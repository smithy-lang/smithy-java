/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.identity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.aws.credentials.chain.IdentityChain;

/**
 * Demonstrates that the assembled {@link IdentityChain} fails to fall through past the
 * {@code JAVA_SYSTEM_PROPERTIES} slot.
 *
 * <p>{@link SystemPropertiesCredentialProvider} registers its resolver via
 * {@link ChainSetup#addTerminalResolver} unconditionally, without first checking whether the system properties it
 * reads actually exist. Because {@code JAVA_SYSTEM_PROPERTIES} is the highest-priority slot, assembly always stops
 * there: every later provider (environment variables, shared config, profiles, IMDS) is dropped from the chain.
 *
 * <p>This test encodes the correct behavior and therefore fails against the current implementation.
 */
public class IdentityChainFallthroughTest {

    /**
     * When system properties are absent, the assembled chain should contain the {@code Environment} provider so
     * credentials can fall through to environment variables. Previously it did not: assembly broke at the
     * unconditionally-terminal {@code JavaSystemProperties} provider before {@code EnvironmentCredentialProvider}
     * was ever set up.
     */
    @Test
    void assembledChainShouldFallThroughToEnvironmentProvider() {
        // The test JVM sets these globally (see build.gradle.kts); clear them to simulate a real environment with
        // no aws.accessKeyId/aws.secretAccessKey system properties, then restore.
        String savedAccessKey = System.clearProperty("aws.accessKeyId");
        String savedSecretKey = System.clearProperty("aws.secretAccessKey");
        try (var chain = IdentityChain.create(AwsCredentialsIdentity.class)) {
            var providers = chain.providerNames();
            assertTrue(providers.contains("Environment"),
                    "Environment provider was dropped from the chain. SystemPropertiesCredentialProvider "
                            + "registered a terminal resolver despite no system properties being set, so assembly "
                            + "stopped before EnvironmentCredentialProvider.setup() ran. Assembled chain was: "
                            + providers);
        } finally {
            if (savedAccessKey != null) {
                System.setProperty("aws.accessKeyId", savedAccessKey);
            }
            if (savedSecretKey != null) {
                System.setProperty("aws.secretAccessKey", savedSecretKey);
            }
        }
    }
}
