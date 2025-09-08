/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Decodes frames from bytes.
 */
public interface FrameDecoder<F extends Frame<?>> {
    /**
     * Decode 0 or more frames from a buffer, holding on to excess data
     * that can be prepended to the next `decode` call.
     * @param buffer the buffer to attempt to read frames from
     * @return all the frames readable from this pass
     */
    List<F> decode(ByteBuffer buffer);

    /**
     * Called once after building the publisher to allow the decoder to do any one-time setup prior to start processing
     * events.
     *
     * @param publisher The events publisher.
     */
    default void onPrepare(Flow.Publisher<SerializableStruct> publisher) {
        // does nothing by default.
    }
}
