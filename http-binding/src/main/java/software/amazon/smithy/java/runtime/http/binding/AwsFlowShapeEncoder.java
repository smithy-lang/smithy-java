/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;
import software.amazon.smithy.java.runtime.core.schema.EventStreamingException;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.EventEncoder;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.ErrorTrait;

public final class AwsFlowShapeEncoder<T extends SerializableStruct> implements EventEncoder<T, AwsFlowFrame> {

    private final Schema eventSchema;
    private final Codec codec;
    private final Set<String> possibleTypes;
    private final Map<ShapeId, Schema> possibleExceptions;

    public AwsFlowShapeEncoder(Schema eventSchema, Codec codec) {
        this.eventSchema = eventSchema;
        this.codec = codec;
        this.possibleTypes = eventSchema.members().stream().map(Schema::memberName).collect(Collectors.toSet());
        this.possibleExceptions = eventSchema.members()
            .stream()
            .filter(s -> s.hasTrait(ErrorTrait.class))
            .map(Schema::memberTarget)
            .collect(Collectors.toMap(Schema::id, Function.identity()));
    }

    @Override
    public AwsFlowFrame encode(T item) {
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

        return new AwsFlowFrame(new Message(headers, os.toByteArray()));
    }

    @Override
    public AwsFlowFrame encodeFailure(Throwable exception) {

        AwsFlowFrame frame;
        if (exception instanceof ModeledApiException me && possibleExceptions.containsKey(me.getShapeId())) {
            var headers = new HashMap<String, HeaderValue>();
            headers.put(":message-type", HeaderValue.fromString("exception"));
            headers.put(
                ":exception-type",
                HeaderValue.fromString(possibleExceptions.get(me.getShapeId()).memberName())
            );
            var os = new ByteArrayOutputStream();
            try (var serializer = codec.createSerializer(os)) {
                me.serialize(serializer);
            }
            frame = new AwsFlowFrame(new Message(headers, os.toByteArray()));
        } else {
            EventStreamingException es;
            if (exception instanceof EventStreamingException e) {
                es = e;
            } else {
                es = new EventStreamingException("InternalServerError", "internal server error");
            }
            var headers = new HashMap<String, HeaderValue>();
            headers.put(":message-type", HeaderValue.fromString("error"));
            headers.put(":error-code", HeaderValue.fromString(es.getErrorCode()));
            headers.put(":error-message", HeaderValue.fromString(es.getMessage()));

            frame = new AwsFlowFrame(new Message(headers, new byte[0]));
        }
        return frame;

    }
}
