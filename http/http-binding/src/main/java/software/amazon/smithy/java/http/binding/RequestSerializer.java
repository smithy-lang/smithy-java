/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.core.serde.event.Frame;
import software.amazon.smithy.java.core.serde.event.InternalEventStreamWriter;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.uri.URIBuilder;

/**
 * Serializes an HTTP request from an input shape that uses HTTP binding traits.
 */
public final class RequestSerializer {

    private Codec payloadCodec;
    private ApiOperation<?, ?> operation;
    private String payloadMediaType;
    private URI endpoint;
    private SerializableShape shapeValue;
    private EventEncoderFactory<?> eventStreamEncodingFactory;
    private boolean omitEmptyPayload = false;
    private boolean allowEmptyStructPayload = false;
    private final ConcurrentMap<Schema, BindingMatcher> bindingCache;

    RequestSerializer(ConcurrentMap<Schema, BindingMatcher> bindingCache) {
        this.bindingCache = bindingCache;
    }

    /**
     * Schema of the operation to serialize.
     *
     * @param operation the operation
     * @return Returns the serializer.
     */
    public RequestSerializer operation(ApiOperation<?, ?> operation) {
        this.operation = operation;
        return this;
    }

    /**
     * Codec to use in the payload of requests.
     *
     * @param payloadCodec Payload codec.
     * @return Returns the serializer.
     */
    public RequestSerializer payloadCodec(Codec payloadCodec) {
        this.payloadCodec = payloadCodec;
        return this;
    }

    /**
     * Set the required media typed used in payloads serialized by the provided codec.
     *
     * @param payloadMediaType Media type to use in the payload.
     * @return the serializer.
     */
    public RequestSerializer payloadMediaType(String payloadMediaType) {
        this.payloadMediaType = payloadMediaType;
        return this;
    }

    /**
     * Set the endpoint of the request.
     *
     * @param endpoint Request endpoint.
     * @return Returns the serializer.
     */
    public RequestSerializer endpoint(URI endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    /**
     * Set the value of the request shape.
     *
     * @param shapeValue Request shape value to serialize.
     * @return Returns the serializer.
     */
    public RequestSerializer shapeValue(SerializableShape shapeValue) {
        this.shapeValue = shapeValue;
        return this;
    }

    /**
     * Enables event streaming support.
     *
     * @param eventStreamEncodingFactory a factory for event stream encoding.
     * @return Returns the serializer.
     */
    public <F extends Frame<?>> RequestSerializer eventEncoderFactory(
            EventEncoderFactory<F> eventStreamEncodingFactory
    ) {
        this.eventStreamEncodingFactory = eventStreamEncodingFactory;
        return this;
    }

    /**
     * Set to true to not serialize any payload when no members are part of the body or bound to the payload.
     *
     * @param omitEmptyPayload True to omit an empty payload.
     * @return the serializer.
     */
    public RequestSerializer omitEmptyPayload(boolean omitEmptyPayload) {
        this.omitEmptyPayload = omitEmptyPayload;
        return this;
    }

    public RequestSerializer allowEmptyStructPayload(boolean allowEmptyStructPayload) {
        this.allowEmptyStructPayload = allowEmptyStructPayload;
        return this;
    }

    /**
     * Finishes setting up the serializer and creates an HTTP request.
     *
     * @return Returns the created request.
     */
    @SuppressWarnings("unchecked")
    public HttpRequest serializeRequest() {
        Objects.requireNonNull(shapeValue, "shapeValue is not set");
        Objects.requireNonNull(operation, "operation is not set");
        Objects.requireNonNull(payloadCodec, "payloadCodec is not set");
        Objects.requireNonNull(endpoint, "endpoint is not set");
        Objects.requireNonNull(payloadMediaType, "payloadMediaType is not set");

        var matcher = bindingCache.computeIfAbsent(operation.inputSchema(), BindingMatcher::requestMatcher);
        var httpTrait = operation.schema().expectTrait(TraitKey.HTTP_TRAIT);
        var serializer = new HttpBindingSerializer(
                httpTrait,
                payloadCodec,
                payloadMediaType,
                matcher,
                omitEmptyPayload,
                false,
                allowEmptyStructPayload);
        shapeValue.serialize(serializer);
        serializer.flush();

        var uriBuilder = URIBuilder.of(endpoint);

        // Append the path using simple concatenation, not using RFC 3986 resolution.
        uriBuilder.concatPath(serializer.getPath());

        if (serializer.hasQueryString()) {
            uriBuilder.query(serializer.getQueryString());
        }

        var targetEndpoint = uriBuilder.build();

        HttpRequest.Builder builder = HttpRequest.builder()
                .method(httpTrait.getMethod())
                .uri(targetEndpoint);

        var eventStream = serializer.getEventStream();
        if (eventStream != null && operation instanceof InputEventStreamingApiOperation<?, ?, ?>) {
            InternalEventStreamWriter<SerializableStruct, SerializableStruct, Frame<?>> writer =
                    InternalEventStreamWriter.toInternal(eventStream);

            writer.bootstrap(new InternalEventStreamWriter.Bootstrap<>() {
                @Override
                @SuppressWarnings("unchecked")
                public EventEncoderFactory<Frame<?>> encoder() {
                    return (EventEncoderFactory) eventStreamEncodingFactory;
                }

                @Override
                public SerializableStruct initialEvent() {
                    return null;
                }
            });
            builder.body(writer.toDataStream());
            serializer.setContentType(eventStreamEncodingFactory.contentType());
        } else if (serializer.hasBody()) {
            builder.body(serializer.getBody());
        }

        return builder.headers(serializer.getHeaders()).build();
    }

    private static <T> T doCast(Class<T> xclass, Object value) {
        return xclass.cast(value);
    }
}
