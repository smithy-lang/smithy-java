/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * This class is used by the protocol deserializers to bind the user facing {@link EventStreamReader}
 * to the downstream {@link DataStream} that reads the bytes over the wire. The deserializer is expected
 * to create an instance of it using {@link #newReader(DataStream, EventDecoderFactory, boolean)}  and
 * set it to the event stream member of the class being deserialized.
 *
 * @param <IE> The type of the initial event
 * @param <T>  the type of the event
 */
public sealed interface InternalEventStreamReader<IE extends SerializableStruct, T extends SerializableStruct>
        extends EventStreamReader<T> permits DefaultEventStreamReader {
    /**
     * Reads the initial response from the event stream.
     *
     * <p>Some protocols encode the initial event as the first event in the stream.
     * This method reads and decodes that first event as an initial response of type IR.
     *
     * @return the initial event
     */
    IE readInitialEvent();

    /**
     * Creates a new reader using the dataStream to read from, and the decoder factory to create
     * decoders from bytes to frames and from frames to user facing events.
     *
     * @param dataStream           the data stream to read from
     * @param eventDecoderFactory  the decoder factory to create the frame and event decoders.
     * @param initialEventExpected true if the protocol encodes the initial event in the event stream, false if the protocol does not.
     * @param <IE>                 The type of the initial event
     * @param <T>                  The type of the events
     * @param <F>                  The type of the frame
     * @return The new reader
     */
    static <IE extends SerializableStruct, T extends SerializableStruct, F extends Frame<?>> InternalEventStreamReader<
            IE, T> newReader(
                    DataStream dataStream,
                    EventDecoderFactory<F> eventDecoderFactory,
                    boolean initialEventExpected
            ) {
        return new DefaultEventStreamReader<>(dataStream, eventDecoderFactory, initialEventExpected);
    }
}
