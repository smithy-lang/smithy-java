/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * This class is used by the protocol serializers to bind the user facing {@link EventStreamWriter}
 * to the downstream {@link DataStream} that sends the bytes over the wire. The serializer
 * can safely cast the {@link EventStreamWriter} member to this class using {@link #toInternal(EventStream)}
 * to access the internal methods for bootstrapping by providing the encoder and the initial event if needed.
 *
 * @param <T>  The event type
 * @param <IE> The initial event type
 */
public sealed interface InternalEventStreamWriter<T extends SerializableStruct, IE extends SerializableStruct,
        F extends Frame<?>> extends EventStreamWriter<T> permits DefaultEventStreamWriter {
    /**
     * Converts to the writer to a DataStream. This method will be called
     * by the serializer to send the stream of bytes representing the
     * events on the wire.
     *
     * @return The data stream from the writer.
     */
    DataStream toDataStream();

    /**
     * Bootstraps the writer providing the encoding factory to convert events
     * to frames and frames to bytes, and the initial event that must be sent
     * as the first event of the stream if the protocol requires one.
     *
     * <p>This method will be called using a proper data by the protocol serializer.
     * Writes will be blocked until this method is called.
     *
     * @param bootstrap The sink to write events to
     */
    void bootstrap(Bootstrap<IE, F> bootstrap);

    /**
     * Contains the protocol dependant encoder factory and the initial event needed to bootstrap the writer.
     * Protocols that do not require an initial event to be sent as part of the stream must return null
     * in the {@link #initialEvent()} method.
     *
     * @param <IE> the initial event type
     * @param <F>  the frame type
     */
    interface Bootstrap<IE extends SerializableStruct, F extends Frame<?>> {
        /**
         * Returns the event encoder factory to serialize events and encode to frames.
         *
         * @return the event encoder factory to serialize events and encode to frames.
         */
        EventEncoderFactory<F> encoder();

        /**
         * Returns the initial event of the event stream. This method must return
         * {@code null} if the implementing protocol does not require the initial
         * event to be encoded as an event.
         *
         * @return the initial event of the event stream.
         */
        IE initialEvent();
    }

    /**
     * Utility method to convert a {@link EventStreamWriter} to a {@link InternalEventStreamWriter}.
     *
     * @param <T>  the type of the event
     * @param <IE> the type of the internal event
     * @param <F>  the type of the frame
     * @return The writer converted to an internal writer.
     */
    @SuppressWarnings("unchecked")
    static <T extends SerializableStruct, IE extends SerializableStruct,
            F extends Frame<?>> InternalEventStreamWriter<T, IE, F> toInternal(
                    EventStream<? extends SerializableStruct> writer
            ) {
        if (!(writer instanceof InternalEventStreamWriter)) {
            throw new IllegalArgumentException("writer must be an instance of InternalEventStreamWriter");
        }
        return (InternalEventStreamWriter<T, IE, F>) writer;
    }
}
