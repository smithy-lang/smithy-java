/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.auth.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.AuthProperties;
import software.amazon.smithy.java.auth.api.identity.TokenIdentity;

public class IdentityResolverTest {
    private static final TokenIdentity TEST_IDENTITY = TokenIdentity.create("==MyToken==");
    private static final IdentityResolver<TokenIdentity> TEST_RESOLVER = IdentityResolver.of(TEST_IDENTITY);

    @Test
    void testStaticIdentityReturnsExpected() {
        assertEquals(TEST_RESOLVER.identityType(), TEST_IDENTITY.getClass());
        var resolved = TEST_RESOLVER.resolveIdentity(AuthProperties.empty()).join();
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
        var result = resolver.resolveIdentity(AuthProperties.empty()).join();
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
                CompletionException.class,
                () -> resolver.resolveIdentity(AuthProperties.empty()).join());
        assertTrue(exc.getMessage().contains("BAD!"));
    }

    @Test
    void testIdentityResolverAggregatesExceptions() {
        var resolver = IdentityResolver.chain(
                List.of(
                        EmptyResolver.INSTANCE,
                        EmptyResolver.INSTANCE,
                        EmptyResolver.INSTANCE));
        var result = resolver.resolveIdentity(AuthProperties.empty()).join();

        assertTrue(
                result.error()
                        .contains(
                                "[IdentityResult[error='No token. Womp Womp.', resolver=software.amazon.smithy.java.client.core.auth.identity.IdentityResolverTest$EmptyResolver], "
                                        + "IdentityResult[error='No token. Womp Womp.', resolver=software.amazon.smithy.java.client.core.auth.identity.IdentityResolverTest$EmptyResolver], "
                                        + "IdentityResult[error='No token. Womp Womp.', resolver=software.amazon.smithy.java.client.core.auth.identity.IdentityResolverTest$EmptyResolver]]"));

        var e = assertThrows(IdentityNotFoundException.class, result::unwrap);

        assertTrue(e.getMessage().startsWith("Unable to resolve an identity: Attempted resolvers: "));
    }

    /**
     * Always returns exceptional result with {@link IdentityNotFoundException}.
     */
    private static final class EmptyResolver implements IdentityResolver<TokenIdentity> {
        private static final EmptyResolver INSTANCE = new EmptyResolver();

        @Override
        public CompletableFuture<IdentityResult<TokenIdentity>> resolveIdentity(AuthProperties requestProperties) {
            return CompletableFuture.completedFuture(IdentityResult.ofError(getClass(), "No token. Womp Womp."));
        }

        @Override
        public Class<TokenIdentity> identityType() {
            return TokenIdentity.class;
        }
    }

    /**
     * Always returns a failed future with a {@link IllegalArgumentException}.
     */
    private static final class FailingResolver implements IdentityResolver<TokenIdentity> {
        private static final FailingResolver INSTANCE = new FailingResolver();
        private static final IllegalArgumentException ILLEGAL_ARGUMENT_EXCEPTION = new IllegalArgumentException("BAD!");

        @Override
        public CompletableFuture<IdentityResult<TokenIdentity>> resolveIdentity(AuthProperties requestProperties) {
            return CompletableFuture.failedFuture(ILLEGAL_ARGUMENT_EXCEPTION);
        }

        @Override
        public Class<TokenIdentity> identityType() {
            return TokenIdentity.class;
        }
    }
}
