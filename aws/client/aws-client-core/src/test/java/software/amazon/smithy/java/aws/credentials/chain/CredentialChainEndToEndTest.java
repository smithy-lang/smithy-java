/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.core.identity.EnvironmentCredentialProvider;
import software.amazon.smithy.java.aws.client.core.identity.SystemPropertiesCredentialProvider;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.config.SessionKeysHandler;
import software.amazon.smithy.java.aws.credentials.chain.config.StaticKeysHandler;
import software.amazon.smithy.java.context.Context;

/**
 * End-to-end assembly + resolution test that wires the <em>real</em> credential providers together through the
 * real {@link CredentialChain#assemble} and exercises fall-through across the sources.
 *
 * <p>Scope: this module ({@code aws-client-core}) sees the system-property, environment, and profile
 * (static/session keys) providers. It does not depend on {@code aws-credentials-sts} or
 * {@code aws-credentials-imds}, so the STS and IMDS slots are not covered here — a fuller test spanning those
 * would need a module that depends on all of them.
 *
 * <p>Determinism comes from the package-private {@link CredentialChain#assemble} overload that accepts a
 * caller-built {@link ChainSetup}: the environment is injected and the profile is pre-set, so nothing reads the
 * real process environment or {@code ~/.aws} files. The profile is set directly rather than via
 * {@code SharedConfigProvider} (which loads real config files and cannot be pointed at a temp path).
 */
class CredentialChainEndToEndTest {

    private static final List<ChainIdentityProvider> PROVIDERS = List.of(
            new SystemPropertiesCredentialProvider(),
            new EnvironmentCredentialProvider(),
            new StaticKeysHandler(),
            new SessionKeysHandler());

    @Test
    void resolvesFromSystemPropertiesFirst() {
        // System properties are set globally in the test JVM (build.gradle.kts). They are the highest-priority
        // slot, so the chain resolves from them and never consults env or profile.
        var chain = assembleWith(name -> null, null);

        var identity = chain.resolveIdentity(Context.empty()).unwrap();
        assertEquals("property_access_key", identity.accessKeyId());
        // Terminal at the first slot: only the system-properties resolver is in the chain.
        assertEquals(List.of("JavaSystemProperties"), chain.providerNames());
    }

    @Test
    void fallsThroughToEnvironmentWhenSystemPropertiesAbsent() {
        var env = Map.of(
                "AWS_ACCESS_KEY_ID",
                "env_ak",
                "AWS_SECRET_ACCESS_KEY",
                "env_sk",
                "AWS_SESSION_TOKEN",
                "env_tok");
        var chain = withoutSystemProperties(() -> assembleWith(env::get, null));

        var identity = chain.resolveIdentity(Context.empty()).unwrap();
        assertEquals("env_ak", identity.accessKeyId());
        assertEquals("env_tok", identity.sessionToken());
        // System properties fell through (no terminal there); environment claimed the chain.
        assertEquals(List.of("Environment"), chain.providerNames());
    }

    @Test
    void fallsThroughToProfileStaticKeysWhenSystemPropertiesAndEnvAbsent(@TempDir Path tmp) throws IOException {
        var profileFile = writeConfig(tmp, """
                [default]
                aws_access_key_id = profile_ak
                aws_secret_access_key = profile_sk
                """);
        var chain = withoutSystemProperties(() -> assembleWith(name -> null, profileFile));

        var identity = chain.resolveIdentity(Context.empty()).unwrap();
        assertEquals("profile_ak", identity.accessKeyId());
        // Fell through both env-based slots before reaching the profile static keys.
        assertEquals(List.of("StaticKeys"), chain.providerNames());
    }

    @Test
    void returnsAggregatedErrorWhenNoSourceResolves() {
        // No system properties, empty environment, and no profile: every provider declines and the chain reports
        // an error rather than resolving or throwing.
        var chain = withoutSystemProperties(() -> assembleWith(name -> null, null));

        var result = chain.resolveIdentity(Context.empty());
        assertNull(result.identity());
    }

    private static CredentialChain<AwsCredentialsIdentity> assembleWith(
            Function<String, String> env,
            AwsProfileFile profileFile
    ) {
        var setup = ChainSetup.builder().env(env).build();
        if (profileFile != null) {
            setup.setProfileFile(profileFile);
            setup.setProfile(profileFile.profile("default"));
        }
        return CredentialChain.assemble(AwsCredentialsIdentity.class, PROVIDERS, null, setup);
    }

    private static AwsProfileFile writeConfig(Path tmp, String contents) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, contents, StandardCharsets.UTF_8);
        return AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
    }

    /**
     * Runs the supplier with the system-property credentials cleared (the test JVM sets them globally), restoring
     * them afterward. The chain is assembled inside the supplier so assembly sees the cleared state.
     */
    private static <T> T withoutSystemProperties(Supplier<T> supplier) {
        String savedAccessKey = System.clearProperty("aws.accessKeyId");
        String savedSecretKey = System.clearProperty("aws.secretAccessKey");
        try {
            return supplier.get();
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
