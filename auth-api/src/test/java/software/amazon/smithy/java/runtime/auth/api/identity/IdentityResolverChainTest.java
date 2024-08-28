/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;

public class IdentityResolverChainTest {
    private static final TokenIdentity TEST_IDENTITY = TokenIdentity.create("==MyToken==");

    @Test
    void testIdentityResolverChainContinuesOnIdentityNotFound() throws ExecutionException, InterruptedException {
        var resolver = IdentityResolver.chain(List.of(EmptyResolver.INSTANCE, TestResolver.INSTANCE));
        var result = resolver.resolveIdentity(AuthProperties.empty()).get();
        assertEquals(result, TEST_IDENTITY);
    }

    @Test
    void testIdentityResolverChainStopsOnUnexpectedFailure() throws ExecutionException, InterruptedException {
        var resolver = IdentityResolver.chain(
            List.of(EmptyResolver.INSTANCE, FailingResolver.INSTANCE, TestResolver.INSTANCE)
        );
        var exc = assertThrows(ExecutionException.class, () -> resolver.resolveIdentity(AuthProperties.empty()).get());
        assertEquals(exc.getCause(), FailingResolver.ILLEGAL_ARGUMENT_EXCEPTION);
    }


    /**
     * Always returns exceptional result with {@link IdentityNotFoundException}.
     */
    private static final class EmptyResolver implements IdentityResolver<TokenIdentity> {
        private static final EmptyResolver INSTANCE = new EmptyResolver();

        @Override
        public CompletableFuture<TokenIdentity> resolveIdentity(AuthProperties requestProperties) {
            return CompletableFuture.failedFuture(
                new IdentityNotFoundException(
                    "No token. Womp Womp.",
                    EmptyResolver.class,
                    TokenIdentity.class
                )
            );
        }

        @Override
        public Class<TokenIdentity> identityType() {
            return TokenIdentity.class;
        }
    }

    /**
     * Always return a successful result with a token identity.
     */
    private static final class TestResolver implements IdentityResolver<TokenIdentity> {
        private static final TestResolver INSTANCE = new TestResolver();

        @Override
        public CompletableFuture<TokenIdentity> resolveIdentity(AuthProperties requestProperties) {
            return CompletableFuture.completedFuture(TEST_IDENTITY);
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
        public CompletableFuture<TokenIdentity> resolveIdentity(AuthProperties requestProperties) {
            return CompletableFuture.failedFuture(ILLEGAL_ARGUMENT_EXCEPTION);
        }

        @Override
        public Class<TokenIdentity> identityType() {
            return TokenIdentity.class;
        }
    }
}
