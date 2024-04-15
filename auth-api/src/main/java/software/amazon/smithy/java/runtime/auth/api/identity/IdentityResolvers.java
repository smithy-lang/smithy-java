/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.identity;

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
}
