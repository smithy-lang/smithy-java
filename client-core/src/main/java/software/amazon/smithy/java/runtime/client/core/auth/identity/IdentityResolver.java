/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.auth.identity;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;

/**
 * Interface for loading {@link Identity} that is used for authentication.
 */
public interface IdentityResolver<IdentityT extends Identity> {
    /**
     * Resolve the identity from this identity resolver.
     *
     * <p>If not identity can be resolved, this method MUST throw {@link IdentityNotFoundException} and never
     * return null.
     *
     * @param requestProperties The request properties used to resolve an Identity.
     * @return a CompletableFuture for the resolved identity.
     * @throws IdentityNotFoundException when an identity cannot be resolved.
     */
    CompletableFuture<IdentityT> resolveIdentity(AuthProperties requestProperties);

    /**
     * Retrieve the class of the identity resolved by this identity resolver.
     *
     * @return the class of the identity.
     */
    Class<IdentityT> identityType();

    /**
     * Create an implementation of {@link IdentityResolver} that returns a specific, pre-defined instance of {@link Identity}.
     */
    static <I extends Identity> IdentityResolver<I> of(I identity) {
        return new StaticIdentityResolver<>(identity);
    }
}
