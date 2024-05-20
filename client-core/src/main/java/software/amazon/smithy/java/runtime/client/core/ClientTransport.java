/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import software.amazon.smithy.java.runtime.core.schema.ModeledSdkException;
import software.amazon.smithy.java.runtime.core.schema.SdkException;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

/**
 * Serializes inputs, sends requests on the wire, and deserializes responses.
 */
public interface ClientTransport {
    /**
     * Send a call using the transport and protocol.
     *
     * @param call Call to send.
     * @return the deserialized output.
     * @param <I> Input shape.
     * @param <O> Output shape.
     * @throws ModeledSdkException if a modeled error occurs.
     * @throws SdkException if an error occurs.
     */
    <I extends SerializableStruct, O extends SerializableStruct> O send(ClientCall<I, O> call);

    /**
     * Marker interface to indicate that the transport is SRA compliant.
     */
    interface SraCompliant {}
}
