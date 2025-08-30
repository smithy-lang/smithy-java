/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.event.EventEncoder;
import software.amazon.smithy.java.core.serde.event.EventStreamingException;
import software.amazon.smithy.model.shapes.ShapeId;

public final class AwsEventShapeEncoder implements EventEncoder<AwsEventFrame> {

    private final InitialEventType initialEventType;
    private final Codec codec;
    private final String payloadMediaType;
    private final Map<String, BiFunction<OutputStream, Map<String, HeaderValue>, ShapeSerializer>> possibleTypes;
    private final Map<ShapeId, Schema> possibleExceptions;
    private final Function<Throwable, EventStreamingException> exceptionHandler;

    public AwsEventShapeEncoder(
            InitialEventType initialEventType,
            Schema eventSchema,
            Codec codec,
            String payloadMediaType,
            Function<Throwable, EventStreamingException> exceptionHandler
    ) {
        this.initialEventType = Objects.requireNonNull(initialEventType, "initialEventType");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.payloadMediaType = Objects.requireNonNull(payloadMediaType, "payloadMediaType");
        this.possibleTypes = possibleTypes(Objects.requireNonNull(eventSchema, "eventSchema"),
                codec,
                initialEventType.value());
        this.possibleExceptions = possibleExceptions(Objects.requireNonNull(eventSchema, "eventSchema"));
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
    }

    @Override
    public AwsEventFrame encode(SerializableStruct item) {
        var typeHolder = new AtomicReference<String>();
        var headers = new HashMap<String, HeaderValue>();
        var payload = encodeInput(item, typeHolder, headers);
        headers.put(":message-type", HeaderValue.fromString("event"));
        headers.put(":event-type", HeaderValue.fromString(typeHolder.get()));
        headers.put(":content-type", HeaderValue.fromString(payloadMediaType));
        return new AwsEventFrame(new Message(headers, payload));
    }

    private byte[] encodeInput(
            SerializableStruct item,
            AtomicReference<String> typeHolder,
            Map<String, HeaderValue> headers
    ) {
        if (isInitialRequest(item.schema())) {
            // The initial event is serialized fully instead of just a single member as for events.
            typeHolder.set(initialEventType.value());
            var os = new ByteArrayOutputStream();
            try (var baseSerializer = possibleTypes.get(initialEventType.value()).apply(os, headers)) {
                SchemaUtils.withFilteredMembers(item.schema(), item, AwsEventShapeEncoder::excludeEventStreamMember)
                        .serialize(baseSerializer);
            }
            return os.toByteArray();
        }
        var os = new ByteArrayOutputStream();
        var serializer = new SpecificShapeSerializer() {
            @Override
            public void writeStruct(Schema schema, SerializableStruct struct) {
                var memberName = schema.memberName();
                if (possibleTypes.containsKey(memberName) &&
                        typeHolder.compareAndSet(null, schema.memberName())) {
                    try (var baseSerializer = possibleTypes.get(memberName).apply(os, headers)) {
                        baseSerializer.writeStruct(schema, struct);
                    }
                }
            }
        };
        item.serializeMembers(serializer);
        return os.toByteArray();
    }

    private boolean isInitialRequest(Schema schema) {
        for (var member : schema.members()) {
            if (isEventStreamMember(member)) {
                return true;
            }
        }
        return false;
    }

    private static boolean excludeEventStreamMember(Schema schema) {
        return !isEventStreamMember(schema);
    }

    private static boolean isEventStreamMember(Schema schema) {
        if (schema.isMember() && schema.memberTarget().hasTrait(TraitKey.STREAMING_TRAIT)) {
            return true;
        }
        return false;
    }

