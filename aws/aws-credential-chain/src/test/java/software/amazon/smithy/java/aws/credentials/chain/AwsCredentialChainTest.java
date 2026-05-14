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

class AwsCredentialChainTest {
    @Test
    void standardProvidersAreOrderedByEnumOrder() {
        var chain = CredentialChain.assemble(AwsCredentialsIdentity.class,
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
        var chain = CredentialChain.assemble(AwsCredentialsIdentity.class,
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
        var chain = CredentialChain.assemble(AwsCredentialsIdentity.class,
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
                () -> CredentialChain.assemble(AwsCredentialsIdentity.class,
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
        var chain = CredentialChain.assemble(AwsCredentialsIdentity.class,
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
        var chain = CredentialChain.assemble(AwsCredentialsIdentity.class,
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
        var chain = CredentialChain.assemble(AwsCredentialsIdentity.class,
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
                () -> CredentialChain.assemble(AwsCredentialsIdentity.class,
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
        var chain = CredentialChain.assemble(AwsCredentialsIdentity.class, List.of(), null);
        IdentityResult<AwsCredentialsIdentity> result = chain.resolveIdentity(Context.empty());

        assertNull(result.identity());
        assertTrue(result.error().contains("No credential providers were discovered"));
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
        IdentityResult<AwsCredentialsIdentity> result = IdentityResult.ofError(AwsCredentialChainTest.class, msg);
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
