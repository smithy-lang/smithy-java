/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import software.amazon.eventstream.Message;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.event.EventEncoder;
import software.amazon.smithy.java.core.serde.event.EventStreamingException;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * A encoder for AWS events.
 */
final class AwsEventShapeEncoder implements EventEncoder<AwsEventFrame> {

    private final InitialEventType initialEventType;
    private final Codec codec;
    private final String payloadMediaType;
    private final boolean emitEmptyPayload;
    private final Set<String> possibleTypes;
    private final Map<ShapeId, Schema> possibleExceptions;
    private final Function<Throwable, EventStreamingException> exceptionHandler;

    public AwsEventShapeEncoder(
            InitialEventType initialEventType,
            Schema eventSchema,
            Codec codec,
            String payloadMediaType,
            boolean emitEmptyPayload,
            Function<Throwable, EventStreamingException> exceptionHandler
    ) {
        this.initialEventType = Objects.requireNonNull(initialEventType, "initialEventType");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.payloadMediaType = Objects.requireNonNull(payloadMediaType, "payloadMediaType");
        this.emitEmptyPayload = emitEmptyPayload;
        this.possibleTypes = possibleTypes(Objects.requireNonNull(eventSchema, "eventSchema"),
                initialEventType.value());
        this.possibleExceptions = possibleExceptions(Objects.requireNonNull(eventSchema, "eventSchema"));
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
    }

    @Override
    public AwsEventFrame encode(SerializableStruct item) {
        var typeHolder = new Holder<String>();
        var contentTypeHolder = new Holder<>(payloadMediaType);
        var headers = HeadersBuilder.forEvent();
        var payload = encodeInput(item, headers, typeHolder, contentTypeHolder);
        headers.eventType(typeHolder.get());
        var contentType = contentTypeHolder.get();
        if (contentType != null) {
            headers.contentType(contentTypeHolder.get());
        }
        return new AwsEventFrame(new Message(headers.build(), payload));
    }

    @Override
    public AwsEventFrame encodeFailure(Throwable exception) {
        Schema exceptionSchema;
        if (exception instanceof ModeledException me
                && (exceptionSchema = possibleExceptions.get(me.schema().id())) != null) {
            var headers = HeadersBuilder.forException()
                    .exceptionType(exceptionSchema.memberName());
            var contentTypeHolder = new Holder<>(payloadMediaType);
            var payload = encodeModeledException(me, headers, contentTypeHolder);
            var contentType = contentTypeHolder.get();
            if (contentType != null) {
                headers.contentType(contentTypeHolder.get());
            }
            return new AwsEventFrame(new Message(headers.build(), payload));
        }
        var es = exceptionHandler.apply(exception);
        var headers = HeadersBuilder.forError()
                .errorCode(es.getErrorCode())
                .errorMessage(es.getMessage());
        return new AwsEventFrame(new Message(headers.build(), new byte[0]));
    }

    /**
     * Encodes the item event returning the bytes for the payload. The headers builder is also updated
     * when a member is serialized in the headers.
     *
     * <p>
     * The serialization captures the union member and sets its value in the `typeHolder` reference. This
     * value is set in the `:event-type` header of the event.
     *
     * <p>
     * The content type can change depending on the use of `@eventPayload` for blobs and string members. The
     * contentEncodingHolder is updated to reflect this value. If nothing gets serialized to the payload,
     * this reference is updated to `null` to signal the caller that the content-type should not be set.
     */
    private byte[] encodeInput(
            SerializableStruct item,
            HeadersBuilder headers,
            Holder<String> typeHolder,
            Holder<String> contentEncodingHolder
    ) {
        var out = new ByteArrayOutputStream();
        var baseSerializer = createSerializer(out, codec, headers, emitEmptyPayload, contentEncodingHolder);
        var ext = EventStreamSchemaExtensions.extensionOf(item.schema());
        if (ext.isInitialEvent()) {
            // The initial event is serialized fully instead of just a single member as for events.
            typeHolder.set(initialEventType.value());
            new EventStreamSchemaExtensions.BindingExcludingStruct(
                    item,
                    ext.bindings(),
                    EventStreamSchemaExtensions.Binding.STREAMING).serialize(baseSerializer);
        } else {
            var serializer = new SpecificShapeSerializer() {
                @Override
                public void writeStruct(Schema schema, SerializableStruct struct) {
                    var memberName = schema.memberName();
                    if (possibleTypes.contains(memberName) &&
                            typeHolder.setIfAbsent(memberName)) {
                        baseSerializer.writeStruct(schema, struct);
                    }
                }
            };
            item.serializeMembers(serializer);
        }
        return out.toByteArray();
    }

    /**
     * Encodes the item event returning the bytes for the payload. The headers builder is also updated
     * when a member is serialized in the headers.
     */
    private byte[] encodeModeledException(
            ModeledException item,
            HeadersBuilder headers,
            Holder<String> contentEncodingHolder
    ) {
        var out = new ByteArrayOutputStream();
        var baseSerializer = createSerializer(out, codec, headers, emitEmptyPayload, contentEncodingHolder);
        item.serialize(baseSerializer);
        return out.toByteArray();
    }

