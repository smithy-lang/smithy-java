/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    public IdentityResult<IdentityT> resolveIdentity(Context requestProperties) {
        List<IdentityResult<?>> errors = new ArrayList<>();
        for (IdentityResolver<IdentityT> resolver : resolvers) {
            IdentityResult<IdentityT> result = resolver.resolveIdentity(requestProperties);
            if (result.error() == null) {
                return result;
            }
            errors.add(result);
        }
        return IdentityResult.ofError(IdentityResolverChain.class, "Attempted resolvers: " + errors);
    }
}
