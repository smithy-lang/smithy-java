/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;

class IdentityResolversTest {

    @Test
    void findsResolverByExactType() {
        var resolver = typedResolver(TokenIdentity.class, TokenIdentity.create("token"));
        var resolvers = IdentityResolvers.of(resolver);

        assertSame(resolver, resolvers.identityResolver(TokenIdentity.class));
    }

    @Test
    void findsStaticResolverByIdentityInterface() {
        // IdentityResolver.of reports the hidden record class, not TokenIdentity.
        var resolver = IdentityResolver.of(TokenIdentity.create("token"));
        var resolvers = IdentityResolvers.of(resolver);

        assertSame(resolver, resolvers.identityResolver(TokenIdentity.class));
        assertSame(resolver, resolvers.identityResolver(Identity.class));
    }

    @Test
    void prefersExactTypeOverSubtype() {
        var exact = typedResolver(TokenIdentity.class, TokenIdentity.create("exact"));
        var subtype = IdentityResolver.of(TokenIdentity.create("subtype"));
        var resolvers = IdentityResolvers.of(exact, subtype);

        assertSame(exact, resolvers.identityResolver(TokenIdentity.class));
    }

    @Test
    void lastRegisteredResolverWinsForSameType() {
        var first = typedResolver(TokenIdentity.class, TokenIdentity.create("first"));
        var second = typedResolver(TokenIdentity.class, TokenIdentity.create("second"));

        assertSame(second, IdentityResolvers.of(first, second).identityResolver(TokenIdentity.class));
    }

    @Test
    void lastRegisteredResolverWinsForSubtypeMatch() {
        var first = IdentityResolver.of(TokenIdentity.create("first"));
        var second = IdentityResolver.of(TokenIdentity.create("second"));

        assertSame(second, IdentityResolvers.of(first, second).identityResolver(TokenIdentity.class));
    }

    @Test
    void returnsNullWhenNoResolverMatches() {
        var resolvers = IdentityResolvers.of(IdentityResolver.of(TokenIdentity.create("token")));

        assertNull(resolvers.identityResolver(ApiKeyIdentity.class));
    }

    @Test
    void typedStaticResolverReportsGivenType() {
        var identity = TokenIdentity.create("token");
        var resolver = IdentityResolver.of(TokenIdentity.class, identity);

        assertEquals(TokenIdentity.class, resolver.identityType());
        assertEquals(IdentityResult.of(identity), resolver.resolveIdentity(Context.empty()));
    }

    @Test
    void typedStaticResolverRejectsMismatchedIdentity() {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Class<Identity> wrongType = (Class) ApiKeyIdentity.class;
        Identity identity = TokenIdentity.create("token");

        assertThrows(IllegalArgumentException.class, () -> IdentityResolver.of(wrongType, identity));
    }

    private static <T extends Identity> IdentityResolver<T> typedResolver(Class<T> type, T identity) {
        return new IdentityResolver<>() {
            @Override
            public IdentityResult<T> resolveIdentity(Context requestProperties) {
                return IdentityResult.of(identity);
            }

            @Override
            public Class<T> identityType() {
                return type;
            }
        };
    }
}
