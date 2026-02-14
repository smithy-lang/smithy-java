/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.SpecificShapeDeserializer;
import software.amazon.smithy.java.core.serde.event.EventDecoder;
import software.amazon.smithy.java.core.serde.event.EventStream;

/**
 * A decoder for AWS events
 *
 * @param <E>  The type of the event
 * @param <IR> The type of the initial event
 */
public final class AwsEventShapeDecoder<E extends SerializableStruct, IR extends SerializableStruct>
        implements EventDecoder<AwsEventFrame> {

    private final InitialEventType initialEventType;
    private final Supplier<ShapeBuilder<IR>> initialEventBuilder;
    private final Supplier<ShapeBuilder<E>> eventBuilder;
    private final Schema eventSchema;
    private final Codec codec;

    AwsEventShapeDecoder(
            InitialEventType initialEventType,
            Supplier<ShapeBuilder<IR>> initialEventBuilder,
            Supplier<ShapeBuilder<E>> eventBuilder,
            Schema eventSchema,
            Codec codec
    ) {
        this.initialEventType = Objects.requireNonNull(initialEventType, "initialEventType");
        this.initialEventBuilder = Objects.requireNonNull(initialEventBuilder, "initialEventBuilder");
        this.eventBuilder = Objects.requireNonNull(eventBuilder, "eventBuilder");
        this.eventSchema = Objects.requireNonNull(eventSchema, "eventSchema");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public SerializableStruct decode(AwsEventFrame frame) {
        return decodeEvent(frame);
    }

    private E decodeEvent(AwsEventFrame frame) {
        var message = frame.unwrap();
        var eventType = getEventType(message);
        var memberSchema = eventSchema.member(eventType);
        if (memberSchema == null) {
            throw new IllegalArgumentException("Unsupported event type: " + eventType);
        }
        var codecDeserializer = codec.createDeserializer(message.getPayload());
        var headers = message.getHeaders();
        var deserializer = new EventStreamDeserializer(codecDeserializer, new HeadersDeserializer(headers));
        var memberTarget = memberSchema.memberTarget();
        var shapeBuilder = memberTarget.shapeBuilder();
        shapeBuilder.deserialize(deserializer);
        var builder = eventBuilder.get();
        builder.setMemberValue(memberSchema, shapeBuilder.build());
        return builder.build();
    }

    @Override
    public IR decodeInitialEvent(AwsEventFrame frame, EventStream<?> eventStream) {
        var message = frame.unwrap();
        var builder = initialEventBuilder.get();
        var publisherMember = getEventStreamMember(builder.schema());
        // Set the publisher member
        var responseDeserializer = new InitialResponseDeserializer(publisherMember, eventStream);
        builder.deserialize(responseDeserializer);
        // Deserialize the rest of the members if any
        var headers = message.getHeaders();
        var codecDeserializer = codec.createDeserializer(message.getPayload());
        var deserializer = new EventStreamDeserializer(codecDeserializer, new HeadersDeserializer(headers));
        builder.deserialize(deserializer);
        return builder.build();
    }

    private Schema getEventStreamMember(Schema schema) {
        for (var member : schema.members()) {
            if (member.memberTarget().hasTrait(TraitKey.STREAMING_TRAIT)) {
                return member;
            }
        }
        throw new IllegalArgumentException("cannot find streaming member");
    }

    private String getEventType(Message message) {
        return message.getHeaders().get(":event-type").getString();
    }

    static class InitialResponseDeserializer extends SpecificShapeDeserializer {
        private final Schema publisherMember;
        private final EventStream<?> eventStream;

        InitialResponseDeserializer(Schema publisherMember, EventStream<?> eventStream) {
            this.publisherMember = publisherMember;
            this.eventStream = eventStream;
        }

        @Override
        public EventStream<? extends SerializableStruct> readEventStream(Schema schema) {
            return eventStream;
        }

        @Override
        public <T> void readStruct(Schema schema, T state, ShapeDeserializer.StructMemberConsumer<T> consumer) {
            consumer.accept(state, publisherMember, this);
        }
    }

    static class EventStreamDeserializer extends SpecificShapeDeserializer {
        private final ShapeDeserializer codecDeserializer;
        private final HeadersDeserializer headersDeserializer;

        EventStreamDeserializer(ShapeDeserializer codecDeserializer, HeadersDeserializer headersDeserializer) {
            this.codecDeserializer = codecDeserializer;
            this.headersDeserializer = headersDeserializer;
        }

        @Override
        public <T> void readStruct(Schema schema, T builder, ShapeDeserializer.StructMemberConsumer<T> consumer) {
            var payloadWritten = false;
            for (Schema member : schema.members()) {
                if (member.hasTrait(TraitKey.EVENT_HEADER_TRAIT)) {
                    consumer.accept(builder, member, headersDeserializer);
                } else if (member.hasTrait(TraitKey.EVENT_PAYLOAD_TRAIT)) {
                    consumer.accept(builder, member, codecDeserializer);
                    payloadWritten = true;
                }
            }
            // Deserialize from the payload if still needed.
            if (!payloadWritten) {
                codecDeserializer.readStruct(schema, builder, consumer);
            }
        }
    }

    static class HeadersDeserializer extends SpecificShapeDeserializer {
        private final Map<String, HeaderValue> headers;

        HeadersDeserializer(Map<String, HeaderValue> headers) {
            this.headers = headers;
        }

        @Override
        public ByteBuffer readBlob(Schema schema) {
            return getValueForShapeType(schema);
        }

        @Override

        public byte readByte(Schema schema) {
            return getValueForShapeType(schema);
        }

        @Override
        public short readShort(Schema schema) {
            return getValueForShapeType(schema);
        }

        @Override
        public int readInteger(Schema schema) {
            return getValueForShapeType(schema);
        }

        @Override
        public long readLong(Schema schema) {
            return getValueForShapeType(schema);
        }

        @Override
        public String readString(Schema schema) {
            return getValueForShapeType(schema);
        }

        @Override
        public boolean readBoolean(Schema schema) {
            return getValueForShapeType(schema);
        }

        @Override
        public Instant readTimestamp(Schema schema) {
            return getValueForShapeType(schema);
        }

        @SuppressWarnings("unchecked")
        private <T> T getValueForShapeType(Schema member) {
            HeaderValue value = headers.get(member.memberName());
            if (value == null) {
                return null;
            }
            return (T) switch (member.type()) {
                case BLOB -> value.getByteBuffer();
                case BOOLEAN -> value.getBoolean();
                case BYTE -> value.getByte();
                case SHORT -> value.getShort();
                case INTEGER, INT_ENUM -> value.getInteger();
                case LONG -> value.getLong();
                case TIMESTAMP -> value.getTimestamp();
                case STRING -> value.getString();
                default -> throw new IllegalArgumentException("Unsupported shape type: " + member.type());
            };
        }
    }
}
