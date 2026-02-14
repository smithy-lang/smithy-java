/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * An abstraction that represents an event stream. The event stream can be used for
 * reading or writing events depending on the use case.
 *
 * @param <T> The type of the event stream.
 */
public sealed interface EventStream<T extends SerializableStruct> extends AutoCloseable
        permits EventStreamReader, EventStreamWriter {
    /**
     * Returns this event stream as a reader.
     *
     * @return this event stream as a reader.
     * @throws IllegalStateException if the event stream is for writing.
     */
    EventStreamReader<T> asReader();

    /**
     * Returns this event stream as a writer.
     *
     * @return this event stream as a writer.
     * @throws IllegalStateException if the event stream is for reading.
     */
    EventStreamWriter<T> asWriter();

    /**
     * Closes the event stream.
     */
    @Override
    void close();

    /**
     * Closes the event stream with the given error.
     *
     * @param e the cause to close the event stream.
     */
    void closeWithError(Exception e);

    /**
     * Creates a new writer to be used by the client to write events. This writer must
     * be set to the event stream member of the structure and the user should keep a
     * reference to it that can be used to write the events. The returned writer
     * won't be fully setup until the containing structure is serialized by the protocol.
     * Until then, attempts to use the write method will be blocked until ready.
     *
     * @param <T> The type of the event.
     * @return the new event writer.
     */
    static <T extends SerializableStruct> EventStream<T> newWriter() {
        return new DefaultEventStreamWriter<>();
    }
}
