/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.auth.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;

/**
 * Chain of Identity resolvers.
 *
 * <p>Each identity resolver is checked in order, returning the identity resolved or attempting the next resolver in the
 * chain if the resolver returns an {@link IdentityNotFoundException}. Exceptions from each resolver are aggregated and
 * returned in the resulting {@link IdentityNotFoundException} if all resolvers in the chain fail.
 *
 * <p>This chain can optionally cache the last successful resolver when chaining resolvers, avoiding the
 * need to go through the full chain each time an identity is resolved. If the cached resolver fails,
 * the chaining resolver will fall back to checking the full resolution chain.
 * 
 * @param <IdentityT> Identity class to resolve.
 */
public final class IdentityResolverChain<IdentityT extends Identity> implements IdentityResolver<IdentityT> {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(IdentityResolverChain.class);
    private final Class<IdentityT> identityClass;
    private final List<IdentityResolver<IdentityT>> resolvers;
    private final boolean reuseLastProvider;

    private IdentityResolver<IdentityT> lastUsedResolver;

    private IdentityResolverChain(Builder<IdentityT> builder) {
        this.resolvers = Objects.requireNonNull(builder.resolvers, "resolvers cannot be null");
        this.reuseLastProvider = builder.reuseLastProvider;
        if (resolvers.isEmpty()) {
            throw new IllegalArgumentException("Cannot chain empty resolvers list.");
        }
        identityClass = resolvers.get(0).identityType();
    }

    @Override
    public Class<IdentityT> identityType() {
        return identityClass;
    }

    @Override
    public CompletableFuture<IdentityT> resolveIdentity(AuthProperties requestProperties) {
        if (reuseLastProvider && lastUsedResolver != null) {
            // Attempt to resolve identity using cached resolver, otherwise fall back to default chain.
            return lastUsedResolver.resolveIdentity(requestProperties).exceptionallyComposeAsync(exc -> {
                if (exc instanceof IdentityNotFoundException) {
                    LOGGER.debug(
                        "Could not resolve identity using previously successful resolver {}. " +
                            "Falling back to default chain.",
                        lastUsedResolver,
                        exc
                    );
                    lastUsedResolver = null;
                    return this.resolveIdentity(requestProperties);
                }
                return CompletableFuture.failedFuture(exc);
            });
        }

        List<String> excMessages = new ArrayList<>(resolvers.size());
        return executeChain(resolvers.get(0), requestProperties, excMessages, 0);
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<IdentityT> executeChain(
        IdentityResolver<IdentityT> resolver,
        AuthProperties requestProperties,
        List<String> excMessages,
        int idx
    ) {
        lastUsedResolver = resolver;
        var result = resolver.resolveIdentity(requestProperties);
        if (idx + 1 < resolvers.size()) {
            var nextResolver = resolvers.get(idx + 1);
            return result.exceptionallyCompose(exc -> {
                if (exc instanceof IdentityNotFoundException) {
                    excMessages.add(exc.getMessage());
                    return executeChain(nextResolver, requestProperties, excMessages, idx + 1);
                }
                return CompletableFuture.failedFuture(exc);
            });
        }
        return result.exceptionallyComposeAsync(exc -> {
            if (exc instanceof IdentityNotFoundException) {
                excMessages.add(exc.getMessage());
                lastUsedResolver = null;
                return CompletableFuture.failedFuture(
                    new IdentityNotFoundException(
                        "Could not resolve identity with any resolvers in the chain : " + excMessages,
                        (Class<? extends IdentityResolver<?>>) this.getClass(),
                        identityClass
                    )
                );
            }
            return CompletableFuture.failedFuture(exc);
        });
    }

    /**
     * Create a {@link IdentityResolverChain} builder.
     *
     * @return Returns the created builder.
     * @param <I> Identity type
     */
    public static <I extends Identity> Builder<I> builder() {
        return new Builder<>();
    }

    /**
     * Builder for {@link IdentityResolverChain}.
     */
    public static final class Builder<IdentityT extends Identity> {
        private final List<IdentityResolver<IdentityT>> resolvers = new ArrayList<>();
        private boolean reuseLastProvider = true;

        private Builder() {
        }

        /**
         * Add identity resolvers to the chain.
         *
         * <p>Note: Chained Identity resolvers are checked in the order added.
         *
         * @param resolvers resolvers to add to chain.
         * @return this builder.
         */
        public Builder<IdentityT> addResolvers(List<IdentityResolver<IdentityT>> resolvers) {
            this.resolvers.addAll(resolvers);
            return this;
        }

        /**
         * Add identity resolver to the chain.
         *
         * <p>Note: Chained Identity resolvers are checked in the order added.
         *
         * @param resolver Resolver to add to the chain.
         * @return this builder.
         */
        public Builder<IdentityT> addResolver(IdentityResolver<IdentityT> resolver) {
            resolvers.add(resolver);
            return this;
        }

        /**
         * Whether to cache the last successful identity provider.
         *
         * @param reuseLastProvider whether to cache last successful provider. Defaults to true.
         * @return this builder.
         */
        public Builder<IdentityT> reuseLastProvider(boolean reuseLastProvider) {
            this.reuseLastProvider = reuseLastProvider;
            return this;
        }

        public IdentityResolverChain<IdentityT> build() {
            return new IdentityResolverChain<>(this);
        }
    }
}
