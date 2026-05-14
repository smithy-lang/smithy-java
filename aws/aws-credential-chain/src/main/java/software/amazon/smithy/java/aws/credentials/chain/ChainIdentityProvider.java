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
 * <p>Implementations are discovered via the language's plugin mechanism (e.g., {@code ServiceLoader}
 * in Java) and sorted by {@link #ordering()} before {@link #create} is called. A provider
 * registers its resolver by calling {@link ChainSetup#addResolver} or
 * {@link ChainSetup#addTerminalResolver} from within {@code create()}.
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
     * Called once during chain assembly in sorted order. The provider inspects the identity
     * type and shared state on the setup, then optionally calls {@link ChainSetup#addResolver}
     * or {@link ChainSetup#addTerminalResolver} to register a resolver.
     *
     * <p>If this provider's preconditions are not met (wrong identity type, source not
     * configured), it simply returns without calling any add method.
     *
     * @param identityType the identity class the chain is resolving.
     * @param setup        the chain setup context.
     */
    void create(Class<? extends Identity> identityType, ChainSetup setup);
}
