/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.events.aws;

import java.util.function.Supplier;
import software.amazon.smithy.java.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.OutputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.event.EventDecoder;
import software.amazon.smithy.java.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.core.serde.event.FrameDecoder;
import software.amazon.smithy.java.core.serde.event.FrameTransformer;

public class AwsEventDecoderFactory<E extends SerializableStruct> implements EventDecoderFactory<AwsEventFrame> {

    private final Schema schema;
    private final Codec codec;
    private final Supplier<ShapeBuilder<E>> eventBuilder;
    private final FrameTransformer<AwsEventFrame> transformer;

    private AwsEventDecoderFactory(
            Schema schema,
            Codec codec,
            Supplier<ShapeBuilder<E>> eventBuilder,
            FrameTransformer<AwsEventFrame> transformer
    ) {
        this.schema = schema.isMember() ? schema.memberTarget() : schema;
        this.codec = codec;
        this.eventBuilder = eventBuilder;
        this.transformer = transformer;
    }

    public static <IE extends SerializableStruct> AwsEventDecoderFactory<IE> forInputStream(
            InputEventStreamingApiOperation<?, ?, IE> operation,
            Codec codec,
            FrameTransformer<AwsEventFrame> transformer
    ) {
        return new AwsEventDecoderFactory<>(
                operation.inputStreamMember(),
                codec,
                operation.inputEventBuilderSupplier(),
                transformer);
    }

    public static <OE extends SerializableStruct> AwsEventDecoderFactory<OE> forOutputStream(
            OutputEventStreamingApiOperation<?, ?, OE> operation,
            Codec codec,
            FrameTransformer<AwsEventFrame> transformer
    ) {
        return new AwsEventDecoderFactory<>(
                operation.outputStreamMember(),
                codec,
                operation.outputEventBuilderSupplier(),
                transformer);
    }

    @Override
    public EventDecoder<AwsEventFrame> newEventDecoder() {
        return new AwsEventShapeDecoder<>(eventBuilder, schema, codec);
    }

    @Override
    public FrameDecoder<AwsEventFrame> newFrameDecoder() {
        return new AwsFrameDecoder(transformer);
    }
}
