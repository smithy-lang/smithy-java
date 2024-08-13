/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;

/**
 * Creates a {@link ClientProtocol}.
 * <p>This is the interface used to create protocols within a client builder (i.e. when a
 * protocol is used as the default protocol for a client).
 *
 * @param <T> Trait class used to apply protocol in the Smithy model.
 */
public interface ClientProtocolFactory<T extends Trait> {
    /**
     * Get the ID of the protocol (e.g., aws.protocols#restJson1).
     *
     * @return the protocol ID.
     */
    ShapeId id();

    /**
     * Factory method to create the protocol.
     * @param settings protocol settings to use for instantiating a protocol.
     * @param trait initialized trait used to defined protocol in Smithy model.
     * @return protocol implementation
     */
    ClientProtocol<?, ?> createProtocol(ProtocolSettings settings, T trait);
}
