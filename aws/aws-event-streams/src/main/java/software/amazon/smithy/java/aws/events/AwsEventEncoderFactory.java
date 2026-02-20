/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.util.Objects;
import java.util.function.Function;
import software.amazon.smithy.java.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.OutputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.event.EventEncoder;
import software.amazon.smithy.java.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.core.serde.event.EventStreamingException;
import software.amazon.smithy.java.core.serde.event.FrameEncoder;
import software.amazon.smithy.java.core.serde.event.FrameTransformer;

/**
 * A {@link EventEncoderFactory} for AWS events.
 */
public final class AwsEventEncoderFactory implements EventEncoderFactory<AwsEventFrame> {
    private final InitialEventType initialEventType;
    private final Schema schema;
    private final Codec codec;
    private final String payloadMediaType;
    private final FrameTransformer<AwsEventFrame> transformer;
    private final Function<Throwable, EventStreamingException> exceptionHandler;

    private AwsEventEncoderFactory(
            InitialEventType initialEventType,
            Schema schema,
            Codec codec,
            String payloadMediaType,
            FrameTransformer<AwsEventFrame> transformer,
            Function<Throwable, EventStreamingException> exceptionHandler
    ) {
        this.initialEventType = Objects.requireNonNull(initialEventType, "initialEventType");
        this.schema = Objects.requireNonNull(schema, "schema").isMember() ? schema.memberTarget() : schema;
        this.codec = Objects.requireNonNull(codec, "codec");
        this.payloadMediaType = Objects.requireNonNull(payloadMediaType, "payloadMediaType");
        this.transformer = Objects.requireNonNull(transformer, "transformer");
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
    }

    /**
     * Creates a new input stream encoder factory.
     *
     * @param operation        The input operation for the factory
     * @param codec            The protocol codec to decode the payload
     * @param payloadMediaType The payload media type
     * @param exceptionHandler The handler to convert exceptions for event streaming
     * @return A new event encoder factory
     */
    public static AwsEventEncoderFactory forInputStream(
            InputEventStreamingApiOperation<?, ?, ?> operation,
            Codec codec,
            String payloadMediaType,
            FrameTransformer<AwsEventFrame> transformer,
            Function<Throwable, EventStreamingException> exceptionHandler
    ) {
        return new AwsEventEncoderFactory(InitialEventType.INITIAL_REQUEST,
                operation.inputStreamMember(),
                codec,
                payloadMediaType,
                transformer,
                exceptionHandler);
    }

    /**
     * Creates a new output stream encoder factory.
     *
     * @param operation        The output operation for the factory
     * @param codec            The protocol codec to decode the payload
     * @param payloadMediaType The payload media type
     * @param exceptionHandler The handler to convert exceptions for event streaming
     * @return A new event encoder factory
     */
    public static AwsEventEncoderFactory forOutputStream(
            OutputEventStreamingApiOperation<?, ?, ?> operation,
            Codec codec,
            String payloadMediaType,
            FrameTransformer<AwsEventFrame> transformer,
            Function<Throwable, EventStreamingException> exceptionHandler
    ) {
        return new AwsEventEncoderFactory(InitialEventType.INITIAL_RESPONSE,
                operation.outputStreamMember(),
                codec,
                payloadMediaType,
                transformer,
                exceptionHandler);
    }

    @Override
    public EventEncoder<AwsEventFrame> newEventEncoder() {
        return new AwsEventShapeEncoder(initialEventType,
                schema,
                codec,
                payloadMediaType,
                transformer,
                exceptionHandler);
    }

    @Override
    public FrameEncoder<AwsEventFrame> newFrameEncoder() {
        return new AwsFrameEncoder();
    }

    @Override
    public String contentType() {
        return "application/vnd.amazon.eventstream";
    }
}
