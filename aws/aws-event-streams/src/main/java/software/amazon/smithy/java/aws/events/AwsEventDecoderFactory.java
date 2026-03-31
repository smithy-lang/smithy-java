/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.util.Objects;
import java.util.function.Supplier;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.event.EventDecoder;
import software.amazon.smithy.java.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.core.serde.event.FrameDecoder;
import software.amazon.smithy.java.core.serde.event.FrameProcessor;

/**
 * A {@link EventDecoderFactory} for AWS events.
 *
 * @param <E>  The event shape type
 * @param <IR> The initial request shape type
 */
public final class AwsEventDecoderFactory<E extends SerializableStruct, IR extends SerializableStruct>
        implements EventDecoderFactory<AwsEventFrame> {

    private final InitialEventType initialEventType;
    private final Supplier<ShapeBuilder<IR>> initialEventBuilder;
    private final Schema eventSchema;
    private final Codec codec;
    private final Supplier<ShapeBuilder<E>> eventBuilder;
    private final FrameProcessor<AwsEventFrame> frameProcessor;

    private AwsEventDecoderFactory(
            InitialEventType initialEventType,
            Supplier<ShapeBuilder<IR>> initialEventBuilder,
            Schema eventSchema,
            Codec codec,
            Supplier<ShapeBuilder<E>> eventBuilder,
            FrameProcessor<AwsEventFrame> frameProcessor
    ) {
        this.initialEventType = Objects.requireNonNull(initialEventType, "initialEventType");
        this.initialEventBuilder = Objects.requireNonNull(initialEventBuilder, "initialEventBuilder");
        this.eventSchema = Objects.requireNonNull(eventSchema, "eventSchema").isMember() ? eventSchema.memberTarget()
                : eventSchema;
        this.codec = Objects.requireNonNull(codec, "codec");
        this.eventBuilder = Objects.requireNonNull(eventBuilder, "eventBuilder");
        this.frameProcessor = Objects.requireNonNull(frameProcessor, "transformer");
    }

    /**
     * Creates a new input stream decoder factory.
     *
     * @param operation   The input operation for the factory
     * @param codec       The protocol codec to decode the payload
     * @param frameProcessor The frame transformer
     * @param <IE>        The output event type
     * @return A new event decoder factory
     */
    @SuppressWarnings("unchecked")
    public static <IE extends SerializableStruct> AwsEventDecoderFactory<IE, ?> forInputStream(
            ApiOperation<?, ?> operation,
            Codec codec,
            FrameProcessor<AwsEventFrame> frameProcessor
    ) {
        return new AwsEventDecoderFactory<>(
                InitialEventType.INITIAL_REQUEST,
                operation::inputBuilder,
                operation.inputStreamMember(),
                codec,
                (Supplier<ShapeBuilder<IE>>) (Supplier<?>) operation.inputEventBuilderSupplier(),
                frameProcessor);
    }

    /**
     * Creates a new output stream decoder factory.
     *
     * @param operation   The output operation for the factory
     * @param codec       The protocol codec to decode the payload
     * @param frameProcessor The frame transformer
     * @param <OE>        The output event type
     * @return A new event decoder factory
     */
    @SuppressWarnings("unchecked")
    public static <OE extends SerializableStruct> AwsEventDecoderFactory<OE, ?> forOutputStream(
            ApiOperation<?, ?> operation,
            Codec codec,
            FrameProcessor<AwsEventFrame> frameProcessor
    ) {
        return new AwsEventDecoderFactory<>(
                InitialEventType.INITIAL_RESPONSE,
                operation::outputBuilder,
                operation.outputStreamMember(),
                codec,
                (Supplier<ShapeBuilder<OE>>) (Supplier<?>) operation.outputEventBuilderSupplier(),
                frameProcessor);
    }

    @Override
    public EventDecoder<AwsEventFrame> newEventDecoder() {
        return new AwsEventShapeDecoder<>(initialEventType, initialEventBuilder, eventBuilder, eventSchema, codec);
    }

    @Override
    public FrameDecoder<AwsEventFrame> newFrameDecoder() {
        return new AwsFrameDecoder(frameProcessor);
    }
}
