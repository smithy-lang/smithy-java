/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.identity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An interface to allow retrieving an IdentityProvider based on the identity class.
 */
public interface IdentityResolvers {
    /**
     * Retrieve an identity provider for the provided identity type.
     *
     * @param identityClass Identity type to retrieve.
     * @return the identity resolver or null if not found.
     */
    <T extends Identity> IdentityResolver<T> identityResolver(Class<T> identityClass);


    /**
     * Create a new IdentityResolvers
     * @param identityResolvers The {@link IdentityResolver}s to use
     * @return the IdentityResolvers
     */
    static IdentityResolvers of(IdentityResolver<?>... identityResolvers) {
        return of(Arrays.asList(identityResolvers));
    }

    /**
     * Create a new IdentityResolvers
     * @param identityResolvers The {@link IdentityResolver}s to use
     * @return the IdentityResolvers
     */
    static IdentityResolvers of(List<IdentityResolver<?>> identityResolvers) {
        Map<Class<?>, IdentityResolver<?>> result = new HashMap<>();
        for (IdentityResolver<?> identityResolver : identityResolvers) {
            result.put(identityResolver.identityType(), identityResolver);
        }

        return new IdentityResolvers() {
            @Override
            public <T extends Identity> IdentityResolver<T> identityResolver(Class<T> identityClass) {
                return (IdentityResolver<T>) result.get(identityClass);
            }
        };
    }
}
