/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.core.identity.EnvironmentCredentialProvider;
import software.amazon.smithy.java.aws.client.core.identity.SystemPropertiesCredentialProvider;
import software.amazon.smithy.java.context.Context;

/**
 * Verifies that {@link SystemPropertiesCredentialProvider} does not claim the chain terminally when the system
 * properties it reads are absent.
 *
 * <p>Lives in the {@code aws.credentials.chain} package so it can read the package-private
 * {@link ChainSetup#isTerminal()} flag directly, isolating the assembly-time decision from runtime resolution.
 *
 * <p>This test encodes the correct behavior and therefore fails against the current implementation, which calls
 * {@link ChainSetup#addTerminalResolver} unconditionally.
 */
public class SystemPropertiesProviderTerminalTest {

    /**
     * With no {@code aws.accessKeyId}/{@code aws.secretAccessKey} system properties set, the provider has nothing
     * to resolve and must not mark the chain terminal — doing so strands every downstream provider.
     */
    @Test
    void doesNotClaimTerminalWhenPropertiesAbsent() {
        // The test JVM sets these globally (see build.gradle.kts); clear them to simulate a real environment with
        // no aws.accessKeyId/aws.secretAccessKey system properties, then restore.
        String savedAccessKey = System.clearProperty("aws.accessKeyId");
        String savedSecretKey = System.clearProperty("aws.secretAccessKey");
        try {
            var setup = ChainSetup.builder().build();
            var provider = new SystemPropertiesCredentialProvider();
            setup.setCurrentProvider(provider);
            provider.setup(AwsCredentialsIdentity.class, setup);

            assertFalse(setup.isTerminal(),
                    "SystemPropertiesCredentialProvider marked the chain terminal even though no "
                            + "aws.accessKeyId/aws.secretAccessKey system properties are set. This stops chain "
                            + "assembly and prevents fall-through to environment variables, shared config, and IMDS.");
        } finally {
            if (savedAccessKey != null) {
                System.setProperty("aws.accessKeyId", savedAccessKey);
            }
            if (savedSecretKey != null) {
                System.setProperty("aws.secretAccessKey", savedSecretKey);
            }
        }
    }

    /**
     * With no {@code AWS_ACCESS_KEY_ID}/{@code AWS_SECRET_ACCESS_KEY} environment variables, the provider must not
     * mark the chain terminal — doing so strands shared config and IMDS. Env vars can't be unset in-JVM, so this
     * uses {@link ChainSetup.Builder#env} to supply an empty environment.
     */
    @Test
    void environmentProviderDoesNotClaimTerminalWhenVarsAbsent() {
        var setup = ChainSetup.builder().env(name -> null).build();
        var provider = new EnvironmentCredentialProvider();
        setup.setCurrentProvider(provider);
        provider.setup(AwsCredentialsIdentity.class, setup);

        assertFalse(setup.isTerminal(),
                "EnvironmentCredentialProvider marked the chain terminal even though no "
                        + "AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY environment variables are set. This stops chain "
                        + "assembly and prevents fall-through to shared config and IMDS.");
    }

    /**
     * The system-properties provider snapshots the property values at assembly time. A property cleared after
     * setup (but before first resolve) must not affect the already-registered terminal resolver — otherwise a
     * terminal chain could be stranded on a NOT_FOUND result for credentials that were present when we committed.
     */
    @Test
    void systemPropertiesProviderSnapshotsAtAssembly() {
        // The test JVM sets aws.accessKeyId/aws.secretAccessKey globally (see build.gradle.kts).
        var setup = ChainSetup.builder().build();
        var provider = new SystemPropertiesCredentialProvider();
        setup.setCurrentProvider(provider);
        provider.setup(AwsCredentialsIdentity.class, setup);

        assertTrue(setup.isTerminal(), "Expected terminal when properties are present at assembly.");
        var resolver = setup.resolvers().get(0).resolver();

        // Pull the properties out from under the chain after assembly committed to terminal.
        String savedAccessKey = System.clearProperty("aws.accessKeyId");
        String savedSecretKey = System.clearProperty("aws.secretAccessKey");
        try {
            var identity = resolver.resolveIdentity(Context.empty()).unwrap();
            assertEquals(
                    AwsCredentialsIdentity.create("property_access_key", "property_secret_key", "property_token"),
                    identity);
        } finally {
            if (savedAccessKey != null) {
                System.setProperty("aws.accessKeyId", savedAccessKey);
            }
            if (savedSecretKey != null) {
                System.setProperty("aws.secretAccessKey", savedSecretKey);
            }
        }
    }

    /**
     * When credentials are present in the injected environment, the provider registers a terminal resolver that
     * resolves those exact credentials — proving it reads the environment through {@code setup::getenv} and
     * snapshots what it read, rather than re-reading the real process environment.
     */
    @Test
    void environmentProviderResolvesFromInjectedEnvironment() {
        var env = Map.of(
                "AWS_ACCESS_KEY_ID",
                "injected_access_key",
                "AWS_SECRET_ACCESS_KEY",
                "injected_secret_key",
                "AWS_SESSION_TOKEN",
                "injected_token");
        var setup = ChainSetup.builder().env(env::get).build();
        var provider = new EnvironmentCredentialProvider();
        setup.setCurrentProvider(provider);
        provider.setup(AwsCredentialsIdentity.class, setup);

        assertTrue(setup.isTerminal(), "Expected the provider to claim the chain terminally when creds are present.");
        assertEquals(1, setup.resolvers().size());
        var identity = setup.resolvers().get(0).resolver().resolveIdentity(Context.empty()).unwrap();
        assertEquals(AwsCredentialsIdentity.create("injected_access_key", "injected_secret_key", "injected_token"),
                identity);
    }
}
