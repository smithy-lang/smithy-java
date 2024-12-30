/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.events.aws;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;
import software.amazon.smithy.java.core.schema.ModeledApiException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.event.EventEncoder;
import software.amazon.smithy.java.core.serde.event.EventStreamingException;
import software.amazon.smithy.model.shapes.ShapeId;

public final class AwsEventShapeEncoder implements EventEncoder<AwsEventFrame> {

    private final Schema eventSchema;
    private final Codec codec;
    private final String payloadMediaType;
    private final Set<String> possibleTypes;
    private final Map<ShapeId, Schema> possibleExceptions;
    private final Function<Throwable, EventStreamingException> exceptionHandler;

    public AwsEventShapeEncoder(
            Schema eventSchema,
            Codec codec,
            String payloadMediaType,
            Function<Throwable, EventStreamingException> exceptionHandler
    ) {
        this.eventSchema = eventSchema;
        this.codec = codec;
        this.payloadMediaType = payloadMediaType;
        this.possibleTypes = eventSchema.members().stream().map(Schema::memberName).collect(Collectors.toSet());
        this.possibleExceptions = eventSchema.members()
                .stream()
                .filter(s -> s.hasTrait(TraitKey.ERROR_TRAIT))
                .collect(Collectors.toMap(s -> s.memberTarget().id(), Function.identity()));
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public AwsEventFrame encode(SerializableStruct item) {
        var os = new ByteArrayOutputStream();
        var typeHolder = new AtomicReference<String>();
        try (var baseSerializer = codec.createSerializer(os)) {

            item.serializeMembers(new SpecificShapeSerializer() {
                @Override
                public void writeStruct(Schema schema, SerializableStruct struct) {
                    if (possibleTypes.contains(schema.memberName())) {
                        typeHolder.compareAndSet(null, schema.memberName());
                    }
                    baseSerializer.writeStruct(schema, struct);
                }
            });
        }

        var headers = new HashMap<String, HeaderValue>();
        headers.put(":event-type", HeaderValue.fromString(typeHolder.get()));
        headers.put(":message-type", HeaderValue.fromString("event"));
        headers.put(":content-type", HeaderValue.fromString(payloadMediaType));

        return new AwsEventFrame(new Message(headers, os.toByteArray()));
    }

    @Override
    public AwsEventFrame encodeFailure(Throwable exception) {
        AwsEventFrame frame;
        Schema exceptionSchema;
        if (exception instanceof ModeledApiException me && (exceptionSchema = possibleExceptions.get(
                me.schema().id())) != null) {
            var headers = new HashMap<String, HeaderValue>();
            headers.put(":message-type", HeaderValue.fromString("exception"));
            headers.put(
                    ":exception-type",
                    HeaderValue.fromString(exceptionSchema.memberName()));
            headers.put(":content-type", HeaderValue.fromString(payloadMediaType));
            var payload = codec.serialize(me);
            var bytes = new byte[payload.remaining()];
            payload.get(bytes);
            frame = new AwsEventFrame(new Message(headers, bytes));
        } else {
            EventStreamingException es = exceptionHandler.apply(exception);
            var headers = new HashMap<String, HeaderValue>();
            headers.put(":message-type", HeaderValue.fromString("error"));
            headers.put(":error-code", HeaderValue.fromString(es.getErrorCode()));
            headers.put(":error-message", HeaderValue.fromString(es.getMessage()));

            frame = new AwsEventFrame(new Message(headers, new byte[0]));
        }
        return frame;

    }
}
