/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.auth.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;

final class IdentityResolverChain<IdentityT extends Identity> implements IdentityResolver<IdentityT> {
    private final Class<IdentityT> identityClass;
    private final List<IdentityResolver<IdentityT>> resolvers;

    public IdentityResolverChain(List<IdentityResolver<IdentityT>> resolvers) {
        this.resolvers = Objects.requireNonNull(resolvers, "resolvers cannot be null");
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
        List<String> excMessages = new ArrayList<>();
        return executeChain(resolvers.get(0), requestProperties, excMessages, 0);
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<IdentityT> executeChain(
        IdentityResolver<IdentityT> resolver,
        AuthProperties requestProperties,
        List<String> excMessages,
        int idx
    ) {
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
}
