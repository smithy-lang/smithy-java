/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api.identity;

import java.util.List;
import software.amazon.smithy.java.context.Context;

/**
 * Interface for loading {@link Identity} that is used for authentication.
 */
public interface IdentityResolver<IdentityT extends Identity> {
    /**
     * Resolve the identity from this identity resolver.
     *
     * <p>Expected errors like missing environment variables are expected to return a result that contains an
     * error string. Unexpected errors like malformed input or networking errors are allowed to throw exceptions.
     *
     * @param requestProperties The request properties used to resolve an Identity.
     * @return the resolved identity result.
     */
    IdentityResult<IdentityT> resolveIdentity(Context requestProperties);

    /**
     * Retrieve the class of the identity resolved by this identity resolver.
     *
     * @return the class of the identity.
     */
    Class<IdentityT> identityType();

    /**
     * Combines multiple identity resolvers with the same identity type into a single resolver.
     *
     * @param resolvers Resolvers to combine.
     * @return the combined resolvers.
     */
    static <I extends Identity> IdentityResolver<I> chain(List<IdentityResolver<I>> resolvers) {
        return new IdentityResolverChain<>(resolvers);
    }

    /**
     * Create an implementation of {@link IdentityResolver} that returns a specific, pre-defined instance of
     * {@link Identity}.
     */
    static <I extends Identity> IdentityResolver<I> of(I identity) {
        return new StaticIdentityResolver<>(identity);
    }
}
