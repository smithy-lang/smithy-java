/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.scheme;

import java.util.Optional;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolvers;

/**
 * An auth scheme for {@code smithy.api#noAuth} that represents no authentication.
 */
final class NoAuthAuthScheme implements AuthScheme<Object, Identity> {

    public static NoAuthAuthScheme INSTANCE = new NoAuthAuthScheme();
    private static final IdentityResolver<Identity> NULL_IDENTITY_RESOLVER = new NullIdentityResolver();

    private NoAuthAuthScheme() {}

    @Override
    public String schemeId() {
        return "smithy.api#noAuth";
    }

    @Override
    public Class<Object> requestClass() {
        return Object.class;
    }

    @Override
    public Class<Identity> identityClass() {
        return Identity.class;
    }

    /**
     * Retrieve an identity provider associated with this authentication scheme, that unconditionally returns an empty
     * {@link Identity}, independent of what resolvers are provided.
     *
     * @param resolvers Resolver repository.
     * @return An identity provider that unconditionally returns an empty identity.
     */
    @Override
    public Optional<IdentityResolver<Identity>> identityResolver(IdentityResolvers resolvers) {
        return Optional.of(NULL_IDENTITY_RESOLVER);
    }

    @Override
    public Signer<Object, Identity> signer() {
        return Signer.nullSigner();
    }

    private static class NullIdentityResolver implements IdentityResolver<Identity> {
        public static final Identity NULL_IDENTITY = new Identity() {};

        @Override
        public Identity resolveIdentity(AuthProperties requestProperties) {
            return NULL_IDENTITY;
        }

        @Override
        public Class<Identity> identityType() {
            return Identity.class;
        }
    }
}
