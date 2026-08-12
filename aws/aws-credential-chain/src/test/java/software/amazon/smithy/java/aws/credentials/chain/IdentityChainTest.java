/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.config.SessionKeysHandler;
import software.amazon.smithy.java.aws.credentials.chain.config.SharedConfigProvider;
import software.amazon.smithy.java.aws.credentials.chain.config.StaticKeysHandler;
import software.amazon.smithy.java.context.Context;

class IdentityChainTest {
    @Test
    void awsCachingMatchesInvalidationByAccessKeyId() {
        var calls = new AtomicInteger();
        IdentityResolver<AwsCredentialsIdentity> delegate = new IdentityResolver<>() {
            @Override
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
                int call = calls.incrementAndGet();
                return IdentityResult.of(AwsCredentialsIdentity.create(
                        call == 1 ? "AKID" : "REFRESHED",
                        "secret-" + call,
                        null,
                        null));
            }

            @Override
            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }
        };
        var executor = Executors.newSingleThreadScheduledExecutor();
        var resolver = AwsCredentialCaching.staticallyStable(delegate, executor);
        try {
            assertEquals("AKID", resolver.resolveIdentity(Context.empty()).identity().accessKeyId());

            resolver.invalidate(AwsCredentialsIdentity.create("AKID", "different-secret", null, null));

            assertEquals("REFRESHED", resolver.resolveIdentity(Context.empty()).identity().accessKeyId());
            assertEquals(2, calls.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void standardProvidersAreOrderedByEnumOrder() {
        var chain = IdentityChain.assemble(AwsCredentialsIdentity.class,
                List.of(
                        registration("imds",
                                new OrderingConstraint.Standard(StandardProvider.EC2_INSTANCE_METADATA),
                                errorResolver("imds")),
                        registration("env",
                                new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                errorResolver("env")),
                        registration("profile",
                                new OrderingConstraint.Standard(StandardProvider.SHARED_CONFIG),
                                errorResolver("profile"))),
                null);

        assertEquals(List.of("env", "profile", "imds"), chain.providerNames());
    }

    @Test
    void rejectsResolverWithIncompatibleIdentityType() {
        IdentityResolver<TokenIdentity> tokenResolver = new IdentityResolver<>() {
            @Override
            public IdentityResult<TokenIdentity> resolveIdentity(Context requestProperties) {
                return IdentityResult.of(TokenIdentity.create("token"));
            }

            @Override
            public Class<TokenIdentity> identityType() {
                return TokenIdentity.class;
            }
        };

        var error = assertThrows(IllegalStateException.class,
                () -> IdentityChain.assemble(
                        AwsCredentialsIdentity.class,
                        List.of(registration(
                                "wrong-type",
                                new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                tokenResolver)),
                        null));

        assertTrue(error.getMessage().contains("wrong-type"));
        assertTrue(error.getMessage().contains(TokenIdentity.class.getName()));
        assertTrue(error.getMessage().contains(AwsCredentialsIdentity.class.getName()));
    }

    @Test
    void failedTypeValidationClosesResolverAndExecutor() {
        var closes = new AtomicInteger();
        var resolver = new CloseableResolver<>(
                TokenIdentity.class,
                TokenIdentity.create("token"),
                closes);
        var executor = Executors.newSingleThreadScheduledExecutor();
        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> IdentityChain.assemble(
                            AwsCredentialsIdentity.class,
                            List.of(registration(
                                    "wrong-type",
                                    new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                    resolver)),
                            executor));

            assertEquals(1, closes.get());
            assertTrue(executor.isShutdown());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void providerSetupFailureClosesPreviouslyRegisteredResolversAndExecutor() {
        var closes = new AtomicInteger();
        var resolver = new CloseableResolver<>(
                AwsCredentialsIdentity.class,
                AwsCredentialsIdentity.create("AK", "SK"),
                closes);
        ChainIdentityProvider failingProvider = new ChainIdentityProvider() {
            @Override
            public String name() {
                return "failing";
            }

            @Override
            public OrderingConstraint ordering() {
                return new OrderingConstraint.Standard(StandardProvider.SHARED_CONFIG);
            }

            @Override
            public void setup(Class<? extends Identity> identityType, ChainSetup setup) {
                throw new IllegalStateException("setup failed");
            }
        };
        var executor = Executors.newSingleThreadScheduledExecutor();
        try {
            var error = assertThrows(
                    IllegalStateException.class,
                    () -> IdentityChain.assemble(
                            AwsCredentialsIdentity.class,
                            List.of(
                                    registration(
                                            "first",
                                            new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                            resolver),
                                    failingProvider),
                            executor));

            assertEquals("setup failed", error.getMessage());
            assertEquals(1, closes.get());
            assertTrue(executor.isShutdown());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void closesResolverRegisteredMoreThanOnceOnlyOnce() {
        var closes = new AtomicInteger();
        var resolver = new CloseableResolver<>(
                AwsCredentialsIdentity.class,
                AwsCredentialsIdentity.create("AK", "SK"),
                closes);
        ChainIdentityProvider provider = new ChainIdentityProvider() {
            @Override
            public String name() {
                return "duplicate-resolver";
            }

            @Override
            public OrderingConstraint ordering() {
                return new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT);
            }

            @Override
            public void setup(Class<? extends Identity> identityType, ChainSetup setup) {
                setup.addResolver(resolver);
                setup.addResolver(resolver);
            }
        };
        var chain = IdentityChain.assemble(
                AwsCredentialsIdentity.class,
                List.of(provider),
                null);

        chain.close();

        assertEquals(1, closes.get());
    }

    @Test
    void awsCredentialCacheClosesItsOwnedDelegate() throws Exception {
        var closes = new AtomicInteger();
        var delegate = new CloseableResolver<>(
                AwsCredentialsIdentity.class,
                AwsCredentialsIdentity.create("AK", "SK"),
                closes);
        var executor = Executors.newSingleThreadScheduledExecutor();
        var cache = AwsCredentialCaching.staticallyStable(delegate, executor);
        try {
            ((AutoCloseable) cache).close();

            assertEquals(1, closes.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void acceptsResolverWithCompatibleIdentitySubtype() {
        var identity = new TestCredentials("AK", "SK");
        var chain = IdentityChain.assemble(
                AwsCredentialsIdentity.class,
                List.of(registration(
                        "subtype",
                        new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                        IdentityResolver.of(identity))),
                null);

        IdentityResult<AwsCredentialsIdentity> result = chain.resolveIdentity(Context.empty());

        assertEquals(identity, result.identity());
    }

    @Test
    void invalidatesOnlyResolverCompatibleWithRejectedIdentity() {
        var firstInvalidations = new AtomicInteger();
        var secondInvalidations = new AtomicInteger();
        var chain = IdentityChain.assemble(
                AwsCredentialsIdentity.class,
                List.of(
                        registration(
                                "first",
                                new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                trackingResolver(
                                        TestCredentials.class,
                                        new TestCredentials("AK", "SK"),
                                        firstInvalidations)),
                        registration(
                                "second",
                                new OrderingConstraint.Standard(StandardProvider.SHARED_CONFIG),
                                trackingResolver(
                                        OtherCredentials.class,
                                        new OtherCredentials("OTHER_AK", "OTHER_SK"),
                                        secondInvalidations))),
                null);

        AwsCredentialsIdentity rejectedIdentity = chain.resolveIdentity(Context.empty()).identity();
        assertNotNull(rejectedIdentity);
        chain.invalidate(rejectedIdentity);

        assertEquals(1, firstInvalidations.get());
        assertEquals(0, secondInvalidations.get());
    }

    @Test
    void firstSuccessfulProviderWins() {
        var chain = IdentityChain.assemble(AwsCredentialsIdentity.class,
                List.of(
                        registration("env",
                                new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                errorResolver("env")),
                        registration("profile",
                                new OrderingConstraint.Standard(StandardProvider.SHARED_CONFIG),
                                staticResolver("AK", "SK"))),
                null);
        IdentityResult<AwsCredentialsIdentity> result = chain.resolveIdentity(Context.empty());

        assertNotNull(result.identity());
        assertEquals("AK", result.identity().accessKeyId());
    }

    @Test
    void allFailReturnsAggregatedError() {
        var chain = IdentityChain.assemble(AwsCredentialsIdentity.class,
                List.of(
                        registration("env",
                                new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                errorResolver("no env")),
                        registration("profile",
                                new OrderingConstraint.Standard(StandardProvider.SHARED_CONFIG),
                                errorResolver("no profile"))),
                null);
        IdentityResult<AwsCredentialsIdentity> result = chain.resolveIdentity(Context.empty());

        assertNull(result.identity());
        assertTrue(result.error().contains("no env"));
        assertTrue(result.error().contains("no profile"));
    }

    @Test
    void duplicateSlotThrows() {
        assertThrows(IllegalStateException.class,
                () -> IdentityChain.assemble(AwsCredentialsIdentity.class,
                        List.of(
                                registration("a",
                                        new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                        errorResolver("a")),
                                registration("b",
                                        new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                        errorResolver("b"))),
                        null));
    }

    @Test
    void relativeAfterInsertsCorrectly() {
        var chain = IdentityChain.assemble(AwsCredentialsIdentity.class,
                List.of(
                        registration("env",
                                new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                errorResolver("env")),
                        registration("profile",
                                new OrderingConstraint.Standard(StandardProvider.SHARED_CONFIG),
                                errorResolver("profile")),
                        registration("custom",
                                new OrderingConstraint.After(StandardProvider.ENVIRONMENT),
                                errorResolver("custom"))),
                null);

        assertEquals(List.of("env", "custom", "profile"), chain.providerNames());
    }

    @Test
    void relativeBeforeInsertsCorrectly() {
        var chain = IdentityChain.assemble(AwsCredentialsIdentity.class,
                List.of(
                        registration("env",
                                new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                errorResolver("env")),
                        registration("profile",
                                new OrderingConstraint.Standard(StandardProvider.SHARED_CONFIG),
                                errorResolver("profile")),
                        registration("custom",
                                new OrderingConstraint.Before(StandardProvider.SHARED_CONFIG),
                                errorResolver("custom"))),
                null);

        assertEquals(List.of("env", "custom", "profile"), chain.providerNames());
    }

    @Test
    void relativeToUnclaimedSlotAppendsAtEnd() {
        var chain = IdentityChain.assemble(AwsCredentialsIdentity.class,
                List.of(
                        registration("env",
                                new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                errorResolver("env")),
                        registration("custom",
                                new OrderingConstraint.After(StandardProvider.EC2_INSTANCE_METADATA),
                                errorResolver("custom"))),
                null);

        assertEquals(List.of("env", "custom"), chain.providerNames());
    }

    @Test
    void afterUnclaimedSlotPrecedesNextClaimedSlot() {
        var chain = IdentityChain.assemble(AwsCredentialsIdentity.class,
                List.of(
                        registration("env",
                                new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                errorResolver("env")),
                        registration("imds",
                                new OrderingConstraint.Standard(StandardProvider.EC2_INSTANCE_METADATA),
                                errorResolver("imds")),
                        registration("custom",
                                new OrderingConstraint.After(StandardProvider.PROFILE_SSO_SESSION),
                                errorResolver("custom"))),
                null);

        assertEquals(List.of("env", "custom", "imds"), chain.providerNames());
    }

    @Test
    void relativeProvidersPreserveInsertionOrder() {
        var chain = IdentityChain.assemble(AwsCredentialsIdentity.class,
                List.of(
                        registration("before-a",
                                new OrderingConstraint.Before(StandardProvider.ENVIRONMENT),
                                errorResolver("before-a")),
                        registration("before-b",
                                new OrderingConstraint.Before(StandardProvider.ENVIRONMENT),
                                errorResolver("before-b")),
                        registration("env",
                                new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                errorResolver("env")),
                        registration("after-a",
                                new OrderingConstraint.After(StandardProvider.ENVIRONMENT),
                                errorResolver("after-a")),
                        registration("after-b",
                                new OrderingConstraint.After(StandardProvider.ENVIRONMENT),
                                errorResolver("after-b"))),
                null);

        assertEquals(
                List.of("before-a", "before-b", "env", "after-a", "after-b"),
                chain.providerNames());
    }

    @Test
    void duplicateNameThrows() {
        assertThrows(IllegalStateException.class,
                () -> IdentityChain.assemble(AwsCredentialsIdentity.class,
                        List.of(
                                registration("env",
                                        new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                        errorResolver("env")),
                                registration("env",
                                        new OrderingConstraint.Standard(StandardProvider.JAVA_SYSTEM_PROPERTIES),
                                        errorResolver("env2"))),
                        null));
    }

    @Test
    void emptyChainReturnsDescriptiveError() {
        var chain = IdentityChain.assemble(AwsCredentialsIdentity.class, List.of(), null);
        IdentityResult<AwsCredentialsIdentity> result = chain.resolveIdentity(Context.empty());

        assertNull(result.identity());
        assertTrue(result.error().contains("No credential providers were discovered"));
    }

    @Test
    void diagnosticsSinkReceivesProvidersTriedOnFailure() {
        var chain = IdentityChain.assemble(AwsCredentialsIdentity.class,
                List.of(
                        registration("env",
                                new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                errorResolver("no env")),
                        registration("profile",
                                new OrderingConstraint.Standard(StandardProvider.SHARED_CONFIG),
                                errorResolver("no profile"))),
                null);

        var captured = new ChainResolutionDiagnostics[1];
        var context = Context.create();
        context.put(IdentityChain.DIAGNOSTICS, d -> captured[0] = d);

        IdentityResult<AwsCredentialsIdentity> result = chain.resolveIdentity(context);

        assertNull(result.identity());
        assertNotNull(captured[0]);
        assertEquals(List.of("env", "profile"), captured[0].providersTried());
        assertTrue(captured[0].moduleSuggestions().isEmpty());
    }

    @Test
    void diagnosticsSinkNotInvokedOnSuccess() {
        var chain = IdentityChain.assemble(AwsCredentialsIdentity.class,
                List.of(
                        registration("env",
                                new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT),
                                staticResolver("ak", "sk"))),
                null);

        var captured = new ChainResolutionDiagnostics[1];
        var context = Context.create();
        context.put(IdentityChain.DIAGNOSTICS, d -> captured[0] = d);

        IdentityResult<AwsCredentialsIdentity> result = chain.resolveIdentity(context);

        assertNotNull(result.identity());
        // No sink invocation on success: diagnostics are a failure-path hook only.
        assertNull(captured[0]);
    }

    @Test
    void detectedSlotClaimedByProviderProducesNoMissingModuleHint() {
        // A provider claims a detected slot (JAVA_SYSTEM_PROPERTIES) but fails to resolve. Because the slot is
        // claimed, the aggregated error MUST NOT suggest adding a module for it. Regression test for a casing bug
        // where isClaimed() compared a provider name ("JavaSystemProperties") against the lower-cased slot name
        // ("java_system_properties"), never matched, and appended a spurious "add module" hint for claimed slots.
        var previous = System.getProperty("aws.accessKeyId");
        System.setProperty("aws.accessKeyId", "detected");
        try {
            var chain = IdentityChain.assemble(AwsCredentialsIdentity.class,
                    List.of(
                            registration("JavaSystemProperties",
                                    new OrderingConstraint.Standard(StandardProvider.JAVA_SYSTEM_PROPERTIES),
                                    errorResolver("no system property credentials"))),
                    null);
            IdentityResult<AwsCredentialsIdentity> result = chain.resolveIdentity(Context.empty());

            assertNull(result.identity());
            assertTrue(result.error().contains("JavaSystemProperties: no system property credentials"));
            assertTrue(!result.error().contains("add"),
                    "Claimed slot must not produce a missing-module hint, but got: " + result.error());
        } finally {
            if (previous == null) {
                System.clearProperty("aws.accessKeyId");
            } else {
                System.setProperty("aws.accessKeyId", previous);
            }
        }
    }

    @Test
    void registeredProviderWithoutResolverProducesNoMissingModuleHint() {
        ChainIdentityProvider registeredEcsProvider = new ChainIdentityProvider() {
            @Override
            public String name() {
                return "Ecs";
            }

            @Override
            public OrderingConstraint ordering() {
                return new OrderingConstraint.Standard(StandardProvider.ECS_CONTAINER);
            }

            @Override
            public void setup(Class<? extends Identity> identityType, ChainSetup setup) {
                // This installed module does not support the requested identity type.
            }
        };
        var setup = ChainSetup.builder()
                .env(name -> name.equals("AWS_CONTAINER_CREDENTIALS_FULL_URI") ? "http://localhost" : null)
                .build();
        var chain = IdentityChain.assemble(
                AwsCredentialsIdentity.class,
                List.of(registeredEcsProvider),
                null,
                setup);

        var diagnostics = new ChainResolutionDiagnostics[1];
        var context = Context.create();
        context.put(IdentityChain.DIAGNOSTICS, value -> diagnostics[0] = value);
        var result = chain.resolveIdentity(context);

        assertTrue(result.error().contains("No credential providers were discovered"));
        assertTrue(diagnostics[0].moduleSuggestions().isEmpty());
        assertTrue(!result.error().contains("aws-credentials-ecs"));
    }

    @Test
    void profileRoleDefersCoreKeysWhenStsModuleIsMissing(@TempDir Path tempDir) throws IOException {
        Path config = tempDir.resolve("config");
        Files.writeString(config, """
                [default]
                role_arn = arn:aws:iam::123456789012:role/Foo
                source_profile = default
                aws_access_key_id = AKID
                aws_secret_access_key = SECRET
                """);
        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var setup = ChainSetup.builder().profileFile(profileFile).env(name -> null).build();
        var chain = IdentityChain.assemble(
                AwsCredentialsIdentity.class,
                List.of(new SharedConfigProvider(), new SessionKeysHandler(), new StaticKeysHandler()),
                null,
                setup);
        var diagnostics = new ChainResolutionDiagnostics[1];
        var context = Context.create();
        context.put(IdentityChain.DIAGNOSTICS, value -> diagnostics[0] = value);

        var result = chain.resolveIdentity(context);

        assertNull(result.identity());
        assertEquals(
                List.of("software.amazon.smithy.java:aws-credentials-sts"),
                diagnostics[0].moduleSuggestions());
        assertTrue(result.error().contains("aws-credentials-sts"));
    }

    private static ChainIdentityProvider registration(
            String name,
            OrderingConstraint ordering,
            IdentityResolver<?> resolver
    ) {
        return new ChainIdentityProvider() {
            public String name() {
                return name;
            }

            public OrderingConstraint ordering() {
                return ordering;
            }

            public void setup(Class<? extends Identity> identityType, ChainSetup setup) {
                setup.addResolver(resolver);
            }
        };
    }

    private static IdentityResolver<AwsCredentialsIdentity> errorResolver(String msg) {
        IdentityResult<AwsCredentialsIdentity> result = IdentityResult.ofError(IdentityChainTest.class, msg);
        return new IdentityResolver<>() {
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
                return result;
            }

            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }
        };
    }

    private static IdentityResolver<AwsCredentialsIdentity> staticResolver(String ak, String sk) {
        IdentityResult<AwsCredentialsIdentity> result = IdentityResult.of(AwsCredentialsIdentity.create(ak, sk));
        return new IdentityResolver<>() {
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
                return result;
            }

            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }
        };
    }

    private static <I extends Identity> IdentityResolver<I> trackingResolver(
            Class<I> identityType,
            I identity,
            AtomicInteger invalidations
    ) {
        return new IdentityResolver<>() {
            @Override
            public IdentityResult<I> resolveIdentity(Context requestProperties) {
                return IdentityResult.of(identity);
            }

            @Override
            public Class<I> identityType() {
                return identityType;
            }

            @Override
            public void invalidate(I rejectedIdentity) {
                invalidations.incrementAndGet();
            }
        };
    }

    private static final class CloseableResolver<I extends Identity>
            implements IdentityResolver<I>, AutoCloseable {
        private final Class<I> identityType;
        private final IdentityResult<I> result;
        private final AtomicInteger closes;

        CloseableResolver(Class<I> identityType, I identity, AtomicInteger closes) {
            this.identityType = identityType;
            this.result = IdentityResult.of(identity);
            this.closes = closes;
        }

        @Override
        public IdentityResult<I> resolveIdentity(Context requestProperties) {
            return result;
        }

        @Override
        public Class<I> identityType() {
            return identityType;
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }

    private record TestCredentials(String accessKeyId, String secretAccessKey) implements AwsCredentialsIdentity {}

    private record OtherCredentials(String accessKeyId, String secretAccessKey) implements AwsCredentialsIdentity {}
}
