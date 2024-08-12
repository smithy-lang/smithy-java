/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

/**
 * Creates a {@link ClientProtocol}.
 *
 * <p>This is the interface used to create protocols within a client builder (i.e. when a
 * protocol is used as the default protocol for a client).
 */
public interface ClientProtocolFactory {
    /**
     * Get the ID of the protocol (e.g., aws.protocols#restJson1).
     *
     * @return the protocol ID.
     */
    String id();

    /**
     * Factory method to create the protocol.
     * @param settings protocol settings to use for instantiating a protocol.
     * @return protocol implementation
     */
    ClientProtocol<?, ?> createProtocol(ProtocolSettings settings);
}
