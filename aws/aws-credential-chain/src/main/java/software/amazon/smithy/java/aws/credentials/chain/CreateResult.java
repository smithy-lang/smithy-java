/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;

/**
 * The result of {@link ChainIdentityProvider#create}. Indicates whether the provider
 * participates in the chain and whether it is unconditionally authoritative.
 *
 * @param <I> the identity type.
 */
public sealed interface CreateResult<I extends Identity> {
    /**
     * This provider does not participate in the chain (preconditions not met).
     */
    record Pass<I extends Identity>() implements CreateResult<I> {}

    /**
     * This provider might resolve credentials, but resolution could fail at request time
     * (e.g., IMDS network call, STS assume-role). Assembly continues to discover fallback providers.
     *
     * @param resolver the resolver to add to the chain.
     */
    record PossibleMatch<I extends Identity>(IdentityResolver<I> resolver) implements CreateResult<I> {}

    /**
     * This provider will unconditionally resolve credentials (e.g., environment variables are set,
     * static keys are present). Assembly stops — no further providers are needed.
     *
     * @param resolver the resolver to add to the chain.
     */
    record UnconditionalMatch<I extends Identity>(IdentityResolver<I> resolver) implements CreateResult<I> {}

    /**
     * Returns a {@link Pass} result.
     */
    @SuppressWarnings("unchecked")
    static <I extends Identity> CreateResult<I> pass() {
        return (CreateResult<I>) PASS;
    }

    Pass<?> PASS = new Pass<>();
}
