/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api.identity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class DefaultIdentityResolvers implements IdentityResolvers {

    private final List<IdentityResolver<?>> resolvers;
    private final Map<Class<?>, IdentityResolver<?>> cache = new ConcurrentHashMap<>();

    DefaultIdentityResolvers(List<IdentityResolver<?>> identityResolvers) {
        this.resolvers = List.copyOf(identityResolvers);
        for (IdentityResolver<?> resolver : resolvers) {
            var type =
                    Objects.requireNonNull(resolver.identityType(), () -> resolver + " returned a null identityType");
            cache.put(type, resolver);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Identity> IdentityResolver<T> identityResolver(Class<T> identityClass) {
        var resolver = cache.get(identityClass);
        if (resolver == null) {
            resolver = findAssignable(identityClass);
            if (resolver != null) {
                cache.put(identityClass, resolver);
            }
        }
        return (IdentityResolver<T>) resolver;
    }

    private IdentityResolver<?> findAssignable(Class<?> identityClass) {
        // Search in reverse so that the last registered resolver wins, the same as for exact matches.
        for (int i = resolvers.size() - 1; i >= 0; i--) {
            var resolver = resolvers.get(i);
            if (identityClass.isAssignableFrom(resolver.identityType())) {
                return resolver;
            }
        }
        return null;
    }
}
