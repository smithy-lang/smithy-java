/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Interface for writing events to an event stream.
 *
 * @param <T> The type of the event stream.
 */
public sealed interface EventStreamWriter<T extends SerializableStruct> extends AutoCloseable, EventStream<T>
        permits InternalEventStreamWriter {

    /**
     * Writes the given event to the event stream. This method will block until the write is possible.
     *
     * @param event The event to write.
     */
    void write(T event);
}
