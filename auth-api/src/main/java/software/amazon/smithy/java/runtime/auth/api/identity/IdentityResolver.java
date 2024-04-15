/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.identity;

import software.amazon.smithy.java.runtime.auth.api.AuthProperties;

/**
 * Interface for loading {@link Identity} that is used for authentication.
 */
public interface IdentityResolver<IdentityT extends Identity> {
    /**
     * Resolve the identity from this identity provider.
     *
     * <p>If not identity can be resolved, this method MUST throw {@link IdentityNotFoundException} and never
     * return null.
     *
     * @param requestProperties The request properties used to resolve an Identity.
     * @return the resolved identity.
     * @throws IdentityNotFoundException when an identity cannot be resolved.
     */
    IdentityT resolveIdentity(AuthProperties requestProperties);

    /**
     * Retrieve the class of identity this identity resolver produces.
     *
     * @return the class of the identity.
     */
    Class<IdentityT> identityType();
}
