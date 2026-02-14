/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import software.amazon.smithy.java.core.schema.SerializableStruct;

public interface EventDecoder<F extends Frame<?>> {

    SerializableStruct decode(F frame);

    /**
     * Decodes the frame into an initial event using the given even stream
     * to set to the corresponding member.
     *
     * @param frame The frame to that contains the initial event
     * @param stream the event stream value to be set in the member
     * @return the initial event struct.
     */
    SerializableStruct decodeInitialEvent(F frame, EventStream<?> stream);
}
