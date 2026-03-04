/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Encodes serializable structs from the model to frames.
 *
 * @param <F> the type of the frame.
 */
public interface EventEncoder<F extends Frame<?>> {

    /**
     * Returns the frame containing the encoded item.
     *
     * @param item the item to encode
     * @return the frame containing the encoded item
     */
    F encode(SerializableStruct item);

    /**
     * Returns the frame containing the encoded exception.
     *
     * @param exception the exception to encode
     * @return the frame containing the encoded exception
     */
    F encodeFailure(Throwable exception);

    /**
     * Returns a closing frame to be sent when the stream ends as defined in the protocol and/or auth scheme
     * signing the frames if any.
     * <p>
     * This method will return null if no closing frame needs to be sent.
     *
     * @return the closing frame.
     */
    F closingFrame();
}
