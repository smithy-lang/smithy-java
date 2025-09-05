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
import software.amazon.smithy.java.logging.InternalLogger;

public final class EventStreamFrameEncodingProcessor<F extends Frame<?>, T extends SerializableStruct>
        extends BufferingFlatMapProcessor<T, ByteBuffer> {
    private static final InternalLogger LOG = InternalLogger.getLogger(EventStreamFrameEncodingProcessor.class);
    private final EventEncoder<F> eventEncoder;
    private final FrameEncoder<F> encoder;

    private EventStreamFrameEncodingProcessor(
            Flow.Publisher<T> publisher,
            EventEncoder<F> eventEncoder,
            FrameEncoder<F> encoder
    ) {
        super(publisher);
        this.eventEncoder = eventEncoder;
        this.encoder = encoder;
    }

    public static <F extends Frame<?>> EventStreamFrameEncodingProcessor<F, SerializableStruct> create(
            Flow.Publisher<SerializableStruct> publisher,
            EventEncoderFactory<F> encoderFactory
    ) {
        var processor = new EventStreamFrameEncodingProcessor<>(
                publisher,
                encoderFactory.newEventEncoder(),
                encoderFactory.newFrameEncoder());
        return processor;
    }

    public static <F extends Frame<?>> EventStreamFrameEncodingProcessor<F, SerializableStruct> create(
            Flow.Publisher<SerializableStruct> publisher,
            EventEncoderFactory<F> encoderFactory,
            SerializableStruct firstItem
    ) {
        var processor = new EventStreamFrameEncodingProcessor<>(
                publisher,
                encoderFactory.newEventEncoder(),
                encoderFactory.newFrameEncoder());
        processor.enqueueItem(firstItem);
        return processor;
    }

    @Override
    protected Stream<ByteBuffer> map(T item) {
        return Stream.of(encoder.encode(eventEncoder.encode(item)));
    }

    @Override
    protected void handleError(Throwable error, Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onNext(encoder.encode(eventEncoder.encodeFailure(error)));
        LOG.warn("Unexpected error", error);
        subscriber.onComplete();
    }
}