    /**
     * Returns the set of all the possible event types.
     */
    static Set<String> possibleTypes(Schema eventSchema, String initialEventType) {
        var result = new HashSet<String>();
        for (var memberSchema : eventSchema.members()) {
            var memberName = memberSchema.memberName();
            result.add(memberName);
        }
        result.add(initialEventType);
        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns the mapping between shape id and schema for all possible modeled exceptions for this stream.
     */
    static Map<ShapeId, Schema> possibleExceptions(Schema eventSchema) {
        var result = new HashMap<ShapeId, Schema>();
        for (var memberSchema : eventSchema.members()) {
            if (memberSchema.hasTrait(TraitKey.ERROR_TRAIT)) {
                var targetId = memberSchema.memberTarget().id();
                if (result.put(targetId, memberSchema) != null) {
                    throw new IllegalStateException("Duplicate key: " + targetId);
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    static ShapeSerializer createSerializer(
            OutputStream out,
            Codec codec,
            HeadersBuilder headers,
            boolean emitEmptyPayload,
            Holder<String> contentTypeHolder
    ) {
        var eventSerializer = new EventHeaderSerializer(headers);
        return new EventSerializer(out, codec, eventSerializer, emitEmptyPayload, contentTypeHolder);
    }

    /**
     * Serializes an event, class implements the logic to serialize into the headers,
     * or the payload of the message depending on the presence of the event serialization
     * traits.
     */
    static class EventSerializer extends SpecificShapeSerializer {
        private final OutputStream out;
        private final Codec codec;
        private final EventHeaderSerializer headerSerializer;
        private final boolean emitEmptyPayload;
        private final Holder<String> contentTypeHolder;

        public EventSerializer(
                OutputStream out,
                Codec codec,
                EventHeaderSerializer headerSerializer,
                boolean emitEmptyPayload,
                Holder<String> contentTypeHolder
        ) {
            this.out = out;
            this.codec = codec;
            this.headerSerializer = headerSerializer;
            this.emitEmptyPayload = emitEmptyPayload;
            this.contentTypeHolder = contentTypeHolder;
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            var ext = EventStreamSchemaExtensions.extensionOf(schema);
            var bindings = ext.bindings();
            if (ext.hasEventPayload()) {
                try (var serializer = new EventPayloadSerializer(out, codec, contentTypeHolder)) {
                    new EventStreamSchemaExtensions.BindingFilteredStruct(
                            struct,
                            bindings,
                            EventStreamSchemaExtensions.Binding.EVENT_PAYLOAD).serializeMembers(serializer);
                }
            } else if (emitEmptyPayload || ext.hasPayloadMembers()) {
                // Serialize the body via codec.serialize() rather than createSerializer(out). For codecs
                // with a pooled serialize() (Smithy JSON, CBOR) this reuses a pooled serializer and buffer
                // across frames.
                var body = codec.serialize(new EventStreamSchemaExtensions.BindingFilteredStruct(
                        struct,
                        bindings,
                        EventStreamSchemaExtensions.Binding.PAYLOAD));
                try {
                    out.write(ByteBufferUtils.getBytes(body));
                } catch (IOException e) {
                    throw new SerializationException(e);
                }
            } else {
                contentTypeHolder.set(null);
            }
            if (ext.hasHeaderMembers()) {
                new EventStreamSchemaExtensions.BindingFilteredStruct(
                        struct,
                        bindings,
                        EventStreamSchemaExtensions.Binding.HEADER).serialize(headerSerializer);
            }
        }
    }

    /**
     * Serializes a member with the trait `@eventPayload` directly into the payload of the event.
     * The following member types have special handling:
     * <ul>
     *     <li><b>blob</b> For blob members the non-encoded bytes are the payload of the event and the
     *     event `:content-type` header is set to "application/octet-stream"</li>
     *     <li><b>string</b> For string members the UTF-8 encoded bytes are the payload of the event and the
     *     event `:content-type` header is set to "text/plain"</li>
     *     <li>Any other is encoded with the underlying protocol and uses the content type defined for it</li>
     * </ul>
     */
    static class EventPayloadSerializer extends InterceptingSerializer {
        private final OutputStream out;
        private final Codec codec;
        private final Holder<String> contentTypeHolder;
        private ShapeSerializer codecSerializer;

        EventPayloadSerializer(OutputStream out, Codec codec, Holder<String> contentTypeHolder) {
            this.out = out;
            this.codec = codec;
            this.contentTypeHolder = contentTypeHolder;
        }

        @Override
        protected ShapeSerializer before(Schema schema) {
            return switch (schema.type()) {
                case BLOB -> new SpecificShapeSerializer() {
                    @Override
                    public void writeBlob(Schema schema, ByteBuffer value) {
                        contentTypeHolder.set("application/octet-stream");
                        try {
                            out.write(value.array(), value.arrayOffset() + value.position(), value.remaining());
                        } catch (IOException e) {
                            throw new SerializationException(e);
                        }
                    }
                };
                case STRING -> new SpecificShapeSerializer() {
                    @Override
                    public void writeString(Schema schema, String value) {
                        contentTypeHolder.set("text/plain");
                        try {
                            out.write(value.getBytes(StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            throw new SerializationException(e);
                        }
                    }
                };
                default -> (codecSerializer = codec.createSerializer(out));
            };
        }

        @Override
        public void flush() {
            if (codecSerializer != null) {
                codecSerializer.flush();
            }
        }
    }
}
