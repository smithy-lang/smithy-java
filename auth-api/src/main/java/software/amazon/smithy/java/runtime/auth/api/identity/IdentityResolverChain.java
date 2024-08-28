/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.identity;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;

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
    public CompletableFuture<IdentityT> resolveIdentity(AuthProperties requestProperties) {
        CompletableFuture<IdentityT> result = resolvers.get(0).resolveIdentity(requestProperties);
        for (var idx = 1; idx < resolvers.size(); idx++) {
            var next = resolvers.get(idx);
            result = result.exceptionallyComposeAsync(exc -> {
                if (exc instanceof IdentityNotFoundException) {
                    return next.resolveIdentity(requestProperties);
                }
                return CompletableFuture.failedFuture(exc);
            });
        }
        return result;
    }

    @Override
    public Class<IdentityT> identityType() {
        return identityClass;
    }
}
