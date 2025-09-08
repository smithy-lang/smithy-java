/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import java.util.concurrent.Flow;
import software.amazon.smithy.java.core.schema.SerializableStruct;

public interface EventDecoder<F extends Frame<?>> {

    SerializableStruct decode(F frame);

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