    @Override
    public AwsEventFrame encodeFailure(Throwable exception) {
        AwsEventFrame frame;
        Schema exceptionSchema;
        if (exception instanceof ModeledException me
                && (exceptionSchema = possibleExceptions.get(me.schema().id())) != null) {
            var headers = Map.of(
                    ":message-type",
                    HeaderValue.fromString("exception"),
                    ":exception-type",
                    HeaderValue.fromString(exceptionSchema.memberName()),
                    ":content-type",
                    HeaderValue.fromString(payloadMediaType));
            var payload = codec.serialize(me);
            var bytes = new byte[payload.remaining()];
            payload.get(bytes);
            frame = new AwsEventFrame(new Message(headers, bytes));
        } else {
            EventStreamingException es = exceptionHandler.apply(exception);
            var headers = Map.of(
                    ":message-type",
                    HeaderValue.fromString("error"),
                    ":error-code",
                    HeaderValue.fromString(es.getErrorCode()),
                    ":error-message",
                    HeaderValue.fromString(es.getMessage()));
            frame = new AwsEventFrame(new Message(headers, new byte[0]));
        }
        return frame;

    }

    static Map<String, BiFunction<OutputStream, Map<String, HeaderValue>, ShapeSerializer>> possibleTypes(
            Schema eventSchema,
            Codec codec,
            String initialEventType
    ) {
        var result = new HashMap<String, BiFunction<OutputStream, Map<String, HeaderValue>, ShapeSerializer>>();
        var factory = new EventShapeSerializerFactory(codec);
        for (var memberSchema : eventSchema.members()) {
            String memberName = memberSchema.memberName();
            result.put(memberName, factory::createSerializer);
        }
        result.put(initialEventType, factory::createSerializer);
        return Collections.unmodifiableMap(result);
    }

    static Map<ShapeId, Schema> possibleExceptions(Schema eventSchema) {
        var result = new HashMap<ShapeId, Schema>();
        for (var memberSchema : eventSchema.members()) {
            if (memberSchema.hasTrait(TraitKey.ERROR_TRAIT)) {
                if (result.put(memberSchema.memberTarget().id(), memberSchema) != null) {
                    throw new IllegalStateException("Duplicate key");
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    static class EventShapeSerializerFactory {
        private final Codec codec;

        EventShapeSerializerFactory(Codec codec) {
            this.codec = codec;
        }

        public ShapeSerializer createSerializer(OutputStream out, Map<String, HeaderValue> headers) {
            var eventSerializer = new EventHeaderSerializer(headers);
            var baseSerializer = codec.createSerializer(out);
            return new EventSerializer(eventSerializer, baseSerializer);
        }
    }

    static class EventSerializer extends SpecificShapeSerializer {
        private final EventHeaderSerializer headerSerializer;
        private final ShapeSerializer baseSerializer;

        public EventSerializer(EventHeaderSerializer headerSerializer, ShapeSerializer baseSerializer) {
            this.headerSerializer = headerSerializer;
            this.baseSerializer = baseSerializer;
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            if (hasEventPayloadMember(schema)) {
                SchemaUtils.withFilteredMembers(schema, struct, this::isEventPayload)
                        .serializeMembers(baseSerializer);

            } else {
                SchemaUtils.withFilteredMembers(schema, struct, this::isPayloadMember)
                        .serialize(baseSerializer);
            }
            SchemaUtils.withFilteredMembers(schema, struct, this::isHeadersMember)
                    .serialize(headerSerializer);
        }

        private boolean isPayloadMember(Schema schema) {
            if (schema.hasTrait(TraitKey.EVENT_HEADER_TRAIT)) {
                return false;
            }
            if (isEventStreamMember(schema)) {
                return false;
            }
            return true;
        }

        private boolean isEventPayload(Schema schema) {
            if (schema.hasTrait(TraitKey.EVENT_PAYLOAD_TRAIT)) {
                return true;
            }
            return false;
        }

        private boolean hasEventPayloadMember(Schema schema) {
            for (var member : schema.members()) {
                if (member.hasTrait(TraitKey.EVENT_PAYLOAD_TRAIT)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isHeadersMember(Schema schema) {
            return schema.hasTrait(TraitKey.EVENT_HEADER_TRAIT);
        }

        @Override
        public void flush() {
            baseSerializer.flush();
        }
    }
}
