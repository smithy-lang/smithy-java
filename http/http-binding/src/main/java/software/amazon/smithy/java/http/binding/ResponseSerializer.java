/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.OutputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.core.serde.event.EventStreamFrameEncodingProcessor;
import software.amazon.smithy.java.http.api.HttpResponse;

/**
 * Serializes HTTP responses.
 */
public final class ResponseSerializer {

    private Codec payloadCodec;
    private String payloadMediaType;
    private ApiOperation<?, ?> operation;
    private SerializableShape shapeValue;
    private EventEncoderFactory<?> eventEncoderFactory;
    private Schema errorSchema;
    private boolean omitEmptyPayload = false;
    private final ConcurrentMap<Schema, BindingMatcher> bindingCache;

    ResponseSerializer(ConcurrentMap<Schema, BindingMatcher> bindingCache) {
        this.bindingCache = bindingCache;
    }

    /**
     * Schema of the operation response to serialize.
     *
     * @param operation Operation schema.
     * @return Returns the serializer.
     */
    public ResponseSerializer operation(ApiOperation<?, ?> operation) {
        this.operation = operation;
        return this;
    }

    /**
     * Codec to use in the payload of the response.
     *
     * @param payloadCodec Payload codec.
     * @return Returns the serializer.
     */
    public ResponseSerializer payloadCodec(Codec payloadCodec) {
        this.payloadCodec = payloadCodec;
        return this;
    }

    /**
     * Set the required media typed used in payloads serialized by the provided codec.
     *
     * @param payloadMediaType Media type to use in the payload.
     * @return the serializer.
     */
    public ResponseSerializer payloadMediaType(String payloadMediaType) {
        this.payloadMediaType = payloadMediaType;
        return this;
    }

    /**
     * Set the value of the response shape.
     *
     * @param shapeValue Response shape value to serialize.
     * @return Returns the serializer.
     */
    public ResponseSerializer shapeValue(SerializableShape shapeValue) {
        this.shapeValue = shapeValue;
        return this;
    }

    /**
     * Enables event streaming support.
     *
     * @param encoderFactory the encoder factory for the protocol
     * @return Returns the serializer.
     */
    public ResponseSerializer eventEncoderFactory(
            EventEncoderFactory<?> encoderFactory
    ) {
        this.eventEncoderFactory = encoderFactory;
        return this;
    }

    /**
     * Set to true to not serialize any payload when no members are part of the body or bound to the payload.
     *
     * @param omitEmptyPayload True to omit an empty payload.
     * @return the serializer.
     */
    public ResponseSerializer omitEmptyPayload(boolean omitEmptyPayload) {
        this.omitEmptyPayload = omitEmptyPayload;
        return this;
    }

    /**
     * Used to serialize an error instead of the response.
     *
     * @param errorSchema Schema of the error to serialize.
     * @return the serializer.
     */
    public ResponseSerializer errorSchema(Schema errorSchema) {
        this.errorSchema = errorSchema;
        return this;
    }

    /**
     * Finishes setting up the serializer and creates an HTTP response.
     *
     * @return Returns the created response.
     */
    @SuppressWarnings("unchecked")
    public HttpResponse serializeResponse() {
        Objects.requireNonNull(shapeValue, "shapeValue is not set");
        Objects.requireNonNull(operation, "operation is not set");
        Objects.requireNonNull(payloadCodec, "payloadCodec is not set");
        Objects.requireNonNull(payloadMediaType, "payloadMediaType is not set");

        Schema schema;
        var isFailure = errorSchema != null;
        if (isFailure) {
            schema = errorSchema;
        } else {
            schema = operation.outputSchema();
        }

        var httpTrait = operation.schema().expectTrait(TraitKey.HTTP_TRAIT);
        var serializer = new HttpBindingSerializer(
                httpTrait,
                payloadCodec,
                payloadMediaType,
                bindingCache.computeIfAbsent(schema, BindingMatcher::responseMatcher),
                omitEmptyPayload,
                isFailure,
                false);
        shapeValue.serialize(serializer);
        serializer.flush();

        var builder = HttpResponse.builder()
                .statusCode(serializer.getResponseStatus());

        var eventStream = (Flow.Publisher<SerializableStruct>) serializer.getEventStream();
        if (eventStream != null && operation instanceof OutputEventStreamingApiOperation<?, ?, ?>) {
            builder.body(EventStreamFrameEncodingProcessor.create(eventStream, eventEncoderFactory));
            serializer.setContentType(eventEncoderFactory.contentType());
        } else if (serializer.hasBody()) {
            builder.body(serializer.getBody());
        }

        return builder.headers(serializer.getHeaders()).build();
    }
}
