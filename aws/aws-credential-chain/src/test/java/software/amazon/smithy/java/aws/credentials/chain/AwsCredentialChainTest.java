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
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsResolver;
import software.amazon.smithy.java.context.Context;

class AwsCredentialChainTest {
    @Test
    void builtinProvidersAreOrderedByEnumOrder() {
        var chain = AwsCredentialChain.assemble(List.of(
                registration("imds",
                        new OrderingConstraint.Builtin(BuiltinProvider.EC2_INSTANCE_METADATA),
                        errorResolver("imds")),
                registration("env",
                        new OrderingConstraint.Builtin(BuiltinProvider.ENVIRONMENT),
                        errorResolver("env")),
                registration("profile",
                        new OrderingConstraint.Builtin(BuiltinProvider.SHARED_CONFIG),
                        errorResolver("profile"))));

        assertEquals(List.of("env", "profile", "imds"), chain.providerNames());
    }

    @Test
    void firstSuccessfulProviderWins() {
        var chain = AwsCredentialChain.assemble(List.of(
                registration("env",
                        new OrderingConstraint.Builtin(BuiltinProvider.ENVIRONMENT),
                        errorResolver("env")),
                registration("profile",
                        new OrderingConstraint.Builtin(BuiltinProvider.SHARED_CONFIG),
                        staticResolver("AK", "SK"))));
        IdentityResult<AwsCredentialsIdentity> result = chain.resolveIdentity(Context.empty());

        assertNotNull(result.identity());
        assertEquals("AK", result.identity().accessKeyId());
    }

    @Test
    void allFailReturnsAggregatedError() {
        var chain = AwsCredentialChain.assemble(List.of(
                registration("env",
                        new OrderingConstraint.Builtin(BuiltinProvider.ENVIRONMENT),
                        errorResolver("no env")),
                registration("profile",
                        new OrderingConstraint.Builtin(BuiltinProvider.SHARED_CONFIG),
                        errorResolver("no profile"))));
        IdentityResult<AwsCredentialsIdentity> result = chain.resolveIdentity(Context.empty());

        assertNull(result.identity());
        assertTrue(result.error().contains("no env"));
        assertTrue(result.error().contains("no profile"));
    }

    @Test
    void duplicateSlotThrows() {
        assertThrows(IllegalStateException.class,
                () -> AwsCredentialChain.assemble(List.of(
                        registration("a",
                                new OrderingConstraint.Builtin(BuiltinProvider.ENVIRONMENT),
                                errorResolver("a")),
                        registration("b",
                                new OrderingConstraint.Builtin(BuiltinProvider.ENVIRONMENT),
                                errorResolver("b")))));
    }

    @Test
    void relativeAfterInsertsCorrectly() {
        var chain = AwsCredentialChain.assemble(List.of(
                registration("env",
                        new OrderingConstraint.Builtin(BuiltinProvider.ENVIRONMENT),
                        errorResolver("env")),
                registration("profile",
                        new OrderingConstraint.Builtin(BuiltinProvider.SHARED_CONFIG),
                        errorResolver("profile")),
                registration("custom",
                        new OrderingConstraint.After(BuiltinProvider.ENVIRONMENT),
                        errorResolver("custom"))));

        assertEquals(List.of("env", "custom", "profile"), chain.providerNames());
    }

    @Test
    void relativeBeforeInsertsCorrectly() {
        var chain = AwsCredentialChain.assemble(List.of(
                registration("env",
                        new OrderingConstraint.Builtin(BuiltinProvider.ENVIRONMENT),
                        errorResolver("env")),
                registration("profile",
                        new OrderingConstraint.Builtin(BuiltinProvider.SHARED_CONFIG),
                        errorResolver("profile")),
                registration("custom",
                        new OrderingConstraint.Before(BuiltinProvider.SHARED_CONFIG),
                        errorResolver("custom"))));

        assertEquals(List.of("env", "custom", "profile"), chain.providerNames());
    }

    @Test
    void relativeToUnclaimedSlotAppendsAtEnd() {
        var chain = AwsCredentialChain.assemble(List.of(
                registration("env",
                        new OrderingConstraint.Builtin(BuiltinProvider.ENVIRONMENT),
                        errorResolver("env")),
                registration("custom",
                        new OrderingConstraint.After(BuiltinProvider.EC2_INSTANCE_METADATA),
                        errorResolver("custom"))));
        assertEquals(List.of("env", "custom"), chain.providerNames());
    }

    @Test
    void duplicateNameThrows() {
        assertThrows(IllegalStateException.class,
                () -> AwsCredentialChain.assemble(List.of(
                        registration("env",
                                new OrderingConstraint.Builtin(BuiltinProvider.ENVIRONMENT),
                                errorResolver("env")),
                        registration("env",
                                new OrderingConstraint.Builtin(BuiltinProvider.JAVA_SYSTEM_PROPERTIES),
                                errorResolver("env2")))));
    }

    @Test
    void emptyChainReturnsDescriptiveError() {
        var chain = AwsCredentialChain.assemble(List.of());
        IdentityResult<AwsCredentialsIdentity> result = chain.resolveIdentity(Context.empty());

        assertNull(result.identity());
        assertTrue(result.error().contains("No credential providers were discovered"));
    }

    private static AwsCredentialProvider registration(
            String name,
            OrderingConstraint ordering,
            AwsCredentialsResolver resolver
    ) {
        return new AwsCredentialProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public OrderingConstraint ordering() {
                return ordering;
            }

            @Override
            public AwsCredentialsResolver create(ProviderContext context) {
                return resolver;
            }
        };
    }

    private static AwsCredentialsResolver errorResolver(String msg) {
        return ctx -> IdentityResult.ofError(AwsCredentialChainTest.class, msg);
    }

    private static AwsCredentialsResolver staticResolver(String ak, String sk) {
        return ctx -> IdentityResult.of(AwsCredentialsIdentity.create(ak, sk));
    }
}
