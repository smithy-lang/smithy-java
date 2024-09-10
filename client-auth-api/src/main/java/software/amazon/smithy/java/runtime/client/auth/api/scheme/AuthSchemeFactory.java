/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.auth.api.scheme;

import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;

/**
 * Creates an {@link AuthScheme}.
 * <p>This is the interface used to create protocols within a client builder (i.e. when a
 * protocol is used as the default protocol for a client).
 *
 * @param <T> Trait class used to apply protocol in the Smithy model.
 */
public interface AuthSchemeFactory<T extends Trait> {
    /**
     * Get the ID of the protocol (e.g., aws.protocols#restJson1).
     *
     * @return the protocol ID.
     */
    ShapeId schemeId();

    /**
     * Factory method to create the authScheme .
     * @param trait initialized trait used to define auth scheme in Smithy model.
     * @return AuthScheme implementation
     */
    AuthScheme<?, ?> createAuthScheme(T trait);
}
