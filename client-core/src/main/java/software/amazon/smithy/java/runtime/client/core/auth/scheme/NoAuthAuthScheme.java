/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.auth.scheme;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;
import software.amazon.smithy.java.runtime.client.core.auth.identity.IdentityResolver;
import software.amazon.smithy.java.runtime.client.core.auth.identity.IdentityResolvers;
import software.amazon.smithy.java.runtime.client.core.auth.identity.IdentityResult;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * An auth scheme for {@code smithy.api#noAuth} that represents no authentication.
 */
final class NoAuthAuthScheme implements AuthScheme<Object, Identity> {
    public static final NoAuthAuthScheme INSTANCE = new NoAuthAuthScheme();
    private static final IdentityResolver<Identity> NULL_IDENTITY_RESOLVER = new NullIdentityResolver();
    private static final ShapeId ID = ShapeId.from("smithy.api#noAuth");

    private NoAuthAuthScheme() {}

    @Override
    public ShapeId schemeId() {
        return ID;
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
     * Retrieve an identity resolver associated with this authentication scheme, that unconditionally returns an empty
     * {@link Identity}, independent of what resolvers are provided.
     *
     * @param resolvers Resolver repository.
     * @return An identity resolver that unconditionally returns an empty identity.
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
        public static final CompletableFuture<IdentityResult<Identity>> NULL_IDENTITY = CompletableFuture
            .completedFuture(
                IdentityResult.of(new Identity() {})
            );

        @Override
        public CompletableFuture<IdentityResult<Identity>> resolveIdentity(AuthProperties requestProperties) {
            return NULL_IDENTITY;
        }

        @Override
        public Class<Identity> identityType() {
            return Identity.class;
        }
    }
}
