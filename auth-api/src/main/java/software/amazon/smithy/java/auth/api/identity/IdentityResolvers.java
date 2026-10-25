/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api.identity;

import java.util.Arrays;
import java.util.List;

/**
 * An interface to allow retrieving an {@link IdentityResolver} based on the identity class.
 */
public interface IdentityResolvers {
    /**
     * Retrieve an identity resolver for the provided identity type.
     *
     * <p>A resolver matches if its identity type is the requested type or a subtype of it. An exact match has
     * priority over a subtype match.
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
     *
     * <p>If more than one resolver matches an identity type, the last resolver in the list wins.
     *
     * @param identityResolvers The {@link IdentityResolver}s to use
     * @return the IdentityResolvers
     */
    static IdentityResolvers of(List<IdentityResolver<?>> identityResolvers) {
        return new DefaultIdentityResolvers(identityResolvers);
    }
}
