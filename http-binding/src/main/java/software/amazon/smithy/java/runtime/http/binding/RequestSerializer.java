/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.net.URI;
import java.util.Objects;
import software.amazon.smithy.java.runtime.common.uri.URIBuilder;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.event.EventEncoderFactory;
import software.amazon.smithy.java.runtime.core.serde.event.EventStreamFrameEncodingProcessor;
import software.amazon.smithy.java.runtime.core.serde.event.Frame;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.model.traits.HttpTrait;

/**
 * Serializes an HTTP request from an input shape that uses HTTP binding traits.
 */
public final class RequestSerializer {

    private Codec payloadCodec;
    private ApiOperation<?, ?> operation;
    private URI endpoint;
    private SerializableShape shapeValue;
    private EventEncoderFactory<?> eventStreamEncodingFactory;
    private final BindingMatcher bindingMatcher = BindingMatcher.requestMatcher();

    RequestSerializer() {}

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
     * Finishes setting up the serializer and creates an HTTP request.
     *
     * @return Returns the created request.
     */
    public SmithyHttpRequest serializeRequest() {
        Objects.requireNonNull(shapeValue, "shapeValue is not set");
        Objects.requireNonNull(operation, "operation is not set");
        Objects.requireNonNull(payloadCodec, "payloadCodec is not set");
        Objects.requireNonNull(payloadCodec, "endpoint is not set");
        Objects.requireNonNull(payloadCodec, "value is not set");

        var httpTrait = operation.schema().expectTrait(HttpTrait.class);
        var serializer = new HttpBindingSerializer(httpTrait, payloadCodec, bindingMatcher);
        shapeValue.serialize(serializer);
        serializer.flush();

        var uriBuilder = URIBuilder.of(endpoint);

        // Append the path using simple concatenation, not using RFC 3986 resolution.
        uriBuilder.concatPath(serializer.getPath());

        if (serializer.hasQueryString()) {
            uriBuilder.query(serializer.getQueryString());
        }

        var targetEndpoint = uriBuilder.build();

        SmithyHttpRequest.Builder builder = SmithyHttpRequest.builder()
            .method(httpTrait.getMethod())
            .uri(targetEndpoint);

        var eventStream = serializer.getEventStream();
        if (eventStream != null && operation instanceof InputEventStreamingApiOperation<?, ?, ?>) {
            builder.body(EventStreamFrameEncodingProcessor.create(eventStream, eventStreamEncodingFactory));
            serializer.setContentType(eventStreamEncodingFactory.contentType());
        } else if (serializer.hasBody()) {
            builder.body(serializer.getBody());
        }

        return builder.headers(serializer.getHeaders()).build();
    }
}
