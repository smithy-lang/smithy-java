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

public final class EventStreamFrameEncodingProcessor<F extends Frame<?>, T extends SerializableStruct>
    extends BufferingFlatMapProcessor<T, ByteBuffer> {
    private final EventEncoder<F> eventEncoder;
    private final FrameEncoder<F> encoder;

    public EventStreamFrameEncodingProcessor(
        Flow.Publisher<T> publisher,
        EventEncoder<F> eventEncoder,
        FrameEncoder<F> encoder
    ) {
        super(publisher);
        this.eventEncoder = eventEncoder;
        this.encoder = encoder;
    }

    public static <F extends Frame<?>> EventStreamFrameEncodingProcessor<F, ?> create(
        Flow.Publisher<? extends SerializableStruct> publisher,
        EventEncoderFactory<F> encoderFactory
    ) {
        return new EventStreamFrameEncodingProcessor<>(
            publisher,
            encoderFactory.newEventEncoder(),
            encoderFactory.newFrameEncoder()
        );
    }

    @Override
    protected Stream<ByteBuffer> map(T item) {
        return Stream.of(encoder.encode(eventEncoder.encode(item)));
    }

    @Override
    protected void handleError(Throwable error, Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onNext(encoder.encode(eventEncoder.encodeFailure(error)));
        subscriber.onComplete();
    }
}
