/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.event;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.stream.Stream;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.BufferingFlatMapProcessor;

public final class EventStreamFrameDecodingProcessor<F extends Frame<?>>
    extends BufferingFlatMapProcessor<ByteBuffer, SerializableStruct> {
    private final FrameDecoder<F> decoder;
    private final EventDecoder<F> eventDecoder;

    public EventStreamFrameDecodingProcessor(
        Flow.Publisher<ByteBuffer> publisher,
        FrameDecoder<F> decoder,
        EventDecoder<F> eventDecoder
    ) {
        super(publisher);
        this.decoder = decoder;
        this.eventDecoder = eventDecoder;
    }

    public static <F extends Frame<?>> EventStreamFrameDecodingProcessor<F> create(
        Flow.Publisher<ByteBuffer> publisher,
        EventDecoderFactory<F> eventDecoderFactory
    ) {
        return new EventStreamFrameDecodingProcessor<>(
            publisher,
            eventDecoderFactory.newFrameDecoder(),
            eventDecoderFactory.newEventDecoder()
        );
    }

    @Override
    protected Stream<SerializableStruct> map(ByteBuffer item) {
        return decoder.decode(item).stream().map(eventDecoder::decode);
    }
}
