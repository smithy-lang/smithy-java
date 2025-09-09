/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.auth.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.identity.IdentityNotFoundException;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.context.Context;

public class IdentityResolverTest {
    private static final TokenIdentity TEST_IDENTITY = TokenIdentity.create("==MyToken==");
    private static final IdentityResolver<TokenIdentity> TEST_RESOLVER = IdentityResolver.of(TEST_IDENTITY);

    @Test
    void testStaticIdentityReturnsExpected() {
        assertEquals(TEST_RESOLVER.identityType(), TEST_IDENTITY.getClass());
        var resolved = TEST_RESOLVER.resolveIdentity(Context.empty());
        assertEquals(IdentityResult.of(TEST_IDENTITY), resolved);
    }

    @Test
    void testIdentityResolverChainContinuesOnIdentityNotFound() {
        var resolver = IdentityResolver.chain(
                List.of(
                        EmptyResolver.INSTANCE,
                        EmptyResolver.INSTANCE,
                        EmptyResolver.INSTANCE,
                        TEST_RESOLVER));
        var result = resolver.resolveIdentity(Context.empty());
        assertEquals(result, IdentityResult.of(TEST_IDENTITY));
    }

    @Test
    void testIdentityResolverFailsOutOnUnknownError() {
        var resolver = IdentityResolver.chain(
                List.of(
                        EmptyResolver.INSTANCE,
                        EmptyResolver.INSTANCE,
                        FailingResolver.INSTANCE));
        var exc = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveIdentity(Context.empty()));
        assertTrue(exc.getMessage().contains("BAD!"));
    }

    @Test
    void testIdentityResolverAggregatesExceptions() {
        var resolver = IdentityResolver.chain(
                List.of(
                        EmptyResolver.INSTANCE,
                        EmptyResolver.INSTANCE,
                        EmptyResolver.INSTANCE));

        var e = Assertions.assertThrows(
                IdentityNotFoundException.class,
                () -> resolver.resolveIdentity(Context.empty()).unwrap());

        assertTrue(e.getMessage()
                .contains(
                        "[IdentityResult[error='No token. Womp Womp.', resolver=software.amazon.smithy.java.client.core.auth.identity.IdentityResolverTest$EmptyResolver], "
                                + "IdentityResult[error='No token. Womp Womp.', resolver=software.amazon.smithy.java.client.core.auth.identity.IdentityResolverTest$EmptyResolver], "
                                + "IdentityResult[error='No token. Womp Womp.', resolver=software.amazon.smithy.java.client.core.auth.identity.IdentityResolverTest$EmptyResolver]]"));

        assertTrue(e.getMessage().startsWith("Unable to resolve an identity: Attempted resolvers: "));
    }

    /**
     * Always returns exceptional result with {@link IdentityNotFoundException}.
     */
    private static final class EmptyResolver implements IdentityResolver<TokenIdentity> {
        private static final EmptyResolver INSTANCE = new EmptyResolver();

        @Override
        public IdentityResult<TokenIdentity> resolveIdentity(Context requestProperties) {
            return IdentityResult.ofError(getClass(), "No token. Womp Womp.");
        }

        @Override
        public Class<TokenIdentity> identityType() {
            return TokenIdentity.class;
        }
    }

    /**
     * Always throws {@link IllegalArgumentException}.
     */
    private static final class FailingResolver implements IdentityResolver<TokenIdentity> {
        private static final FailingResolver INSTANCE = new FailingResolver();
        private static final IllegalArgumentException ILLEGAL_ARGUMENT_EXCEPTION = new IllegalArgumentException("BAD!");

        @Override
        public IdentityResult<TokenIdentity> resolveIdentity(Context requestProperties) {
            throw ILLEGAL_ARGUMENT_EXCEPTION;
        }

        @Override
        public Class<TokenIdentity> identityType() {
            return TokenIdentity.class;
        }
    }
}
