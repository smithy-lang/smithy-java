/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.events.aws;

import java.util.function.Function;
import software.amazon.smithy.java.runtime.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.runtime.core.schema.OutputEventStreamingApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.event.EventEncoder;
import software.amazon.smithy.java.runtime.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.runtime.core.serde.event.EventStreamingException;
import software.amazon.smithy.java.runtime.core.serde.event.FrameEncoder;

public class AwsEventEncoderFactory implements EventEncoderFactory<AwsEventFrame> {

    private final Schema schema;
    private final Codec codec;
    private final String payloadMediaType;
    private final Function<Throwable, EventStreamingException> exceptionHandler;

    private AwsEventEncoderFactory(
        Schema schema,
        Codec codec,
        String payloadMediaType,
        Function<Throwable, EventStreamingException> exceptionHandler
    ) {
        this.schema = schema;
        this.codec = codec;
        this.payloadMediaType = payloadMediaType;
        this.exceptionHandler = exceptionHandler;
    }

    public static AwsEventEncoderFactory forInputStream(
        InputEventStreamingApiOperation<?, ?, ?> operation,
        Codec codec,
        String payloadMediaType,
        Function<Throwable, EventStreamingException> exceptionHandler
    ) {
        return new AwsEventEncoderFactory(operation.inputEventSchema(), codec, payloadMediaType, exceptionHandler);
    }

    public static AwsEventEncoderFactory forOutputStream(
        OutputEventStreamingApiOperation<?, ?, ?> operation,
        Codec codec,
        String payloadMediaType,
        Function<Throwable, EventStreamingException> exceptionHandler
    ) {
        return new AwsEventEncoderFactory(operation.outputEventSchema(), codec, payloadMediaType, exceptionHandler);
    }

    @Override
    public EventEncoder<AwsEventFrame> newEventEncoder() {
        return new AwsEventShapeEncoder(schema, codec, payloadMediaType, exceptionHandler);
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
