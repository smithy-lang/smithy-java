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

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;

class IdentityChainTest {
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

    private static ChainIdentityProvider registration(
            String name,
            OrderingConstraint ordering,
            IdentityResolver<AwsCredentialsIdentity> resolver
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
}
