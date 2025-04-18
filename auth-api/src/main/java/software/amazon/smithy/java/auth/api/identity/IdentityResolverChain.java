/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.context.Context;

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
    public CompletableFuture<IdentityResult<IdentityT>> resolveIdentity(Context requestProperties) {
        List<IdentityResult<?>> errors = new ArrayList<>();
        return executeChain(resolvers.get(0), requestProperties, errors, 0);
    }

    private CompletableFuture<IdentityResult<IdentityT>> executeChain(
            IdentityResolver<IdentityT> resolver,
            Context requestProperties,
            List<IdentityResult<?>> errors,
            int idx
    ) {
        var result = resolver.resolveIdentity(requestProperties);
        if (idx + 1 < resolvers.size()) {
            var nextResolver = resolvers.get(idx + 1);
            return result.thenCompose(ir -> {
                if (ir.error() != null) {
                    errors.add(ir);
                    return executeChain(nextResolver, requestProperties, errors, idx + 1);
                }
                return CompletableFuture.completedFuture(ir);
            });
        }

        return result.thenApply(ir -> {
            if (ir.error() != null) {
                errors.add(ir);
                return IdentityResult.ofError(IdentityResolverChain.class, "Attempted resolvers: " + errors);
            }
            return ir;
        });
    }
}
