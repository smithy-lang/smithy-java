/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.event.EventDecoderFactory;
import software.amazon.smithy.java.runtime.core.serde.event.Frame;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;

/**
 * Deserializes the HTTP request of an operation that uses HTTP bindings into a builder.
 */
public final class RequestDeserializer {

    private final HttpBindingDeserializer.Builder deserBuilder = HttpBindingDeserializer.builder().request(true);
    private ShapeBuilder<?> inputShapeBuilder;
    private ShapeBuilder<? extends ModeledApiException> errorShapeBuilder;

    RequestDeserializer() {}

    /**
     * Codec to use in the payload of requests.
     *
     * @param payloadCodec Payload codec.
     * @return Returns the deserializer.
     */
    public RequestDeserializer payloadCodec(Codec payloadCodec) {
        deserBuilder.payloadCodec(payloadCodec);
        return this;
    }

    /**
     * HTTP request to deserialize.
     *
     * @param request Request to deserialize into the builder.
     * @return Returns the deserializer.
     */
    public RequestDeserializer request(SmithyHttpRequest request) {
        DataStream bodyDataStream = bodyDataStream(request);
        deserBuilder.headers(request.headers())
            .requestRawQueryString(request.uri().getRawQuery())
            .body(bodyDataStream)
            .shapeBuilder(inputShapeBuilder);
        return this;
    }

    private DataStream bodyDataStream(SmithyHttpRequest request) {
        if (request.body() instanceof DataStream ds) {
            return ds;
        }
        var contentType = request.headers().firstValue("content-type").orElse(null);
        var contentLength = request.headers().firstValue("content-length").map(Long::valueOf).orElse(-1L);
        return DataStream.ofPublisher(request.body(), contentType, contentLength);
    }

    /**
     * Input shape builder to populate from the request.
     *
     * @param inputShapeBuilder Output shape builder.
     * @return Returns the deserializer.
     */
    public RequestDeserializer inputShapeBuilder(ShapeBuilder<?> inputShapeBuilder) {
        this.inputShapeBuilder = inputShapeBuilder;
        errorShapeBuilder = null;
        return this;
    }

    /**
     * Enables input event decoding.
     *
     * @param eventDecoderFactory event decoding support
     * @return Returns the deserializer.
     */
    public <F extends Frame<?>> RequestDeserializer eventDecoderFactory(EventDecoderFactory<F> eventDecoderFactory) {
        deserBuilder.eventDecoderFactory(eventDecoderFactory);
        return this;
    }


    public RequestDeserializer pathLabelValues(Map<String, String> labelValues) {
        deserBuilder.requestPathLabels(labelValues);
        return this;
    }

    /**
     * Error shape builder to populate from the request.
     *
     * @param errorShapeBuilder Error shape builder.
     * @return Returns the deserializer.
     */
    public RequestDeserializer errorShapeBuilder(ShapeBuilder<? extends ModeledApiException> errorShapeBuilder) {
        this.errorShapeBuilder = errorShapeBuilder;
        inputShapeBuilder = null;
        return this;
    }

    /**
     * Finish setting up and deserialize the response into the builder.
     */
    public CompletableFuture<Void> deserialize() {
        if (errorShapeBuilder == null && inputShapeBuilder == null) {
            throw new IllegalStateException("Either errorShapeBuilder or outputShapeBuilder must be set");
        }

        HttpBindingDeserializer deserializer = deserBuilder.build();

        if (inputShapeBuilder != null) {
            inputShapeBuilder.deserialize(deserializer);
        } else {
            errorShapeBuilder.deserialize(deserializer);
        }

        return deserializer.completeBodyDeserialization();
    }
}
