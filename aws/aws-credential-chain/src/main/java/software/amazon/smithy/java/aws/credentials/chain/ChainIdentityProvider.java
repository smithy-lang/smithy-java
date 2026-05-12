/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;

/**
 * SPI for registering an identity provider into a credential/token chain.
 *
 * <p>A single provider can support multiple identity types by checking the requested type in
 * {@link #create(Class, ProviderContext)} and returning {@code null} for unsupported types.
 */
public interface ChainIdentityProvider {
    /**
     * @return the unique name of this provider.
     */
    String name();

    /**
     * @return the ordering constraint for this provider.
     */
    OrderingConstraint ordering();

    /**
     * The business metric feature IDs emitted when this provider successfully resolves an identity.
     *
     * @return the feature IDs, or empty if none.
     */
    default Set<CredentialFeatureId> featureIds() {
        return Set.of();
    }

    /**
     * Create the identity resolver for the requested identity type.
     *
     * <p>Called once during chain assembly. If this provider does not support the requested identity
     * type, it MUST return {@code null} and the chain will skip it.
     *
     * @param identityType the identity class the chain is resolving.
     * @param context      shared resources provided by the chain.
     * @param <I>          the identity type.
     * @return the resolver, or {@code null} if this provider does not support the requested type.
     */
    <I extends Identity> IdentityResolver<I> create(Class<I> identityType, ProviderContext context);
}
