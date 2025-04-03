/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.util.function.Supplier;
import software.amazon.eventstream.Message;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.event.EventDecoder;

public final class AwsEventShapeDecoder<E extends SerializableStruct> implements EventDecoder<AwsEventFrame> {

    private final Supplier<ShapeBuilder<E>> eventBuilder;
    private final Schema eventSchema;
    private final Codec codec;

    public AwsEventShapeDecoder(
            Supplier<ShapeBuilder<E>> eventBuilder,
            Schema eventSchema,
            Codec codec
    ) {
        this.eventBuilder = eventBuilder;
        this.eventSchema = eventSchema;
        this.codec = codec;
    }

    @Override
    public E decode(AwsEventFrame frame) {
        Message message = frame.unwrap();
        String messageType = getMessageType(message);
        if (!messageType.equals("event")) {
            throw new UnsupportedOperationException("Unsupported frame type: " + messageType);
        }
        String eventType = getEventType(message);
        Schema memberSchema = eventSchema.member(eventType);
        if (memberSchema == null) {
            throw new IllegalArgumentException("Unsupported event type: " + eventType);
        }

        return eventBuilder.get()
                .deserialize(
                        new AwsEventDeserializer(
                                memberSchema,
                                codec.createDeserializer(message.getPayload())))
                .build();
    }

    private String getEventType(Message message) {
        return message.getHeaders().get(":event-type").getString();
    }

    private String getMessageType(Message message) {
        return message.getHeaders().get(":message-type").getString();
    }
}
