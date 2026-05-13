/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.Identity;

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
     * <p>Called once during chain assembly in slot order. Return:
     * <ul>
     *   <li>{@link CreateResult#pass()} — this provider does not participate</li>
     *   <li>{@link CreateResult.PossibleMatch} — resolver added, assembly continues</li>
     *   <li>{@link CreateResult.UnconditionalMatch} — resolver added, assembly stops</li>
     * </ul>
     *
     * <p>Providers that need AWS config file data should read it from
     * {@link ProviderContext#profile()}, populated by the {@code SHARED_CONFIG} provider.
     *
     * @param identityType the identity class the chain is resolving.
     * @param context      shared resources provided by the chain.
     * @param <I>          the identity type.
     * @return the create result.
     */
    <I extends Identity> CreateResult<I> create(Class<I> identityType, ProviderContext context);
}
