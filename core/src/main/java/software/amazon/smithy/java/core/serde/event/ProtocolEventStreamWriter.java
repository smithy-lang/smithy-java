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
 * can safely cast the {@link EventStreamWriter} member to this class using {@link #of(EventStream)}
 * to access the internal methods for bootstrapping by providing the encoder and the initial event if needed.
 *
 * @param <T>  The event type
 * @param <IE> The initial event type
 *             // ProtocolEventStreamWriter
 */
public sealed interface ProtocolEventStreamWriter<T extends SerializableStruct, IE extends SerializableStruct,
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
     * @param encoderFactory the event encoder factory to serialize events and encode to frames.
     * @param initialEvent   the initial event, can be null if the protocol does not send the initial event in the stream
     */
    void bootstrap(EventEncoderFactory<F> encoderFactory, IE initialEvent);

    /**
     * Sets the signer to sign events in the stream
     *
     * @param eventSigner the signer to sign events in the stream
     */
    void setSigner(FrameProcessor<F> eventSigner);

    /**
     * Utility method to convert a {@link EventStreamWriter} to a {@link ProtocolEventStreamWriter}.
     *
     * @param <T>  the type of the event
     * @param <IE> the type of the internal event
     * @param <F>  the type of the frame
     * @return The writer converted to an internal writer.
     */
    @SuppressWarnings("unchecked")
    static <T extends SerializableStruct, IE extends SerializableStruct,
            F extends Frame<?>> ProtocolEventStreamWriter<T, IE, F> of(
                    EventStream<? extends SerializableStruct> writer
            ) {
        if (!(writer instanceof ProtocolEventStreamWriter)) {
            throw new IllegalArgumentException("writer must be an instance of ProtocolEventStreamWriter");
        }
        return (ProtocolEventStreamWriter<T, IE, F>) writer;
    }
}
