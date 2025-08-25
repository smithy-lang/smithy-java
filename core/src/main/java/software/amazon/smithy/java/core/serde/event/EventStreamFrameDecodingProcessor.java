/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.stream.Stream;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.BufferingFlatMapProcessor;

/**
 * Processor to pipe raw byte arrays to frame encoders and then to event encoders.
 *
 * @param <F> The frame type.
 */
public final class EventStreamFrameDecodingProcessor<F extends Frame<?>>
        extends BufferingFlatMapProcessor<ByteBuffer, SerializableStruct> {
    private final FrameDecoder<F> frameDecoder;
    private final EventDecoder<F> eventDecoder;

    EventStreamFrameDecodingProcessor(
            Flow.Publisher<ByteBuffer> publisher,
            FrameDecoder<F> frameDecoder,
            EventDecoder<F> eventDecoder
    ) {
        super(publisher);
        this.frameDecoder = frameDecoder;
        this.eventDecoder = eventDecoder;
    }

    /**
     * Creates a new processor with the given publisher and decoder factory. This method calls prepare to setup the
     * decoders prior to start processing events.
     *
     * @param publisher           The publisher generating the events
     * @param eventDecoderFactory The decoder factory to decode the raw bytes
     * @param <F>                 The type of the frame
     * @return A new processor
     */
    public static <F extends Frame<?>> EventStreamFrameDecodingProcessor<F> create(
            Flow.Publisher<ByteBuffer> publisher,
            EventDecoderFactory<F> eventDecoderFactory
    ) {
        var result = new EventStreamFrameDecodingProcessor<>(
                publisher,
                eventDecoderFactory.newFrameDecoder(),
                eventDecoderFactory.newEventDecoder());
        result.prepare();
        return result;
    }

    /**
     * Called once after building the frame processor to allow decoders to do any one-time setup prior to start
     * processing events.
     */
    void prepare() {
        frameDecoder.onPrepare(this);
        eventDecoder.onPrepare(this);
    }

    @Override
    protected Stream<SerializableStruct> map(ByteBuffer item) {
        return frameDecoder.decode(item).stream().map(eventDecoder::decode);
    }
}
